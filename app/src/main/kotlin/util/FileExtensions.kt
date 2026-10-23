package io.github.roaxio.dashguardpro.util

import io.github.roaxio.dashguardpro.model.VideoClip
import io.github.roaxio.dashguardpro.media.TelemetryVideoFusionEngine
import java.io.File

/**
 * Foundation extensions for canonical sidecar file resolution and coordinate evaluation.
 * Centralizes .srt and -telemetry-fused.mp4 path algebra across storage, models, and UI.
 */
object FileExtensions

// --- VideoClip File Extensions ---
val VideoClip.videoFile: File
    get() = File(filePath)

val VideoClip.srtSidecarFile: File
    get() = File(filePath.substringBeforeLast(".") + ".srt")

val VideoClip.fusedVideoFile: File
    get() = TelemetryVideoFusionEngine.getFusedVideoFile(videoFile)

// --- VideoClip Coordinate Extensions ---
val VideoClip.hasValidCoordinates: Boolean
    get() = (latitude != 0.0 && longitude != 0.0) || (startLatitude != 0.0 && startLongitude != 0.0)

val VideoClip.effectiveCoordinates: Pair<Double, Double>?
    get() = when {
        latitude != 0.0 && longitude != 0.0 -> Pair(latitude, longitude)
        startLatitude != 0.0 && startLongitude != 0.0 -> Pair(startLatitude, startLongitude)
        incidentLatitude != 0.0 && incidentLongitude != 0.0 -> Pair(incidentLatitude, incidentLongitude)
        else -> null
    }

val VideoClip.effectiveStartCoordinates: Pair<Double, Double>?
    get() = if (startLatitude != 0.0 && startLongitude != 0.0) Pair(startLatitude, startLongitude) else effectiveCoordinates

val VideoClip.effectiveEndCoordinates: Pair<Double, Double>?
    get() = if (endLatitude != 0.0 && endLongitude != 0.0) Pair(endLatitude, endLongitude) else effectiveCoordinates

// --- File Extensions ---
val File.srtSidecarFile: File
    get() = File(parentFile ?: File("."), "${nameWithoutExtension.replace("-telemetry-fused", "")}.srt")

val File.fusedVideoFile: File
    get() = TelemetryVideoFusionEngine.getFusedVideoFile(this)

val File.isFusedVideo: Boolean
    get() = name.contains("-telemetry-fused")

val File.rawSourceVideoFile: File
    get() {
        if (!isFusedVideo) return this
        val cleanName = nameWithoutExtension.replace("-telemetry-fused", "")
        val ext = extension.ifBlank { "mp4" }
        return File(parentFile ?: File("."), "$cleanName.$ext")
    }

val DASHGUARD_RECORDING_PATTERN = Regex(
    """^VID_(?:DASH|SENTRY|LOOP|MANUAL|INCIDENT)_\d{8}_\d{6}(?:_\d+)?(?:-telemetry-fused)?\.(mp4|srt)$""",
    RegexOption.IGNORE_CASE
)

/**
 * Returns true if the file conforms to DashGuard-Pro's canonical recording or sidecar naming signature.
 * Strictly prevents non-app/foreign media files located in shared or custom storage directories
 * from being mistakenly ingested into the dashcam database, deleted, or purged during FIFO retention.
 */
val File.isDashGuardRecordingFile: Boolean
    get() = DASHGUARD_RECORDING_PATTERN.matches(name)
