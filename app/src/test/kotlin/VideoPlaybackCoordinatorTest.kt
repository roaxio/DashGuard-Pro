package io.github.roaxio.dashguardpro

import io.github.roaxio.dashguardpro.model.TripSession
import io.github.roaxio.dashguardpro.model.VideoClip
import io.github.roaxio.dashguardpro.playback.VideoPlaybackCoordinator
import io.github.roaxio.dashguardpro.playback.VideoPlaybackState
import io.github.roaxio.dashguardpro.media.TelemetrySubtitleEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VideoPlaybackCoordinatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var coordinator: VideoPlaybackCoordinator

    @Before
    fun setup() {
        coordinator = VideoPlaybackCoordinator()
    }

    @Test
    fun testInitialState_isDefault() {
        val state = coordinator.playbackState.value
        assertNull(state.clipFilePath)
        assertTrue(state.isPlaying)
        assertEquals(0, state.currentPositionMs)
        assertFalse(state.isFullScreen)
        assertFalse(state.isLooping)
        assertTrue(state.showControls)
        assertTrue(state.isContinuousPlayback)
    }

    @Test
    fun testOpenClipForPlayback_andReopenBehavior() {
        val clip = VideoClip(
            id = 1L,
            fileName = "clip1.mp4",
            filePath = "/storage/clip1.mp4",
            timestamp = 1000L,
            durationMs = 60000L,
            fileSizeBytes = 1024L
        )

        coordinator.openClipForPlayback(clip, continuous = true)
        var state = coordinator.playbackState.value
        assertEquals("/storage/clip1.mp4", state.clipFilePath)
        assertEquals(0, state.currentPositionMs)
        assertTrue(state.isPlaying)
        assertTrue(state.isContinuousPlayback)

        // Advance position manually
        coordinator.updatePlaybackState { it.copy(currentPositionMs = 25000) }
        assertEquals(25000, coordinator.playbackState.value.currentPositionMs)

        // Reopen same clip without forceRestart -> preserves position
        coordinator.openClipForPlayback(clip, continuous = false, forceRestart = false)
        state = coordinator.playbackState.value
        assertEquals(25000, state.currentPositionMs)
        assertFalse(state.isContinuousPlayback)

        // Reopen with forceRestart -> resets to 0
        coordinator.openClipForPlayback(clip, continuous = true, forceRestart = true)
        state = coordinator.playbackState.value
        assertEquals(0, state.currentPositionMs)
        assertTrue(state.isContinuousPlayback)
    }

    @Test
    fun testClosePlayback_resetsToDefault() {
        val clip = VideoClip(
            id = 1L,
            fileName = "clip1.mp4",
            filePath = "/storage/clip1.mp4",
            timestamp = 1000L,
            durationMs = 60000L,
            fileSizeBytes = 1024L
        )
        coordinator.openClipForPlayback(clip)
        assertNotNull(coordinator.playbackState.value.clipFilePath)

        coordinator.closePlayback()
        assertNull(coordinator.playbackState.value.clipFilePath)
    }

    @Test
    fun testToggleContinuousPlayback() {
        assertTrue(coordinator.playbackState.value.isContinuousPlayback)
        coordinator.toggleContinuousPlayback()
        assertFalse(coordinator.playbackState.value.isContinuousPlayback)
        coordinator.toggleContinuousPlayback()
        assertTrue(coordinator.playbackState.value.isContinuousPlayback)
    }

    @Test
    fun testFindSubtitleAtPosition_binarySearchAccuracy() {
        val entries = listOf(
            TelemetrySubtitleEntry(index = 1, startMs = 0L, endMs = 1000L, text = "0 km/h"),
            TelemetrySubtitleEntry(index = 2, startMs = 1000L, endMs = 2000L, text = "25 km/h"),
            TelemetrySubtitleEntry(index = 3, startMs = 2000L, endMs = 3000L, text = "50 km/h"),
            TelemetrySubtitleEntry(index = 4, startMs = 3000L, endMs = 4000L, text = "60 km/h")
        )

        // Matches inside boundaries
        assertEquals("0 km/h", coordinator.findSubtitleAtPosition(entries, 500L)?.text)
        assertEquals("25 km/h", coordinator.findSubtitleAtPosition(entries, 1200L)?.text)
        assertEquals("50 km/h", coordinator.findSubtitleAtPosition(entries, 2999L)?.text)
        assertEquals("60 km/h", coordinator.findSubtitleAtPosition(entries, 3500L)?.text)

        // Exact boundary edges
        assertEquals("0 km/h", coordinator.findSubtitleAtPosition(entries, 0L)?.text)
        assertEquals("25 km/h", coordinator.findSubtitleAtPosition(entries, 1000L)?.text)

        // Out of range
        assertNull(coordinator.findSubtitleAtPosition(entries, -10L))
        assertNull(coordinator.findSubtitleAtPosition(entries, 4001L))
        assertNull(coordinator.findSubtitleAtPosition(emptyList(), 500L))
    }

    @Test
    fun testLoadSubtitles_cachingAndParsing() {
        val videoFile = tempFolder.newFile("dashcam_clip.mp4")
        val srtFile = File(tempFolder.root, "dashcam_clip.srt")

        val srtContent = """
            1
            00:00:00,000 --> 00:00:01,000
            SPD: 35 km/h | G: 1.0

            2
            00:00:01,000 --> 00:00:02,000
            SPD: 42 km/h | G: 1.1
        """.trimIndent()
        srtFile.writeText(srtContent)

        // First load parses from file
        val entries1 = coordinator.loadSubtitles(videoFile.absolutePath)
        assertEquals(2, entries1.size)
        assertEquals(0L, entries1[0].startMs)
        assertEquals(1000L, entries1[0].endMs)

        // Delete .srt file; cached entries should still be served
        srtFile.delete()
        val entriesCached = coordinator.loadSubtitles(videoFile.absolutePath)
        assertEquals(2, entriesCached.size)

        // Clear cache; reloading now finds missing file and returns empty
        coordinator.clearSubtitleCache()
        val entriesReloaded = coordinator.loadSubtitles(videoFile.absolutePath)
        assertTrue(entriesReloaded.isEmpty())
    }

    @Test
    fun testGetTripAdjacentClips() {
        val clip1 = VideoClip(id = 1L, fileName = "1.mp4", filePath = "/1.mp4", timestamp = 1000L, durationMs = 10000L, fileSizeBytes = 100L)
        val clip2 = VideoClip(id = 2L, fileName = "2.mp4", filePath = "/2.mp4", timestamp = 11000L, durationMs = 10000L, fileSizeBytes = 100L)
        val clip3 = VideoClip(id = 3L, fileName = "3.mp4", filePath = "/3.mp4", timestamp = 21000L, durationMs = 10000L, fileSizeBytes = 100L)

        val trip = TripSession(
            id = "trip1",
            startTimeMs = 1000L,
            endTimeMs = 31000L,
            clips = listOf(clip1, clip2, clip3),
            totalDurationMs = 30000L,
            totalDistanceMeters = 500f,
            avgSpeedKmh = 40f,
            maxSpeedKmh = 50f,
            lockedCount = 0,
            incidentCount = 0
        )

        val (prev1, next1) = coordinator.getTripAdjacentClips(clip1, trip)
        assertNull(prev1)
        assertEquals(clip2, next1)

        val (prev2, next2) = coordinator.getTripAdjacentClips(clip2, trip)
        assertEquals(clip1, prev2)
        assertEquals(clip3, next2)

        val (prev3, next3) = coordinator.getTripAdjacentClips(clip3, trip)
        assertEquals(clip2, prev3)
        assertNull(next3)
    }
}
