package io.github.roaxio.dashguardpro.analytics

import io.github.roaxio.dashguardpro.model.TripSession
import io.github.roaxio.dashguardpro.model.VideoClip
import kotlin.math.max

/**
 * Domain engine for grouping individual dashcam video loops into continuous Trip Sessions
 * and determining strict temporal adjacency for seamless Next / Previous video playback.
 */
object TripGroupingEngine {

    const val DEFAULT_TRIP_GAP_MINUTES = 3
    const val DEFAULT_ADJACENCY_GAP_SECONDS = 30

    /**
     * Filters out non-original telemetry fused duplicates from trip analysis.
     */
    fun isOriginalRawClip(clip: VideoClip): Boolean {
        return clip.isOriginalRawClip
    }

    /**
     * Groups a collection of video clips into coherent, chronologically ordered Trip Sessions.
     *
     * @param clips Raw clips from the database or file system.
     * @param maxGapMinutes Inactivity/pause limit between loops before splitting into a new trip.
     * @param minTripDurationMinutes Minimum duration required for a cluster to qualify as a Trip Session (0 = show all).
     * @param includeGapsInTripDuration If true, uses wall-clock elapsed time (endTime - startTime) instead of net clip footage time.
     * @param customTitlesMap Map of tripId to user-defined custom trip title.
     * @return List of TripSessions ordered newest trip first (for UI gallery rendering).
     */
    fun groupClipsIntoTrips(
        clips: List<VideoClip>,
        maxGapMinutes: Int = DEFAULT_TRIP_GAP_MINUTES,
        minTripDurationMinutes: Int = 0,
        includeGapsInTripDuration: Boolean = false,
        customTitlesMap: Map<String, String> = emptyMap()
    ): List<TripSession> {
        val validClips = clips
            .filter { isOriginalRawClip(it) }
            .sortedBy { it.timestamp }

        if (validClips.isEmpty()) {
            return emptyList()
        }

        val maxGapMs = maxGapMinutes.toLong() * 60_000L
        val tripClusters = mutableListOf<MutableList<VideoClip>>()
        var currentCluster = mutableListOf<VideoClip>()

        for (clip in validClips) {
            if (currentCluster.isEmpty()) {
                currentCluster.add(clip)
            } else {
                val prevClip = currentCluster.last()
                val prevEndMs = prevClip.timestamp + max(1000L, prevClip.durationMs)
                val gapMs = clip.timestamp - prevEndMs
                val sameMode = prevClip.isSentryEvent == clip.isSentryEvent

                // Contiguous if gap is within tolerance (allowing small negative overlap for recorder rotation)
                if (sameMode && gapMs <= maxGapMs && gapMs >= -60_000L) {
                    currentCluster.add(clip)
                } else {
                    tripClusters.add(currentCluster)
                    currentCluster = mutableListOf(clip)
                }
            }
        }
        if (currentCluster.isNotEmpty()) {
            tripClusters.add(currentCluster)
        }

        // Build candidate domain TripSessions sorted chronologically (oldest first)
        val candidateSessions = tripClusters.mapNotNull { cluster ->
            buildTripSession(cluster)
        }.sortedBy { it.startTimeMs }

        val minDurationMs = minTripDurationMinutes.toLong() * 60_000L
        val qualifiedSessions = mutableListOf<TripSession>()

        for (session in candidateSessions) {
            val effectiveDurationMs = if (includeGapsInTripDuration) {
                maxOf(session.totalDurationMs, session.endTimeMs - session.startTimeMs)
            } else {
                session.totalDurationMs
            }
            if (minTripDurationMinutes <= 0 || effectiveDurationMs >= minDurationMs) {
                qualifiedSessions.add(session)
            }
        }

        // Assign stable chronological sequence numbers (#1 is oldest, #N is newest)
        val numberedTrips = qualifiedSessions.mapIndexed { index, session ->
            val tripNum = index + 1
            val customTitle = customTitlesMap[session.id]
            session.copy(
                tripNumber = tripNum,
                customTitle = customTitle
            )
        }

        // Return newest trips first for presentation in the gallery
        return numberedTrips.sortedByDescending { it.startTimeMs }
    }

    /**
     * Determines whether a clip qualifies as a safety/emergency incident (e.g. impact shock, crash, collision, emergency lock).
     */
    fun isIncidentClip(clip: VideoClip): Boolean {
        return clip.incidentGForce > 0f ||
            clip.triggerReason.contains("Impact", ignoreCase = true) ||
            clip.triggerReason.contains("Shock", ignoreCase = true) ||
            clip.triggerReason.contains("Crash", ignoreCase = true) ||
            clip.triggerReason.contains("Collision", ignoreCase = true) ||
            clip.triggerReason.contains("Emergency", ignoreCase = true)
    }

    /**
     * Determines whether a clip qualifies as a parked Sentry surveillance event (optical motion or acoustic trigger).
     */
    fun isSentrySurveillanceClip(clip: VideoClip): Boolean {
        return clip.isSentryEvent ||
            clip.triggerReason.contains("Motion", ignoreCase = true) ||
            clip.triggerReason.contains("Acoustic", ignoreCase = true)
    }

