package io.github.roaxio.dashguardpro

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.roaxio.dashguardpro.camera.CameraHardwareProfile
import io.github.roaxio.dashguardpro.data.settings.DashGuardSettings
import io.github.roaxio.dashguardpro.data.settings.Sensitivity
import io.github.roaxio.dashguardpro.data.settings.SpeedUnit
import io.github.roaxio.dashguardpro.data.settings.StorageLocationType
import io.github.roaxio.dashguardpro.data.settings.VideoAspectRatio
import io.github.roaxio.dashguardpro.data.settings.VideoQuality
import io.github.roaxio.dashguardpro.storage.StorageDirectoryManager
import io.github.roaxio.dashguardpro.storage.StorageOptionInfo
import io.github.roaxio.dashguardpro.camera.SupportedAspectRatioInfo
import io.github.roaxio.dashguardpro.camera.SupportedResolutionInfo
import io.github.roaxio.dashguardpro.ui.components.settings.CustomSentryArmingDelayDialog
import io.github.roaxio.dashguardpro.ui.components.settings.CustomSentryDemoteTempDialog
import io.github.roaxio.dashguardpro.ui.components.settings.CustomSentryIncidentDurationDialog
import io.github.roaxio.dashguardpro.ui.components.settings.CustomTripGapDialog
import io.github.roaxio.dashguardpro.ui.components.settings.DashcamSettingsSection
import io.github.roaxio.dashguardpro.ui.components.settings.RecordingMediaSettingsSection
import io.github.roaxio.dashguardpro.ui.components.settings.SensorsSettingsSection
import io.github.roaxio.dashguardpro.ui.components.settings.StorageLocationSubMenuDialog
import io.github.roaxio.dashguardpro.ui.components.settings.StorageSettingsSection
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
 * Hermetic UI Unit Test Suite for modular settings components.
 * Validates rendering, test tags, and callback bindings for all decomposed settings sections and dialogs.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsComponentsTest {

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

    private val sampleOption = StorageOptionInfo(
        type = StorageLocationType.DEFAULT_APP_DATA,
        title = "App Data Directory (Default)",
        description = "Sandboxed app storage. Guaranteed accessible and isolated.",
        resolvedPath = ApplicationProvider.getApplicationContext<DashGuardApplication>().filesDir.absolutePath,
        freeBytes = 50L * 1024 * 1024 * 1024,
        totalBytes = 128L * 1024 * 1024 * 1024,
        isWritable = true,
        isAvailable = true
    )

    private val sampleCameraProfile = CameraHardwareProfile(
        cameraId = "0",
        lensFacing = "BACK",
        sensorMegapixels = 12.2f,
        sensorDimensions = "4032x3024",
        supportedAspectRatios = listOf(
            SupportedAspectRatioInfo(
                aspectRatio = VideoAspectRatio.RATIO_16_9,
                title = "16:9 (Widescreen)",
                description = "Standard landscape",
                isHardwareSupported = true
            )
        ),
        supportedResolutions = listOf(
            SupportedResolutionInfo(
                quality = VideoQuality.FHD_1080P,
                title = "1080p Full HD",
                subtitle = "1920x1080 (16:9)",
                width = 1920,
                height = 1080,
                aspectRatio = VideoAspectRatio.RATIO_16_9,
                supportedFps = listOf(30, 60),
                isHardwareSupported = true
            )
        )
    )

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testStorageSettingsSection_rendersActivePathAndTriggersCallbacks() {
        var quotaChanged = false
        var quotaHelpClicked = false
        var permissionClicked = false
        var changeLocationClicked = false

        composeTestRule.setContent {
            DashGuardProTheme {
                StorageSettingsSection(
                    displayedActivePath = "/storage/emulated/0/DashGuard",
                    hasStoragePermission = false,
                    activeOption = sampleOption,
                    settings = DashGuardSettings(maxStorageGb = 20),
                    maxDirectoryGb = 48,
                    storageMetrics = sampleMetrics,
                    appUsedBytes = 2L * 1024 * 1024 * 1024,
                    onRequestStoragePermission = { permissionClicked = true },
                    onChangeStorageLocation = { changeLocationClicked = true },
                    onShowQuotaInfo = { quotaHelpClicked = true },
                    onMaxStorageGbChanged = { quotaChanged = true }
                )
            }
        }

        composeTestRule.onNodeWithText("STORAGE & VIDEO DIRECTORY").assertExists()
        composeTestRule.onNodeWithText("/storage/emulated/0/DashGuard").assertExists()
        composeTestRule.onNodeWithTag("btn_grant_storage_permission").assertExists().performClick()
        assertTrue("Storage permission callback should fire", permissionClicked)

        composeTestRule.onNodeWithTag("btn_select_storage_locations_submenu").assertExists().performClick()
        assertTrue("Change storage location callback should fire", changeLocationClicked)

        composeTestRule.onNodeWithTag("btn_quota_help_info").assertExists().performClick()
        assertTrue("Quota help info callback should fire", quotaHelpClicked)

        composeTestRule.onNodeWithTag("btn_set_max_quota").assertExists().performClick()
        assertTrue("Max quota button should fire quota change", quotaChanged)

        // Verify clicking the quota pill opens Custom Storage Quota dialog
        quotaChanged = false
        composeTestRule.onNodeWithTag("btn_custom_storage_quota").assertExists().performClick()
        composeTestRule.onNodeWithText("Custom Storage Quota").assertExists()
        composeTestRule.onNodeWithText("Save").assertExists().performClick()
        assertTrue("Saving custom storage quota should fire quota change", quotaChanged)
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testDashcamSettingsSection_rendersCameraSpecsAndOptions() {
        var updatedSettings: DashGuardSettings? = null

        composeTestRule.setContent {
            DashGuardProTheme {
                DashcamSettingsSection(
                    settings = DashGuardSettings(loopSegmentMinutes = 3, incidentBufferSeconds = 30),
                    cameraProfile = sampleCameraProfile,
                    onSettingsChanged = { updatedSettings = it }
                )
            }
        }

        composeTestRule.onNodeWithText("DASHCAM & LOOP BUFFER").assertExists()
        composeTestRule.onNodeWithText("SENSOR: BACK").assertExists()
        composeTestRule.onNodeWithText("12.2 MP Sensor Array (4032x3024)").assertExists()
        composeTestRule.onNodeWithText("Loop Segment Duration").assertExists()
        composeTestRule.onNodeWithText("Incident Time Window Guarantee").assertExists()
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testSensorsSettingsSection_rendersSensitivitiesAndSpeedUnit() {
        var updatedSettings: DashGuardSettings? = null

        composeTestRule.setContent {
            DashGuardProTheme {
                SensorsSettingsSection(
                    settings = DashGuardSettings(impactSensitivity = Sensitivity.MEDIUM, speedUnit = SpeedUnit.MPH),
                    onSettingsChanged = { updatedSettings = it }
                )
            }
        }

        composeTestRule.onNodeWithText("INTELLIGENT SENSORS & IMPACTS").assertExists()
        composeTestRule.onNodeWithText("G-Force Impact Sensitivity").assertExists()
        composeTestRule.onNodeWithText("Speedometer Unit").assertExists()
        composeTestRule.onNodeWithText("MPH").assertExists()
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testRecordingMediaSettingsSection_rendersToggles() {
        var updatedSettings: DashGuardSettings? = null

        composeTestRule.setContent {
            DashGuardProTheme {
                RecordingMediaSettingsSection(
                    settings = DashGuardSettings(audioRecordingEnabled = true, showWatermark = true),
                    onSettingsChanged = { updatedSettings = it }
                )
            }
        }

        composeTestRule.onNodeWithText("RECORDING MEDIA & OVERLAYS").assertExists()
        composeTestRule.onNodeWithText("Audio Recording").assertExists()
        composeTestRule.onNodeWithText("Telemetry Watermark Overlay").assertExists()
        composeTestRule.onNodeWithText("ADVANCED & DEBUG").assertExists()
        composeTestRule.onNodeWithText("Allow Portrait Recording").assertExists()
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testStorageLocationSubMenuDialog_rendersOptionsAndTriggersSelection() {
        var selectedOption: StorageOptionInfo? = null
        var dismissed = false

        composeTestRule.setContent {
            DashGuardProTheme {
                StorageLocationSubMenuDialog(
                    availableOptions = listOf(sampleOption),
                    selectedType = StorageLocationType.DEFAULT_APP_DATA,
                    onDismiss = { dismissed = true },
                    onSelectLocation = { selectedOption = it },
                    onOpenViewfinder = {},
                    onLaunchSafPicker = {}
                )
            }
        }

        composeTestRule.onNodeWithText("STORAGE LOCATIONS").assertExists()
        composeTestRule.onNodeWithText("Select where dashcam videos are saved").assertExists()
        composeTestRule.onNodeWithText("App Data Directory (Default)").assertExists().performClick()
        assertEquals(sampleOption, selectedOption)
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun testSettingsThresholdDialogs_renderAndConfirm() {
        var confirmedTripGap: Int? = null
        var confirmedArmingDelay: Int? = null
        var confirmedIncidentDuration: Int? = null
        var confirmedDemoteTemp: Float? = null
        val activeDialog = androidx.compose.runtime.mutableIntStateOf(0)

        composeTestRule.setContent {
            DashGuardProTheme {
                when (activeDialog.intValue) {
                    0 -> CustomTripGapDialog(
                        currentMinutes = 15,
                        adjacencyThresholdSeconds = 30,
                        onDismiss = {},
                        onConfirm = {
                            confirmedTripGap = it
                            activeDialog.intValue = 1
                        }
                    )
                    1 -> CustomSentryArmingDelayDialog(
                        currentSeconds = 20,
                        onDismiss = {},
                        onConfirm = {
                            confirmedArmingDelay = it
                            activeDialog.intValue = 2
                        }
                    )
                    2 -> CustomSentryIncidentDurationDialog(
                        currentSeconds = 60,
                        onDismiss = {},
                        onConfirm = {
                            confirmedIncidentDuration = it
                            activeDialog.intValue = 3
                        }
                    )
                    3 -> CustomSentryDemoteTempDialog(
                        currentTempC = 40f,
                        maxAllowedC = 50,
                        onDismiss = {},
                        onConfirm = {
                            confirmedDemoteTemp = it
                        }
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Custom Trip Gap Timeout").assertExists()
        composeTestRule.onNodeWithText("Save").assertExists().performClick()
        composeTestRule.waitForIdle()
        assertEquals(15, confirmedTripGap)

        composeTestRule.onNodeWithText("Custom Sentry Arming Delay").assertExists()
        composeTestRule.onNodeWithText("Save").assertExists().performClick()
        composeTestRule.waitForIdle()
        assertEquals(20, confirmedArmingDelay)

        composeTestRule.onNodeWithText("Custom Incident Duration").assertExists()
        composeTestRule.onNodeWithText("Save").assertExists().performClick()
        composeTestRule.waitForIdle()
        assertEquals(60, confirmedIncidentDuration)

        composeTestRule.onNodeWithText("Custom Demotion Temperature").assertExists()
        composeTestRule.onNodeWithText("Save").assertExists().performClick()
        composeTestRule.waitForIdle()
        assertEquals(40f, confirmedDemoteTemp)
    }
}

