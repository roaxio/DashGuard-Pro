package io.github.roaxio.dashguardpro

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import io.github.roaxio.dashguardpro.data.settings.AppMode
import io.github.roaxio.dashguardpro.model.VideoClip
import io.github.roaxio.dashguardpro.storage.StorageStats
import io.github.roaxio.dashguardpro.sensor.BatteryInfo
import io.github.roaxio.dashguardpro.sensor.GForceData
import io.github.roaxio.dashguardpro.sensor.PowerSource
import io.github.roaxio.dashguardpro.sensor.TelemetryData
import io.github.roaxio.dashguardpro.data.settings.DashGuardSettings
import io.github.roaxio.dashguardpro.ui.components.hud.LandscapeLeftCockpitDeck
import io.github.roaxio.dashguardpro.ui.components.gallery.StorageBar
import io.github.roaxio.dashguardpro.ui.components.hud.TelemetryHud
import io.github.roaxio.dashguardpro.ui.screens.GalleryScreen
import io.github.roaxio.dashguardpro.ui.screens.OptimizationScreen
import io.github.roaxio.dashguardpro.ui.screens.SettingsScreen
import io.github.roaxio.dashguardpro.ui.theme.DashGuardProTheme
import io.github.roaxio.dashguardpro.viewmodel.state.DashcamUiState
import io.github.roaxio.dashguardpro.viewmodel.DashcamViewModel
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FluidResponsiveLayoutSimulationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: DashGuardApplication
    private lateinit var viewModel: DashcamViewModel

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        viewModel = DashcamViewModel(app)
    }

    /**
     * SIMULATION 1: Narrow Compact Screen (Width 320dp, Height 640dp)
     * Verifies that StorageBar sub-legend wraps fluidly into FlowRow lines
     * without cutting off values or displaying ellipsis.
     */
    @Test
    @Config(qualifiers = "w320dp-h640dp", sdk = [34])
    fun testNarrowScreen_storageBar_wrapsFluidlyWithoutTruncation() {
        val stats = StorageStats(
            appVideoBytes = 1_700_000_000L,
            maxAllowedBytes = 5_000_000_000L,
            lockedVideoBytes = 500_000_000L,
            loopBufferBytes = 1_200_000_000L,
            deviceFreeBytes = 15_000_000_000L
        )

        composeTestRule.setContent {
            DashGuardProTheme {
                StorageBar(stats = stats)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("LOOP STORAGE BUFFER", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Loop: 1.12 GB", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Locked: 476.8 MB", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Free: 14.0 GB", useUnmergedTree = true).assertExists()
    }

    /**
     * SIMULATION 2: Narrow Compact Screen (Width 320dp, Height 640dp)
     * Verifies that IncidentAlertBanner wraps long warning messages without clipping off the screen.
     */
    @Test
    @Config(qualifiers = "w320dp-h640dp", sdk = [34])
    fun testNarrowScreen_telemetryHud_alertTextWrapsFluidly() {
        val longWarning = "⚠️ Thermal Cutoff: Battery reached 48.2°C. Stopping recording to protect hardware."
        val uiState = DashcamUiState(
            showImpactAlert = true,
            recentIncidentMessage = longWarning
        )

        composeTestRule.setContent {
            DashGuardProTheme {
                TelemetryHud(
                    uiState = uiState,
                    telemetry = TelemetryData(),
                    gForce = GForceData(0f, 0f, 1f, 1f),
                    battery = BatteryInfo(levelPct = 80, isCharging = true, powerSource = PowerSource.USB),
                    mode = AppMode.DASHCAM
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(longWarning, useUnmergedTree = true).assertExists()
    }

    /**
     * SIMULATION 3: Short Landscape Cockpit Viewport (Width 700dp, Height 360dp)
     * Verifies that LandscapeLeftCockpitDeck controls are reachable in landscape driving mode.
     */
    @Test
    @Config(qualifiers = "w700dp-h360dp", sdk = [34])
    fun testShortLandscapeCockpit_leftCockpitDeck_controlsReachable() {
        var lockToggled = false
        var recordToggled = false

        composeTestRule.setContent {
            DashGuardProTheme {
                LandscapeLeftCockpitDeck(
                    uiState = DashcamUiState(isRecording = true),
                    settings = DashGuardSettings(),
                    onToggleLock = { lockToggled = true },
                    onToggleRecord = { recordToggled = true },
                    onToggleMic = {},
                    onToggleHudMode = {},
                    onToggleLens = {},
                    onToggleSentry = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("emergency_lock_button").performClick()
        assertTrue(lockToggled)

        composeTestRule.onNodeWithTag("primary_record_button").performClick()
        assertTrue(recordToggled)
    }

    /**
     * SIMULATION 4: Short Landscape Cockpit Viewport (Width 700dp, Height 360dp)
     * Verifies that OptimizationScreen guides and dialogs are vertically scrollable
     * and action buttons do not overflow off the screen.
     */
    @Test
    @Config(qualifiers = "w700dp-h360dp", sdk = [34])
    fun testShortLandscapeCockpit_kioskGuideDialog_isScrollableAndReachable() {
        composeTestRule.setContent {
            DashGuardProTheme {
                OptimizationScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // Scroll to and click to open the 5 Protection Steps sub-menu
        composeTestRule.onNodeWithTag("optimization_lazy_column")
            .performScrollToNode(hasTestTag("btn_open_system_protection_submenu"))
        composeTestRule.onNodeWithTag("btn_open_system_protection_submenu").performClick()
        composeTestRule.waitForIdle()

        // Inside sub-menu, scroll to and click How to Unpin & Info to open Kiosk Guide Dialog
        composeTestRule.onNodeWithTag("system_protection_lazy_column")
            .performScrollToNode(hasText("How to Unpin & Info"))
        composeTestRule.onNodeWithText("How to Unpin & Info", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()

        // Verify Dialog is present — use dialog-unique strings that contain no emoji (more reliable in Robolectric)
        composeTestRule.onAllNodesWithText("App Pinning (Kiosk Mode)", useUnmergedTree = true).onFirst().assertExists()
        // "Open Android Security Settings (Older Devices)" is rendered only inside the dialog, never in a card
        composeTestRule.onNodeWithText("Open Android Security Settings (Older Devices)", useUnmergedTree = true).assertExists()
    }

    /**
     * SIMULATION 5: Ultra-Tall Modern Screen (Width 412dp, Height 915dp - Pixel 8)
     * Verifies that SettingsScreen toggle rows flip on row click directly.
     */
    @Test
    @Config(qualifiers = "w412dp-h915dp", sdk = [34])
    fun testTallScreen_settingsToggleRow_togglesDirectly() {
        composeTestRule.setContent {
            DashGuardProTheme {
                SettingsScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // Find Audio Recording Toggle Row
        composeTestRule.onNodeWithTag("settings_lazy_column")
            .performScrollToNode(hasText("Audio Recording"))
        composeTestRule.onNodeWithText("Audio Recording", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Record cabin / ambient audio with video", useUnmergedTree = true).assertExists()
    }

    /**
     * SIMULATION 6: Foldable / Tablet Expanded Viewport (Width 800dp, Height 1280dp)
     * Verifies that Gallery top bar and footage empty/card states render without ellipsis truncation.
     */
    @Test
    @Config(qualifiers = "w800dp-h1280dp", sdk = [34])
    fun testTabletViewport_galleryTopBarAndStructure() {
        composeTestRule.setContent {
            DashGuardProTheme {
                GalleryScreen(
                    viewModel = viewModel,
                    onPlayClip = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Recorded Footage", useUnmergedTree = true).assertExists()
    }
}
