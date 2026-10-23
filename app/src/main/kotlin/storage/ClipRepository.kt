package io.github.roaxio.dashguardpro.storage

import android.content.Context
import android.content.SharedPreferences
import io.github.roaxio.dashguardpro.analytics.TripGroupingEngine
import io.github.roaxio.dashguardpro.data.ClipDao
import io.github.roaxio.dashguardpro.data.SettingsRepository
import io.github.roaxio.dashguardpro.model.TripSession
import io.github.roaxio.dashguardpro.model.VideoClip
import io.github.roaxio.dashguardpro.media.TelemetrySubtitleManager
import io.github.roaxio.dashguardpro.util.isDashGuardRecordingFile
import io.github.roaxio.dashguardpro.util.rawSourceVideoFile
import io.github.roaxio.dashguardpro.util.srtSidecarFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Repository orchestrating video clip and trip persistence operations in Room Database.
 * Adheres to AGENTS.md Rule 7 (Vertical Slice Integrity) and Rule 11 (Domain Metric Authority).
 */
class ClipRepository(
    private val clipDao: ClipDao,
    private val settingsRepo: SettingsRepository,
    private val scope: CoroutineScope,
    private val context: Context? = null,
    tripTitlesPreferences: SharedPreferences? = null
) {
    private val tripTitlesPrefs: SharedPreferences? =
        tripTitlesPreferences ?: context?.getSharedPreferences("dashguard_trip_titles", Context.MODE_PRIVATE)

    private val _tripCustomTitles = MutableStateFlow<Map<String, String>>(loadTripCustomTitles())
    val tripCustomTitles: StateFlow<Map<String, String>> = _tripCustomTitles.asStateFlow()

    private fun loadTripCustomTitles(): Map<String, String> {
        val prefs = tripTitlesPrefs ?: return emptyMap()
        return prefs.all.mapNotNull { (key, value) ->
            if (value is String) key to value else null
        }.toMap()
    }

    fun saveTripCustomTitle(tripId: String, title: String) {
        val trimmed = title.trim()
        tripTitlesPrefs?.let { prefs ->
            if (trimmed.isEmpty()) {
                prefs.edit().remove(tripId).apply()
            } else {
                prefs.edit().putString(tripId, trimmed).apply()
            }
        }
        val current = _tripCustomTitles.value.toMutableMap()
        if (trimmed.isEmpty()) {
            current.remove(tripId)
        } else {
            current[tripId] = trimmed
        }
        _tripCustomTitles.value = current
    }

    fun getTripCustomTitle(tripId: String): String? {
        return _tripCustomTitles.value[tripId]
    }

    val allClips: StateFlow<List<VideoClip>> = clipDao.getAllClips().stateIn(
        scope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val lockedClips: StateFlow<List<VideoClip>> = clipDao.getLockedClips().stateIn(
        scope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val trips: StateFlow<List<TripSession>> = combine(allClips, settingsRepo.settings, tripCustomTitles) { clips, setts, customTitles ->
        TripGroupingEngine.groupClipsIntoTrips(
            clips = clips,
            maxGapMinutes = setts.tripGroupingGapMinutes,
            minTripDurationMinutes = setts.minTripDurationMinutes,
            includeGapsInTripDuration = setts.includeGapsInTripDuration,
            customTitlesMap = customTitles
        )
    }.stateIn(
        scope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    suspend fun getClipById(id: Long): VideoClip? = withContext(Dispatchers.IO) {
        clipDao.getClipById(id)
    }

    suspend fun getClipByPath(path: String): VideoClip? = withContext(Dispatchers.IO) {
        clipDao.getClipByPath(path)
    }

    suspend fun getLatestClip(): VideoClip? = withContext(Dispatchers.IO) {
        clipDao.getLatestClip()
    }

    suspend fun insertOrUpdateClip(clip: VideoClip) = withContext(Dispatchers.IO) {
        val existing = clipDao.getClipByPath(clip.filePath)
        if (existing != null) {
            clipDao.updateClip(clip.copy(id = existing.id))
        } else {
            clipDao.insertClip(clip)
        }
    }

    suspend fun setLockStatus(clipId: Long, isLocked: Boolean) = withContext(Dispatchers.IO) {
        clipDao.setLockStatus(clipId, isLocked)
    }

    suspend fun toggleClipLock(clip: VideoClip) = withContext(Dispatchers.IO) {
        clipDao.setLockStatus(clip.id, !clip.isLocked)
    }

    suspend fun lockLatestClip(): Boolean = withContext(Dispatchers.IO) {
        val latest = clipDao.getLatestClip() ?: return@withContext false
        clipDao.setLockStatus(latest.id, true)
        true
    }

    suspend fun toggleLatestClipLock(): Boolean = withContext(Dispatchers.IO) {
        val latest = clipDao.getLatestClip() ?: return@withContext false
        val newLockState = !latest.isLocked
        clipDao.setLockStatus(latest.id, newLockState)
        newLockState
    }

    suspend fun updateLatestClipIncidentLocation(latitude: Double, longitude: Double) = withContext(Dispatchers.IO) {
        val latest = clipDao.getLatestClip()
        if (latest != null) {
            clipDao.updateClipIncidentLocation(latest.id, latitude, longitude)
        }
    }

    suspend fun lockTripClips(trip: TripSession): Int = withContext(Dispatchers.IO) {
        var lockedCount = 0
        trip.clips.forEach { clip ->
            if (!clip.isLocked) {
                clipDao.updateClip(clip.copy(isLocked = true))
                lockedCount++
            }
        }
        lockedCount
    }

    suspend fun unlockTripClips(trip: TripSession, preserveIncidents: Boolean): Int = withContext(Dispatchers.IO) {
        var unlockedCount = 0
        trip.clips.forEach { clip ->
            val isIncident = TripGroupingEngine.isIncidentClip(clip)
            if (preserveIncidents && isIncident) {
                // Retain pre-existing incident lock
            } else if (clip.isLocked) {
                clipDao.updateClip(clip.copy(isLocked = false))
                unlockedCount++
            }
        }
        unlockedCount
    }

    suspend fun registerFusedClip(sourceClip: VideoClip, fusedFile: File) = withContext(Dispatchers.IO) {
        if (!fusedFile.exists() || fusedFile.length() == 0L) return@withContext
        val existing = clipDao.getClipByPath(fusedFile.absolutePath)
        val fusedClip = VideoClip(
            id = existing?.id ?: 0,
            filePath = fusedFile.absolutePath,
            fileName = fusedFile.name,
            timestamp = sourceClip.timestamp,
            durationMs = sourceClip.durationMs,
            fileSizeBytes = fusedFile.length(),
            isLocked = true,
            isSentryEvent = sourceClip.isSentryEvent,
            triggerReason = "Telemetry Fused",
            speedKmh = sourceClip.speedKmh,
            avgSpeedKmh = sourceClip.avgSpeedKmh,
            distanceMeters = sourceClip.distanceMeters,
            speedSource = sourceClip.speedSource,
            latitude = sourceClip.latitude,
            longitude = sourceClip.longitude,
            startLatitude = sourceClip.startLatitude,
            startLongitude = sourceClip.startLongitude,
            endLatitude = sourceClip.endLatitude,
            endLongitude = sourceClip.endLongitude,
            incidentLatitude = sourceClip.incidentLatitude,
            incidentLongitude = sourceClip.incidentLongitude,
            incidentVideoOffsetSeconds = sourceClip.incidentVideoOffsetSeconds,
            incidentGForce = sourceClip.incidentGForce,
            batteryTempC = sourceClip.batteryTempC
        )
        if (existing != null) {
            clipDao.updateClip(fusedClip)
        } else {
            clipDao.insertClip(fusedClip)
        }
    }

    private val syncMutex = Mutex()

    private suspend fun syncFusedClipsInternal(videosDir: File) {
        try {
            if (!videosDir.exists()) return
            val fusedFiles = videosDir.listFiles { f ->
                f.isFile && f.name.contains("-telemetry-fused") && f.name.endsWith(".mp4", ignoreCase = true) && f.isDashGuardRecordingFile
            } ?: emptyArray()
            for (f in fusedFiles) {
                val existing = clipDao.getClipByPath(f.absolutePath)
                if (existing == null && f.length() > 0) {
                    val origFile = f.rawSourceVideoFile
                    val origClip = clipDao.getClipByPath(origFile.absolutePath)
                    val newClip = VideoClip(
                        id = 0,
                        filePath = f.absolutePath,
                        fileName = f.name,
                        timestamp = origClip?.timestamp ?: f.lastModified(),
                        durationMs = origClip?.durationMs ?: 60000L,
                        fileSizeBytes = f.length(),
                        isLocked = true,
                        isSentryEvent = origClip?.isSentryEvent ?: false,
                        triggerReason = "Telemetry Fused",
                        speedKmh = origClip?.speedKmh ?: 0f,
                        avgSpeedKmh = origClip?.avgSpeedKmh ?: 0f,
                        distanceMeters = origClip?.distanceMeters ?: 0f,
                        latitude = origClip?.latitude ?: 0.0,
                        longitude = origClip?.longitude ?: 0.0
                    )
                    clipDao.insertClip(newClip)
                }
            }
        } catch (_: Exception) {}
    }

    private suspend fun syncExistingClipsInternal(videosDir: File) {
        try {
            // Self-healing: purge any duplicate file path entries keeping the lowest canonical id
            clipDao.deduplicateClipsByPath()

            if (!videosDir.exists()) return
            val files = videosDir.listFiles { f ->
                f.isFile && f.name.endsWith(".mp4", ignoreCase = true) && !f.name.contains("-telemetry-fused")
            } ?: emptyArray()

            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

            for (f in files) {
                // Strictly ignore non-app/foreign media files that do not match DashGuard-Pro's canonical recording signature
                if (!f.isDashGuardRecordingFile) continue

                // Delete empty/aborted DashGuard recordings
                if (f.length() == 0L) {
                    try { f.delete() } catch (_: Exception) {}
                    continue
                }

                val existing = clipDao.getClipByPath(f.absolutePath)
                if (existing != null) {
                    // Heal byte size if physical file length differs from cached database metadata
                    if (existing.fileSizeBytes != f.length() && f.length() > 0L) {
                        clipDao.updateClip(existing.copy(fileSizeBytes = f.length()))
                    }
                    continue
                }

                var clipTimestamp = f.lastModified()
                val name = f.name
                val regex = Regex("""VID_(?:DASH|SENTRY|LOOP|MANUAL|INCIDENT)_(\d{8}_\d{6})""", RegexOption.IGNORE_CASE)
                val match = regex.find(name)
                if (match != null) {
                    try {
                        val parsed = dateFormat.parse(match.groupValues[1])
                        if (parsed != null) {
                            clipTimestamp = parsed.time
                        }
                    } catch (_: Exception) {}
                } else {
                    // Skip files that do not conform to canonical timestamped pattern
                    continue
                }

                val isSentry = name.contains("SENTRY", ignoreCase = true)
                val isLocked = name.contains("LOCK", ignoreCase = true) || name.contains("INCIDENT", ignoreCase = true)
                val triggerReason = if (isLocked) "Protected Recording" else if (isSentry) "Sentry Event" else "Normal Loop"

                var durationMs = 60000L
                val srtFile = f.srtSidecarFile
                if (srtFile.exists() && srtFile.length() > 0) {
                    try {
                        val srtEntries = TelemetrySubtitleManager.parseSubtitleFile(srtFile)
                        if (srtEntries.isNotEmpty()) {
                            durationMs = srtEntries.last().endMs.coerceAtLeast(1000L)
                        }
                    } catch (_: Exception) {}
                }

                val newClip = VideoClip(
                    id = 0,
                    filePath = f.absolutePath,
                    fileName = f.name,
                    timestamp = clipTimestamp,
                    durationMs = durationMs,
                    fileSizeBytes = f.length(),
                    isLocked = isLocked,
                    isSentryEvent = isSentry,
                    triggerReason = triggerReason,
                    speedKmh = 0f,
                    avgSpeedKmh = 0f,
                    distanceMeters = 0f,
                    latitude = 0.0,
                    longitude = 0.0
                )
                clipDao.insertClip(newClip)
            }
        } catch (_: Exception) {}
    }

    suspend fun syncFusedClips(videosDir: File) = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            syncFusedClipsInternal(videosDir)
        }
    }

    suspend fun syncExistingClips(videosDir: File) = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            syncExistingClipsInternal(videosDir)
        }
    }

    suspend fun syncClipsFromDirectory(videosDir: File) = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            syncExistingClipsInternal(videosDir)
            syncFusedClipsInternal(videosDir)
        }
    }
}
