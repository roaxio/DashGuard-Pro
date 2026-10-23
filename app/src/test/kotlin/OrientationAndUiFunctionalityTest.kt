package io.github.roaxio.dashguardpro

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import io.github.roaxio.dashguardpro.data.settings.AppMode
import io.github.roaxio.dashguardpro.model.VideoClip
import io.github.roaxio.dashguardpro.sensor.BatteryInfo
import io.github.roaxio.dashguardpro.sensor.GForceData
import io.github.roaxio.dashguardpro.sensor.PowerSource
import io.github.roaxio.dashguardpro.sensor.TelemetryData
import io.github.roaxio.dashguardpro.ui.components.hud.PortraitCockpitDeck
import io.github.roaxio.dashguardpro.ui.components.hud.OledPowerSaverOverlay
import io.github.roaxio.dashguardpro.ui.components.SpeedSafetyLockoutOverlay
import io.github.roaxio.dashguardpro.ui.components.hud.TelemetryHud
import io.github.roaxio.dashguardpro.ui.screens.GalleryScreen
import io.github.roaxio.dashguardpro.ui.screens.OptimizationScreen
import io.github.roaxio.dashguardpro.ui.screens.SettingsScreen
import io.github.roaxio.dashguardpro.ui.screens.VideoPlayerScreen
import io.github.roaxio.dashguardpro.ui.theme.DashGuardProTheme
import io.github.roaxio.dashguardpro.viewmodel.state.DashcamUiState
import io.github.roaxio.dashguardpro.viewmodel.DashcamViewModel
import io.github.roaxio.dashguardpro.viewmodel.state.DeviceSleepMode
import io.github.roaxio.dashguardpro.viewmodel.state.TripStats
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Multi-Orientation (Portrait & Landscape) and Comprehensive UI Functionality Test Suite.
 * Validates responsive UI layouts, interactive controls, HUDs, overlays, and user flows
 * across device orientations using an active ComponentActivity lifecycle.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OrientationAndUiFunctionalityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: DashGuardApplication
    private lateinit var viewModel: DashcamViewModel

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        viewModel = DashcamViewModel(app)
    }

    // =========================================================================
    // 1. PORTRAIT ORIENTATION TESTS
    // =========================================================================

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testPortrait_telemetryHud_displaysAllMetricsAndAlerts() {
        val uiState = DashcamUiState(
            hudDisplayMode = io.github.roaxio.dashguardpro.data.settings.HudDisplayMode.FULL,
            isRecording = true,
            segmentSeconds = 120,
            loopSegmentMinutes = 3,
            targetSegmentSeconds = 180,
            isSegmentLocked = true,
            lastTriggerReason = "💥 G-Force Shock (2.8G)"
        )
        val telemetry = TelemetryData(
            speedKmh = 88.0f,
            speedDisplay = 88.0f,
            speedUnitLabel = "km/h",
            headingDegrees = 90f,
            headingCardinal = "E",
            latitude = 37.7749,
            longitude = -122.4194,
            altitudeMeters = 42.0,
            hasGpsFix = true,
            accuracyMeters = 2.1f
        )
        val gForce = GForceData(
            x = 0.2f,
            y = 0.3f,
            z = 0.95f,
            totalG = 1.05f,
            impactDetected = false
        )
        val battery = BatteryInfo(
            levelPct = 95,
            isCharging = true,
            powerSource = PowerSource.USB,
            temperatureC = 33.5f
        )

        composeTestRule.setContent {
            DashGuardProTheme {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0F1D))) {
                    TelemetryHud(
                        uiState = uiState,
                        telemetry = telemetry,
                        gForce = gForce,
                        battery = battery,
                        mode = AppMode.DASHCAM
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        // Assert core metrics exist in semantics tree
        composeTestRule.onNodeWithText("REC", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("GPS 3D", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("95%", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("33°C", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("KM/H", useUnmergedTree = true).assertExists()
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testPortrait_galleryScreen_rendersAndFilters() {
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

        // Top bar title is rendered
        composeTestRule.onNodeWithText("Recorded Footage", useUnmergedTree = true).assertExists()
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testPortrait_settingsScreen_rendersOptions() {
        composeTestRule.setContent {
            DashGuardProTheme {
                SettingsScreen(viewModel = viewModel)
            }
        }

        composeTestRule.waitForIdle()

        // Settings Screen title
        composeTestRule.onNodeWithText("DashGuard Settings", useUnmergedTree = true).assertExists()
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testSettingsScreen_clickConsolidatedCard_navigatesToOptimization() {
        var navigatedToOpt = false
        composeTestRule.setContent {
            DashGuardProTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToOptimization = { navigatedToOpt = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_lazy_column")
            .performScrollToNode(hasTestTag("btn_go_to_optimize"))

        composeTestRule.onNodeWithTag("btn_go_to_optimize", useUnmergedTree = true)
            .performClick()

        assertTrue(navigatedToOpt)
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testPortrait_optimizationScreen_rendersDiagnostics() {
        composeTestRule.setContent {
            DashGuardProTheme {
                OptimizationScreen(viewModel = viewModel)
            }
        }

        composeTestRule.waitForIdle()

        // Diagnostics title and headers
        composeTestRule.onNodeWithText("Dedicated Dashcam Optimization", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("HARDWARE & SYSTEM DIAGNOSTICS", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Battery Temperature", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Power Source", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Storage Health & Free Quota", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("GPS Telemetry Fix", useUnmergedTree = true).assertExists()
    }

    // =========================================================================
    // 2. LANDSCAPE ORIENTATION TESTS (Automotive Cockpit Mode)
    // =========================================================================

    @Test
    @Config(qualifiers = "+land", sdk = [34])
    fun testLandscape_cockpitTelemetryHud_rendersHighContrastLandscapeLayout() {
        val uiState = DashcamUiState(
            hudDisplayMode = io.github.roaxio.dashguardpro.data.settings.HudDisplayMode.FULL,
            isRecording = true,
            segmentSeconds = 55,
            loopSegmentMinutes = 1,
            targetSegmentSeconds = 60,
            isSegmentLocked = false,
            tripStats = TripStats(
                driveSeconds = 600,
                distanceKm = 12.5f,
                maxSpeedKmh = 110.2f,
                avgSpeedKmh = 75.0f
            )
        )
        val telemetry = TelemetryData(
            speedKmh = 92.0f,
            speedDisplay = 92.0f,
            speedUnitLabel = "km/h",
            headingDegrees = 270f,
            headingCardinal = "W",
            hasGpsFix = true,
            accuracyMeters = 1.8f
        )
        val gForce = GForceData(x = 0.05f, y = 0.1f, z = 0.99f, totalG = 1.0f)
        val battery = BatteryInfo(levelPct = 100, isCharging = true, powerSource = PowerSource.AC)

        composeTestRule.setContent {
            DashGuardProTheme {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF04070D))) {
                    TelemetryHud(
                        uiState = uiState,
                        telemetry = telemetry,
                        gForce = gForce,
                        battery = battery,
                        mode = AppMode.DASHCAM
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        // Assert landscape cockpit speed & telemetry exist
        composeTestRule.onNodeWithText("REC", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("100%", useUnmergedTree = true).assertExists()
    }

    @Test
    @Config(qualifiers = "+land", sdk = [34])
    fun testLandscape_oledPowerSaverOverlay_rendersOverlayElements() {
        val uiState = DashcamUiState(
            isRecording = true,
            segmentSeconds = 90,
            totalSessionMinutes = 15,
            isSegmentLocked = true,
            lastTriggerReason = "Manual Emergency Lock"
        )
        val battery = BatteryInfo(levelPct = 80, isCharging = true, powerSource = PowerSource.USB, temperatureC = 31.0f)

        composeTestRule.setContent {
            DashGuardProTheme {
                OledPowerSaverOverlay(
                    uiState = uiState,
                    battery = battery,
                    mode = AppMode.DASHCAM,
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        // Verify OLED stealth overlay is mounted
        composeTestRule.onNodeWithTag("oled_stealth_overlay").assertExists()
        composeTestRule.onNodeWithText("SURVEILLANCE ACTIVE", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("80%", useUnmergedTree = true).assertExists()
    }

    @Test
    @Config(qualifiers = "+land", sdk = [34])
    fun testLandscape_speedSafetyLockoutOverlay_rendersAndReturnsToHud() {
        composeTestRule.setContent {
            DashGuardProTheme {
                SpeedSafetyLockoutOverlay(
                    currentSpeedKmh = 45.0f,
                    onReturnToHud = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("speed_lockout_overlay").assertExists()
        composeTestRule.onNodeWithText("DRIVING SAFETY LOCKOUT", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("return_to_hud_button", useUnmergedTree = true).assertExists()
    }

    @Test
    @Config(qualifiers = "+land", sdk = [34])
    fun testLandscape_videoPlayerScreen_rendersTelemetryAndControls() {
        val clip = VideoClip(
            id = 42,
            filePath = "/mock/sample.mp4",
            fileName = "VID_DASH_20260101_150000.mp4",
            timestamp = System.currentTimeMillis(),
            durationMs = 180000L,
            fileSizeBytes = 45L * 1024 * 1024,
            isLocked = true,
            triggerReason = "Manual Emergency Lock",
            speedKmh = 68.5f,
            avgSpeedKmh = 62.0f,
            distanceMeters = 3100f,
            incidentGForce = 1.8f
        )

        composeTestRule.setContent {
            DashGuardProTheme {
                VideoPlayerScreen(
                    clip = clip,
                    onBack = {},
                    onToggleLock = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        // Verify Clip details are rendered
        composeTestRule.onNodeWithText("VID_DASH_20260101_150000.mp4", useUnmergedTree = true).assertExists()
        composeTestRule.onAllNodesWithText("Manual Emergency Lock", useUnmergedTree = true).onFirst().assertExists()
    }

    // =========================================================================
    // 3. PORTRAIT COCKPIT DECK TESTS
    // =========================================================================

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testPortraitCockpitDeck_optionsAndActions() {
        var lockToggled = false
        var recordToggled = false

        composeTestRule.setContent {
            DashGuardProTheme {
                PortraitCockpitDeck(
                    uiState = DashcamUiState(),
                    settingsMode = AppMode.DASHCAM,
                    micEnabled = true,
                    onToggleLock = { lockToggled = true },
                    onToggleRecord = { recordToggled = true },
                    onToggleMic = {},
                    onToggleHudMode = {},
                    onToggleLens = {},
                    onToggleOled = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        // Assert controls are present and clickable
        composeTestRule.onNodeWithTag("emergency_lock_button").performClick()
        assertTrue(lockToggled)

        composeTestRule.onNodeWithTag("primary_record_button").performClick()
        assertTrue(recordToggled)
    }

    // =========================================================================
    // 4. RECORDING ORIENTATION LOCK TESTS
    // =========================================================================

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testPortrait_recordingActive_locksOrientationToPortrait_andUnlocksWhenStopped() {
        composeTestRule.setContent {
            DashGuardProTheme {
                DashGuardApp(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // 1. Initial Standby State: full user auto-rotation enabled
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, composeTestRule.activity.requestedOrientation)

        // 2. Start Recording in Portrait
        viewModel.updateUiState { it.copy(isRecording = true) }
        composeTestRule.waitForIdle()

        // 3. Invariant Enforced: Activity is locked to Portrait
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, composeTestRule.activity.requestedOrientation)

        // 4. Stop Recording
        viewModel.updateUiState { it.copy(isRecording = false) }
        composeTestRule.waitForIdle()

        // 5. Invariant Restored: Returned to full user auto-rotation
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, composeTestRule.activity.requestedOrientation)
    }

    @Test
    @Config(qualifiers = "+land", sdk = [34])
    fun testLandscape_recordingActive_locksOrientationToLandscape_andUnlocksWhenStopped() {
        composeTestRule.setContent {
            DashGuardProTheme {
                DashGuardApp(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // 1. Initial Standby State: full user auto-rotation enabled
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, composeTestRule.activity.requestedOrientation)

        // 2. Start Recording in Landscape
        viewModel.updateUiState { it.copy(isRecording = true) }
        composeTestRule.waitForIdle()

        // 3. Invariant Enforced: Activity is locked to Landscape
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, composeTestRule.activity.requestedOrientation)

        // 4. Stop Recording
        viewModel.updateUiState { it.copy(isRecording = false) }
        composeTestRule.waitForIdle()

        // 5. Invariant Restored: Returned to full user auto-rotation
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, composeTestRule.activity.requestedOrientation)
    }

    @Test
    fun testHudDisplayMode_defaultIsCompact_andCyclePersistsToSettings() {
        // 1. Invariant: Default HUD display mode is COMPACT
        assertEquals(io.github.roaxio.dashguardpro.data.settings.HudDisplayMode.COMPACT, viewModel.uiState.value.hudDisplayMode)
        assertEquals(io.github.roaxio.dashguardpro.data.settings.HudDisplayMode.COMPACT, viewModel.settings.value.hudDisplayMode)

        // 2. Cycle mode -> FULL
        viewModel.cycleHudDisplayMode()
        assertEquals(io.github.roaxio.dashguardpro.data.settings.HudDisplayMode.FULL, viewModel.uiState.value.hudDisplayMode)
        assertEquals(io.github.roaxio.dashguardpro.data.settings.HudDisplayMode.FULL, viewModel.settings.value.hudDisplayMode)

        // 3. Cycle mode -> COMPACT
        viewModel.cycleHudDisplayMode()
        assertEquals(io.github.roaxio.dashguardpro.data.settings.HudDisplayMode.COMPACT, viewModel.uiState.value.hudDisplayMode)
        assertEquals(io.github.roaxio.dashguardpro.data.settings.HudDisplayMode.COMPACT, viewModel.settings.value.hudDisplayMode)
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testOledPowerSaverOverlay_rendersSentryArmedAndThermalCoolingStates() {
        val sentryUiState = DashcamUiState(
            isSentryArmed = true,
            isScreenDimmed = true
        )
        val battery = BatteryInfo(levelPct = 85, isCharging = false, temperatureC = 32.0f)

        composeTestRule.setContent {
            DashGuardProTheme {
                OledPowerSaverOverlay(
                    uiState = sentryUiState,
                    battery = battery,
                    mode = AppMode.SENTRY,
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Tap pitch-black overlay to reveal status HUD (Tap-to-Peek)
        composeTestRule.onNodeWithTag("oled_stealth_overlay").performClick()
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("🛡️ SENTRY ARMED", useUnmergedTree = true).assertExists()
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testOledPowerSaverOverlay_rendersThermalCoolingState() {
        val thermalUiState = DashcamUiState(
            deviceSleepMode = DeviceSleepMode.THERMAL_COOLING,
            thermalCoolingTempC = 48.5f,
            thermalCoolingTargetC = 44.0f
        )
        val battery = BatteryInfo(levelPct = 85, isCharging = true, temperatureC = 48.5f)

        composeTestRule.setContent {
            DashGuardProTheme {
                OledPowerSaverOverlay(
                    uiState = thermalUiState,
                    battery = battery,
                    mode = AppMode.DASHCAM,
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Tap pitch-black overlay to reveal cooling HUD
        composeTestRule.onNodeWithTag("oled_stealth_overlay").performClick()
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("❄️ COOLING ACTIVE", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Camera & GPS paused • Emergency Shock Armed", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testSettingsScreen_rendersSentryCard() {
        composeTestRule.setContent {
            DashGuardProTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_lazy_column")
            .performScrollToNode(hasTestTag("settings_sentry_sub_menu_button"))
        composeTestRule.onNodeWithTag("settings_sentry_sub_menu_button", useUnmergedTree = true).assertExists()
            .performClick()

        composeTestRule.waitForIdle()

        // Inside the Sentry sub-menu screen:
        composeTestRule.onNodeWithText("Enable Sentry Mode", useUnmergedTree = true).assertExists()

        // When in Tier 2 (OPTICAL_MOTION) with Sentry enabled, demotion threshold rows must be displayed
        viewModel.updateSettings(
            viewModel.settings.value.copy(
                sentryModeEnabled = true,
                sentryGuardType = io.github.roaxio.dashguardpro.data.settings.SentryGuardType.OPTICAL_MOTION
            )
        )
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("sentry_sub_menu_lazy_column")
            .performScrollToNode(hasText("Tier 2 Demotion Battery Floor"))
        composeTestRule.onNodeWithText("Tier 2 Demotion Battery Floor", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Tier 2 Demotion Temperature Limit", useUnmergedTree = true).assertExists()

        // Test back navigation from Sentry sub-menu
        composeTestRule.onNodeWithTag("btn_back_sentry_sub_menu", useUnmergedTree = true).assertExists()
            .performClick()

        composeTestRule.waitForIdle()
        // Verify returned to main settings screen
        composeTestRule.onNodeWithTag("settings_lazy_column", useUnmergedTree = true).assertExists()
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testOptimizationScreen_rendersMidDriveThermalRecovery() {
        composeTestRule.setContent {
            DashGuardProTheme {
                OptimizationScreen(
                    viewModel = viewModel,
                    onNavigateBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("optimization_lazy_column")
            .performScrollToNode(hasText("Mid-Drive Thermal Recovery Guard"))
        composeTestRule.onNodeWithText("Mid-Drive Thermal Recovery Guard", useUnmergedTree = true).assertExists()
        // Default maxTemp is 48°C -> recovery is 44°C
        composeTestRule.onNodeWithText("cooled below 44°C", substring = true, useUnmergedTree = true).assertExists()

        // Switch to 50°C cutoff -> recovery should dynamically become 46°C
        viewModel.updateSettings(viewModel.settings.value.copy(maxTempThresholdC = 50.0f))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("cooled below 46°C", substring = true, useUnmergedTree = true).assertExists()

        // Disable thermal cutoff -> warning banner should appear and recovery should state inactive
        viewModel.updateSettings(viewModel.settings.value.copy(maxTempThresholdC = 0.0f))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Thermal Safety Cutoff Disabled", substring = true, useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Max Temperature Cutoff is disabled", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testSettingsScreen_sectionOrder_sensorsBeforeTripContinuity() {
        composeTestRule.setContent {
            DashGuardProTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Verify INTELLIGENT SENSORS & IMPACTS section is reachable first
        composeTestRule.onNodeWithTag("settings_lazy_column")
            .performScrollToNode(hasText("INTELLIGENT SENSORS & IMPACTS"))
        composeTestRule.onNodeWithText("INTELLIGENT SENSORS & IMPACTS", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("G-Force Impact Sensitivity", useUnmergedTree = true).assertExists()

        // 2. Verify SMART PLAYBACK & TRIP CONTINUITY follows
        composeTestRule.onNodeWithTag("settings_lazy_column")
            .performScrollToNode(hasText("SMART PLAYBACK & TRIP CONTINUITY"))
        composeTestRule.onNodeWithText("SMART PLAYBACK & TRIP CONTINUITY", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("settings_smart_playback_sub_menu_button", useUnmergedTree = true).assertExists()

        // 3. Verify SENTRY MODE (PARK & PROTECT) sub-menu button follows
        composeTestRule.onNodeWithTag("settings_lazy_column")
            .performScrollToNode(hasText("SENTRY MODE (PARK & PROTECT)"))
        composeTestRule.onNodeWithText("SENTRY MODE (PARK & PROTECT)", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("settings_sentry_sub_menu_button", useUnmergedTree = true).assertExists()
    }
}
