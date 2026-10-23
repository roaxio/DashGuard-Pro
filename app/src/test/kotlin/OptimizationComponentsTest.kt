package io.github.roaxio.dashguardpro

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.roaxio.dashguardpro.data.settings.AutoDimTimeout
import io.github.roaxio.dashguardpro.data.settings.DashGuardSettings
import io.github.roaxio.dashguardpro.storage.StorageDirectoryManager
import io.github.roaxio.dashguardpro.sensor.BatteryInfo
import io.github.roaxio.dashguardpro.sensor.PowerSource
import io.github.roaxio.dashguardpro.sensor.TelemetryData
import io.github.roaxio.dashguardpro.system.SystemOptimizationState
import io.github.roaxio.dashguardpro.ui.components.optimization.BatteryWhitelistStepCard
import io.github.roaxio.dashguardpro.ui.components.optimization.CustomLowBatteryDialog
import io.github.roaxio.dashguardpro.ui.components.optimization.CustomSleepEmergencyDurationDialog
import io.github.roaxio.dashguardpro.ui.components.optimization.CustomThermalTempDialog
import io.github.roaxio.dashguardpro.ui.components.optimization.DiagRow
import io.github.roaxio.dashguardpro.ui.components.optimization.GuideStepRow
import io.github.roaxio.dashguardpro.ui.components.optimization.HardwareDiagnosticsSection
import io.github.roaxio.dashguardpro.ui.components.optimization.KioskAppPinningStepCard
import io.github.roaxio.dashguardpro.ui.components.optimization.KioskGuideDialog
import io.github.roaxio.dashguardpro.ui.components.optimization.OemBackgroundProtectionCard
import io.github.roaxio.dashguardpro.ui.components.optimization.OemGuideDialog
import io.github.roaxio.dashguardpro.ui.components.optimization.OptimizationDropdownRow
import io.github.roaxio.dashguardpro.ui.components.optimization.OptimizationStepCard
import io.github.roaxio.dashguardpro.ui.components.optimization.OptimizationToggleRow
import io.github.roaxio.dashguardpro.ui.components.optimization.SilentCockpitDndCard
import io.github.roaxio.dashguardpro.ui.components.optimization.SystemProtectionSubMenuScreen
import io.github.roaxio.dashguardpro.ui.components.optimization.VehiclePowerAutomationSection
import io.github.roaxio.dashguardpro.ui.components.optimization.WindshieldThermalGuideCard
import io.github.roaxio.dashguardpro.ui.theme.DashGuardProTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Hermetic UI Unit Test Suite for modular optimization components.
 * Validates rendering, test tags, and callback bindings for all decomposed optimization sections and dialogs.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OptimizationComponentsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val sampleMetrics = StorageDirectoryManager.StorageMetrics(
        physicalFreeBytes = 50L * 1024 * 1024 * 1024,
        appUsedBytes = 2L * 1024 * 1024 * 1024,
        totalPoolBytes = 52L * 1024 * 1024 * 1024,
        osBufferBytes = 2L * 1024 * 1024 * 1024,
        usableSafeBytes = 48L * 1024 * 1024 * 1024,
        maxAssignableGb = 48
    )

    private val sampleBattery = BatteryInfo(
        levelPct = 85,
        isCharging = true,
        powerSource = PowerSource.USB,
        temperatureC = 35.0f,
        voltageMv = 4100
    )

    private val sampleTelemetry = TelemetryData(
        speedKmh = 60f,
        hasGpsFix = true,
        accuracyMeters = 3.5f
    )

    private val sampleOptState = SystemOptimizationState(
        isBatteryIgnored = true,
        isAppPinned = false,
        isDndGranted = true,
        hasStoragePermission = true
    )

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testOptimizationPrimitives_renderAndClick() {
        var stepActionClicked = false
        var toggleFlipped = false

        composeTestRule.setContent {
            DashGuardProTheme {
                OptimizationStepCard(
                    stepNumber = "1",
                    icon = Icons.Default.BatteryChargingFull,
                    title = "Test Step Title",
                    description = "Test Step Description",
                    actionLabel = "Do Action",
                    isCompleted = false,
                    onAction = { stepActionClicked = true }
                )
                OptimizationToggleRow(
                    title = "Test Toggle",
                    subtitle = "Test Toggle Subtitle",
                    checked = false,
                    onCheckedChange = { toggleFlipped = it }
                )
                DiagRow(
                    label = "Test Diag Metric",
                    value = "100%",
                    isGood = true,
                    statusText = "Healthy"
                )
                GuideStepRow(
                    number = "1",
                    text = "Instruction for step 1"
                )
            }
        }

        composeTestRule.onNodeWithText("Test Step Title").assertExists()
        composeTestRule.onNodeWithText("Test Step Description").assertExists()
        composeTestRule.onNodeWithText("Do Action").assertExists().performClick()
        assertTrue("Step card button should trigger callback", stepActionClicked)

        composeTestRule.onNodeWithText("Test Toggle").assertExists().performClick()
        assertTrue("Toggle row click should trigger onCheckedChange", toggleFlipped)

        composeTestRule.onNodeWithText("Test Diag Metric").assertExists()
        composeTestRule.onNodeWithText("Instruction for step 1").assertExists()
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testHardwareDiagnosticsSection_rendersMetricsAndCallbacks() {
        var dimSuggested = false
        var whitelistClicked = false

        composeTestRule.setContent {
            DashGuardProTheme {
                HardwareDiagnosticsSection(
                    battery = sampleBattery.copy(temperatureC = 43.0f), // Warm temp triggers dim suggestion
                    telemetry = sampleTelemetry,
                    settings = DashGuardSettings(autoDimTimeout = AutoDimTimeout.DISABLED),
                    systemOptState = sampleOptState.copy(isBatteryIgnored = false),
                    storageMetrics = sampleMetrics,
                    onSetAutoDim10s = { dimSuggested = true },
                    onLaunchBatteryWhitelist = { whitelistClicked = true },
                    onToggleAppPinning = {},
                    onLaunchDndSettings = {}
                )
            }
        }

        composeTestRule.onNodeWithText("HARDWARE & SYSTEM DIAGNOSTICS").assertExists()
        composeTestRule.onNodeWithText("Battery Temperature").assertExists()
        composeTestRule.onNodeWithText("43.0°C").assertExists()
        composeTestRule.onNodeWithText("Set 10s Dim").assertExists().performClick()
        assertTrue("Auto dim suggestion button should fire", dimSuggested)

        composeTestRule.onNodeWithText("Power Source").assertExists()
        composeTestRule.onNodeWithText("Storage Health & Free Quota").assertExists()
        composeTestRule.onNodeWithText("GPS Telemetry Fix").assertExists()
        composeTestRule.onNodeWithText("Battery Optimization").assertExists().performClick()
        assertTrue("Battery whitelist row should fire when not ignored", whitelistClicked)
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testVehiclePowerAutomationSection_rendersControlsAndTriggersUpdates() {
        var updatedSettings: DashGuardSettings? = null
        var customLowBatClicked = false

        composeTestRule.setContent {
            DashGuardProTheme {
                VehiclePowerAutomationSection(
                    settings = DashGuardSettings(autoRecordOnPower = false, maxTempThresholdC = 0f),
                    onUpdateSettings = { updatedSettings = it },
                    onShowCustomLowBattery = { customLowBatClicked = true },
                    onShowCustomWarmDim = {},
                    onShowCustomThermalRecovery = {},
                    onShowCustomSleepEmergency = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Vehicle Power & Thermal Automation").assertExists()
        composeTestRule.onNodeWithText("Auto-Start on Power Connect").assertExists().performClick()
        assertTrue("Auto start toggle should flip setting", updatedSettings?.autoRecordOnPower == true)

        composeTestRule.onNodeWithText("⚠️ Thermal Safety Cutoff Disabled").assertExists()
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testBatteryWhitelistStepCard_triggersCallback() {
        var whitelistClicked = false

        composeTestRule.setContent {
            DashGuardProTheme {
                BatteryWhitelistStepCard(
                    isBatteryIgnored = false,
                    onLaunchBatteryWhitelist = { whitelistClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Bypass Android Battery Optimization").assertExists()
        composeTestRule.onNodeWithText("Whitelist DashGuard").assertExists().performClick()
        assertTrue("Whitelist button should trigger callback", whitelistClicked)
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testKioskAppPinningStepCard_triggersCallbacks() {
        var pinningToggled = false
        var guideShown = false

        composeTestRule.setContent {
            DashGuardProTheme {
                KioskAppPinningStepCard(
                    isAppPinned = false,
                    onToggleAppPinning = { pinningToggled = true },
                    onShowKioskGuide = { guideShown = true }
                )
            }
        }

        composeTestRule.onNodeWithText("App Pinning (Kiosk Mode)").assertExists()
        composeTestRule.onNodeWithText("📌 Pin DashGuard Now").assertExists().performClick()
        assertTrue("Pin DashGuard should trigger callback", pinningToggled)
        composeTestRule.onNodeWithText("How to Unpin & Info").assertExists().performClick()
        assertTrue("Kiosk guide callback should fire", guideShown)
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testSilentCockpitDndCard_triggersToggle() {
        var dndToggled = false

        composeTestRule.setContent {
            DashGuardProTheme {
                SilentCockpitDndCard(
                    isDndGranted = true,
                    autoDndWhileRecording = false,
                    onLaunchDndSettings = {},
                    onToggleAutoDnd = { dndToggled = it },
                    onConfigureDndRules = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Silent Cockpit (Auto Do Not Disturb)").assertExists()
        composeTestRule.onNodeWithText("Auto-Silence While Recording: DISABLED").assertExists().performClick()
        assertTrue("Auto DND toggle should trigger callback", dndToggled)
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testOemAndThermalCards_renderProperly() {
        var guideShown = false

        composeTestRule.setContent {
            DashGuardProTheme {
                androidx.compose.foundation.layout.Column {
                    OemBackgroundProtectionCard(
                        manufacturer = "Xiaomi",
                        oemOptimizationCompleted = false,
                        onLaunchAppDetails = {},
                        onShowOemGuide = { guideShown = true },
                        onToggleOemOptimizationCompleted = {}
                    )
                    WindshieldThermalGuideCard()
                }
            }
        }

        composeTestRule.onNodeWithText("OEM Background Protection (Xiaomi)").assertExists()
        composeTestRule.onNodeWithText("View Xiaomi Specific Guide").assertExists().performClick()
        assertTrue("OEM guide callback should fire", guideShown)
        composeTestRule.onNodeWithText("Windshield Thermal & Hardware Health Guide").assertExists()
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testOemGuideDialog_rendersManufacturerInstructions() {
        var appDetailsClicked = false
        var dismissed = false

        composeTestRule.setContent {
            DashGuardProTheme {
                OemGuideDialog(
                    manufacturer = "Samsung",
                    isXiaomi = false,
                    isSamsung = true,
                    isHuawei = false,
                    isOppo = false,
                    onDismiss = { dismissed = true },
                    onOpenAppDetails = { appDetailsClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Samsung Optimization").assertExists()
        composeTestRule.onNodeWithText("Device Care: Remove DashGuard from 'Sleeping apps' or 'Deep sleeping apps'.").assertExists()
        composeTestRule.onNodeWithText("Open DashGuard App Settings").assertExists().performClick()
        assertTrue("Open app details should trigger callback", appDetailsClicked)
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testSystemProtectionSubMenuScreen_rendersAndBackCallback() {
        var backClicked = false
        var whitelistClicked = false

        composeTestRule.setContent {
            DashGuardProTheme {
                SystemProtectionSubMenuScreen(
                    systemOptState = sampleOptState.copy(isBatteryIgnored = false),
                    oemOptimizationCompleted = false,
                    autoDndWhileRecording = false,
                    onNavigateBack = { backClicked = true },
                    onLaunchBatteryWhitelist = { whitelistClicked = true },
                    onToggleAppPinning = {},
                    onShowKioskGuide = {},
                    onLaunchDndSettings = {},
                    onToggleAutoDnd = {},
                    onConfigureDndRules = {},
                    onLaunchAppDetails = {},
                    onShowOemGuide = {},
                    onToggleOemOptimizationCompleted = {}
                )
            }
        }

        composeTestRule.onNodeWithText("System & OEM Protection").assertExists()
        composeTestRule.onNodeWithText("Android & Background Hardening").assertExists()
        composeTestRule.onNodeWithText("Bypass Android Battery Optimization").assertExists()
        composeTestRule.onNodeWithText("App Pinning (Kiosk Mode)").assertExists()
        composeTestRule.onNodeWithText("Silent Cockpit (Auto Do Not Disturb)").assertExists()
        composeTestRule.onNodeWithText("Whitelist DashGuard").assertExists().performClick()
        assertTrue("Whitelist button should trigger callback", whitelistClicked)

        composeTestRule.onNodeWithContentDescription("Back to Optimization").assertExists().performClick()
        assertTrue("Back button should trigger callback", backClicked)
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testOptimizationDropdownRow_rendersAndSelectsOption() {
        var selectedOption: String? = null

        composeTestRule.setContent {
            DashGuardProTheme {
                OptimizationDropdownRow(
                    title = "Shutdown Delay",
                    subtitle = "Delay after ignition off",
                    currentValue = "10s",
                    options = listOf("10s", "30s", "60s"),
                    onSelect = { selectedOption = it }
                )
            }
        }

        composeTestRule.onNodeWithText("Shutdown Delay").assertExists().performClick()
        composeTestRule.onNodeWithText("30s").assertExists().performClick()
        assertEquals("30s", selectedOption)
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testKioskGuideDialog_rendersAndTriggersActions() {
        var securitySettingsClicked = false
        var tryPinClicked = false

        composeTestRule.setContent {
            DashGuardProTheme {
                KioskGuideDialog(
                    onDismiss = {},
                    onOpenSecuritySettings = { securitySettingsClicked = true },
                    onTryPinNow = { tryPinClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("App Pinning (Kiosk Mode)").assertExists()
        composeTestRule.onNodeWithText("📌 Pin DashGuard Now").assertExists().performClick()
        assertTrue("Try Pinning button should trigger callback", tryPinClicked)

        composeTestRule.onNodeWithText("Open Android Security Settings (Older Devices)").assertExists().performClick()
        assertTrue("Open Security Settings button should trigger callback", securitySettingsClicked)
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testOptimizationThresholdDialogs_renderAndConfirm() {
        var confirmedPct: Int? = null
        var confirmedTemp: Float? = null
        var confirmedSec: Int? = null
        val activeDialog = androidx.compose.runtime.mutableIntStateOf(0)

        composeTestRule.setContent {
            DashGuardProTheme {
                when (activeDialog.intValue) {
                    0 -> CustomLowBatteryDialog(
                        currentPct = 20,
                        onDismiss = {},
                        onConfirm = {
                            confirmedPct = it
                            activeDialog.intValue = 1
                        }
                    )
                    1 -> CustomThermalTempDialog(
                        title = "Custom Warm Auto-Dim Temp",
                        description = "Specify warm warning temperature.",
                        currentTempC = 42f,
                        minTempC = 30,
                        maxTempC = 55,
                        onDismiss = {},
                        onConfirm = {
                            confirmedTemp = it
                            activeDialog.intValue = 2
                        }
                    )
                    2 -> CustomSleepEmergencyDurationDialog(
                        currentDurationSeconds = 45,
                        onDismiss = {},
                        onConfirm = {
                            confirmedSec = it
                        }
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Custom Battery Safety Cutoff").assertExists()
        composeTestRule.onNodeWithText("Apply").assertExists().performClick()
        composeTestRule.waitForIdle()
        assertEquals(20, confirmedPct)

        composeTestRule.onNodeWithText("Custom Warm Auto-Dim Temp").assertExists()
        composeTestRule.onNodeWithText("Apply").assertExists().performClick()
        composeTestRule.waitForIdle()
        assertEquals(42f, confirmedTemp)

        composeTestRule.onNodeWithText("Custom Sleep Shock Duration").assertExists()
        composeTestRule.onNodeWithText("Apply").assertExists().performClick()
        composeTestRule.waitForIdle()
        assertEquals(45, confirmedSec)
    }
}
