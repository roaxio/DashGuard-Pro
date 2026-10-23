package io.github.roaxio.dashguardpro

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.roaxio.dashguardpro.data.settings.AppMode
import io.github.roaxio.dashguardpro.viewmodel.DashcamIntent
import io.github.roaxio.dashguardpro.viewmodel.DashcamViewModel
import io.github.roaxio.dashguardpro.data.settings.HudDisplayMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DashcamIntentTest {

    private lateinit var context: Context
    private lateinit var app: DashGuardApplication
    private lateinit var viewModel: DashcamViewModel

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        app = context as DashGuardApplication
        viewModel = DashcamViewModel(app)
    }

    @Test
    fun testToggleNightExposureIntent() {
        assertFalse(viewModel.uiState.value.isNightExposureMode)
        viewModel.onIntent(DashcamIntent.ToggleNightExposure)
        assertTrue(viewModel.uiState.value.isNightExposureMode)
        viewModel.onIntent(DashcamIntent.ToggleNightExposure)
        assertFalse(viewModel.uiState.value.isNightExposureMode)
    }

    @Test
    fun testSetScreenBrightnessIntent() {
        viewModel.onIntent(DashcamIntent.SetScreenBrightness(0.5f))
        assertEquals(0.5f, viewModel.uiState.value.screenBrightnessLevel, 0.01f)

        // Verifies clamping
        viewModel.onIntent(DashcamIntent.SetScreenBrightness(0.01f))
        assertEquals(0.1f, viewModel.uiState.value.screenBrightnessLevel, 0.01f)
    }

    @Test
    fun testCycleHudDisplayModeIntent() {
        val initialMode = viewModel.uiState.value.hudDisplayMode
        viewModel.onIntent(DashcamIntent.CycleHudDisplayMode)
        val nextMode = viewModel.uiState.value.hudDisplayMode
        assertTrue(initialMode != nextMode)
    }

    @Test
    fun testSetModeIntent() {
        viewModel.onIntent(DashcamIntent.SetMode(AppMode.SENTRY))
        assertEquals(AppMode.SENTRY, viewModel.settings.value.currentMode)

        viewModel.onIntent(DashcamIntent.SetMode(AppMode.DASHCAM))
        assertEquals(AppMode.DASHCAM, viewModel.settings.value.currentMode)
    }

    @Test
    fun testClearIncidentMessageIntent() {
        viewModel.showTransientMessage("Test Incident Alert")
        viewModel.onIntent(DashcamIntent.ClearIncidentMessage)
        assertNull(viewModel.uiState.value.recentIncidentMessage)
    }

    @Test
    fun testDismissSpeedLockoutIntent() {
        viewModel.checkOrShowSpeedLockout()
        viewModel.onIntent(DashcamIntent.DismissSpeedLockout)
        assertFalse(viewModel.uiState.value.isSpeedLockoutAlertVisible)
    }

    @Test
    fun testGalleryViewModeAndFilterIntents() {
        assertEquals(io.github.roaxio.dashguardpro.model.GalleryViewMode.TRIP_LOOPS, viewModel.galleryNavState.value.viewMode)
        viewModel.onIntent(DashcamIntent.SetGalleryViewMode(io.github.roaxio.dashguardpro.model.GalleryViewMode.ALL_CLIPS))
        assertEquals(io.github.roaxio.dashguardpro.model.GalleryViewMode.ALL_CLIPS, viewModel.galleryNavState.value.viewMode)

        assertEquals(io.github.roaxio.dashguardpro.model.GalleryFilter.ALL, viewModel.galleryNavState.value.selectedFilter)
        viewModel.onIntent(DashcamIntent.SetGalleryFilter(io.github.roaxio.dashguardpro.model.GalleryFilter.LOCKED))
        assertEquals(io.github.roaxio.dashguardpro.model.GalleryFilter.LOCKED, viewModel.galleryNavState.value.selectedFilter)
    }

    @Test
    fun testToggleAndSetTripExpandedIntents() {
        val tripId = "trip_20261001_120000"
        assertFalse(viewModel.galleryNavState.value.expandedTripIds.contains(tripId))

        viewModel.onIntent(DashcamIntent.ToggleTripExpanded(tripId))
        assertTrue(viewModel.galleryNavState.value.expandedTripIds.contains(tripId))

        viewModel.onIntent(DashcamIntent.ToggleTripExpanded(tripId))
        assertFalse(viewModel.galleryNavState.value.expandedTripIds.contains(tripId))

        viewModel.onIntent(DashcamIntent.SetTripExpanded(tripId, true))
        assertTrue(viewModel.galleryNavState.value.expandedTripIds.contains(tripId))

        viewModel.onIntent(DashcamIntent.SetTripExpanded(tripId, false))
        assertFalse(viewModel.galleryNavState.value.expandedTripIds.contains(tripId))
    }

    @Test
    fun testOpenClipForPlayback_updatesGalleryNavState_andClosePlaybackPreservesState() {
        val testClip = io.github.roaxio.dashguardpro.model.VideoClip(
            id = 501L,
            filePath = "/storage/emulated/0/DCIM/LOOP_20261001_143000.mp4",
            fileName = "LOOP_20261001_143000.mp4",
            timestamp = 1727785800000L,
            durationMs = 60_000L,
            fileSizeBytes = 45_000_000L
        )

        viewModel.onIntent(DashcamIntent.OpenClipForPlayback(testClip))

        // Last played clip and pending scroll should be set
        assertEquals(testClip.filePath, viewModel.galleryNavState.value.lastPlayedClipFilePath)
        assertEquals(testClip.filePath, viewModel.galleryNavState.value.pendingScrollToClip)
        assertEquals(testClip.filePath, viewModel.playbackState.value.clipFilePath)

        // Consuming pending scroll removes pending target while retaining lastPlayedClipFilePath
        viewModel.consumePendingScrollToClip()
        assertNull(viewModel.galleryNavState.value.pendingScrollToClip)
        assertEquals(testClip.filePath, viewModel.galleryNavState.value.lastPlayedClipFilePath)

        // Closing playback (user pressed back) resets playbackState but preserves gallery memory
        viewModel.onIntent(DashcamIntent.ClosePlayback)
        assertNull(viewModel.playbackState.value.clipFilePath)
        assertEquals(testClip.filePath, viewModel.galleryNavState.value.lastPlayedClipFilePath)
    }

    @Test
    fun testUpdateGalleryScrollPosition_andResetOnExit() {
        viewModel.updateGalleryScrollPosition(io.github.roaxio.dashguardpro.model.GalleryViewMode.TRIP_LOOPS, 4, 120)
        assertEquals(4, viewModel.galleryNavState.value.tripScrollIndex)
        assertEquals(120, viewModel.galleryNavState.value.tripScrollOffset)

        viewModel.updateGalleryScrollPosition(io.github.roaxio.dashguardpro.model.GalleryViewMode.ALL_CLIPS, 9, 250)
        assertEquals(9, viewModel.galleryNavState.value.allClipsScrollIndex)
        assertEquals(250, viewModel.galleryNavState.value.allClipsScrollOffset)

        viewModel.onIntent(DashcamIntent.ToggleTripExpanded("trip_1"))
        assertTrue(viewModel.galleryNavState.value.expandedTripIds.contains("trip_1"))

        // Exiting the Footage screen via Back resets scroll positions and expanded trips
        viewModel.onIntent(DashcamIntent.ResetGalleryNavigationMemory)
        assertEquals(0, viewModel.galleryNavState.value.tripScrollIndex)
        assertEquals(0, viewModel.galleryNavState.value.tripScrollOffset)
        assertEquals(0, viewModel.galleryNavState.value.allClipsScrollIndex)
        assertEquals(0, viewModel.galleryNavState.value.allClipsScrollOffset)
        assertTrue(viewModel.galleryNavState.value.expandedTripIds.isEmpty())
        assertNull(viewModel.galleryNavState.value.lastPlayedClipFilePath)
    }
}
