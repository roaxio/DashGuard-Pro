package io.github.roaxio.dashguardpro.storage

import android.content.Context
import android.os.Environment
import io.github.roaxio.dashguardpro.data.ClipDao
import io.github.roaxio.dashguardpro.data.SettingsRepository
import io.github.roaxio.dashguardpro.data.settings.StorageLocationType
import io.github.roaxio.dashguardpro.model.VideoClip
import io.github.roaxio.dashguardpro.util.fusedVideoFile
import io.github.roaxio.dashguardpro.util.isDashGuardRecordingFile
import io.github.roaxio.dashguardpro.util.rawSourceVideoFile
import io.github.roaxio.dashguardpro.util.srtSidecarFile
import io.github.roaxio.dashguardpro.util.videoFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StorageStats(
    val appVideoBytes: Long = 0,
    val lockedVideoBytes: Long = 0,
    val loopBufferBytes: Long = 0,
    val deviceFreeBytes: Long = 0,
    val deviceTotalBytes: Long = 0,
    val maxAllowedBytes: Long = 10L * 1024 * 1024 * 1024
)

class LoopStorageManager(
    private val context: Context,
    private val clipDao: ClipDao,
    private val settingsRepository: SettingsRepository? = null
) {
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    val defaultAppVideosDir: File
        get() = StorageDirectoryManager.getDefaultAppStorageDirectory(context)

    val videosDir: File
        get() {
            val settings = settingsRepository?.settings?.value
            val locationType = settings?.storageLocationType ?: StorageLocationType.DEFAULT_APP_DATA
            val customPath = settings?.customStoragePath
            return StorageDirectoryManager.resolveStorageDirectory(context, locationType, customPath)
        }

    val snapshotsDir: File
        get() {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir, "dashguard_snapshots")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    private var lastTimestampMillis = 0L

    @Synchronized
    fun createNewVideoFileWithTimestamp(prefix: String = "LOOP"): Pair<File, Long> {
        var now = System.currentTimeMillis()
        if (now <= lastTimestampMillis) {
            now = lastTimestampMillis + 1000L // Strictly advancing monotonic timestamp
        }
        lastTimestampMillis = now

        val targetDir = videosDir
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now))
        val baseName = "VID_${prefix}_${timestamp}"
        var candidateName = "$baseName.mp4"
        var candidateFile = File(targetDir, candidateName)
        var counter = 1

        val existingFileNames = targetDir.list()?.toSet() ?: emptySet()
        while (candidateFile.exists() || existingFileNames.contains(candidateName)) {
            candidateName = "${baseName}_${counter}.mp4"
            candidateFile = File(targetDir, candidateName)
            counter++
        }

        return Pair(candidateFile, now)
    }

    @Synchronized
    fun createNewVideoFile(prefix: String = "LOOP"): File {
        return createNewVideoFileWithTimestamp(prefix).first
    }

    @Synchronized
    fun createNewSnapshotFile(prefix: String = "SNAP"): File {
        var now = System.currentTimeMillis()
        if (now <= lastTimestampMillis) {
            now = lastTimestampMillis + 1000L
        }
        lastTimestampMillis = now

        val targetDir = snapshotsDir
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now))
        val baseName = "IMG_${prefix}_${timestamp}"
        var candidateName = "$baseName.jpg"
        var candidateFile = File(targetDir, candidateName)
        var counter = 1

        val existingFileNames = targetDir.list()?.toSet() ?: emptySet()
        while (candidateFile.exists() || existingFileNames.contains(candidateName)) {
            candidateName = "${baseName}_${counter}.jpg"
            candidateFile = File(targetDir, candidateName)
            counter++
        }

        try {
            candidateFile.createNewFile()
        } catch (_: Exception) {}

        return candidateFile
    }

    /**
     * Canonical deletion of a video clip, its companion subtitle (.srt), and any fused derivatives.
     * Symmetrically removes files from disk, purges Room DB record, and returns true if deleted, false if protected/locked.
     */
    suspend fun deleteClipAndSidecars(clip: VideoClip, forceDeleteLocked: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (clip.isLocked && !forceDeleteLocked) {
            return@withContext false
        }

        val file = clip.videoFile
        if (file.exists()) {
            file.delete()
        }

        val srtFile = clip.srtSidecarFile
        if (srtFile.exists()) {
            srtFile.delete()
        }

        val fusedFile = clip.fusedVideoFile
        if (fusedFile.absolutePath != file.absolutePath) {
            val fusedClipRecord = try { clipDao.getClipByPath(fusedFile.absolutePath) } catch (_: Exception) { null }
            val canDeleteFused = fusedClipRecord == null || !fusedClipRecord.isLocked || forceDeleteLocked
            if (canDeleteFused) {
                if (fusedFile.exists()) {
                    fusedFile.delete()
                }
                if (fusedClipRecord != null) {
                    try { clipDao.deleteClip(fusedClipRecord) } catch (_: Exception) {}
                }
            }
        }

        clipDao.deleteClip(clip)
        true
    }

    suspend fun getStorageStats(maxAllowedGb: Int): StorageStats = withContext(Dispatchers.IO) {
        var diskAppTotal = 0L
        val dir = videosDir
        val files = dir.listFiles() ?: emptyArray()
        for (f in files) {
            if (f.isDashGuardRecordingFile) {
                diskAppTotal += f.length()
            }
        }

        // Calculate DB clips aggregate scoped strictly to clips belonging to the active storage directory
        val allClips = try {
            clipDao.getAllClipsList()
        } catch (_: Exception) {
            emptyList<VideoClip>()
        }

        val activeDirClips = StorageMigrationManager.getClipsInDirectory(allClips, dir)
        val activeClipPaths = activeDirClips.map { it.filePath }.toSet()

        var lockedTotal = 0L
        var loopBuffer = 0L

        for (clip in activeDirClips) {
            val fusedPath = clip.videoFile.fusedVideoFile.absolutePath
            val isFusedSeparatelyInList = fusedPath != clip.filePath && activeClipPaths.contains(fusedPath)
            val clipBytes = StorageMigrationManager.calculateClipTotalBytes(clip, includeFusedCompanion = !isFusedSeparatelyInList)
            if (clip.isLocked) {
                lockedTotal += clipBytes
            } else {
                loopBuffer += clipBytes
            }
        }

        val clipsTotal = lockedTotal + loopBuffer
        val appTotal = maxOf(diskAppTotal, clipsTotal)
        val effectiveLoopBuffer = (appTotal - lockedTotal).coerceAtLeast(0L)

        val freeBytes = StorageDirectoryManager.getAvailableBytes(dir)
        val totalBytes = StorageDirectoryManager.getTotalBytes(dir)
        val maxDirGb = StorageDirectoryManager.getMaxAssignableStorageGb(dir, appTotal)
        val effectiveLimitGb = if (maxDirGb > 0) maxAllowedGb.coerceAtMost(maxDirGb).coerceAtLeast(1) else 0
        val maxAllowed = effectiveLimitGb.toLong() * 1024L * 1024L * 1024L

        StorageStats(
            appVideoBytes = appTotal,
            lockedVideoBytes = lockedTotal,
            loopBufferBytes = loopBuffer,
            deviceFreeBytes = freeBytes,
            deviceTotalBytes = totalBytes,
            maxAllowedBytes = maxAllowed
        )
    }

    suspend fun performLoopCleanupIfNeeded(maxAllowedGb: Int): Boolean = withContext(Dispatchers.IO) {
        val dir = videosDir
        if (!dir.exists()) return@withContext false

        var currentVideoBytes = 0L
        val diskFiles = dir.listFiles() ?: emptyArray()
        for (f in diskFiles) {
            currentVideoBytes += f.length()
        }

        val maxDirGb = StorageDirectoryManager.getMaxAssignableStorageGb(dir, currentVideoBytes)
        val effectiveLimitGb = if (maxDirGb > 0) maxAllowedGb.coerceAtMost(maxDirGb).coerceAtLeast(1) else 0
        val maxAllowedBytes = effectiveLimitGb.toLong() * 1024L * 1024L * 1024L

        val deviceFree = StorageDirectoryManager.getAvailableBytes(dir)
        val osSafetyBuffer = StorageDirectoryManager.getOsSafetyBufferBytes(dir)

        val needsCleanup = (maxAllowedBytes > 0L && currentVideoBytes > maxAllowedBytes) || deviceFree < osSafetyBuffer
        if (!needsCleanup) return@withContext true

        // Retrieve all locked clips to ensure strict protection of videos and sidecar .srt files
        val allDbClips = try { clipDao.getAllClipsList() } catch (_: Exception) { emptyList<VideoClip>() }
        val lockedBaseNames = allDbClips
            .filter { it.isLocked }
            .map { it.videoFile.rawSourceVideoFile.nameWithoutExtension }
            .toSet()

        // 1. First pass: Delete oldest UNLOCKED clips from database along with their matching .srt files
        // Strictly scoped to clips physically residing within the active storage directory (dir).
        // Historical footage residing in older/other directories is safely preserved and never deleted by active quota reclamation.
        val unlockedClips = StorageMigrationManager.getClipsInDirectory(clipDao.getUnlockedClipsOldestFirst(), dir)
        for (clip in unlockedClips) {
            // STRICT SAFETY: Never delete if marked locked
            if (clip.isLocked) continue

            val baseName = clip.videoFile.rawSourceVideoFile.nameWithoutExtension
            if (lockedBaseNames.contains(baseName)) continue

            val file = clip.videoFile
            val srtFile = clip.srtSidecarFile
            val fusedFile = clip.fusedVideoFile

            val freedBytes = (if (file.exists()) file.length() else 0L) +
                             (if (srtFile.exists()) srtFile.length() else 0L) +
                             (if (fusedFile.exists()) fusedFile.length() else 0L)

            deleteClipAndSidecars(clip, forceDeleteLocked = false)
            currentVideoBytes -= freedBytes

            val currentFree = StorageDirectoryManager.getAvailableBytes(dir)
            if ((maxAllowedBytes == 0L || currentVideoBytes <= maxAllowedBytes) && currentFree >= osSafetyBuffer) {
                return@withContext true
            }
        }

        // 2. Second pass: Purge any orphaned or untracked non-locked files on disk
        val remainingFiles = (dir.listFiles() ?: emptyArray()).sortedBy { it.lastModified() }
        for (file in remainingFiles) {
            val baseName = file.rawSourceVideoFile.nameWithoutExtension

            // NEVER delete files belonging to locked clips or created/modified within the last 120 seconds
            if (lockedBaseNames.contains(baseName) || (System.currentTimeMillis() - file.lastModified() < 120_000L)) {
                android.util.Log.d("SentryFix", "LoopStorageManager: SKIP protected/recent file ${file.name}")
                continue
            }

            // Strictly protect non-app files: only purge orphaned DashGuard files
            if (file.isDashGuardRecordingFile) {
                val size = file.length()
                val deleted = file.delete()
                if (deleted) {
                    currentVideoBytes -= size
                }
            }

            val currentFree = StorageDirectoryManager.getAvailableBytes(dir)
            if ((maxAllowedBytes == 0L || currentVideoBytes <= maxAllowedBytes) && currentFree >= osSafetyBuffer) {
                return@withContext true
            }
        }

        val finalFree = StorageDirectoryManager.getAvailableBytes(dir)
        val isWithinQuota = maxAllowedBytes == 0L || currentVideoBytes <= maxAllowedBytes
        val hasBuffer = finalFree >= osSafetyBuffer
        return@withContext isWithinQuota && hasBuffer
    }
}
