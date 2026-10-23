package io.github.roaxio.dashguardpro

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import io.github.roaxio.dashguardpro.model.TripSession
import io.github.roaxio.dashguardpro.model.VideoClip
import io.github.roaxio.dashguardpro.analytics.TripGroupingEngine
import io.github.roaxio.dashguardpro.ui.screens.GalleryScreen
import io.github.roaxio.dashguardpro.ui.screens.OptimizationScreen
import io.github.roaxio.dashguardpro.ui.screens.SettingsScreen
import io.github.roaxio.dashguardpro.ui.screens.VideoPlayerScreen
import io.github.roaxio.dashguardpro.ui.theme.DashGuardProTheme
import io.github.roaxio.dashguardpro.viewmodel.DashcamViewModel
import io.github.roaxio.dashguardpro.playback.VideoPlaybackState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SmartPlaybackAndTripUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: DashGuardApplication
    private lateinit var viewModel: DashcamViewModel

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        viewModel = DashcamViewModel(app)
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testGalleryScreen_rendersTripLoopsTabAndSwitchesToAllClips() {
        var playedClip: VideoClip? = null

        composeTestRule.setContent {
            DashGuardProTheme {
                GalleryScreen(
                    viewModel = viewModel,
                    onPlayClip = { playedClip = it }
                )
            }
        }

        composeTestRule.waitForIdle()

        // Verify top bar and mode selector tabs exist
        composeTestRule.onNodeWithText("Recorded Footage", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("gallery_tab_trip_loops", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("gallery_tab_all_clips", useUnmergedTree = true).assertExists()

        // Switch to All Clips tab
        composeTestRule.onNodeWithTag("gallery_tab_all_clips").performClick()
        composeTestRule.waitForIdle()

        // All Clips tab active: Filter chips or empty state appear
        composeTestRule.onNodeWithTag("gallery_tab_all_clips", useUnmergedTree = true).assertExists()
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testGalleryScreen_lockedFusedTelemetryClip_showsUpInProtectedFilter() {
        val baseTime = 1725530000000L
        val fusedClip = VideoClip(
            id = 999L,
            filePath = "/storage/emulated/0/DCIM/LOOP_20260927_120000-telemetry-fused.mp4",
            fileName = "LOOP_20260927_120000-telemetry-fused.mp4",
            timestamp = baseTime,
            durationMs = 60_000L,
            fileSizeBytes = 50_000_000L,
            isLocked = true,
            triggerReason = "Telemetry Fused"
        )

        runBlocking {
            app.database.clipDao().insertClip(fusedClip)
        }

        composeTestRule.setContent {
            DashGuardProTheme {
                GalleryScreen(
                    viewModel = viewModel,
                    onPlayClip = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Switch to All Clips mode
        composeTestRule.onNodeWithTag("gallery_tab_all_clips").performClick()
        composeTestRule.waitForIdle()

        // Verify Protected filter chip includes the fused locked clip
        composeTestRule.onNodeWithText("🔒 Protected (1)", useUnmergedTree = true).assertExists()

        // Click Protected filter
        composeTestRule.onNodeWithTag("gallery_filter_locked").performClick()
        composeTestRule.waitForIdle()

        // Verify the fused clip is visible under Protected filter
        composeTestRule.onNodeWithTag("video_clip_card_999", useUnmergedTree = true).assertExists()
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testVideoPlayerScreen_smartPlaybackControls_renderedAndInteractive() {
        val baseTime = 1725530000000L
        val clip1 = VideoClip(
            id = 1L,
            filePath = "/storage/emulated/0/DCIM/clip1.mp4",
            fileName = "clip1.mp4",
            timestamp = baseTime,
            durationMs = 60_000L,
            fileSizeBytes = 50_000_000L,
            speedKmh = 45f
        )
        val clip2 = VideoClip(
            id = 2L,
            filePath = "/storage/emulated/0/DCIM/clip2.mp4",
            fileName = "clip2.mp4",
            timestamp = baseTime + 60_000L,
            durationMs = 60_000L,
            fileSizeBytes = 50_000_000L,
            speedKmh = 50f
        )

        val trip = TripSession(
            id = "trip-test",
            startTimeMs = baseTime,
            endTimeMs = baseTime + 120_000L,
            clips = listOf(clip1, clip2),
            totalDurationMs = 120_000L,
            totalDistanceMeters = 2000f,
            avgSpeedKmh = 48f,
            maxSpeedKmh = 55f,
            lockedCount = 0,
            incidentCount = 0
        )

        var selectedNextClip: VideoClip? = null
        var autoAdvanceToggled = false

        composeTestRule.setContent {
            DashGuardProTheme {
                VideoPlayerScreen(
                    clip = clip1,
                    previousClip = null,
                    nextClip = clip2,
                    tripPreviousClip = null,
                    tripNextClip = clip2,
                    trip = trip,
                    onSelectClip = { selectedNextClip = it },
                    onToggleAutoAdvance = { autoAdvanceToggled = true },
                    playbackState = VideoPlaybackState(
                        clipFilePath = clip1.filePath,
                        isPlaying = true,
                        isContinuousPlayback = true,
                        showControls = true
                    ),
                    onUpdatePlaybackState = {},
                    onBack = {},
                    onToggleLock = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        // Skip Previous is disabled since previousClip is null, but node exists
        composeTestRule.onNodeWithTag("player_skip_previous_button", useUnmergedTree = true).assertExists()

        // Skip Next is enabled and clickable
        composeTestRule.onNodeWithTag("player_skip_next_button", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("player_skip_next_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(clip2, selectedNextClip)

        // Auto advance toggle exists and responds to tap
        composeTestRule.onNodeWithTag("player_autonext_toggle_button", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("player_autonext_toggle_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(autoAdvanceToggled)
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testSettingsScreen_rendersSmartPlaybackAndTripContinuitySettings() {
        composeTestRule.setContent {
            DashGuardProTheme {
                SettingsScreen(viewModel = viewModel)
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_lazy_column")
            .performScrollToNode(hasText("SMART PLAYBACK & TRIP CONTINUITY"))

        // Header and settings sub-menu entry for smart playback
        composeTestRule.onNodeWithText("SMART PLAYBACK & TRIP CONTINUITY", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("settings_smart_playback_sub_menu_button", useUnmergedTree = true).assertExists()
            .performClick()

        composeTestRule.waitForIdle()

        // Inside the sub-menu screen:
        composeTestRule.onNodeWithText("Trip Continuity & Playback", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Trip Inactivity Gap", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Player Loop Adjacency Window", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Auto-Advance Trip Loops", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Minimum Trip Duration", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Include Gaps in Trip Duration", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Qualify Single Incident Clips as Trips", useUnmergedTree = true).assertDoesNotExist()

        // Test back navigation from sub-menu
        composeTestRule.onNodeWithTag("btn_back_smart_playback_sub_menu", useUnmergedTree = true).assertExists()
            .performClick()

        composeTestRule.waitForIdle()
        // Verify returned to main settings screen
        composeTestRule.onNodeWithTag("settings_lazy_column", useUnmergedTree = true).assertExists()
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testVideoPlayerScreen_timelinePlaybackResetsToZeroWhenNavigatingLoops() {
        val baseTime = 1725530000000L
        val clip1 = VideoClip(
            id = 1L,
            filePath = "/storage/emulated/0/DCIM/clip1.mp4",
            fileName = "clip1.mp4",
            timestamp = baseTime,
            durationMs = 60_000L,
            fileSizeBytes = 50_000_000L,
            speedKmh = 45f
        )
        val clip2 = VideoClip(
            id = 2L,
            filePath = "/storage/emulated/0/DCIM/clip2.mp4",
            fileName = "clip2.mp4",
            timestamp = baseTime + 60_000L,
            durationMs = 60_000L,
            fileSizeBytes = 50_000_000L,
            speedKmh = 50f
        )

        var lastUpdatedState: VideoPlaybackState? = null
        var selectedNextClip: VideoClip? = null

        composeTestRule.setContent {
            DashGuardProTheme {
                VideoPlayerScreen(
                    clip = clip1,
                    previousClip = null,
                    nextClip = clip2,
                    tripPreviousClip = null,
                    tripNextClip = clip2,
                    onSelectClip = { selectedNextClip = it },
                    playbackState = VideoPlaybackState(
                        clipFilePath = clip1.filePath,
                        isPlaying = true,
                        currentPositionMs = 50_000,
                        showControls = true
                    ),
                    onUpdatePlaybackState = { lastUpdatedState = it },
                    onBack = {},
                    onToggleLock = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        // Verify initial position display is 00:50
        composeTestRule.onNodeWithText("00:50", useUnmergedTree = true).assertExists()

        // Tap Skip Next button
        composeTestRule.onNodeWithTag("player_skip_next_button").performClick()
        composeTestRule.waitForIdle()

        // Verify that onSelectClip was called with clip2
        assertEquals(clip2, selectedNextClip)
        // Verify that playback position was explicitly reset to 0 in updatePlayback
        assertNotNull(lastUpdatedState)
        assertEquals(0, lastUpdatedState?.currentPositionMs)
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testVideoPlayerScreen_clipCompletionWithoutAutoAdvance_pausesAtEndWithoutLooping() {
        val baseTime = 1700000000000L
        val clip = VideoClip(
            id = 1L,
            filePath = "/storage/emulated/0/DCIM/clip1.mp4",
            fileName = "clip1.mp4",
            timestamp = baseTime,
            durationMs = 60_000L,
            fileSizeBytes = 50_000_000L,
            speedKmh = 45f
        )

        var lastUpdatedState: VideoPlaybackState? = null

        composeTestRule.setContent {
            DashGuardProTheme {
                VideoPlayerScreen(
                    clip = clip,
                    previousClip = null,
                    nextClip = null,
                    tripPreviousClip = null,
                    tripNextClip = null,
                    onSelectClip = {},
                    playbackState = VideoPlaybackState(
                        clipFilePath = clip.filePath,
                        isPlaying = true,
                        isContinuousPlayback = false,
                        currentPositionMs = 59_900,
                        showControls = false
                    ),
                    onUpdatePlaybackState = { lastUpdatedState = it },
                    onBack = {},
                    onToggleLock = {}
                )
            }
        }

        composeTestRule.waitUntil(5000) {
            lastUpdatedState?.isPlaying == false
        }

        assertNotNull(lastUpdatedState)
        assertEquals(false, lastUpdatedState?.isPlaying)
        assertEquals(60_000, lastUpdatedState?.currentPositionMs)
        assertEquals(true, lastUpdatedState?.showControls)
        assertEquals(false, lastUpdatedState?.isLooping)
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testVideoPlayerScreen_drivingBreakCard_notShownOnMidClipPause_shownOnlyAtClipEnd() {
        val baseTime = 1700000000000L
        val clip1 = VideoClip(
            id = 1L,
            filePath = "/storage/emulated/0/DCIM/clip1.mp4",
            fileName = "clip1.mp4",
            timestamp = baseTime,
            durationMs = 60_000L,
            fileSizeBytes = 50_000_000L,
            speedKmh = 45f
        )
        // 2 minutes 15 seconds after clip 1 ends -> break between clips
        val clip2 = VideoClip(
            id = 2L,
            filePath = "/storage/emulated/0/DCIM/clip2.mp4",
            fileName = "clip2.mp4",
            timestamp = baseTime + 60_000L + 135_000L,
            durationMs = 60_000L,
            fileSizeBytes = 50_000_000L,
            speedKmh = 45f
        )

        var testPlaybackState by mutableStateOf(
            VideoPlaybackState(
                clipFilePath = clip1.filePath,
                isPlaying = false,
                currentPositionMs = 15_000,
                showControls = true
            )
        )

        composeTestRule.setContent {
            DashGuardProTheme {
                VideoPlayerScreen(
                    clip = clip1,
                    previousClip = null,
                    nextClip = null,
                    tripPreviousClip = null,
                    tripNextClip = clip2,
                    onSelectClip = {},
                    playbackState = testPlaybackState,
                    onUpdatePlaybackState = { testPlaybackState = it },
                    onBack = {},
                    onToggleLock = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Case 1: Paused MID-CLIP at 00:15 -> Resume button must NOT exist
        composeTestRule.onNodeWithText("▶ Resume").assertDoesNotExist()

        // Case 2: Seek to END of clip (01:00)
        testPlaybackState = testPlaybackState.copy(currentPositionMs = 60_000)
        composeTestRule.waitForIdle()

        // Resume button and dynamic text "2m 15s break between clips" must exist at clip end
        composeTestRule.onNodeWithText("▶ Resume").assertExists()
        composeTestRule.onNodeWithText("☕  2m 15s break between clips").assertExists()
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testVideoPlayerScreen_clickingNextAcrossBreakPoint_triggersBreakCardWithResumeButton() {
        val baseTime = 1700000000000L
        val clip1 = VideoClip(
            id = 1L,
            filePath = "/storage/emulated/0/DCIM/clip1.mp4",
            fileName = "clip1.mp4",
            timestamp = baseTime,
            durationMs = 60_000L,
            fileSizeBytes = 50_000_000L,
            speedKmh = 45f
        )
        // 2 minutes 15 seconds after clip 1 ends -> break between clips
        val clip2 = VideoClip(
            id = 2L,
            filePath = "/storage/emulated/0/DCIM/clip2.mp4",
            fileName = "clip2.mp4",
            timestamp = baseTime + 60_000L + 135_000L,
            durationMs = 60_000L,
            fileSizeBytes = 50_000_000L,
            speedKmh = 45f
        )

        var selectedClip: VideoClip? = null
        var playbackState by mutableStateOf(
            VideoPlaybackState(
                clipFilePath = clip1.filePath,
                isPlaying = true,
                currentPositionMs = 20_000,
                showControls = true
            )
        )

        composeTestRule.setContent {
            DashGuardProTheme {
                VideoPlayerScreen(
                    clip = clip1,
                    previousClip = null,
                    nextClip = null,
                    tripPreviousClip = null,
                    tripNextClip = clip2,
                    onSelectClip = { selectedClip = it },
                    playbackState = playbackState,
                    onUpdatePlaybackState = { playbackState = it },
                    onBack = {},
                    onToggleLock = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Initially mid-clip: Resume card is NOT shown
        composeTestRule.onNodeWithText("▶ Resume").assertDoesNotExist()

        // User clicks SkipNext across the break point
        composeTestRule.onNodeWithTag("player_skip_next_button").performClick()
        composeTestRule.waitForIdle()

        // Verify: Playback is paused, break card with Resume button is now triggered!
        composeTestRule.onNodeWithText("▶ Resume").assertExists()
        composeTestRule.onNodeWithText("☕  2m 15s break between clips").assertExists()

        // User clicks Resume button -> advances to clip2!
        composeTestRule.onNodeWithText("▶ Resume").performClick()
        composeTestRule.waitForIdle()
        assertEquals(clip2, selectedClip)
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testVideoPlayerScreen_normalClip_showsWholeTripAndVideoPathButtons_omitsIncidentButton() {
        val normalClip = VideoClip(
            id = 101L,
            filePath = "/storage/emulated/0/DCIM/normal.mp4",
            fileName = "normal.mp4",
            timestamp = 1725530000000L,
            durationMs = 60_000L,
            fileSizeBytes = 50_000_000L,
            speedKmh = 55f,
            startLatitude = 37.4220,
            startLongitude = -122.0840,
            endLatitude = 37.4280,
            endLongitude = -122.0750,
            incidentLatitude = 0.0,
            incidentLongitude = 0.0
        )

        composeTestRule.setContent {
            DashGuardProTheme {
                VideoPlayerScreen(
                    clip = normalClip,
                    onBack = {},
                    onToggleLock = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Whole Trip Map MUST NOT exist for solo clip not part of a multi-clip trip
        composeTestRule.onNodeWithTag("player_whole_trip_map_button", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("player_video_path_button", useUnmergedTree = true).assertExists()

        // Incident button MUST NOT exist when incident coordinates are 0.0
        composeTestRule.onNodeWithTag("player_incident_location_button", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testVideoPlayerScreen_incidentClip_showsIncidentLocationButton() {
        val incidentClip = VideoClip(
            id = 102L,
            filePath = "/storage/emulated/0/DCIM/incident.mp4",
            fileName = "incident.mp4",
            timestamp = 1725530000000L,
            durationMs = 60_000L,
            fileSizeBytes = 50_000_000L,
            speedKmh = 55f,
            startLatitude = 37.4220,
            startLongitude = -122.0840,
            endLatitude = 37.4280,
            endLongitude = -122.0750,
            incidentLatitude = 37.4250,
            incidentLongitude = -122.0795,
            incidentGForce = 3.2f
        )

        composeTestRule.setContent {
            DashGuardProTheme {
                val trip = TripSession(
                    id = "trip_1",
                    startTimeMs = 1725530000000L,
                    endTimeMs = 1725530120000L,
                    clips = listOf(incidentClip, incidentClip.copy(id = 103L, filePath = "/storage/emulated/0/DCIM/incident2.mp4")),
                    totalDurationMs = 120_000L,
                    totalDistanceMeters = 2000f,
                    avgSpeedKmh = 55f,
                    maxSpeedKmh = 65f,
                    lockedCount = 1,
                    incidentCount = 1
                )
                VideoPlayerScreen(
                    clip = incidentClip,
                    trip = trip,
                    onBack = {},
                    onToggleLock = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Both route buttons exist
        composeTestRule.onNodeWithTag("player_whole_trip_map_button", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("player_video_path_button", useUnmergedTree = true).assertExists()

        // Dedicated incident button DOES exist when real incident coordinates are present
        composeTestRule.onNodeWithTag("player_incident_location_button", useUnmergedTree = true).assertExists()
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testVideoPlayerScreen_fullscreenToggle_updatesPlaybackState() {
        val baseTime = 1725530000000L
        val clip = VideoClip(
            id = 1L,
            filePath = "/storage/emulated/0/DCIM/clip1.mp4",
            fileName = "clip1.mp4",
            timestamp = baseTime,
            durationMs = 60_000L,
            fileSizeBytes = 50_000_000L,
            speedKmh = 45f
        )

        var lastPlaybackState: VideoPlaybackState? = null

        composeTestRule.setContent {
            DashGuardProTheme {
                VideoPlayerScreen(
                    clip = clip,
                    playbackState = VideoPlaybackState(
                        clipFilePath = clip.filePath,
                        isPlaying = true,
                        isFullScreen = false,
                        showControls = true
                    ),
                    onUpdatePlaybackState = { lastPlaybackState = it },
                    onBack = {},
                    onToggleLock = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("fullscreen_toggle_button", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("fullscreen_toggle_button").performClick()
        composeTestRule.waitForIdle()

        assertNotNull(lastPlaybackState)
        assertEquals(true, lastPlaybackState?.isFullScreen)
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testGalleryScreen_remembersExpandedTripAndFocusedClipOnPlaybackReturn() {
        val baseTime = 1725530000000L
        val clip1 = VideoClip(
            id = 701L,
            filePath = "/storage/emulated/0/DCIM/LOOP_trip_1.mp4",
            fileName = "LOOP_trip_1.mp4",
            timestamp = baseTime,
            durationMs = 60_000L,
            fileSizeBytes = 50_000_000L
        )
        val clip2 = VideoClip(
            id = 702L,
            filePath = "/storage/emulated/0/DCIM/LOOP_trip_2.mp4",
            fileName = "LOOP_trip_2.mp4",
            timestamp = baseTime + 60_000L,
            durationMs = 60_000L,
            fileSizeBytes = 50_000_000L
        )

        viewModel.updateSettings(viewModel.settings.value.copy(minTripDurationMinutes = 0))

        runBlocking {
            app.database.clipDao().insertClip(clip1)
            app.database.clipDao().insertClip(clip2)
        }

        // Set a specific non-zero scroll position before opening playback
        viewModel.updateGalleryScrollPosition(
            io.github.roaxio.dashguardpro.model.GalleryViewMode.TRIP_LOOPS,
            index = 1,
            offset = 42
        )

        var isPlayingVideo by mutableStateOf(false)

        composeTestRule.setContent {
            val saveableStateHolder = rememberSaveableStateHolder()
            DashGuardProTheme {
                if (!isPlayingVideo) {
                    saveableStateHolder.SaveableStateProvider("GALLERY") {
                        GalleryScreen(
                            viewModel = viewModel,
                            onPlayClip = {
                                viewModel.openClipForPlayback(it)
                                isPlayingVideo = true
                            }
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        val initialIndex = viewModel.galleryNavState.value.tripScrollIndex
        val initialOffset = viewModel.galleryNavState.value.tripScrollOffset

        // Open playback for clip2 - this expands the trip and sets lastPlayedClipFilePath
        viewModel.openClipForPlayback(clip2)
        isPlayingVideo = true
        composeTestRule.waitForIdle()

        // Close playback as if user pressed back button from video playback
        viewModel.closePlayback()
        isPlayingVideo = false
        composeTestRule.waitForIdle()

        // Since the trip is expanded, clip2 card and its inner preview frame are rendered in the composition
        composeTestRule.onNodeWithTag("video_clip_card_702", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("video_clip_preview_702", useUnmergedTree = true).assertExists()
        // Exact scroll position (index & offset) is preserved without snapping the played clip to the top
        assertEquals(initialIndex, viewModel.galleryNavState.value.tripScrollIndex)
        assertEquals(initialOffset, viewModel.galleryNavState.value.tripScrollOffset)
        // And lastPlayedClipFilePath is preserved while pendingScrollToClip is consumed
        assertEquals(clip2.filePath, viewModel.galleryNavState.value.lastPlayedClipFilePath)
        assertEquals(null, viewModel.galleryNavState.value.pendingScrollToClip)
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testSettingsScreen_preservesScrollLocationWhenReturningFromSubMenuAndResetsOnExit() {
        var activeTab by mutableStateOf(NavigationTab.SETTINGS)

        composeTestRule.setContent {
            val saveableStateHolder = rememberSaveableStateHolder()
            DashGuardProTheme {
                saveableStateHolder.SaveableStateProvider(key = activeTab.name) {
                    if (activeTab == NavigationTab.SETTINGS) {
                        SettingsScreen(
                            viewModel = viewModel,
                            onNavigateBack = {
                                saveableStateHolder.removeState(NavigationTab.SETTINGS.name)
                                activeTab = NavigationTab.CAMERA
                            }
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        // Initially at the top of SettingsScreen
        composeTestRule.onNodeWithText("STORAGE & VIDEO DIRECTORY", useUnmergedTree = true).assertExists()

        // Scroll down to Sentry Mode sub-menu card
        composeTestRule.onNodeWithTag("settings_lazy_column")
            .performScrollToNode(hasText("SENTRY MODE (PARK & PROTECT)"))
        composeTestRule.waitForIdle()

        // Top section is now scrolled off-screen
        composeTestRule.onNodeWithText("STORAGE & VIDEO DIRECTORY", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("settings_sentry_sub_menu_button", useUnmergedTree = true).assertExists()
            .performClick()
        composeTestRule.waitForIdle()

        // Verify inside Sentry sub-menu
        composeTestRule.onNodeWithText("Enable Sentry Mode", useUnmergedTree = true).assertExists()

        // Press back from Sentry sub-menu
        composeTestRule.onNodeWithTag("btn_back_sentry_sub_menu", useUnmergedTree = true).assertExists()
            .performClick()
        composeTestRule.waitForIdle()

        // Verify SettingsScreen restored its exact scroll location (Sentry still visible, Storage still off-screen)
        composeTestRule.onNodeWithTag("settings_sentry_sub_menu_button", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("STORAGE & VIDEO DIRECTORY", useUnmergedTree = true).assertDoesNotExist()

        // Exit SettingsScreen via top-bar Back button -> invokes onNavigateBack() which resets Settings scroll memory
        composeTestRule.onNode(
            androidx.compose.ui.test.hasContentDescription("Back to Dashcam HUD")
        ).performClick()
        composeTestRule.waitForIdle()

        // Re-enter SettingsScreen -> starts fresh at the top
        activeTab = NavigationTab.SETTINGS
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("STORAGE & VIDEO DIRECTORY", useUnmergedTree = true).assertExists()
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testOptimizationScreen_preservesScrollLocationWhenReturningFromSubMenuAndResetsOnExit() {
        var activeTab by mutableStateOf(NavigationTab.OPTIMIZATION)

        composeTestRule.setContent {
            val saveableStateHolder = rememberSaveableStateHolder()
            DashGuardProTheme {
                saveableStateHolder.SaveableStateProvider(key = activeTab.name) {
                    if (activeTab == NavigationTab.OPTIMIZATION) {
                        OptimizationScreen(
                            viewModel = viewModel,
                            onNavigateBack = {
                                saveableStateHolder.removeState(NavigationTab.OPTIMIZATION.name)
                                activeTab = NavigationTab.CAMERA
                            }
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        // Initially at the top of OptimizationScreen
        composeTestRule.onNodeWithText("Dedicated In-Car Phone Setup", useUnmergedTree = true).assertExists()

        // Scroll down to Android & OEM Protection sub-menu button
        composeTestRule.onNodeWithTag("optimization_lazy_column")
            .performScrollToNode(hasText("Configure Device Protection →"))
        composeTestRule.waitForIdle()

        // Top overview card is now scrolled off-screen
        composeTestRule.onNodeWithText("Dedicated In-Car Phone Setup", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("btn_open_system_protection_submenu", useUnmergedTree = true).assertExists()
            .performClick()
        composeTestRule.waitForIdle()

        // Verify inside System & OEM Protection sub-menu
        composeTestRule.onNodeWithText("System & OEM Protection", useUnmergedTree = true).assertExists()

        // Press back from System & OEM Protection sub-menu
        composeTestRule.onNodeWithTag("btn_back_system_protection_sub_menu", useUnmergedTree = true).assertExists()
            .performClick()
        composeTestRule.waitForIdle()

        // Verify OptimizationScreen restored its exact scroll location (sub-menu card still visible, top card still off-screen)
        composeTestRule.onNodeWithTag("btn_open_system_protection_submenu", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Dedicated In-Car Phone Setup", useUnmergedTree = true).assertDoesNotExist()

        // Exit OptimizationScreen via top-bar Back button -> invokes onNavigateBack() which resets Optimization scroll memory
        composeTestRule.onNode(
            androidx.compose.ui.test.hasContentDescription("Back to Dashcam HUD")
        ).performClick()
        composeTestRule.waitForIdle()

        // Re-enter OptimizationScreen -> starts fresh at the top
        activeTab = NavigationTab.OPTIMIZATION
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Dedicated In-Car Phone Setup", useUnmergedTree = true).assertExists()
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testSoloClipsBelowMinTripDuration_doNotReturnAdjacentClipsOrAutoAdvance() {
        val baseTime = 1790786126750L
        // Two short clips recorded 3.7 seconds apart (within 15s loopAdjacencyThresholdSeconds),
        // but total duration (2.6s) is below minTripDurationMinutes = 10 -> solo clips (not in any Trip group)
        val soloClip1 = VideoClip(
            id = 264L,
            filePath = "/storage/emulated/0/Movies/DashGuardProCam/VID_DASH_20260930_163526.mp4",
            fileName = "VID_DASH_20260930_163526.mp4",
            timestamp = baseTime,
            durationMs = 1_500L,
            fileSizeBytes = 223_927L,
            triggerReason = "Manual"
        )
        val soloClip2 = VideoClip(
            id = 265L,
            filePath = "/storage/emulated/0/Movies/DashGuardProCam/VID_DASH_20260930_163531.mp4",
            fileName = "VID_DASH_20260930_163531.mp4",
            timestamp = baseTime + 5_239L,
            durationMs = 1_125L,
            fileSizeBytes = 172_525L,
            triggerReason = "Manual Emergency Lock"
        )

        viewModel.updateSettings(
            viewModel.settings.value.copy(
                minTripDurationMinutes = 10,
                loopAdjacencyThresholdSeconds = 15
            )
        )

        runBlocking {
            app.database.clipDao().insertClip(soloClip1)
            app.database.clipDao().insertClip(soloClip2)
        }
        composeTestRule.waitForIdle()

        // Verify neither clip belongs to a qualified TripSession
        assertEquals(null, viewModel.getTripForClip(soloClip1))
        assertEquals(null, viewModel.getTripForClip(soloClip2))

        // Verify getAdjacentClips returns (null, null) for solo clips so playback never auto-advances across solo clips
        assertEquals(Pair(null, null), viewModel.getAdjacentClips(soloClip1))
        assertEquals(Pair(null, null), viewModel.getAdjacentClips(soloClip2))
    }
}

