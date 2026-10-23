package io.github.roaxio.dashguardpro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.github.roaxio.dashguardpro.data.settings.AppMode
import io.github.roaxio.dashguardpro.model.VideoClip
import io.github.roaxio.dashguardpro.storage.StorageStats
import io.github.roaxio.dashguardpro.sensor.BatteryInfo
import io.github.roaxio.dashguardpro.sensor.GForceData
import io.github.roaxio.dashguardpro.sensor.PowerSource
import io.github.roaxio.dashguardpro.sensor.TelemetryData
import io.github.roaxio.dashguardpro.ui.components.hud.OledPowerSaverOverlay
import io.github.roaxio.dashguardpro.ui.components.SpeedSafetyLockoutOverlay
import io.github.roaxio.dashguardpro.ui.components.gallery.StorageBar
import io.github.roaxio.dashguardpro.ui.components.hud.TelemetryHud
import io.github.roaxio.dashguardpro.ui.screens.GalleryScreen
import io.github.roaxio.dashguardpro.ui.screens.OptimizationScreen
import io.github.roaxio.dashguardpro.ui.screens.SettingsScreen
import io.github.roaxio.dashguardpro.ui.screens.VideoPlayerScreen
import io.github.roaxio.dashguardpro.ui.theme.DashGuardProTheme
import io.github.roaxio.dashguardpro.viewmodel.state.DashcamUiState
import io.github.roaxio.dashguardpro.viewmodel.DashcamViewModel
import io.github.roaxio.dashguardpro.viewmodel.state.TripStats
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi Native Graphics Screenshot Test Suite for Portrait & Landscape Orientations.
 * Captures pixel-level reference screenshots across all key cockpit views, overlays,
 * and data visualizers.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OrientationScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: DashGuardApplication
    private lateinit var viewModel: DashcamViewModel

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        viewModel = DashcamViewModel(app)
    }

    private val sampleUiState = DashcamUiState(
        isRecording = true,
        segmentSeconds = 95,
        loopSegmentMinutes = 3,
        targetSegmentSeconds = 180,
        totalSessionMinutes = 12,
        isSegmentLocked = true,
        lastTriggerReason = "💥 G-Force Shock (3.2G)",
        tripStats = TripStats(
            driveSeconds = 720,
            distanceKm = 15.4f,
            maxSpeedKmh = 104.5f,
            avgSpeedKmh = 77.0f,
            lockedIncidentsCount = 1
        )
    )

    private val sampleTelemetry = TelemetryData(
        speedKmh = 84.0f,
        speedDisplay = 84.0f,
        speedUnitLabel = "km/h",
        headingDegrees = 45f,
        headingCardinal = "NE",
        latitude = 37.7749,
        longitude = -122.4194,
        altitudeMeters = 38.0,
        hasGpsFix = true,
        accuracyMeters = 2.4f
    )

    private val sampleGForce = GForceData(
        x = 0.15f,
        y = 0.25f,
        z = 0.98f,
        totalG = 1.04f,
        impactDetected = false
    )

    private val sampleBattery = BatteryInfo(
        levelPct = 91,
        isCharging = true,
        powerSource = PowerSource.USB,
        temperatureC = 34.1f
    )

    // =========================================================================
    // 1. PORTRAIT ORIENTATION SCREENSHOTS
    // =========================================================================

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun screenshot_portrait_telemetryHud_dashcamMode() {
        composeTestRule.setContent {
            DashGuardProTheme {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF070B14))) {
                    TelemetryHud(
                        uiState = sampleUiState,
                        telemetry = sampleTelemetry,
                        gForce = sampleGForce,
                        battery = sampleBattery,
                        mode = AppMode.DASHCAM
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/portrait_telemetry_hud_dashcam.png")
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun screenshot_portrait_telemetryHud_sentryMode() {
        composeTestRule.setContent {
            DashGuardProTheme {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF070B14))) {
                    TelemetryHud(
                        uiState = sampleUiState.copy(lastTriggerReason = "🛡️ Sentry Active (Standing Guard)"),
                        telemetry = sampleTelemetry.copy(speedKmh = 0f, speedDisplay = 0f),
                        gForce = sampleGForce,
                        battery = sampleBattery,
                        mode = AppMode.SENTRY
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/portrait_telemetry_hud_sentry.png")
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun screenshot_portrait_galleryScreen() {
        composeTestRule.setContent {
            DashGuardProTheme {
                GalleryScreen(
                    viewModel = viewModel,
                    onPlayClip = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/portrait_gallery_screen.png")
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun screenshot_portrait_settingsScreen() {
        composeTestRule.setContent {
            DashGuardProTheme {
                SettingsScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/portrait_settings_screen.png")
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun screenshot_portrait_optimizationScreen() {
        composeTestRule.setContent {
            DashGuardProTheme {
                OptimizationScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/portrait_optimization_screen.png")
    }

    // =========================================================================
    // 2. LANDSCAPE ORIENTATION SCREENSHOTS (Automotive Cockpit Mode)
    // =========================================================================

    @Test
    @Config(qualifiers = "+land", sdk = [34])
    fun screenshot_landscape_telemetryHud_dashcamCockpit() {
        composeTestRule.setContent {
            DashGuardProTheme {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF04070D))) {
                    TelemetryHud(
                        uiState = sampleUiState,
                        telemetry = sampleTelemetry,
                        gForce = sampleGForce,
                        battery = sampleBattery,
                        mode = AppMode.DASHCAM
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/landscape_telemetry_hud_dashcam.png")
    }

    @Test
    @Config(qualifiers = "+land", sdk = [34])
    fun screenshot_landscape_telemetryHud_sentryRadarCockpit() {
        composeTestRule.setContent {
            DashGuardProTheme {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF04070D))) {
                    TelemetryHud(
                        uiState = sampleUiState.copy(lastTriggerReason = "🛡️ Sentry Active (Radar Armed)"),
                        telemetry = sampleTelemetry.copy(speedKmh = 0f, speedDisplay = 0f),
                        gForce = sampleGForce,
                        battery = sampleBattery,
                        mode = AppMode.SENTRY
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/landscape_telemetry_hud_sentry.png")
    }

    @Test
    @Config(qualifiers = "+land", sdk = [34])
    fun screenshot_landscape_oledPowerSaverOverlay() {
        composeTestRule.setContent {
            DashGuardProTheme {
                OledPowerSaverOverlay(
                    uiState = sampleUiState,
                    battery = sampleBattery,
                    mode = AppMode.DASHCAM,
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/landscape_oled_power_saver.png")
    }

    @Test
    @Config(qualifiers = "+land", sdk = [34])
    fun screenshot_landscape_speedSafetyLockout() {
        composeTestRule.setContent {
            DashGuardProTheme {
                SpeedSafetyLockoutOverlay(
                    currentSpeedKmh = 62.5f,
                    onReturnToHud = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/landscape_speed_safety_lockout.png")
    }

    @Test
    @Config(qualifiers = "+land", sdk = [34])
    fun screenshot_landscape_videoPlayer_incidentInspector() {
        val testClip = VideoClip(
            id = 99,
            filePath = "/mock/path/video_test.mp4",
            fileName = "VID_DASH_20260101_180000.mp4",
            timestamp = System.currentTimeMillis(),
            durationMs = 180000L,
            fileSizeBytes = 50L * 1024 * 1024,
            isLocked = true,
            triggerReason = "💥 G-Force Shock (3.2G)",
            speedKmh = 76.5f,
            avgSpeedKmh = 71.0f,
            distanceMeters = 3800f,
            incidentGForce = 3.2f,
            incidentVideoOffsetSeconds = 45
        )

        composeTestRule.setContent {
            DashGuardProTheme {
                VideoPlayerScreen(
                    clip = testClip,
                    onBack = {},
                    onToggleLock = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/landscape_video_player_inspector.png")
    }
}
