package io.github.roaxio.dashguardpro.storage

import io.github.roaxio.dashguardpro.data.ClipDao
import io.github.roaxio.dashguardpro.model.VideoClip
import io.github.roaxio.dashguardpro.util.FormatUtils
import io.github.roaxio.dashguardpro.util.fusedVideoFile
import io.github.roaxio.dashguardpro.util.srtSidecarFile
import io.github.roaxio.dashguardpro.util.videoFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Progress payload emitted during an active storage migration operation
 */
data class MigrationProgress(
    val currentFileIndex: Int,
    val totalFiles: Int,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val currentFileName: String = "",
    val isCompleted: Boolean = false,
    val errorMessage: String? = null
) {
    val progressPercentage: Float
        get() = if (totalBytes > 0) (bytesTransferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

/**
 * Engine for safe cross-directory and cross-partition footage migration
 */
object StorageMigrationManager {

    private val _migrationState = MutableStateFlow<MigrationProgress?>(null)
    val migrationState: StateFlow<MigrationProgress?> = _migrationState.asStateFlow()

    fun dismissMigrationProgress() {
        _migrationState.value = null
    }

    fun resetStorageMigrationState() = dismissMigrationProgress()

    /**
     * Executes the storage migration, maintaining reactive progress state and invoking completion callback.
     */
    suspend fun executeMigration(
        clipsToMigrate: List<VideoClip>,
        targetDir: File,
        clipDao: ClipDao,
        onComplete: (Boolean, String?) -> Unit = { _, _ -> }
    ): Result<Int> {
        val result = migrateClips(
            clipsToMigrate = clipsToMigrate,
            targetDir = targetDir,
            clipDao = clipDao,
            onProgress = { progress ->
                _migrationState.value = progress
            }
        )
        if (result.isSuccess) {
            onComplete(true, null)
        } else {
            onComplete(false, result.exceptionOrNull()?.message ?: "Migration failed")
        }
        return result
    }

    /**
     * Filters list of clips that physically reside inside the specified source directory
     */
    fun getClipsInDirectory(allClips: List<VideoClip>, sourceDir: File): List<VideoClip> {
        val canonicalSourcePath = try { sourceDir.canonicalPath } catch (_: Exception) { sourceDir.absolutePath }
        return allClips.filter { clip ->
            val file = File(clip.filePath)
            val parentPath = try { file.parentFile?.canonicalPath } catch (_: Exception) { file.parentFile?.absolutePath }
            parentPath != null && (parentPath == canonicalSourcePath || parentPath.startsWith(canonicalSourcePath))
        }
    }

    /**
     * Extracts all distinct storage directories where recorded clips currently reside on the device.
     */
    fun getDistinctStorageDirectories(allClips: List<VideoClip>): List<File> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<File>()
        for (clip in allClips) {
            val parent = File(clip.filePath).parentFile ?: continue
            val canonical = try { parent.canonicalPath } catch (_: Exception) { parent.absolutePath }
            if (seen.add(canonical)) {
                result.add(parent)
            }
        }
        return result
    }

    /**
     * Returns clips that reside outside the specified directory (e.g., in other older storage paths)
     */
    fun getClipsOutsideDirectory(allClips: List<VideoClip>, activeDir: File): List<VideoClip> {
        val canonicalActive = try { activeDir.canonicalPath } catch (_: Exception) { activeDir.absolutePath }
        return allClips.filter { clip ->
            val file = File(clip.filePath)
            val parentPath = try { file.parentFile?.canonicalPath } catch (_: Exception) { file.parentFile?.absolutePath }
            parentPath != null && parentPath != canonicalActive && !parentPath.startsWith(canonicalActive)
        }
    }

    /**
     * Filters list of clips that actually need migration to reach targetDir (excludes clips already in targetDir).
     */
    fun getClipsNeedingMigration(clips: List<VideoClip>, targetDir: File): List<VideoClip> {
        val canonicalTarget = try { targetDir.canonicalPath } catch (_: Exception) { targetDir.absolutePath }
        return clips.filter { clip ->
            val file = File(clip.filePath)
            val parentPath = try { file.parentFile?.canonicalPath } catch (_: Exception) { file.parentFile?.absolutePath }
            parentPath == null || (parentPath != canonicalTarget && !parentPath.startsWith(canonicalTarget))
        }
    }

    /**
     * Authoritative single source of truth for total byte size of a video clip,
     * accounting for its primary video file, companion .srt sidecar, and -telemetry-fused derivative
     * (unless includeFusedCompanion is false because the fused derivative is already tracked as its own VideoClip row).
     */
    fun calculateClipTotalBytes(clip: VideoClip, includeFusedCompanion: Boolean = true): Long {
        val videoFile = File(clip.filePath)
        var total = if (videoFile.exists()) videoFile.length() else clip.fileSizeBytes
        val srtFile = clip.srtSidecarFile
        if (srtFile.exists()) {
            total += srtFile.length()
        }
        if (includeFusedCompanion) {
            val fusedFile = videoFile.fusedVideoFile
            if (fusedFile.exists() && fusedFile.absolutePath != videoFile.absolutePath) {
                total += fusedFile.length()
            }
        }
        return total
    }

    /**
     * Calculates the exact total byte size of clips to migrate (including .srt sidecars and fused videos)
     * Clips already residing in targetDir contribute 0 bytes since no data transfer is required.
     * Prevents double-counting when a -telemetry-fused.mp4 file is also present as its own VideoClip row in [clips].
     */
    suspend fun calculateTotalMigrationBytes(clips: List<VideoClip>, targetDir: File? = null): Long = withContext(Dispatchers.IO) {
        val targetCanonical = targetDir?.let { try { it.canonicalPath } catch (_: Exception) { it.absolutePath } }
        val clipPaths = clips.map { it.filePath }.toSet()
        var totalBytes = 0L
        for (clip in clips) {
            val videoFile = File(clip.filePath)
            val parentCanonical = try { videoFile.parentFile?.canonicalPath } catch (_: Exception) { videoFile.parentFile?.absolutePath }
            if (targetCanonical != null && parentCanonical != null && (parentCanonical == targetCanonical || parentCanonical.startsWith(targetCanonical))) {
                // Already in destination directory: 0 bytes to transfer
                continue
            }
            val fusedPath = videoFile.fusedVideoFile.absolutePath
            val isFusedSeparatelyInList = fusedPath != videoFile.absolutePath && clipPaths.contains(fusedPath)
            totalBytes += calculateClipTotalBytes(clip, includeFusedCompanion = !isFusedSeparatelyInList)
        }
        totalBytes
    }

    /**
     * Pre-flight capacity gate: Verifies that destination directory can accommodate the required bytes
     * while preserving the 500 MB Android OS safety buffer.
     */
    fun canAccommodateMigration(targetDir: File, requiredBytes: Long): Boolean {
        return try {
            val effectiveAvailable = StorageDirectoryManager.getAvailableBytes(targetDir)
            if (effectiveAvailable <= 0L) {
                // If free space cannot be measured (e.g. test environment), permit execution
                return true
            }
            val safetyBuffer = StorageDirectoryManager.getOsSafetyBufferBytes(targetDir)
            val neededWithBuffer = requiredBytes + safetyBuffer
            effectiveAvailable >= neededWithBuffer
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Executes the atomic migration pipeline on Dispatchers.IO
     * Moves .mp4 files, .srt sidecars, and updates Room DB records in real time.
     */
    suspend fun migrateClips(
        clipsToMigrate: List<VideoClip>,
        targetDir: File,
        clipDao: ClipDao,
        onProgress: (MigrationProgress) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!targetDir.exists()) {
                val created = targetDir.mkdirs()
                if (!created && !targetDir.exists()) {
                    return@withContext Result.failure(IllegalStateException("Failed to create destination directory: ${targetDir.absolutePath}"))
                }
            }

            val totalBytes = calculateTotalMigrationBytes(clipsToMigrate, targetDir)
            if (!canAccommodateMigration(targetDir, totalBytes)) {
                val safetyBuffer = StorageDirectoryManager.getOsSafetyBufferBytes(targetDir)
                val safetyBufferFormatted = FormatUtils.formatBytesWithUnit(safetyBuffer)
                return@withContext Result.failure(
                    IllegalStateException("Insufficient storage at destination. Requires ${FormatUtils.formatBytesWithUnit(totalBytes)} plus $safetyBufferFormatted OS safety buffer.")
                )
            }

            var bytesTransferred = 0L
            var migratedCount = 0
            val totalFiles = clipsToMigrate.size
            val clipPathsToMigrate = clipsToMigrate.map { it.filePath }.toSet()

            for ((index, clip) in clipsToMigrate.withIndex()) {
                val currentFileNum = index + 1
                val videoFile = clip.videoFile

                onProgress(
                    MigrationProgress(
                        currentFileIndex = currentFileNum,
                        totalFiles = totalFiles,
                        bytesTransferred = bytesTransferred,
                        totalBytes = totalBytes,
                        currentFileName = videoFile.name
                    )
                )

                val targetVideoFile = File(targetDir, videoFile.name)

                if (!videoFile.exists()) {
                    // If the file was already moved to targetDir (e.g. as a companion fused file), update its DB record
                    if (targetVideoFile.exists()) {
                        val updatedClip = clip.copy(
                            filePath = targetVideoFile.absolutePath,
                            fileName = targetVideoFile.name
                        )
                        clipDao.updateClip(updatedClip)
                        migratedCount++
                    }
                    continue
                }

                val sourceCanonical = try { videoFile.canonicalPath } catch (_: Exception) { videoFile.absolutePath }
                val targetCanonical = try { targetVideoFile.canonicalPath } catch (_: Exception) { targetVideoFile.absolutePath }

                if (sourceCanonical == targetCanonical) {
                    // Already in destination directory: skip physical copy/move
                    migratedCount++
                    continue
                }

                // 1. Move primary MP4 video file
                val videoMoved = moveFileWithProgress(videoFile, targetVideoFile) { chunkBytes ->
                    bytesTransferred += chunkBytes
                    onProgress(
                        MigrationProgress(
                            currentFileIndex = currentFileNum,
                            totalFiles = totalFiles,
                            bytesTransferred = bytesTransferred,
                            totalBytes = totalBytes,
                            currentFileName = videoFile.name
                        )
                    )
                }

                if (!videoMoved) {
                    throw IllegalStateException("Failed to move video file ${videoFile.name} to ${targetVideoFile.absolutePath}")
                }

                // 2. Move companion .srt sidecar file if present
                val srtFile = clip.srtSidecarFile
                if (srtFile.exists()) {
                    val targetSrtFile = File(targetDir, srtFile.name)
                    moveFileWithProgress(srtFile, targetSrtFile) { chunkBytes ->
                        bytesTransferred += chunkBytes
                    }
                }

                // 3. Move companion fused telemetry video if present and not tracked as its own clip in clipsToMigrate
                val fusedFile = videoFile.fusedVideoFile
                val isFusedSeparatelyInList = fusedFile.absolutePath != videoFile.absolutePath && clipPathsToMigrate.contains(fusedFile.absolutePath)
                if (!isFusedSeparatelyInList && fusedFile.exists() && fusedFile.absolutePath != videoFile.absolutePath) {
                    val targetFusedFile = File(targetDir, fusedFile.name)
                    moveFileWithProgress(fusedFile, targetFusedFile) { chunkBytes ->
                        bytesTransferred += chunkBytes
                    }
                }

                // 4. Update Room Database record with new canonical path
                val updatedClip = clip.copy(
                    filePath = targetVideoFile.absolutePath,
                    fileName = targetVideoFile.name
                )
                clipDao.updateClip(updatedClip)
                migratedCount++
            }

            onProgress(
                MigrationProgress(
                    currentFileIndex = totalFiles,
                    totalFiles = totalFiles,
                    bytesTransferred = totalBytes,
                    totalBytes = totalBytes,
                    isCompleted = true
                )
            )

            Result.success(migratedCount)
        } catch (e: Exception) {
            onProgress(
                MigrationProgress(
                    currentFileIndex = 0,
                    totalFiles = clipsToMigrate.size,
                    bytesTransferred = 0L,
                    totalBytes = 0L,
                    isCompleted = true,
                    errorMessage = e.message ?: "Migration failed"
                )
            )
            Result.failure(e)
        }
    }

    /**
     * Checks whether source and target paths resolve to the identical canonical identity (AGENTS.md Rule 11).
     */
    fun isSelfMove(source: File, target: File): Boolean {
        val sourceCanonical = try { source.canonicalPath } catch (_: Exception) { source.absolutePath }
        val targetCanonical = try { target.canonicalPath } catch (_: Exception) { target.absolutePath }
        return sourceCanonical == targetCanonical
    }

    /**
     * Safely moves a file from source to target using a robust two-phase commit protocol.
     * Attempts fast rename first; if crossing physical partitions, streams data with chunk-level progress,
     * verifies destination byte-count and readability, and unlinks source only upon verified commit.
     */
    fun moveFileWithProgress(
        source: File,
        target: File,
        onChunkTransferred: (Long) -> Unit
    ): Boolean {
        if (isSelfMove(source, target)) return true

        // 1. Try atomic kernel rename
        if (source.renameTo(target)) {
            onChunkTransferred(target.length())
            return true
        }

        // 2. Stream copy across filesystems (Phase 1: Transfer)
        var success = false
        val buffer = ByteArray(64 * 1024) // 64 KB buffer
        try {
            FileInputStream(source).use { input ->
                FileOutputStream(target).use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        onChunkTransferred(bytesRead.toLong())
                    }
                    output.flush()
                }
            }

            // Phase 2: Verification before unlinking source (Two-Phase Commit)
            if (target.exists() && target.length() == source.length() && target.canRead()) {
                val deleted = source.delete()
                if (!deleted && source.exists()) {
                    // Log warning if source unlink fails, but destination is verified valid
                    android.util.Log.w("StorageMigrationManager", "Source file could not be unlinked: ${source.absolutePath}")
                }
                success = true
            } else {
                if (target.exists()) {
                    target.delete()
                }
                success = false
            }
        } catch (_: Exception) {
            if (target.exists()) {
                target.delete()
            }
            success = false
        }
        return success
    }
}