    /**
     * Constructs a single TripSession from a contiguous chronological list of VideoClips.
     */
    private fun buildTripSession(cluster: List<VideoClip>): TripSession? {
        if (cluster.isEmpty()) return null

        val firstClip = cluster.first()
        val lastClip = cluster.last()
        val startTimeMs = firstClip.timestamp
        val endTimeMs = lastClip.timestamp + max(1000L, lastClip.durationMs)

        var totalDurationMs = 0L
        var totalDistanceMeters = 0f
        var peakSpeed = 0f
        var lockedCount = 0
        var incidentCount = 0
        var speedWeightedSum = 0.0

        for (clip in cluster) {
            val clipDur = max(1000L, clip.durationMs)
            totalDurationMs += clipDur
            totalDistanceMeters += clip.distanceMeters

            val clipSpeed = if (clip.avgSpeedKmh > 0f) clip.avgSpeedKmh else clip.speedKmh
            speedWeightedSum += (clipSpeed.toDouble() * clipDur.toDouble())
            if (clip.speedKmh > peakSpeed) peakSpeed = clip.speedKmh
            if (clip.avgSpeedKmh > peakSpeed) peakSpeed = clip.avgSpeedKmh

            if (clip.isLocked) lockedCount++
            if (isIncidentClip(clip)) {
                incidentCount++
            }
        }

        val weightedAvgSpeed = if (totalDurationMs > 0) {
            (speedWeightedSum / totalDurationMs).toFloat()
        } else {
            0f
        }

        val tripId = "trip_${if (firstClip.isSentryEvent) "sentry" else "dash"}_${firstClip.id}_$startTimeMs"

        return TripSession(
            id = tripId,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            clips = cluster,
            totalDurationMs = totalDurationMs,
            totalDistanceMeters = totalDistanceMeters,
            avgSpeedKmh = weightedAvgSpeed,
            maxSpeedKmh = peakSpeed,
            lockedCount = lockedCount,
            incidentCount = incidentCount,
            isSentry = firstClip.isSentryEvent
        )
    }

    /**
     * Determines whether there are valid contiguous predecessor and successor clips for immediate playback.
     *
     * @param currentClip Currently active video clip.
     * @param allClips Full clip list from repository.
     * @param maxAdjacencyGapSeconds Max time gap (default 30s) allowed between clips to be considered continuous.
     * @return Pair of (previousLoop, nextLoop). If predecessor or successor is missing or separated by hours/days, returns null.
     */
    fun findAdjacentClips(
        currentClip: VideoClip,
        allClips: List<VideoClip>,
        maxAdjacencyGapSeconds: Int = DEFAULT_ADJACENCY_GAP_SECONDS
    ): Pair<VideoClip?, VideoClip?> {
        val candidates = allClips
            .filter { isOriginalRawClip(it) && it.isSentryEvent == currentClip.isSentryEvent }
            .sortedBy { it.timestamp }

        val currentIndex = candidates.indexOfFirst {
            it.id == currentClip.id || it.filePath == currentClip.filePath
        }

        if (currentIndex == -1) {
            return Pair(null, null)
        }

        val maxGapMs = maxAdjacencyGapSeconds.toLong() * 1000L

        // Check Previous Clip
        val previousClip: VideoClip? = if (currentIndex > 0) {
            val candidatePrev = candidates[currentIndex - 1]
            val candidatePrevEndMs = candidatePrev.timestamp + max(1000L, candidatePrev.durationMs)
            val gapMs = currentClip.timestamp - candidatePrevEndMs
            if (gapMs <= maxGapMs && gapMs >= -60_000L) {
                candidatePrev
            } else {
                null
            }
        } else {
            null
        }

        // Check Next Clip
        val nextClip: VideoClip? = if (currentIndex < candidates.size - 1) {
            val candidateNext = candidates[currentIndex + 1]
            val currentEndMs = currentClip.timestamp + max(1000L, currentClip.durationMs)
            val gapMs = candidateNext.timestamp - currentEndMs
            if (gapMs <= maxGapMs && gapMs >= -60_000L) {
                candidateNext
            } else {
                null
            }
        } else {
            null
        }

        return Pair(previousClip, nextClip)
    }

    /**
     * Finds the parent TripSession containing the specified clip.
     */
    fun findTripForClip(clip: VideoClip, trips: List<TripSession>): TripSession? {
        return trips.firstOrNull { trip ->
            trip.clips.any { it.id == clip.id || it.filePath == clip.filePath }
        }
    }

    /**
     * Returns the chronological predecessor and successor of [currentClip] within its [trip],
     * regardless of time gap between clips.
     *
     * Used for manual Skip Prev / Skip Next buttons — the player should always be able to
     * navigate across any clip in the same trip session, even if a driving break separates them.
     */
    fun findTripAdjacentClips(
        currentClip: VideoClip,
        trip: TripSession
    ): Pair<VideoClip?, VideoClip?> {
        val idx = trip.clips.indexOfFirst {
            it.id == currentClip.id || it.filePath == currentClip.filePath
        }
        if (idx == -1) return Pair(null, null)
        return Pair(
            if (idx > 0) trip.clips[idx - 1] else null,
            if (idx < trip.clips.size - 1) trip.clips[idx + 1] else null
        )
    }

    /**
     * Returns true when the temporal gap between the end of [prevClip] and the start of [nextClip]
     * exceeds [adjacencyThresholdSeconds] — i.e. they are separated by a driving break.
     *
     * This is used to distinguish tight adjacent loops (auto-advance eligible) from break-separated
     * loops (manual skip only, with a "Resume after break" UI prompt).
     *
     * Both values are normalized to milliseconds for a unit-safe comparison.
     */
    fun isBreakBetween(
        prevClip: VideoClip,
        nextClip: VideoClip,
        adjacencyThresholdSeconds: Int
    ): Boolean {
        val prevEndMs = prevClip.timestamp + max(1000L, prevClip.durationMs)
        val gapMs = nextClip.timestamp - prevEndMs
        return gapMs > adjacencyThresholdSeconds.toLong() * 1_000L
    }
}
