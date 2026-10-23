package io.github.roaxio.dashguardpro

import io.github.roaxio.dashguardpro.model.VideoClip
import io.github.roaxio.dashguardpro.analytics.TripGroupingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripGroupingEngineTest {

    private fun createClip(
        id: Long,
        timestamp: Long,
        durationMs: Long = 180_000L,
        fileName: String = "VID_LOOP_${timestamp}.mp4",
        isLocked: Boolean = false,
        triggerReason: String = "Normal Loop",
        speedKmh: Float = 60f,
        avgSpeedKmh: Float = 60f,
        distanceMeters: Float = 3000f,
        incidentGForce: Float = 0f,
        isSentry: Boolean = false,
        startLat: Double = 0.0,
        startLng: Double = 0.0,
        endLat: Double = 0.0,
        endLng: Double = 0.0
    ): VideoClip {
        return VideoClip(
            id = id,
            filePath = "/storage/videos/$fileName",
            fileName = fileName,
            timestamp = timestamp,
            durationMs = durationMs,
            fileSizeBytes = 45L * 1024 * 1024,
            isLocked = isLocked,
            triggerReason = triggerReason,
            speedKmh = speedKmh,
            avgSpeedKmh = avgSpeedKmh,
            distanceMeters = distanceMeters,
            incidentGForce = incidentGForce,
            isSentryEvent = isSentry,
            latitude = startLat,
            longitude = startLng,
            startLatitude = startLat,
            startLongitude = startLng,
            endLatitude = endLat,
            endLongitude = endLng
        )
    }

    @Test
    fun testGroupClipsIntoTrips_consecutiveClips_groupedIntoSingleTrip() {
        val baseTime = 1725530000000L // arbitrary epoch ms
        val clipDur = 180_000L // 3 mins

        // 3 consecutive clips, 2 seconds switchover gap
        val clip1 = createClip(id = 1, timestamp = baseTime, durationMs = clipDur, distanceMeters = 3000f)
        val clip2 = createClip(id = 2, timestamp = baseTime + clipDur + 2000L, durationMs = clipDur, distanceMeters = 3000f, isLocked = true, triggerReason = "Manual Emergency Lock")
        val clip3 = createClip(id = 3, timestamp = baseTime + (clipDur * 2) + 4000L, durationMs = clipDur, distanceMeters = 3000f)

        val trips = TripGroupingEngine.groupClipsIntoTrips(listOf(clip1, clip2, clip3), maxGapMinutes = 3)

        assertEquals(1, trips.size)
        val trip = trips.first()
        assertEquals(3, trip.loopCount)
        assertEquals(clip1.timestamp, trip.startTimeMs)
        assertEquals(9000f, trip.totalDistanceMeters, 0.1f)
        assertEquals(1, trip.lockedCount)
        assertEquals(1, trip.incidentCount)
        assertTrue(trip.hasIncidents)
        assertEquals(60f, trip.avgSpeedKmh, 0.1f)
    }

    @Test
    fun testGroupClipsIntoTrips_longGap_splitsIntoMultipleTrips() {
        val morningTime = 1725530000000L
        val afternoonTime = morningTime + (4 * 3600_000L) // 4 hours later

        val morningClip1 = createClip(id = 1, timestamp = morningTime)
        val morningClip2 = createClip(id = 2, timestamp = morningTime + 182_000L)

        val afternoonClip1 = createClip(id = 3, timestamp = afternoonTime)
        val afternoonClip2 = createClip(id = 4, timestamp = afternoonTime + 182_000L)

        val trips = TripGroupingEngine.groupClipsIntoTrips(
            listOf(afternoonClip2, morningClip1, afternoonClip1, morningClip2),
            maxGapMinutes = 3
        )

        assertEquals(2, trips.size)
        // Ordered newest first
        assertEquals(afternoonTime, trips[0].startTimeMs)
        assertEquals(morningTime, trips[1].startTimeMs)
        assertEquals(2, trips[0].loopCount)
        assertEquals(2, trips[1].loopCount)
    }

    @Test
    fun testGroupClipsIntoTrips_customGapMinutes_respectsCustomTimeout() {
        val baseTime = 1725530000000L
        // 20-minute pause between clips
        val clip1 = createClip(id = 1, timestamp = baseTime, durationMs = 180_000L)
        val clip2 = createClip(id = 2, timestamp = baseTime + 180_000L + (20 * 60_000L), durationMs = 180_000L)

        // With default 3-minute gap -> should split into 2 trips
        val tripsDefault = TripGroupingEngine.groupClipsIntoTrips(listOf(clip1, clip2), maxGapMinutes = 3)
        assertEquals(2, tripsDefault.size)

        // With user custom 30-minute gap -> should stay in 1 unified trip
        val tripsCustom = TripGroupingEngine.groupClipsIntoTrips(listOf(clip1, clip2), maxGapMinutes = 30)
        assertEquals(1, tripsCustom.size)
        assertEquals(2, tripsCustom[0].loopCount)
    }

    @Test
    fun testFindAdjacentClips_consecutiveLoops_returnsAdjacentClips() {
        val baseTime = 1725530000000L
        val clip1 = createClip(id = 1, timestamp = baseTime, durationMs = 180_000L)
        val clip2 = createClip(id = 2, timestamp = baseTime + 182_000L, durationMs = 180_000L)
        val clip3 = createClip(id = 3, timestamp = baseTime + 364_000L, durationMs = 180_000L)
        val clips = listOf(clip1, clip2, clip3)

        // Middle clip
        val (prev2, next2) = TripGroupingEngine.findAdjacentClips(clip2, clips, maxAdjacencyGapSeconds = 30)
        assertNotNull(prev2)
        assertEquals(clip1.id, prev2?.id)
        assertNotNull(next2)
        assertEquals(clip3.id, next2?.id)

        // First clip
        val (prev1, next1) = TripGroupingEngine.findAdjacentClips(clip1, clips, maxAdjacencyGapSeconds = 30)
        assertNull(prev1)
        assertEquals(clip2.id, next1?.id)

        // Last clip
        val (prev3, next3) = TripGroupingEngine.findAdjacentClips(clip3, clips, maxAdjacencyGapSeconds = 30)
        assertEquals(clip2.id, prev3?.id)
        assertNull(next3)
    }

    @Test
    fun testFindAdjacentClips_clipsHoursApart_returnsNull() {
        val baseTime = 1725530000000L
        val yesterdayClip = createClip(id = 1, timestamp = baseTime - (24 * 3600_000L), durationMs = 180_000L)
        val todayClip = createClip(id = 2, timestamp = baseTime, durationMs = 180_000L)
        val tomorrowClip = createClip(id = 3, timestamp = baseTime + (24 * 3600_000L), durationMs = 180_000L)
        val clips = listOf(yesterdayClip, todayClip, tomorrowClip)

        // For todayClip, neither yesterday nor tomorrow is an adjacent loop
        val (prev, next) = TripGroupingEngine.findAdjacentClips(todayClip, clips, maxAdjacencyGapSeconds = 30)
        assertNull("Preceding clip from yesterday must NOT be treated as adjacent loop", prev)
        assertNull("Follow-up clip from tomorrow must NOT be treated as adjacent loop", next)
    }

    @Test
    fun testFindAdjacentClips_negativeOverlapTolerance_handlesRecorderRotation() {
        val baseTime = 1725530000000L
        // Small 1.5s negative overlap between file timestamps
        val clip1 = createClip(id = 1, timestamp = baseTime, durationMs = 180_000L)
        val clip2 = createClip(id = 2, timestamp = baseTime + 178_500L, durationMs = 180_000L)

        val (prev, next) = TripGroupingEngine.findAdjacentClips(clip2, listOf(clip1, clip2), maxAdjacencyGapSeconds = 30)
        assertNotNull(prev)
        assertEquals(clip1.id, prev?.id)
    }

    @Test
    fun testFiltersOutTelemetryFusedClips() {
        val baseTime = 1725530000000L
        val rawClip = createClip(id = 1, timestamp = baseTime, fileName = "VID_LOOP_1.mp4")
        val fusedClip = createClip(id = 2, timestamp = baseTime, fileName = "VID_LOOP_1-telemetry-fused.mp4", triggerReason = "Telemetry Fused")

        val trips = TripGroupingEngine.groupClipsIntoTrips(listOf(rawClip, fusedClip))
        assertEquals(1, trips.size)
        assertEquals(1, trips[0].loopCount)
        assertEquals("VID_LOOP_1.mp4", trips[0].clips[0].fileName)
    }

    @Test
    fun testGroupClipsIntoTrips_routineTriggersDoNotCountAsIncidents() {
        val baseTime = 1725530000000L
        val clipDur = 180_000L

        // Routine non-incident triggers: "Manual", "Auto Power-On", "Continuous"
        val clip1 = createClip(id = 1, timestamp = baseTime, durationMs = clipDur, triggerReason = "Manual")
        val clip2 = createClip(id = 2, timestamp = baseTime + clipDur + 2000L, durationMs = clipDur, triggerReason = "Auto Power-On")
        val clip3 = createClip(id = 3, timestamp = baseTime + (clipDur * 2) + 4000L, durationMs = clipDur, triggerReason = "Continuous")

        val trips = TripGroupingEngine.groupClipsIntoTrips(listOf(clip1, clip2, clip3), maxGapMinutes = 3)

        assertEquals(1, trips.size)
        val trip = trips.first()
        assertEquals(3, trip.loopCount)
        assertEquals(0, trip.lockedCount)
        assertEquals("Routine loop start reasons must NOT be counted as incidents", 0, trip.incidentCount)
        assertFalse("Trip with routine triggers must not be flagged with hasIncidents", trip.hasIncidents)
    }

    @Test
    fun testGroupClipsIntoTrips_minTripDuration_filtersOutShortTrips() {
        val baseTime = 1725530000000L
        val clipDur = 180_000L // 3 minutes each

        // Two 3-minute clips = 6 minutes total duration
        val clip1 = createClip(id = 1, timestamp = baseTime, durationMs = clipDur)
        val clip2 = createClip(id = 2, timestamp = baseTime + clipDur + 2000L, durationMs = clipDur)
        val clips = listOf(clip1, clip2)

        // Threshold = 10 minutes -> 6 min is below threshold -> 0 trips
        val trips10Min = TripGroupingEngine.groupClipsIntoTrips(clips, minTripDurationMinutes = 10)
        assertEquals("Sub-threshold 6-min trip must be excluded when threshold is 10 min", 0, trips10Min.size)

        // Threshold = 5 minutes -> 6 min meets threshold -> 1 trip
        val trips5Min = TripGroupingEngine.groupClipsIntoTrips(clips, minTripDurationMinutes = 5)
        assertEquals("6-min trip must qualify when threshold is 5 min", 1, trips5Min.size)
        assertEquals(2, trips5Min[0].loopCount)

        // Threshold = 0 (None) -> always qualifies -> 1 trip
        val trips0Min = TripGroupingEngine.groupClipsIntoTrips(clips, minTripDurationMinutes = 0)
        assertEquals(1, trips0Min.size)
    }

    @Test
    fun testGroupClipsIntoTrips_includeGapsInTripDuration_calculatesSpanCorrectly() {
        val baseTime = 1725530000000L
        val clipDur = 180_000L // 3 minutes

        // Two 3-minute clips separated by an 8-minute stop (within a 10-minute maxGap)
        // Video footage duration = 6 minutes.
        // Wall-clock span = (endTime - startTime) = 180s + 480s + 180s = 840s = 14 minutes.
        val gapMs = 8 * 60_000L
        val clip1 = createClip(id = 1, timestamp = baseTime, durationMs = clipDur)
        val clip2 = createClip(id = 2, timestamp = baseTime + clipDur + gapMs, durationMs = clipDur)
        val clips = listOf(clip1, clip2)

        // Threshold = 10 minutes, includeGaps = false -> only 6 min video duration -> excluded
        val tripsWithoutGaps = TripGroupingEngine.groupClipsIntoTrips(
            clips,
            maxGapMinutes = 10,
            minTripDurationMinutes = 10,
            includeGapsInTripDuration = false
        )
        assertEquals(0, tripsWithoutGaps.size)

        // Threshold = 10 minutes, includeGaps = true -> 14 min span counts towards threshold -> included!
        val tripsWithGaps = TripGroupingEngine.groupClipsIntoTrips(
            clips,
            maxGapMinutes = 10,
            minTripDurationMinutes = 10,
            includeGapsInTripDuration = true
        )
        assertEquals(1, tripsWithGaps.size)
        assertEquals(2, tripsWithGaps[0].loopCount)
    }

    @Test
    fun testGroupClipsIntoTrips_incidentClipsMustMeetTripDurationThreshold() {
        val baseTime = 1725530000000L
        // Single 1-minute emergency locked clip (way below a 15-minute threshold)
        val incidentClip = createClip(
            id = 1,
            timestamp = baseTime,
            durationMs = 60_000L,
            isLocked = true,
            triggerReason = "Impact Shock Collision",
            incidentGForce = 3.2f
        )
        val clips = listOf(incidentClip)

        // Threshold = 15 min -> dropped because 1 min < 15 min
        val dropped = TripGroupingEngine.groupClipsIntoTrips(
            clips,
            minTripDurationMinutes = 15
        )
        assertEquals(0, dropped.size)

        // Threshold = 0 min -> qualifies
        val qualified = TripGroupingEngine.groupClipsIntoTrips(
            clips,
            minTripDurationMinutes = 0
        )
        assertEquals(1, qualified.size)
        assertEquals(1, qualified[0].loopCount)
        assertTrue(qualified[0].hasIncidents)
    }

    @Test
    fun testGroupClipsIntoTrips_sentryClipsMustMeetTripDurationThreshold() {
        val baseTime = 1725530000000L
        // Isolated or short sentry clip (e.g. 45s incident clip)
        val sentryClip = createClip(
            id = 1,
            timestamp = baseTime,
            durationMs = 45_000L,
            isLocked = true,
            isSentry = true,
            triggerReason = "Sentry Shock Detection",
            incidentGForce = 2.5f
        )
        val clips = listOf(sentryClip)

        // Threshold = 10 min -> sentry clips must not bypass duration
        val sentryTrips = TripGroupingEngine.groupClipsIntoTrips(
            clips,
            minTripDurationMinutes = 10
        )
        assertEquals("Short sentry clip must NOT be grouped into a Trip unless it meets min trip duration", 0, sentryTrips.size)

        // If minTripDurationMinutes = 0, it qualifies
        val sentryTripsZeroMin = TripGroupingEngine.groupClipsIntoTrips(
            clips,
            minTripDurationMinutes = 0
        )
        assertEquals(1, sentryTripsZeroMin.size)
    }

    @Test
    fun testGroupClipsIntoTrips_chronologicalTripNumbering_and_customTitles() {
        val dayMs = 24 * 3600_000L
        val t1 = 1725500000000L // Day 1 (Oldest)
        val t2 = t1 + dayMs     // Day 2 (Middle)
        val t3 = t1 + (2 * dayMs) // Day 3 (Newest)

        val clip1 = createClip(id = 1, timestamp = t1, durationMs = 300_000L)
        val clip2 = createClip(id = 2, timestamp = t2, durationMs = 300_000L)
        val clip3 = createClip(id = 3, timestamp = t3, durationMs = 300_000L)

        // Find trip2's generated session ID: "trip_dash_${clip.id}_${clip.timestamp}"
        val expectedTrip2Id = "trip_dash_${clip2.id}_$t2"
        val customTitles = mapOf(expectedTrip2Id to "Highway Commute")

        val trips = TripGroupingEngine.groupClipsIntoTrips(
            clips = listOf(clip3, clip1, clip2), // deliberately unsorted input
            customTitlesMap = customTitles
        )

        assertEquals(3, trips.size)
        // Presentation order is newest first:
        // trips[0] is Day 3 (newest, #3)
        assertEquals(3, trips[0].tripNumber)
        assertEquals(t3, trips[0].startTimeMs)
        assertNull(trips[0].customTitle)

        // trips[1] is Day 2 (#2, has custom title)
        assertEquals(2, trips[1].tripNumber)
        assertEquals(t2, trips[1].startTimeMs)
        assertEquals("Highway Commute", trips[1].customTitle)

        // trips[2] is Day 1 (oldest, #1)
        assertEquals(1, trips[2].tripNumber)
        assertEquals(t1, trips[2].startTimeMs)
        assertNull(trips[2].customTitle)
    }

    @Test
    fun testTripSession_startAndEndCoordinates_extractedCorrectly() {
        val baseTime = 1725530000000L
        val clip1 = createClip(
            id = 1,
            timestamp = baseTime,
            startLat = 37.7749,
            startLng = -122.4194,
            endLat = 37.7800,
            endLng = -122.4180
        )
        val clip2 = createClip(
            id = 2,
            timestamp = baseTime + 182_000L,
            startLat = 37.7800,
            startLng = -122.4180,
            endLat = 37.7890,
            endLng = -122.4150
        )

        val trips = TripGroupingEngine.groupClipsIntoTrips(listOf(clip1, clip2))
        assertEquals(1, trips.size)
        val trip = trips.first()

        val startCoords = trip.startCoordinates
        assertNotNull(startCoords)
        assertEquals(37.7749, startCoords!!.first, 0.0001)
        assertEquals(-122.4194, startCoords.second, 0.0001)

        val endCoords = trip.endCoordinates
        assertNotNull(endCoords)
        assertEquals(37.7890, endCoords!!.first, 0.0001)
        assertEquals(-122.4150, endCoords.second, 0.0001)
    }

    @Test
    fun testClipClassification_incidentsAndSentrySurveillance() {
        val shockClip = createClip(id = 1, timestamp = 1000L, triggerReason = "Impact Shock (4.2 G)")
        val crashClip = createClip(id = 2, timestamp = 2000L, triggerReason = "Crash Detection")
        val emergencyClip = createClip(id = 3, timestamp = 3000L, triggerReason = "Emergency Guard Lock")
        val normalClip = createClip(id = 4, timestamp = 4000L, triggerReason = "Normal Loop")
        val motionClip = createClip(id = 5, timestamp = 5000L, triggerReason = "Optical Motion Triggered", isSentry = true)
        val sentryAcousticClip = createClip(id = 6, timestamp = 6000L, triggerReason = "Sentry Acoustic (75 dB)", isSentry = true)

        assertTrue(TripGroupingEngine.isIncidentClip(shockClip))
        assertTrue(TripGroupingEngine.isIncidentClip(crashClip))
        assertTrue(TripGroupingEngine.isIncidentClip(emergencyClip))
        assertFalse(TripGroupingEngine.isIncidentClip(normalClip))
        assertFalse(TripGroupingEngine.isIncidentClip(motionClip))
        assertFalse(TripGroupingEngine.isIncidentClip(sentryAcousticClip))

        assertTrue(TripGroupingEngine.isSentrySurveillanceClip(motionClip))
        assertTrue(TripGroupingEngine.isSentrySurveillanceClip(sentryAcousticClip))
        assertFalse(TripGroupingEngine.isSentrySurveillanceClip(shockClip))
        assertFalse(TripGroupingEngine.isSentrySurveillanceClip(normalClip))

        // Authoritative domain flag test: even with non-standard triggerReason, isSentryEvent=true qualifies
        val customSentryClip = createClip(id = 7, timestamp = 7000L, triggerReason = "Custom Alert", isSentry = true)
        assertTrue(TripGroupingEngine.isSentrySurveillanceClip(customSentryClip))
    }
}
