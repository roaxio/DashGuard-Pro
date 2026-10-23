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
import io.github.roaxio.dashguardpro.data.settings.AppMode
import io.github.roaxio.dashguardpro.storage.StorageStats
import io.github.roaxio.dashguardpro.sensor.BatteryInfo
import io.github.roaxio.dashguardpro.sensor.GForceData
import io.github.roaxio.dashguardpro.sensor.PowerSource
import io.github.roaxio.dashguardpro.sensor.TelemetryData
import io.github.roaxio.dashguardpro.ui.components.hud.OledPowerSaverOverlay
import io.github.roaxio.dashguardpro.ui.components.gallery.StorageBar
import io.github.roaxio.dashguardpro.ui.components.hud.TelemetryHud
import io.github.roaxio.dashguardpro.ui.theme.DashGuardProTheme
import io.github.roaxio.dashguardpro.viewmodel.state.DashcamUiState
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class DashcamScreensScreenshotTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun testOledPowerSaverOverlay_render() {
        composeTestRule.setContent {
            DashGuardProTheme {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    OledPowerSaverOverlay(
                        uiState = DashcamUiState(
                            isRecording = true,
                            segmentSeconds = 125,
                            totalSessionMinutes = 14,
                            isSegmentLocked = true,
                            lastTriggerReason = "Manual Emergency Lock"
                        ),
                        battery = BatteryInfo(
                            levelPct = 88,
                            isCharging = true,
                            powerSource = PowerSource.USB,
                            temperatureC = 34.2f
                        ),
                        mode = AppMode.DASHCAM,
                        onDismiss = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/oled_overlay.png")
    }

    @Test
    fun testTelemetryHud_render() {
        composeTestRule.setContent {
            DashGuardProTheme {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0F1D))) {
                    TelemetryHud(
                        uiState = DashcamUiState(
                            isRecording = true,
                            segmentSeconds = 45,
                            totalSessionMinutes = 5,
                            isSegmentLocked = true,
                            lastTriggerReason = "💥 G-Force Shock (3.1G)"
                        ),
                        telemetry = TelemetryData(
                            speedKmh = 72.4f,
                            speedDisplay = 72.4f,
                            speedUnitLabel = "km/h",
                            headingDegrees = 185f,
                            headingCardinal = "S",
                            latitude = 37.7749,
                            longitude = -122.4194,
                            altitudeMeters = 54.0,
                            hasGpsFix = true,
                            accuracyMeters = 3.5f
                        ),
                        gForce = GForceData(
                            x = 0.1f,
                            y = 0.2f,
                            z = 0.98f,
                            totalG = 1.02f,
                            impactDetected = false
                        ),
                        battery = BatteryInfo(
                            levelPct = 92,
                            isCharging = true,
                            powerSource = PowerSource.USB,
                            temperatureC = 32.8f
                        ),
                        mode = AppMode.DASHCAM
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/telemetry_hud.png")
    }

    @Test
    fun testStorageBar_render() {
        val stats = StorageStats(
            appVideoBytes = 4L * 1024 * 1024 * 1024,
            lockedVideoBytes = 1L * 1024 * 1024 * 1024,
            loopBufferBytes = 3L * 1024 * 1024 * 1024,
            deviceFreeBytes = 45L * 1024 * 1024 * 1024,
            deviceTotalBytes = 128L * 1024 * 1024 * 1024,
            maxAllowedBytes = 10L * 1024 * 1024 * 1024
        )

        composeTestRule.setContent {
            DashGuardProTheme {
                Surface(color = Color(0xFF06090E), modifier = Modifier.padding(16.dp)) {
                    StorageBar(
                        stats = stats,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/storage_bar.png")
    }
}

