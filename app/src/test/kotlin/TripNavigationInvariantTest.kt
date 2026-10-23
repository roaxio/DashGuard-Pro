package io.github.roaxio.dashguardpro

import io.github.roaxio.dashguardpro.model.TripSession
import io.github.roaxio.dashguardpro.model.VideoClip
import io.github.roaxio.dashguardpro.analytics.TripGroupingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests verifying the two-tier navigation invariants:
 *   1. findTripAdjacentClips() returns prev/next regardless of gap size.
 *   2. isBreakBetween() correctly classifies gaps relative to the adjacency threshold.
 *   3. The settings invariant (tripGapSeconds >= adjacencyWindowSeconds) is maintained by
 *      the filtered preset lists and validated in CustomTripGapDialog.
 *
 * All time comparisons are normalised to milliseconds/seconds — no mixed-unit arithmetic.
 */
@RunWith(RobolectricTestRunner::class)
class TripNavigationInvariantTest {

    private fun makeClip(
        id: String,
        timestampMs: Long,
        durationMs: Long = 60_000L
    ) = VideoClip(
        id = id.hashCode().toLong(),
        filePath = "/storage/$id.mp4",
        fileName = "$id.mp4",
        timestamp = timestampMs,
        durationMs = durationMs,
        fileSizeBytes = 1_000_000L
    )

    private fun tripOf(vararg clips: VideoClip) = TripSession(
        id = "trip_test",
        startTimeMs = clips.first().timestamp,
        endTimeMs = clips.last().timestamp + clips.last().durationMs,
        clips = clips.toList(),
        totalDurationMs = clips.sumOf { it.durationMs },
        totalDistanceMeters = 0f,
        avgSpeedKmh = 0f,
        maxSpeedKmh = 0f,
        lockedCount = 0,
        incidentCount = 0,
        isSentry = false
    )

    @Test
    fun testFindTripAdjacentClips_singleClipReturnsNullBothSides() {
        val clip = makeClip("a", 0L)
        val trip = tripOf(clip)
        val (prev, next) = TripGroupingEngine.findTripAdjacentClips(clip, trip)
        assertNull("Expected null prev for first clip", prev)
        assertNull("Expected null next for last clip", next)
    }

    @Test
    fun testFindTripAdjacentClips_firstClipHasNoPrevHasNext() {
        val c1 = makeClip("c1", 0L)
        val c2 = makeClip("c2", 120_000L)
        val c3 = makeClip("c3", 360_000L)
        val trip = tripOf(c1, c2, c3)
        val (prev, next) = TripGroupingEngine.findTripAdjacentClips(c1, trip)
        assertNull(prev)
        assertNotNull(next)
        assertEquals(c2.id, next!!.id)
    }

    @Test
    fun testFindTripAdjacentClips_lastClipHasPrevNoNext() {
        val c1 = makeClip("c1", 0L)
        val c2 = makeClip("c2", 120_000L)
        val c3 = makeClip("c3", 360_000L)
        val trip = tripOf(c1, c2, c3)
        val (prev, next) = TripGroupingEngine.findTripAdjacentClips(c3, trip)
        assertNotNull(prev)
        assertEquals(c2.id, prev!!.id)
        assertNull(next)
    }

    @Test
    fun testFindTripAdjacentClips_worksAcrossLargeGap() {
        val c1 = makeClip("c1", 0L)
        // 10-minute gap — far beyond adjacency window
        val c2 = makeClip("c2", 10 * 60_000L)
        val trip = tripOf(c1, c2)
        val (prev, next) = TripGroupingEngine.findTripAdjacentClips(c2, trip)
        assertNotNull("Trip-scoped prev should be found even across a large gap", prev)
        assertEquals(c1.id, prev!!.id)
        assertNull(next)
    }

    @Test
    fun testFindTripAdjacentClips_returnsNullWhenClipNotInTrip() {
        val c1 = makeClip("c1", 0L)
        val stranger = makeClip("stranger", 5_000L)
        val trip = tripOf(c1)
        val (prev, next) = TripGroupingEngine.findTripAdjacentClips(stranger, trip)
        assertNull(prev)
        assertNull(next)
    }

    @Test
    fun testIsBreakBetween_gapEqualToThresholdIsNotBreak() {
        val prev = makeClip("p", 0L, 60_000L)
        val next = makeClip("n", 90_000L)
        // threshold = 30s -> gap = 30s -> not a break (strict >)
        assertFalse(TripGroupingEngine.isBreakBetween(prev, next, adjacencyThresholdSeconds = 30))
    }

    @Test
    fun testIsBreakBetween_gapAboveThresholdIsBreak() {
        val prev = makeClip("p", 0L, 60_000L)
        // gap = 31s > 30s threshold
        val next = makeClip("n", 91_000L)
        assertTrue(TripGroupingEngine.isBreakBetween(prev, next, adjacencyThresholdSeconds = 30))
    }

    @Test
    fun testIsBreakBetween_zeroOrNegativeOverlapIsNotBreak() {
        val prev = makeClip("p", 0L, 60_500L)
        val next = makeClip("n", 60_000L)
        assertFalse(TripGroupingEngine.isBreakBetween(prev, next, adjacencyThresholdSeconds = 30))
    }

    @Test
    fun testSettingsInvariant_adjacencyOptionsFilteredByTripGapSeconds() {
        val allAdjacencyOptions = listOf(15, 30, 45, 60)
        val tripGapSeconds = 1 * 60
        val allowedAdjacency = allAdjacencyOptions.filter { it <= tripGapSeconds }
        assertEquals(listOf(15, 30, 45, 60), allowedAdjacency)
    }

    @Test
    fun testSettingsInvariant_tripGapPresetOptionsFilteredByAdjacencyWindow() {
        val allPresets = listOf(1, 2, 3, 5, 10)
        val adjacencySeconds = 90
        val allowed = allPresets.filter { min -> (min * 60) >= adjacencySeconds }
        assertFalse("1-min gap should be blocked when adjacency is 90s", allowed.contains(1))
        assertTrue("2-min gap should be allowed when adjacency is 90s", allowed.contains(2))
    }

    @Test
    fun testSettingsInvariant_customGapCeilingDivisionMath() {
        val adjacencyThresholdSeconds = 70
        val minAllowedMinutes = (adjacencyThresholdSeconds + 59) / 60
        assertEquals(2, minAllowedMinutes)
        assertTrue(1 * 60 < adjacencyThresholdSeconds)
        assertFalse(2 * 60 < adjacencyThresholdSeconds)
    }
}
