package io.github.roaxio.dashguardpro.storage

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.core.content.ContextCompat
import io.github.roaxio.dashguardpro.data.settings.StorageLocationType
import java.io.File

/**
 * Metadata for a physical or logical storage option on the device
 */
data class StorageOptionInfo(
    val type: StorageLocationType,
    val title: String,
    val description: String,
    val resolvedPath: String,
    val freeBytes: Long,
    val totalBytes: Long,
    val isWritable: Boolean,
    val isAvailable: Boolean
) {
    val freeSpaceFormatted: String
        get() = io.github.roaxio.dashguardpro.util.FormatUtils.formatStorageCapacityFree(freeBytes)

    val totalSpaceFormatted: String
        get() = io.github.roaxio.dashguardpro.util.FormatUtils.formatStorageCapacityTotal(totalBytes)
}

/**
 * Dynamic Storage Directory Resolver and In-App Folder Explorer
 */
object StorageDirectoryManager {

    /**
     * Returns required storage permissions based on the current Android version
     */
    fun getRequiredStoragePermissions(): List<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                listOf(
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_IMAGES
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                listOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            }
            else -> {
                listOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
        }
    }

    /**
     * Checks if storage permissions are granted
     */
    fun hasStoragePermission(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        } catch (_: Throwable) {
            true
        }
    }

    /**
     * Gets the default guaranteed writable app-specific storage directory
     */
    fun getDefaultAppStorageDirectory(context: Context): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir, "dashguard_videos")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Dynamically reads all available storage location options on the host device (Default vs Custom)
     */
    fun getAvailableStorageOptions(context: Context, customPath: String? = null, customUri: String? = null): List<StorageOptionInfo> {
        val options = mutableListOf<StorageOptionInfo>()

        // 1. Default App Data Directory (Sandboxed external files: Always accessible on Android)
        val defaultDir = getDefaultAppStorageDirectory(context)
        createOptionInfo(
            type = StorageLocationType.DEFAULT_APP_DATA,
            title = "App Data Directory (Default)",
            description = "Sandboxed app storage. Guaranteed accessible and isolated.",
            dir = defaultDir
        )?.let { options.add(it) }

        // 2. Custom Storage Directory (User-chosen folder on phone storage / SD card)
        val hasCustomPath = !customPath.isNullOrBlank()
        val customDir = if (hasCustomPath) File(customPath!!) else defaultDir
        val freeBytes = getAvailableBytes(customDir)
        val totalBytes = getTotalBytes(customDir)
        val isWritable = try {
            if (!customDir.exists() && hasCustomPath) {
                customDir.mkdirs()
            }
            customDir.exists() && customDir.canWrite()
        } catch (_: Exception) {
            false
        }

        val customDisplayPath = when {
            hasCustomPath -> customPath!!
            !customUri.isNullOrBlank() -> {
                try {
                    val parsed = android.net.Uri.parse(customUri)
                    parsed.path ?: customUri
                } catch (_: Exception) {
                    customUri
                }
            }
            else -> "Not configured (Tap to select or enter path)"
        }

        options.add(
            StorageOptionInfo(
                type = StorageLocationType.CUSTOM_DIRECTORY,
                title = "Custom Storage Directory",
                description = "User-selected directory on device storage or SD card.",
                resolvedPath = customDisplayPath,
                freeBytes = freeBytes,
                totalBytes = totalBytes,
                isWritable = if (hasCustomPath) isWritable else true,
                isAvailable = true
            )
        )

        return options
    }

    /**
     * Resolves the actual File directory for a given setting configuration
     */
    fun resolveStorageDirectory(context: Context, type: StorageLocationType, customPath: String?): File {
        val defaultDir = getDefaultAppStorageDirectory(context)

        if (type == StorageLocationType.CUSTOM_DIRECTORY && !customPath.isNullOrBlank()) {
            val customDir = File(customPath)
            try {
                if (!customDir.exists()) {
                    customDir.mkdirs()
                }
            } catch (_: Exception) {}
            return customDir
        }

        return defaultDir
    }

    const val ONE_KB_BYTES: Long = 1024L
    const val ONE_MB_BYTES: Long = 1024L * 1024L
    const val ONE_GB_BYTES: Long = 1024L * 1024L * 1024L

    const val MIN_OS_SAFETY_BUFFER_BYTES: Long = 500L * ONE_MB_BYTES
    const val MAX_OS_SAFETY_BUFFER_BYTES: Long = 5L * ONE_GB_BYTES
    const val OS_SAFETY_BUFFER_RATIO: Double = 0.02

    /**
     * Default baseline 500 MB safety buffer preserved on device for the Android OS.
     */
    const val OS_SAFETY_BUFFER_BYTES: Long = MIN_OS_SAFETY_BUFFER_BYTES

    /**
     * Dynamically computes the OS safety buffer according to AGENTS.md Rule 10 (Dynamic Proportional Capacity):
     * osBuffer = clamp(500 MB, totalPartitionBytes * 0.02, 5 GB).
     */
    fun getOsSafetyBufferBytes(directory: File): Long {
        val total = getTotalBytes(directory)
        return calculateOsSafetyBufferBytes(total)
    }

    fun calculateOsSafetyBufferBytes(totalBytes: Long): Long {
        if (totalBytes <= 0L) return MIN_OS_SAFETY_BUFFER_BYTES
        val proportional = (totalBytes.toDouble() * OS_SAFETY_BUFFER_RATIO).toLong()
        return proportional.coerceIn(MIN_OS_SAFETY_BUFFER_BYTES, MAX_OS_SAFETY_BUFFER_BYTES)
    }

    /**
     * Encapsulates exact raw byte-level storage metrics directly from device StatFs and app disk usage.
     */
    data class StorageMetrics(
        val physicalFreeBytes: Long,
        val appUsedBytes: Long,
        val totalPoolBytes: Long,
        val osBufferBytes: Long,
        val usableSafeBytes: Long,
        val maxAssignableGb: Int
    ) {
        val physicalFreeFormatted: String
            get() = formatBytes(physicalFreeBytes)

        val appUsedFormatted: String
            get() = formatBytes(appUsedBytes)

        val totalPoolFormatted: String
            get() = formatBytes(totalPoolBytes)

        val osBufferFormatted: String
            get() = formatBytes(osBufferBytes)

        val usableSafeFormatted: String
            get() = formatBytes(usableSafeBytes)

        companion object {
            fun formatBytes(bytes: Long): String {
                return io.github.roaxio.dashguardpro.util.FormatUtils.formatBytesWithUnit(bytes)
            }
        }
    }

    /**
     * Authoritative calculation of available free space (in bytes) on the physical filesystem
     * hosting the specified directory, with fallback to usableSpace.
     */
    fun getAvailableBytes(directory: File): Long {
        return try {
            if (!directory.exists()) directory.mkdirs()
            val stat = StatFs(directory.path)
            val available = stat.availableBytes
            if (available > 0L) available else directory.usableSpace
        } catch (_: Exception) {
            try {
                directory.usableSpace
            } catch (_: Exception) {
                0L
            }
        }
    }

    /**
     * Authoritative calculation of total capacity (in bytes) on the physical filesystem
     * hosting the specified directory, with fallback to totalSpace.
     */
    fun getTotalBytes(directory: File): Long {
        return try {
            if (!directory.exists()) directory.mkdirs()
            val stat = StatFs(directory.path)
            val total = stat.totalBytes
            if (total > 0L) total else directory.totalSpace
        } catch (_: Exception) {
            try {
                directory.totalSpace
            } catch (_: Exception) {
                0L
            }
        }
    }

    /**
     * Computes exact raw byte metrics directly from physical disk StatFs and app video bytes.
     */
    fun getStorageMetrics(directory: File, appUsedBytes: Long = 0L): StorageMetrics {
        val osBuffer = getOsSafetyBufferBytes(directory)
        return try {
            val physicalFreeBytes = getAvailableBytes(directory)
            val totalPoolBytes = physicalFreeBytes + appUsedBytes.coerceAtLeast(0L)
            val usableSafeBytes = (totalPoolBytes - osBuffer).coerceAtLeast(0L)
            val maxGb = (usableSafeBytes / ONE_GB_BYTES).toInt().coerceAtLeast(0)
            StorageMetrics(
                physicalFreeBytes = physicalFreeBytes,
                appUsedBytes = appUsedBytes,
                totalPoolBytes = totalPoolBytes,
                osBufferBytes = osBuffer,
                usableSafeBytes = usableSafeBytes,
                maxAssignableGb = maxGb
            )
        } catch (_: Exception) {
            StorageMetrics(
                physicalFreeBytes = 0L,
                appUsedBytes = appUsedBytes,
                totalPoolBytes = appUsedBytes,
                osBufferBytes = osBuffer,
                usableSafeBytes = (appUsedBytes - osBuffer).coerceAtLeast(0L),
                maxAssignableGb = 10
            )
        }
    }

    /**
     * Checks if a target directory exists (or can be created) and is writable
     */
    fun isStorageWritable(directory: File): Boolean {
        return try {
            if (!directory.exists()) directory.mkdirs()
            directory.exists() && directory.canWrite()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Calculates the maximum safe assignable GB quota for a directory after reserving the 500MB OS safety buffer.
     * The assignable pool includes physical free space on disk PLUS storage currently occupied by the app's video files
     * (since older unlocked video files will be recycled and reclaimed as part of the loop storage pool).
     * Returns 0 if there is less than 1 GB of safe free storage remaining.
     */
    fun getMaxAssignableStorageGb(directory: File, appUsedBytes: Long = 0L): Int {
        return getStorageMetrics(directory, appUsedBytes).maxAssignableGb
    }

    /**
     * Generates dynamic quota options (in GB) based on the maximum capacity of the chosen directory
     */
    fun getDynamicQuotaOptions(maxGb: Int): List<Int> {
        if (maxGb <= 0) return emptyList()
        val standardSteps = listOf(1, 2, 3, 5, 8, 10, 15, 20, 30, 40, 50, 64, 80, 100, 128, 200, 256, 512)
        val filtered = standardSteps.filter { it < maxGb }.toMutableList()
        filtered.add(maxGb)
        return filtered.distinct().sorted()
    }

    /**
     * Lists subdirectories of a given directory for in-app viewfinder folder browsing
     */
    fun listSubdirectories(directory: File): List<File> {
        return try {
            if (!directory.exists() || !directory.isDirectory) return emptyList()
            directory.listFiles { file -> file.isDirectory && !file.name.startsWith(".") }
                ?.sortedBy { it.name.lowercase() }
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Creates a new subfolder in a directory
     */
    fun createSubdirectory(parent: File, folderName: String): Result<File> {
        val sanitized = folderName.trim().replace("/", "_").replace("\\", "_")
        if (sanitized.isBlank()) {
            return Result.failure(IllegalArgumentException("Folder name cannot be empty"))
        }
        val newFolder = File(parent, sanitized)
        return try {
            if (!newFolder.exists()) {
                val created = newFolder.mkdirs()
                if (created || newFolder.exists()) {
                    Result.success(newFolder)
                } else {
                    Result.failure(IllegalStateException("Could not create directory at ${newFolder.absolutePath}"))
                }
            } else {
                Result.success(newFolder)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Converts an Android SAF Tree Uri (e.g. content://.../tree/primary:MyFolder) to a canonical filesystem path
     */
    fun getPathFromTreeUri(context: Context, treeUri: android.net.Uri): String {
        try {
            val docId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            val split = docId.split(":")
            val type = split[0]
            val relativePath = if (split.size > 1) split[1] else ""

            if ("primary".equals(type, ignoreCase = true)) {
                val externalDir = Environment.getExternalStorageDirectory()?.absolutePath ?: "/storage/emulated/0"
                return if (relativePath.isNotBlank()) "$externalDir/$relativePath" else externalDir
            } else {
                // Check SD card volumes
                val externalDirs = context.getExternalFilesDirs(null)
                for (f in externalDirs) {
                    if (f != null) {
                        val path = f.absolutePath
                        val index = path.indexOf("/Android/data")
                        if (index > 0) {
                            val rootPath = path.substring(0, index)
                            if (rootPath.contains(type)) {
                                return if (relativePath.isNotBlank()) "$rootPath/$relativePath" else rootPath
                            }
                        }
                    }
                }
                return if (relativePath.isNotBlank()) "/storage/$type/$relativePath" else "/storage/$type"
            }
        } catch (_: Exception) {
            return treeUri.path ?: treeUri.toString()
        }
    }

    /**
     * Gets the initial root starting folder for device folder browsing (e.g. /storage/emulated/0)
     */
    fun getBrowserRootDirectory(): File {
        val external = Environment.getExternalStorageDirectory()
        if (external != null && external.exists() && external.canRead()) {
            return external
        }
        val emulated = File("/storage/emulated/0")
        if (emulated.exists() && emulated.canRead()) {
            return emulated
        }
        return external ?: File("/")
    }

    private fun createOptionInfo(
        type: StorageLocationType,
        title: String,
        description: String,
        dir: File
    ): StorageOptionInfo? {
        var isWritable = false
        var freeBytes = 0L
        var totalBytes = 0L
        var isAvailable = false

        try {
            if (!dir.exists()) {
                dir.mkdirs()
            }
            isAvailable = dir.exists()
            isWritable = dir.canWrite()

            if (!isAvailable) {
                return null
            }

            freeBytes = getAvailableBytes(dir)
            totalBytes = getTotalBytes(dir)
        } catch (_: Exception) {
            return null
        }

        return StorageOptionInfo(
            type = type,
            title = title,
            description = description,
            resolvedPath = dir.absolutePath,
            freeBytes = freeBytes,
            totalBytes = totalBytes,
            isWritable = isWritable,
            isAvailable = isAvailable
        )
    }
}
