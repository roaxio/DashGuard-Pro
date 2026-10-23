package io.github.roaxio.dashguardpro.model

import io.github.roaxio.dashguardpro.util.effectiveEndCoordinates
import io.github.roaxio.dashguardpro.util.effectiveStartCoordinates

/**
 * Domain entity representing an aggregated continuous trip or sentry session.
 * Encapsulates a contiguous sequence of VideoClip loops with authoritative summary telemetry metrics.
 */
data class TripSession(
    val id: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val clips: List<VideoClip>,
    val totalDurationMs: Long,
    val totalDistanceMeters: Float,
    val avgSpeedKmh: Float,
    val maxSpeedKmh: Float,
    val lockedCount: Int,
    val incidentCount: Int,
    val hasIncidents: Boolean = lockedCount > 0 || incidentCount > 0,
    val isSentry: Boolean = false,
    val tripNumber: Int = 0,
    val customTitle: String? = null
) {
    val loopCount: Int
        get() = clips.size

    val startCoordinates: Pair<Double, Double>?
        get() = clips.firstNotNullOfOrNull { it.effectiveStartCoordinates }

    val endCoordinates: Pair<Double, Double>?
        get() = clips.asReversed().firstNotNullOfOrNull { it.effectiveEndCoordinates }
}
