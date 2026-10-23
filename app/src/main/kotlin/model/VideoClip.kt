package io.github.roaxio.dashguardpro.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "video_clips",
    indices = [Index(value = ["filePath"], unique = true)]
)
data class VideoClip(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val filePath: String,
    val fileName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long = 0,
    val fileSizeBytes: Long = 0,
    val isLocked: Boolean = false,
    val isSentryEvent: Boolean = false,
    val triggerReason: String = "Normal Loop", // "Normal Loop", "Impact Shock", "Motion Sentry", "Manual Lock"
    val speedKmh: Float = 0f, // Average speed during segment
    val avgSpeedKmh: Float = 0f,
    val distanceMeters: Float = 0f,
    val speedSource: String = "Sensor Fusion (GPS + Accel)",
    val latitude: Double = 0.0, // Primary coordinate for backward compatibility (defaults to start or incident)
    val longitude: Double = 0.0,
    val startLatitude: Double = 0.0,
    val startLongitude: Double = 0.0,
    val endLatitude: Double = 0.0,
    val endLongitude: Double = 0.0,
    val incidentLatitude: Double = 0.0,
    val incidentLongitude: Double = 0.0,
    val incidentVideoOffsetSeconds: Int = -1, // Offset in seconds from start of file where incident was triggered
    val incidentGForce: Float = 0f,
    val batteryTempC: Float = 0f
) {
    /**
     * Authoritative check for burned-in telemetry overlay derivatives.
     * Evaluates canonical naming convention and trigger reason.
     */
    val isTelemetryFused: Boolean
        get() = fileName.contains("-telemetry-fused") || triggerReason == "Telemetry Fused"

    /**
     * Authoritative check for original raw camera footage (non-derivative).
     */
    val isOriginalRawClip: Boolean
        get() = !isTelemetryFused
}

