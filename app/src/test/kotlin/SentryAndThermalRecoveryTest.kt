package io.github.roaxio.dashguardpro

import androidx.test.core.app.ApplicationProvider
import io.github.roaxio.dashguardpro.data.settings.AppMode
import io.github.roaxio.dashguardpro.data.settings.Sensitivity
import io.github.roaxio.dashguardpro.data.settings.SentryArmingTrigger
import io.github.roaxio.dashguardpro.data.settings.SentryDeterrentMode
import io.github.roaxio.dashguardpro.data.settings.SentryGuardType
import io.github.roaxio.dashguardpro.data.settings.SentryInterruptedClipAction
import io.github.roaxio.dashguardpro.camera.MotionDetectionAnalyzer
import io.github.roaxio.dashguardpro.sensor.PowerSource
import io.github.roaxio.dashguardpro.viewmodel.DashcamViewModel
import io.github.roaxio.dashguardpro.viewmodel.state.DeviceSleepMode
import io.github.roaxio.dashguardpro.viewmodel.state.PreSleepOperatingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SentryAndThermalRecoveryTest {

    private lateinit var app: DashGuardApplication
    private lateinit var viewModel: DashcamViewModel

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        viewModel = DashcamViewModel(app)
    }

    @Test
    fun testSentryArming_masterToggleOffInvariance() {
        val initialSettings = app.settingsRepository.settings.value
        app.settingsRepository.updateSettings(
            initialSettings.copy(
                sentryModeEnabled = false,
                sentryArmingTrigger = SentryArmingTrigger.ON_POWER_LOSS
            )
        )

        // Manual arm attempts must be rejected
        viewModel.armSentryMode(delaySeconds = 10)
        assertFalse(viewModel.uiState.value.isSentryArming)
        assertFalse(viewModel.uiState.value.isSentryArmed)

        viewModel.armSentryMode(delaySeconds = 0)
        assertFalse(viewModel.uiState.value.isSentryArming)
        assertFalse(viewModel.uiState.value.isSentryArmed)

        // Power unplug must NOT arm when master toggle is false
        app.batteryMonitor.updateForTesting(levelPct = 80, tempC = 25f, isCharging = true)
        app.batteryMonitor.updateForTesting(levelPct = 80, tempC = 25f, isCharging = false)
        assertFalse(viewModel.uiState.value.isSentryArmed)
        assertFalse(viewModel.uiState.value.isSentryArming)
    }

    @Test
    fun testSentryArming_directArmAndDisarm() {
        val initialSettings = app.settingsRepository.settings.value
        app.settingsRepository.updateSettings(initialSettings.copy(sentryModeEnabled = true))

        viewModel.armSentryMode(delaySeconds = 0)
        assertTrue(viewModel.uiState.value.isSentryArmed)
        assertFalse(viewModel.uiState.value.isSentryArming)
        assertEquals(0, viewModel.uiState.value.sentryArmingCountdownSeconds)
        assertEquals(AppMode.SENTRY, app.settingsRepository.settings.value.currentMode)
        assertTrue(viewModel.uiState.value.isScreenDimmed)
        assertFalse(app.locationTracker.isTracking)

        viewModel.disarmSentryMode()
        assertFalse(viewModel.uiState.value.isSentryArmed)
        assertFalse(viewModel.uiState.value.isSentryArming)
        assertFalse(viewModel.uiState.value.isScreenDimmed)
        assertTrue(app.locationTracker.isTracking)
    }

    @Test
    fun testSentryArming_countdownAndCancellation() {
        val initialSettings = app.settingsRepository.settings.value
        app.settingsRepository.updateSettings(
            initialSettings.copy(
                sentryModeEnabled = true,
                sentryArmingDelaySeconds = 30
            )
        )

        viewModel.armSentryMode(delaySeconds = 30)
        assertTrue(viewModel.uiState.value.isSentryArming)
        assertEquals(30, viewModel.uiState.value.sentryArmingCountdownSeconds)
        assertFalse(viewModel.uiState.value.isSentryArmed)

        viewModel.cancelSentryArming()
        assertFalse(viewModel.uiState.value.isSentryArming)
        assertEquals(0, viewModel.uiState.value.sentryArmingCountdownSeconds)
        assertFalse(viewModel.uiState.value.isSentryArmed)
        assertTrue(app.locationTracker.isTracking)
    }

    @Test
    fun testLocationTracking_unconditionalOnRecordingStartAndModeTransitions() {
        val initialSettings = app.settingsRepository.settings.value
        app.settingsRepository.updateSettings(
            initialSettings.copy(
                sentryModeEnabled = true,
                debugAllowPortraitRecording = true
            )
        )

        // 1. Arm sentry directly (delaySeconds = 0)
        viewModel.armSentryMode(delaySeconds = 0)
        assertTrue(viewModel.uiState.value.isSentryArmed)
        assertFalse("Location tracking must be paused during sentry standby to conserve battery", app.locationTracker.isTracking)

        // 2. Start recording while previously in Sentry mode - must unconditionally engage GPS
        viewModel.startRecording("Manual")
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue("GPS must unconditionally be active during recording regardless of origin mode", app.locationTracker.isTracking)

        // 3. Stop recording and manually switch mode
        viewModel.stopRecording(autoArmSentry = false)
        app.locationTracker.stopTracking()
        assertFalse(app.locationTracker.isTracking)

        viewModel.setMode(AppMode.DASHCAM)
        assertTrue("Switching mode to DASHCAM must restore GPS tracking", app.locationTracker.isTracking)
    }

    @Test
    fun testParkedDetection_powerLossArmsSentryWhenEnabled() {
        val initialSettings = app.settingsRepository.settings.value
        app.settingsRepository.updateSettings(
            initialSettings.copy(
                sentryModeEnabled = true,
                sentryArmingTrigger = SentryArmingTrigger.ON_POWER_LOSS,
                sentryArmingDelaySeconds = 0
            )
        )

        // Simulate plugged in charging state first
        app.batteryMonitor.updateForTesting(levelPct = 90, tempC = 30f, isCharging = true)

        // Unplug charger (power loss event)
        app.batteryMonitor.updateForTesting(levelPct = 90, tempC = 30f, isCharging = false)

        assertTrue(viewModel.uiState.value.isSentryArmed)
        assertEquals(AppMode.SENTRY, app.settingsRepository.settings.value.currentMode)
    }

    @Test
    fun testStopRecording_armsSentryWhenTriggerIsOnRecordingStop() {
        val initialSettings = app.settingsRepository.settings.value
        app.settingsRepository.updateSettings(
            initialSettings.copy(
                sentryModeEnabled = true,
                sentryArmingTrigger = SentryArmingTrigger.ON_RECORDING_STOP,
                sentryArmingDelaySeconds = 0
            )
        )

        viewModel.updateUiState { it.copy(isRecording = true) }
        assertTrue(viewModel.uiState.value.isRecording)

        viewModel.stopRecording()
        assertFalse(viewModel.uiState.value.isRecording)
        assertTrue(viewModel.uiState.value.isSentryArmed)
    }

    @Test
    fun testMidDriveThermalSleep_triggerOnOverheatWhileCharging() {
        val initialSettings = app.settingsRepository.settings.value
        app.settingsRepository.updateSettings(
            initialSettings.copy(
                maxTempThresholdC = 48.0f,
                midDriveThermalRecoveryEnabled = true
            )
        )

        viewModel.updateUiState { it.copy(isRecording = true) }
        assertTrue(viewModel.uiState.value.isRecording)

        // Charging in car and hits 48.5°C
        app.batteryMonitor.updateForTesting(
            levelPct = 75,
            tempC = 48.5f,
            isCharging = true,
            powerSource = PowerSource.AC
        )

        assertTrue(viewModel.uiState.value.isThermalCoolingActive)
        assertFalse(viewModel.uiState.value.isRecording)
        assertEquals(44.0f, viewModel.uiState.value.thermalCoolingTargetC, 0.01f)
        assertFalse(app.locationTracker.isTracking)
    }

    @Test
    fun testHystereticThermalRecovery_autoResumesRecordingWhenCooled() {
        val initialSettings = app.settingsRepository.settings.value
        app.settingsRepository.updateSettings(
            initialSettings.copy(
                maxTempThresholdC = 48.0f,
                midDriveThermalRecoveryEnabled = true
            )
        )

        viewModel.updateUiState {
            it.copy(
                deviceSleepMode = DeviceSleepMode.THERMAL_COOLING,
                thermalCoolingTempC = 48.5f,
                thermalCoolingTargetC = 44.0f
            )
        }
        assertTrue(viewModel.uiState.value.isThermalCoolingActive)

        // Temperature drops to 43.5°C (below target 44.0°C)
        app.batteryMonitor.updateForTesting(
            levelPct = 75,
            tempC = 43.5f,
            isCharging = true,
            powerSource = PowerSource.AC
        )
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertFalse(viewModel.uiState.value.isThermalCoolingActive)
        assertTrue(app.locationTracker.isTracking)
        assertFalse("Resumed recording must NOT be locked", viewModel.uiState.value.isSegmentLocked)
    }

    @Test
    fun testEmergencyShockDuringThermalSleep_startsImmediateRecordingAndBackfill() {
        val initialSettings = app.settingsRepository.settings.value
        app.settingsRepository.updateSettings(
            initialSettings.copy(
                maxTempThresholdC = 48.0f,
                midDriveThermalRecoveryEnabled = true,
                debugAllowPortraitRecording = true
            )
        )

        viewModel.updateUiState { it.copy(deviceSleepMode = DeviceSleepMode.THERMAL_COOLING) }
        app.locationTracker.stopTracking()
        assertTrue(viewModel.uiState.value.isThermalCoolingActive)
        assertFalse(app.locationTracker.isTracking)

        // Impact shock during cooling sleep
        viewModel.handleImpactDetected(4.2f)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertTrue(viewModel.uiState.value.showImpactAlert)
        assertEquals(4.2f, viewModel.uiState.value.impactG, 0.01f)
        assertTrue(app.locationTracker.isTracking)
        assertTrue("Emergency shock recording during thermal sleep must be locked", viewModel.uiState.value.isSegmentLocked)
    }

    @Test
    fun testThermalGuard_reactiveWarmThresholdUpdatesWithMaxTempSetting() {
        // Default max safe temp 48°C -> warm threshold 41°C
        app.settingsRepository.updateSettings(app.settingsRepository.settings.value.copy(maxTempThresholdC = 48.0f))
        assertEquals(41.0f, app.batteryMonitor.warmThresholdC, 0.01f)

        // User changes max safe temp to 52°C -> warm threshold becomes 45°C
        app.settingsRepository.updateSettings(app.settingsRepository.settings.value.copy(maxTempThresholdC = 52.0f))
        assertEquals(45.0f, app.batteryMonitor.warmThresholdC, 0.01f)

        // User changes max safe temp to 42°C -> clamped to at least 38°C
        app.settingsRepository.updateSettings(app.settingsRepository.settings.value.copy(maxTempThresholdC = 42.0f))
        assertEquals(38.0f, app.batteryMonitor.warmThresholdC, 0.01f)
    }

    @Test
    fun testOpticalMotion_autoDemotionOnLowBatteryOrHighTemp() {
        val initialSettings = app.settingsRepository.settings.value
        app.settingsRepository.updateSettings(
            initialSettings.copy(
                sentryModeEnabled = true,
                sentryGuardType = SentryGuardType.OPTICAL_MOTION
            )
        )

        viewModel.armSentryMode(delaySeconds = 0)
        assertTrue(viewModel.uiState.value.isSentryArmed)

        // Low battery < 30%
        app.batteryMonitor.updateForTesting(levelPct = 25, tempC = 30f, isCharging = false)
        viewModel.handleMotionDetected(score = 50f, avgLuma = 100f)
        assertEquals(SentryGuardType.SHOCK_STANDBY, app.settingsRepository.settings.value.sentryGuardType)

        // Reset to OPTICAL_MOTION, then test high temp >= 44°C (derived 4°C below 48°C max cutoff)
        app.settingsRepository.updateSettings(
            app.settingsRepository.settings.value.copy(sentryGuardType = SentryGuardType.OPTICAL_MOTION)
        )
        app.batteryMonitor.updateForTesting(levelPct = 80, tempC = 44.5f, isCharging = false)
        viewModel.handleMotionDetected(score = 50f, avgLuma = 100f)
        assertEquals(SentryGuardType.SHOCK_STANDBY, app.settingsRepository.settings.value.sentryGuardType)
    }

    @Test
    fun testOpticalMotion_configurableDemotionThresholds() {
        // Configure custom thresholds: battery floor 20%, temp limit 43°C
        val initialSettings = app.settingsRepository.settings.value
        app.settingsRepository.updateSettings(
            initialSettings.copy(
                sentryModeEnabled = true,
                sentryGuardType = SentryGuardType.OPTICAL_MOTION,
                sentryAutoDemoteBatteryPct = 20,
                sentryAutoDemoteTempIsAuto = false,
                sentryAutoDemoteTempC = 43.0f
            )
        )

        viewModel.armSentryMode(delaySeconds = 0)
        assertTrue(viewModel.uiState.value.isSentryArmed)

        // 1. Battery at 25% (would demote under 30% default, but should stay OPTICAL_MOTION with 20% floor)
        app.batteryMonitor.updateForTesting(levelPct = 25, tempC = 30f, isCharging = false)
        viewModel.handleMotionDetected(score = 50f, avgLuma = 100f)
        assertEquals(SentryGuardType.OPTICAL_MOTION, app.settingsRepository.settings.value.sentryGuardType)

        // 2. Battery drains below configured 20% floor (18%) -> triggers demotion
        app.batteryMonitor.updateForTesting(levelPct = 18, tempC = 30f, isCharging = false)
        viewModel.handleMotionDetected(score = 50f, avgLuma = 100f)
        assertEquals(SentryGuardType.SHOCK_STANDBY, app.settingsRepository.settings.value.sentryGuardType)

        // 3. Reset to OPTICAL_MOTION with battery 80%, temp 42°C (would demote under 41°C default, but should stay OPTICAL_MOTION with 43°C limit)
        app.batteryMonitor.updateForTesting(levelPct = 80, tempC = 35f, isCharging = false)
        app.settingsRepository.updateSettings(
            app.settingsRepository.settings.value.copy(sentryGuardType = SentryGuardType.OPTICAL_MOTION)
        )
        app.batteryMonitor.updateForTesting(levelPct = 80, tempC = 42.0f, isCharging = false)
        viewModel.handleMotionDetected(score = 50f, avgLuma = 100f)
        assertEquals(SentryGuardType.OPTICAL_MOTION, app.settingsRepository.settings.value.sentryGuardType)

        // 4. Heat spikes to/above configured 43°C limit (43.5°C) -> triggers demotion
        app.batteryMonitor.updateForTesting(levelPct = 80, tempC = 43.5f, isCharging = false)
        viewModel.handleMotionDetected(score = 50f, avgLuma = 100f)
        assertEquals(SentryGuardType.SHOCK_STANDBY, app.settingsRepository.settings.value.sentryGuardType)

        // 5. Conditions recover: cools to 40.0°C (<= 43°C - 2°C hysteresis) and battery is 80% -> auto-promotes to OPTICAL_MOTION!
        app.batteryMonitor.updateForTesting(levelPct = 80, tempC = 40.0f, isCharging = false)
        assertEquals(SentryGuardType.OPTICAL_MOTION, app.settingsRepository.settings.value.sentryGuardType)
    }

    @Test
    fun testOpticalMotion_autoDemoteDisabled_preservesTier2UnderStress() {
        app.settingsRepository.updateSettings(
            app.settingsRepository.settings.value.copy(
                sentryModeEnabled = true,
                sentryGuardType = SentryGuardType.OPTICAL_MOTION,
                sentryAutoDemoteEnabled = false,
                sentryAutoDemoteBatteryPct = 30,
                sentryAutoDemoteTempC = 44.0f
            )
        )

        viewModel.armSentryMode(delaySeconds = 0)
        assertTrue(viewModel.uiState.value.isSentryArmed)

        // Battery low (20%) and hot (46°C), but autoDemote is disabled -> stays OPTICAL_MOTION
        app.batteryMonitor.updateForTesting(levelPct = 20, tempC = 46.0f, isCharging = false)
        viewModel.handleMotionDetected(score = 50f, avgLuma = 100f)
        assertEquals(SentryGuardType.OPTICAL_MOTION, app.settingsRepository.settings.value.sentryGuardType)
    }

    @Test
    fun testSentrySleepRecovery_OptionA_wakesStraightToTier2WhenConditionsSafe() {
        app.settingsRepository.updateSettings(
            app.settingsRepository.settings.value.copy(
                sentryModeEnabled = true,
                sentryGuardType = SentryGuardType.OPTICAL_MOTION,
                sentryAutoDemoteEnabled = true,
                sentryAutoDemoteBatteryPct = 30,
                sentryAutoDemoteTempC = 44.0f,
                lowBatterySafetyLimitPct = 15
            )
        )

        viewModel.armSentryMode(delaySeconds = 0)
        assertEquals(SentryGuardType.OPTICAL_MOTION, app.settingsRepository.settings.value.sentryGuardType)

        // 1. Demoted to Tier 1 due to battery at 28%
        app.batteryMonitor.updateForTesting(levelPct = 28, tempC = 30.0f, isCharging = false)
        viewModel.handleMotionDetected(score = 50f, avgLuma = 100f)
        assertEquals(SentryGuardType.SHOCK_STANDBY, app.settingsRepository.settings.value.sentryGuardType)

        // 2. Battery continues draining to low battery safety cutoff (12%) -> enters low-power sleep
        app.batteryMonitor.onLowBatteryCutoff?.invoke(12)
        assertEquals(DeviceSleepMode.LOW_BATTERY_STANDBY, viewModel.uiState.value.deviceSleepMode)
        assertEquals(PreSleepOperatingState.SENTRY_TIER1_SHOCK, viewModel.uiState.value.preSleepOrigin)

        // 3. Waking up: Charger connected (isCharging = true, levelPct = 40%), device cool (28°C)
        // Conditions are safe for Tier 2 -> Option A wakes STRAIGHT to Tier 2!
        app.settingsRepository.updateSettings(app.settingsRepository.settings.value.copy(sentryAutoDisarmOnPower = false))
        app.batteryMonitor.updateForTesting(levelPct = 40, tempC = 28.0f, isCharging = true)

        assertEquals(DeviceSleepMode.NONE, viewModel.uiState.value.deviceSleepMode)
        assertTrue(viewModel.uiState.value.isSentryArmed)
        assertEquals(SentryGuardType.OPTICAL_MOTION, app.settingsRepository.settings.value.sentryGuardType)
    }

    @Test
    fun testOwnerPowerOnInterruption_disarmsSentryAndTagsClip() {
        val initialSettings = app.settingsRepository.settings.value
        app.settingsRepository.updateSettings(
            initialSettings.copy(
                sentryModeEnabled = true,
                sentryAutoDisarmOnPower = true,
                sentryInterruptedClipAction = SentryInterruptedClipAction.KEEP_WITH_BADGE
            )
        )

        viewModel.armSentryMode(delaySeconds = 0)
        assertTrue(viewModel.uiState.value.isSentryArmed)

        // Simulate recording active during sentry mode
        viewModel.updateUiState { it.copy(isRecording = true) }
        assertTrue(viewModel.uiState.value.isRecording)

        // Owner connects power (engine started)
        app.batteryMonitor.updateForTesting(levelPct = 80, tempC = 25f, isCharging = true)

        assertFalse(viewModel.uiState.value.isSentryArmed)
        assertFalse(viewModel.uiState.value.isSentryArming)
        assertEquals("Sentry (Interrupted by Power On)", viewModel.uiState.value.lastTriggerReason)
        assertEquals(AppMode.DASHCAM, app.settingsRepository.settings.value.currentMode)
    }

    @Test
    fun testAcousticAlert_incrementsEventCountWhenArmed() {
        val initialSettings = app.settingsRepository.settings.value
        app.settingsRepository.updateSettings(
            initialSettings.copy(
                sentryModeEnabled = true,
                sentryAcousticTriggerSensitivity = Sensitivity.HIGH
            )
        )
        viewModel.armSentryMode(delaySeconds = 0)
        assertTrue(viewModel.uiState.value.isSentryArmed)
        assertEquals(0, viewModel.uiState.value.sentryEventCount)

        viewModel.handleAcousticAlert(78.5f)
        assertEquals(1, viewModel.uiState.value.sentryEventCount)
        assertTrue(viewModel.uiState.value.isAcousticAlert)
        assertEquals(78.5f, viewModel.uiState.value.acousticDb, 0.1f)

        viewModel.handleAcousticAlert(82.0f)
        assertEquals(2, viewModel.uiState.value.sentryEventCount)
    }

    @Test
    fun testThermalCooling_cancellationEscapeHatch() {
        viewModel.cancelThermalCooling()
        assertFalse(viewModel.uiState.value.isThermalCoolingActive)
    }

    @Test
    fun testImpactDetected_duringSentryArmed_incrementsEventCount() {
        val initialSettings = app.settingsRepository.settings.value
        app.settingsRepository.updateSettings(initialSettings.copy(sentryModeEnabled = true))
        viewModel.armSentryMode(delaySeconds = 0)
        assertTrue(viewModel.uiState.value.isSentryArmed)

        val initialEvents = viewModel.uiState.value.sentryEventCount
        viewModel.handleImpactDetected(3.5f)

        assertFalse(viewModel.uiState.value.showImpactAlert)
        assertEquals(3.5f, viewModel.uiState.value.impactG, 0.01f)
        assertEquals(initialEvents + 1, viewModel.uiState.value.sentryEventCount)
    }

    @Test
    fun testCustomDurationSettings_persistenceAndInvariants() {
        val initialSettings = app.settingsRepository.settings.value
        app.settingsRepository.updateSettings(
            initialSettings.copy(
                sentryArmingDelaySeconds = 250,
                sentryIncidentDurationSeconds = 180
            )
        )

        assertEquals(250, app.settingsRepository.settings.value.sentryArmingDelaySeconds)
        assertEquals(180, app.settingsRepository.settings.value.sentryIncidentDurationSeconds)
    }

    @Test
    fun testVoiceAlerts_toggleRespectedWithoutExceptions() {
        val initialSettings = app.settingsRepository.settings.value
        // Verify voice disabled runs clean
        app.settingsRepository.updateSettings(
            initialSettings.copy(
                sentryModeEnabled = true,
                sentryVoiceAlertsEnabled = false
            )
        )
        viewModel.armSentryMode(delaySeconds = 0)
        viewModel.disarmSentryMode()

        // Verify voice enabled runs clean
        app.settingsRepository.updateSettings(
            initialSettings.copy(
                sentryModeEnabled = true,
                sentryVoiceAlertsEnabled = true
            )
        )
        viewModel.armSentryMode(delaySeconds = 0)
        viewModel.disarmSentryMode()
    }

    @Test
    fun testSentryDisarm_resetsAppModeToDashcam() {
        val initialSettings = app.settingsRepository.settings.value
        app.settingsRepository.updateSettings(initialSettings.copy(sentryModeEnabled = true))

        viewModel.armSentryMode(delaySeconds = 0)
        assertEquals(AppMode.SENTRY, app.settingsRepository.settings.value.currentMode)
        assertTrue(viewModel.uiState.value.isScreenDimmed)

        viewModel.disarmSentryMode()
        assertEquals(AppMode.DASHCAM, app.settingsRepository.settings.value.currentMode)
        assertFalse(viewModel.uiState.value.isScreenDimmed)
    }

    @Test
    fun testSentryTrigger_debouncesEventCountWhenAlreadyRecording() {
        val initialSettings = app.settingsRepository.settings.value
        app.settingsRepository.updateSettings(
            initialSettings.copy(
                sentryModeEnabled = true
            )
        )
        viewModel.armSentryMode(delaySeconds = 0)

        // Simulate incident recording is actively in-flight
        viewModel.updateUiState { it.copy(isRecording = true, sentryEventCount = 1) }

        // Subsequent triggers while isRecording is true must not increment event count
        viewModel.handleMotionDetected(score = 90f, avgLuma = 120f)
        assertEquals(1, viewModel.uiState.value.sentryEventCount)

        viewModel.handleAcousticAlert(80f)
        assertEquals(1, viewModel.uiState.value.sentryEventCount)

        viewModel.handleImpactDetected(2.5f)
        assertEquals(1, viewModel.uiState.value.sentryEventCount)
    }

    @Test
    fun testStartRecording_lifecycleAndDuplicateGuards() {
        val initialSettings = app.settingsRepository.settings.value
        app.settingsRepository.updateSettings(
            initialSettings.copy(
                debugAllowPortraitRecording = true
            )
        )

        // Starting recording must initiate start job and enter starting/recording state without being aborted by !isRecording
        val startJob = viewModel.startRecording("Manual")
        assertNotNull(startJob)
        assertTrue(viewModel.uiState.value.isRecording || viewModel.uiState.value.isRecordingStarting)

        // Duplicate start call while starting must be rejected
        val duplicateJob = viewModel.startRecording("Manual")
        assertNull(duplicateJob)

        // Toggle recording while starting must trigger stop
        viewModel.toggleRecording()
        assertFalse(viewModel.uiState.value.isRecording)
        assertFalse(viewModel.uiState.value.isRecordingStarting)
    }

    @Test
    fun testSentryRecording_usesSentryIncidentDurationForTargetSeconds() {
        val initialSettings = app.settingsRepository.settings.value
        app.settingsRepository.updateSettings(
            initialSettings.copy(
                sentryModeEnabled = true,
                sentryIncidentDurationSeconds = 45,
                loopSegmentMinutes = 3,
                debugAllowPortraitRecording = true
            )
        )
        viewModel.armSentryMode(delaySeconds = 0)
        assertEquals(45, viewModel.uiState.value.targetSegmentSeconds)

        viewModel.startRecording("Sentry Shock Incident")
        assertEquals(45, viewModel.uiState.value.targetSegmentSeconds)
    }

    @Test
    fun testSentryDisarm_stopsActiveRecordingAndReturnsToStandby() {
        val initialSettings = app.settingsRepository.settings.value
        app.settingsRepository.updateSettings(
            initialSettings.copy(
                sentryModeEnabled = true,
                debugAllowPortraitRecording = true
            )
        )
        viewModel.armSentryMode(delaySeconds = 0)
        viewModel.startRecording("Sentry Motion Incident")
        assertTrue(viewModel.uiState.value.isRecording || viewModel.uiState.value.isRecordingStarting)

        viewModel.disarmSentryMode()
        assertFalse(viewModel.uiState.value.isRecording)
        assertFalse(viewModel.uiState.value.isRecordingStarting)
        assertFalse(viewModel.uiState.value.isSentryArmed)
        assertEquals(AppMode.DASHCAM, app.settingsRepository.settings.value.currentMode)
        assertEquals("Sentry (Interrupted by Power On)", viewModel.uiState.value.lastTriggerReason)
    }

    @Test
    fun testSentryTriggerReasons_motionAcousticImpact_andInterruptedByOwner() {
        val initialSettings = app.settingsRepository.settings.value
        app.settingsRepository.updateSettings(
            initialSettings.copy(
                sentryModeEnabled = true,
                debugAllowPortraitRecording = true
            )
        )
        // 1. Motion trigger
        viewModel.armSentryMode(delaySeconds = 0)
        viewModel.handleMotionDetected(score = 80f, avgLuma = 100f)
        assertEquals("Motion Sentry Trigger", viewModel.uiState.value.lastTriggerReason)

        // 2. Interrupted by Owner Return (disarm while recording)
        viewModel.disarmSentryMode()
        assertEquals("Sentry (Interrupted by Power On)", viewModel.uiState.value.lastTriggerReason)

        // 3. Acoustic trigger
        viewModel.armSentryMode(delaySeconds = 0)
        viewModel.handleAcousticAlert(82f)
        assertEquals("Sentry Acoustic (82 dB)", viewModel.uiState.value.lastTriggerReason)
        viewModel.stopRecording(autoArmSentry = false)

        // 4. Impact shock trigger
        viewModel.armSentryMode(delaySeconds = 0)
        viewModel.handleImpactDetected(3.2f)
        assertEquals("Impact Shock (3.2G)", viewModel.uiState.value.lastTriggerReason)
        viewModel.stopRecording(autoArmSentry = false)
    }

    @Test
    fun testSentryDisarm_promptsForDashcamWhenPhoneIsCharging() {
        val initialSettings = app.settingsRepository.settings.value
        app.settingsRepository.updateSettings(
            initialSettings.copy(
                sentryModeEnabled = true
            )
        )
        viewModel.armSentryMode(delaySeconds = 0)

        // Device is charging
        app.batteryMonitor.updateForTesting(levelPct = 85, tempC = 28f, isCharging = true)

        viewModel.disarmSentryMode()
        assertTrue(viewModel.uiState.value.showStartDashcamPromptOnDisarm)

        // Dismissing prompt clears flag
        viewModel.dismissStartDashcamPrompt()
        assertFalse(viewModel.uiState.value.showStartDashcamPromptOnDisarm)

        // Re-disarming while charging sets flag again
        viewModel.armSentryMode(delaySeconds = 0)
        viewModel.disarmSentryMode()
        assertTrue(viewModel.uiState.value.showStartDashcamPromptOnDisarm)

        // Confirming prompt dismisses flag and starts recording
        app.settingsRepository.updateSettings(app.settingsRepository.settings.value.copy(debugAllowPortraitRecording = true))
        viewModel.confirmStartDashcamAfterDisarm()
        assertFalse(viewModel.uiState.value.showStartDashcamPromptOnDisarm)
        assertTrue(viewModel.uiState.value.isRecording || viewModel.uiState.value.isRecordingStarting)
    }

    @Test
    fun testSentryMotion_doesNotVibratePhone() {
        val initialSettings = app.settingsRepository.settings.value
        app.settingsRepository.updateSettings(
            initialSettings.copy(
                sentryModeEnabled = true,
                debugAllowPortraitRecording = true
            )
        )
        viewModel.armSentryMode(delaySeconds = 0)
        assertTrue(viewModel.uiState.value.isSentryArmed)

        // Trigger motion in Sentry mode; lockCurrentClip must not trigger vibrator
        viewModel.handleMotionDetected(score = 25f, avgLuma = 120f)
        assertTrue(viewModel.uiState.value.isRecording || viewModel.uiState.value.isRecordingStarting)

        // Calling lockCurrentClip explicitly in Sentry mode must also suppress vibration
        viewModel.lockCurrentClip("Test Sentry Lock")
        assertTrue(viewModel.uiState.value.isSegmentLocked)
    }

    @Test
    fun testMotionDetectionAnalyzer_rejectsGlobalIlluminationShift() {
        var triggerCount = 0
        val analyzer = MotionDetectionAnalyzer { _, _ -> triggerCount++ }
        analyzer.motionSensitivityThreshold = 15f
        analyzer.pixelDeltaThreshold = 20
        analyzer.persistenceFrames = 1

        val totalBlocks = analyzer.gridWidth * analyzer.gridHeight

        // Frame 1: Baseline frame (all blocks at luma 100)
        val frame1 = ByteArray(totalBlocks) { 100.toByte() }
        val score1 = analyzer.processGridInternal(frame1, 100f)
        assertEquals(0f, score1, 0.001f)
        assertEquals(0, triggerCount)

        // Frame 2: Global illumination shift (all blocks increase uniformly by +30 to 130)
        // Auto-exposure / passing cloud / distant headlights
        val frame2 = ByteArray(totalBlocks) { 130.toByte() }
        val score2 = analyzer.processGridInternal(frame2, 130f)
        assertEquals(0f, score2, 0.001f)
        assertEquals(0, triggerCount)
    }

    @Test
    fun testMotionDetectionAnalyzer_rejectsIsolatedNoiseSpikes() {
        var triggerCount = 0
        val analyzer = MotionDetectionAnalyzer { _, _ -> triggerCount++ }
        analyzer.motionSensitivityThreshold = 5f
        analyzer.pixelDeltaThreshold = 20
        analyzer.minNeighborsForCluster = 2
        analyzer.persistenceFrames = 1

        val totalBlocks = analyzer.gridWidth * analyzer.gridHeight

        // Frame 1: Baseline frame (all blocks at luma 100)
        val frame1 = ByteArray(totalBlocks) { 100.toByte() }
        analyzer.processGridInternal(frame1, 100f)

        // Frame 2: Scatter isolated single-pixel/single-block spikes with zero neighbors
        val frame2 = ByteArray(totalBlocks) { 100.toByte() }
        // Place isolated spikes far apart from each other
        val spikeIndices = listOf(2, 60, 150, 250, 380, 500, 620, 710)
        for (idx in spikeIndices) {
            frame2[idx] = 220.toByte()
        }

        val score2 = analyzer.processGridInternal(frame2, 101f)
        // Isolated spikes must be pruned by the 8-neighbor cluster filter
        assertEquals(0f, score2, 0.001f)
        assertEquals(0, triggerCount)
    }

    @Test
    fun testMotionDetectionAnalyzer_detectsCoherentSpatialCluster() {
        var triggerCount = 0
        val analyzer = MotionDetectionAnalyzer { _, _ -> triggerCount++ }
        analyzer.motionSensitivityThreshold = 5f
        analyzer.pixelDeltaThreshold = 20
        analyzer.minNeighborsForCluster = 2
        analyzer.persistenceFrames = 1

        val totalBlocks = analyzer.gridWidth * analyzer.gridHeight

        // Frame 1: Baseline frame
        val frame1 = ByteArray(totalBlocks) { 100.toByte() }
        analyzer.processGridInternal(frame1, 100f)

        // Frame 2: Create a coherent 8x8 contiguous cluster of changed blocks (approaching object)
        val frame2 = ByteArray(totalBlocks) { 100.toByte() }
        var clusterCount = 0
        for (gy in 8..15) {
            for (gx in 8..15) {
                frame2[gy * analyzer.gridWidth + gx] = 200.toByte()
                clusterCount++
            }
        }

        val avgLuma2 = 100f + (clusterCount * 100f / totalBlocks)
        val score2 = analyzer.processGridInternal(frame2, avgLuma2)
        assertTrue("Expected score > 5% for coherent cluster, got $score2%", score2 >= 5f)
        assertEquals(1, triggerCount)
    }

    @Test
    fun testMotionDetectionAnalyzer_enforcesTemporalPersistence() {
        var triggerCount = 0
        val analyzer = MotionDetectionAnalyzer { _, _ -> triggerCount++ }
        analyzer.motionSensitivityThreshold = 5f
        analyzer.pixelDeltaThreshold = 20
        analyzer.minNeighborsForCluster = 2
        analyzer.persistenceFrames = 2 // Requires 2 consecutive frames

        val totalBlocks = analyzer.gridWidth * analyzer.gridHeight

        // Frame 1: Baseline frame
        val frameBaseline = ByteArray(totalBlocks) { 100.toByte() }
        analyzer.processGridInternal(frameBaseline, 100f)

        // Frame 2: First frame with motion cluster -> must NOT trigger yet (needs 2 consecutive motion frames)
        val frameMotion1 = ByteArray(totalBlocks) { 100.toByte() }
        for (gy in 8..15) {
            for (gx in 8..15) {
                frameMotion1[gy * analyzer.gridWidth + gx] = 200.toByte()
            }
        }
        val avgLuma1 = 100f + (64 * 100f / totalBlocks)
        analyzer.processGridInternal(frameMotion1, avgLuma1)
        assertEquals(0, triggerCount)

        // Frame 3: Second consecutive frame with continuous motion (object moves to new position) -> MUST trigger!
        val frameMotion2 = ByteArray(totalBlocks) { 100.toByte() }
        for (gy in 10..17) {
            for (gx in 10..17) {
                frameMotion2[gy * analyzer.gridWidth + gx] = 200.toByte()
            }
        }
        val avgLuma2 = 100f + (64 * 100f / totalBlocks)
        analyzer.processGridInternal(frameMotion2, avgLuma2)
        assertEquals(1, triggerCount)

        // Frame 4: Object stops moving (scene remains static identical to Frame 3) -> motion stops
        analyzer.processGridInternal(frameMotion2, avgLuma2)
        assertEquals(1, triggerCount) // triggerCount remains 1

        // Frame 5: One-off motion spike after quiet frame -> must require 2 frames again
        val frameMotion3 = ByteArray(totalBlocks) { 100.toByte() }
        for (gy in 12..19) {
            for (gx in 12..19) {
                frameMotion3[gy * analyzer.gridWidth + gx] = 200.toByte()
            }
        }
        val avgLuma3 = 100f + (64 * 100f / totalBlocks)
        analyzer.processGridInternal(frameMotion3, avgLuma3)
        assertEquals(1, triggerCount) // Not triggered yet (needs 2 frames)
    }

    @Test
    fun testScreenDimmingAndConfigGuard_regularRecording() {
        app.settingsRepository.updateSettings(
            app.settingsRepository.settings.value.copy(debugAllowPortraitRecording = true)
        )
        viewModel.startRecording("Regular Loop Recording")
        assertTrue(viewModel.uiState.value.isRecording || viewModel.uiState.value.isRecordingStarting)

        // 1. Toggling screen dimming must NOT disrupt active recording
        viewModel.setScreenDimmed(true)
        assertTrue(viewModel.uiState.value.isScreenDimmed)
        assertTrue(viewModel.uiState.value.isRecording || viewModel.uiState.value.isRecordingStarting)

        // 2. Undimming must restore preview surface while recording continues
        viewModel.setScreenDimmed(false)
        assertFalse(viewModel.uiState.value.isScreenDimmed)
        assertTrue(viewModel.uiState.value.isRecording || viewModel.uiState.value.isRecordingStarting)

        // 3. Attempting lens switch during active recording must be deferred/blocked
        val initialLens = app.settingsRepository.settings.value.frontCameraSelected
        viewModel.toggleCameraLens()
        assertEquals(initialLens, app.settingsRepository.settings.value.frontCameraSelected)
        assertTrue(viewModel.uiState.value.isRecording || viewModel.uiState.value.isRecordingStarting)
    }

    @Test
    fun testScreenDimming_sentryTier1IncidentRecording() {
        app.settingsRepository.updateSettings(
            app.settingsRepository.settings.value.copy(
                sentryModeEnabled = true,
                sentryGuardType = SentryGuardType.SHOCK_STANDBY,
                debugAllowPortraitRecording = true
            )
        )
        viewModel.armSentryMode(delaySeconds = 0)
        assertTrue(viewModel.uiState.value.isSentryArmed)
        assertTrue(viewModel.uiState.value.isScreenDimmed)

        // Impact shock in Tier 1 triggers Sentry incident recording
        viewModel.handleImpactDetected(3.5f)
        assertTrue(viewModel.uiState.value.isRecording || viewModel.uiState.value.isRecordingStarting)

        // Toggling dim state during Tier 1 incident must preserve recording session
        viewModel.setScreenDimmed(false)
        assertFalse(viewModel.uiState.value.isScreenDimmed)
        assertTrue(viewModel.uiState.value.isRecording || viewModel.uiState.value.isRecordingStarting)

        viewModel.setScreenDimmed(true)
        assertTrue(viewModel.uiState.value.isScreenDimmed)
        assertTrue(viewModel.uiState.value.isRecording || viewModel.uiState.value.isRecordingStarting)
    }

    @Test
    fun testScreenDimming_sentryTier2Monitoring() {
        app.settingsRepository.updateSettings(
            app.settingsRepository.settings.value.copy(
                sentryModeEnabled = true,
                sentryGuardType = SentryGuardType.OPTICAL_MOTION,
                debugAllowPortraitRecording = true
            )
        )
        viewModel.armSentryMode(delaySeconds = 0)
        assertTrue(viewModel.uiState.value.isSentryArmed)
        assertTrue(viewModel.uiState.value.isScreenDimmed)

        // In Tier 2 Standby, screen is dimmed while motion monitoring runs.
        // Optical motion trigger starts incident recording.
        viewModel.handleMotionDetected(score = 25f, avgLuma = 120f)
        assertTrue(viewModel.uiState.value.isRecording || viewModel.uiState.value.isRecordingStarting)

        // Screen waking or dimming must not abort the in-flight incident
        viewModel.setScreenDimmed(false)
        assertTrue(viewModel.uiState.value.isRecording || viewModel.uiState.value.isRecordingStarting)
    }

    @Test
    fun testScreenDimming_thermalGuardWarmTrigger() {
        app.settingsRepository.updateSettings(
            app.settingsRepository.settings.value.copy(
                debugAllowPortraitRecording = true,
                maxTempThresholdC = 45.0f
            )
        )
        viewModel.startRecording("Thermal Shield Test")
        assertTrue(viewModel.uiState.value.isRecording || viewModel.uiState.value.isRecordingStarting)
        assertFalse(viewModel.uiState.value.isScreenDimmed)

        // Battery heats up to warm warning threshold -> triggers Thermal Shield auto-dim
        app.batteryMonitor.onWarmTrigger?.invoke(42.0f)

        // Thermal Shield must dim the screen to cool the device
        assertTrue(viewModel.uiState.value.isScreenDimmed)
        // CRITICAL INVARIANT: Auto-dimming must NOT interrupt the active recording session!
        assertTrue(viewModel.uiState.value.isRecording || viewModel.uiState.value.isRecordingStarting)
    }

    @Test
    fun testSentryAutoLockEvents_settingRespected() {
        app.batteryMonitor.updateForTesting(levelPct = 90, tempC = 25.0f, isCharging = false)
        // 1. When sentryAutoLockEvents is false, sentry triggers do NOT lock the recording
        app.settingsRepository.updateSettings(
            app.settingsRepository.settings.value.copy(
                sentryModeEnabled = true,
                sentryGuardType = SentryGuardType.OPTICAL_MOTION,
                sentryAutoLockEvents = false,
                debugAllowPortraitRecording = true
            )
        )
        viewModel.armSentryMode(delaySeconds = 0)
        assertTrue(viewModel.uiState.value.isSentryArmed)

        viewModel.handleMotionDetected(score = 25f, avgLuma = 120f)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertFalse("Clip must not be locked when sentryAutoLockEvents is false", viewModel.uiState.value.isSegmentLocked)

        viewModel.disarmSentryMode()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        app.batteryMonitor.updateForTesting(levelPct = 90, tempC = 25.0f, isCharging = false)
        // 2. When sentryAutoLockEvents is true (default), sentry triggers DO lock the recording
        app.settingsRepository.updateSettings(
            app.settingsRepository.settings.value.copy(
                sentryModeEnabled = true,
                sentryGuardType = SentryGuardType.OPTICAL_MOTION,
                sentryAutoLockEvents = true,
                debugAllowPortraitRecording = true
            )
        )
        viewModel.armSentryMode(delaySeconds = 0)
        assertTrue(viewModel.uiState.value.isSentryArmed)

        viewModel.handleMotionDetected(score = 25f, avgLuma = 120f)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue("Clip must be locked when sentryAutoLockEvents is true", viewModel.uiState.value.isSegmentLocked)
    }

    private fun awaitJob(job: kotlinx.coroutines.Job, timeoutMs: Long = 3000L) {
        val start = System.currentTimeMillis()
        while (!job.isCompleted && System.currentTimeMillis() - start < timeoutMs) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            Thread.sleep(10)
        }
        assertTrue("Job timed out", job.isCompleted)
    }

    @Test
    fun testTripLockUnlock_losslessTriggerReasonPreserved() {
        val c1 = io.github.roaxio.dashguardpro.model.VideoClip(
            id = 101L,
            filePath = "/fake/c1.mp4",
            fileName = "c1.mp4",
            timestamp = 1000L,
            durationMs = 60_000L,
            fileSizeBytes = 1000L,
            isLocked = false,
            triggerReason = "Normal Loop"
        )
        val c2 = io.github.roaxio.dashguardpro.model.VideoClip(
            id = 102L,
            filePath = "/fake/c2.mp4",
            fileName = "c2.mp4",
            timestamp = 70_000L,
            durationMs = 60_000L,
            fileSizeBytes = 1000L,
            isLocked = true,
            triggerReason = "Impact Shock (3.5 G)"
        )
        kotlinx.coroutines.runBlocking {
            app.database.clipDao().insertClip(c1)
            app.database.clipDao().insertClip(c2)
        }

        val trip = io.github.roaxio.dashguardpro.model.TripSession(
            id = "test_trip",
            startTimeMs = 1000L,
            endTimeMs = 130_000L,
            clips = listOf(c1, c2),
            totalDurationMs = 120_000L,
            totalDistanceMeters = 0f,
            avgSpeedKmh = 0f,
            maxSpeedKmh = 0f,
            lockedCount = 1,
            incidentCount = 1,
            isSentry = false
        )

        // Lock all clips
        awaitJob(viewModel.lockTripClips(trip))

        val allLocked = kotlinx.coroutines.runBlocking { app.database.clipDao().getAllClipsList() }
        val dbC1 = allLocked.first { it.id == 101L }
        val dbC2 = allLocked.first { it.id == 102L }
        assertTrue(dbC1.isLocked)
        assertTrue(dbC2.isLocked)
        // INVARIANT: triggerReason must never be overwritten with "Trip Lock" or "Normal Loop"
        assertEquals("Normal Loop", dbC1.triggerReason)
        assertEquals("Impact Shock (3.5 G)", dbC2.triggerReason)

        // Unlock with preserveIncidents = true
        val updatedTrip = trip.copy(clips = listOf(dbC1, dbC2))
        awaitJob(viewModel.unlockTripClips(updatedTrip, preserveIncidents = true))

        val afterPreserveUnlock = kotlinx.coroutines.runBlocking { app.database.clipDao().getAllClipsList() }
        val c1After = afterPreserveUnlock.first { it.id == 101L }
        val c2After = afterPreserveUnlock.first { it.id == 102L }
        assertFalse("Normal loop should be unlocked", c1After.isLocked)
        assertTrue("Impact shock must stay locked when preserveIncidents is true", c2After.isLocked)
        assertEquals("Normal Loop", c1After.triggerReason)
        assertEquals("Impact Shock (3.5 G)", c2After.triggerReason)

        // Unlock with preserveIncidents = false
        awaitJob(viewModel.unlockTripClips(updatedTrip, preserveIncidents = false))

        val afterFullUnlock = kotlinx.coroutines.runBlocking { app.database.clipDao().getAllClipsList() }
        assertFalse(afterFullUnlock.first { it.id == 101L }.isLocked)
        assertFalse(afterFullUnlock.first { it.id == 102L }.isLocked)
        assertEquals("Impact Shock (3.5 G)", afterFullUnlock.first { it.id == 102L }.triggerReason)
    }

    @Test
    fun testDeleteTrip_preservesLockedClipsWhenRequested() {
        val c1 = io.github.roaxio.dashguardpro.model.VideoClip(
            id = 201L,
            filePath = "/fake/del1.mp4",
            fileName = "del1.mp4",
            timestamp = 1000L,
            durationMs = 60_000L,
            fileSizeBytes = 1000L,
            isLocked = false,
            triggerReason = "Normal Loop"
        )
        val c2 = io.github.roaxio.dashguardpro.model.VideoClip(
            id = 202L,
            filePath = "/fake/del2.mp4",
            fileName = "del2.mp4",
            timestamp = 70_000L,
            durationMs = 60_000L,
            fileSizeBytes = 1000L,
            isLocked = true,
            triggerReason = "Impact Shock (4.0 G)"
        )
        kotlinx.coroutines.runBlocking {
            app.database.clipDao().insertClip(c1)
            app.database.clipDao().insertClip(c2)
        }

        val trip = io.github.roaxio.dashguardpro.model.TripSession(
            id = "test_del_trip",
            startTimeMs = 1000L,
            endTimeMs = 130_000L,
            clips = listOf(c1, c2),
            totalDurationMs = 120_000L,
            totalDistanceMeters = 0f,
            avgSpeedKmh = 0f,
            maxSpeedKmh = 0f,
            lockedCount = 1,
            incidentCount = 1,
            isSentry = false
        )

        // Delete unlocked only (deleteLockedClips = false)
        awaitJob(viewModel.deleteTrip(trip, deleteLockedClips = false))

        var remaining = kotlinx.coroutines.runBlocking { app.database.clipDao().getAllClipsList() }
        assertNull("Unlocked clip must be deleted", remaining.find { it.id == 201L })
        assertNotNull("Locked clip must be preserved", remaining.find { it.id == 202L })

        // Delete all including locked (deleteLockedClips = true)
        awaitJob(viewModel.deleteTrip(trip, deleteLockedClips = true))

        remaining = kotlinx.coroutines.runBlocking { app.database.clipDao().getAllClipsList() }
        assertNull("Locked clip must also be deleted", remaining.find { it.id == 202L })
    }

    @Test
    fun testLowBatterySafetyCutoff_entersLowPowerSleep_andDoesNotRecoverOnThermalCoolOff() {
        app.settingsRepository.updateSettings(
            app.settingsRepository.settings.value.copy(
                lowBatterySafetyLimitPct = 15,
                maxTempThresholdC = 48.0f,
                thermalRecoveryTargetTempC = 44.0f,
                debugAllowPortraitRecording = true
            )
        )
        viewModel.updateUiState { it.copy(isRecording = true) }
        assertTrue(viewModel.uiState.value.isRecording)

        // Battery drops to 15% unplugged -> triggers Low Battery Cutoff
        app.batteryMonitor.updateForTesting(
            levelPct = 15,
            tempC = 45.0f,
            isCharging = false,
            powerSource = PowerSource.BATTERY
        )
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        // Verify low battery sleep entered
        assertTrue("Must enter low battery sleep on low battery cutoff", viewModel.uiState.value.isLowBatterySleepActive)
        assertEquals(DeviceSleepMode.LOW_BATTERY_STANDBY, viewModel.uiState.value.deviceSleepMode)
        assertFalse("Thermal cooling must not be active", viewModel.uiState.value.isThermalCoolingActive)
        assertFalse("Recording must halt", viewModel.uiState.value.isRecording)

        // Temperature cools down to 35°C (well below target 44°C) while still unplugged at 15%
        app.batteryMonitor.updateForTesting(
            levelPct = 15,
            tempC = 35.0f,
            isCharging = false,
            powerSource = PowerSource.BATTERY
        )
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        // Cool-off must NOT exit low-battery sleep!
        assertTrue("Must remain in low-battery sleep on cool-off alone", viewModel.uiState.value.isLowBatterySleepActive)
        assertEquals(DeviceSleepMode.LOW_BATTERY_STANDBY, viewModel.uiState.value.deviceSleepMode)

        // Charger connected but battery only 16% (safeFloor is 15 + 2 = 17%)
        app.batteryMonitor.updateForTesting(
            levelPct = 16,
            tempC = 35.0f,
            isCharging = true,
            powerSource = PowerSource.AC
        )
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue("Must remain in sleep until battery exceeds safe floor", viewModel.uiState.value.isLowBatterySleepActive)

        // Battery reaches 18% with charger connected -> Exits sleep
        app.batteryMonitor.updateForTesting(
            levelPct = 18,
            tempC = 35.0f,
            isCharging = true,
            powerSource = PowerSource.AC
        )
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertFalse("Must exit sleep once charging and past safe floor", viewModel.uiState.value.isLowBatterySleepActive)
        assertEquals(DeviceSleepMode.NONE, viewModel.uiState.value.deviceSleepMode)
    }

    @Test
    fun testPreSleepStateMemory_thermalRecoveryRestoresSentryTier1WithAcoustic() {
        app.settingsRepository.updateSettings(
            app.settingsRepository.settings.value.copy(
                sentryModeEnabled = true,
                sentryGuardType = SentryGuardType.SHOCK_STANDBY,
                sentryAcousticTriggerSensitivity = Sensitivity.HIGH,
                maxTempThresholdC = 48.0f,
                thermalRecoveryTargetTempC = 44.0f
            )
        )
        viewModel.armSentryMode(delaySeconds = 0)
        assertTrue(viewModel.uiState.value.isSentryArmed)

        // Thermal overheat triggers sleep
        app.batteryMonitor.onThermalWarning?.invoke(49.0f)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue(viewModel.uiState.value.isThermalCoolingActive)

        // Cool-off occurs while parked (unplugged)
        app.batteryMonitor.updateForTesting(
            levelPct = 80,
            tempC = 43.0f,
            isCharging = false,
            powerSource = PowerSource.BATTERY
        )
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertFalse(viewModel.uiState.value.isThermalCoolingActive)
        assertTrue("Must restore Sentry mode", viewModel.uiState.value.isSentryArmed)
        assertEquals(AppMode.SENTRY, viewModel.settings.value.currentMode)
        assertEquals(SentryGuardType.SHOCK_STANDBY, viewModel.settings.value.sentryGuardType)
        assertEquals(Sensitivity.HIGH, app.acousticNoiseMonitor.sensitivity)
    }

    @Test
    fun testPreSleepStateMemory_thermalRecoveryRestoresSentryTier2WithAcoustic() {
        app.settingsRepository.updateSettings(
            app.settingsRepository.settings.value.copy(
                sentryModeEnabled = true,
                sentryGuardType = SentryGuardType.OPTICAL_MOTION,
                sentryAcousticTriggerSensitivity = Sensitivity.MEDIUM,
                sentryAutoDemoteBatteryPct = 30,
                maxTempThresholdC = 48.0f,
                thermalRecoveryTargetTempC = 44.0f
            )
        )
        viewModel.armSentryMode(delaySeconds = 0)
        assertTrue(viewModel.uiState.value.isSentryArmed)

        // Thermal overheat triggers sleep
        app.batteryMonitor.onThermalWarning?.invoke(49.0f)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue(viewModel.uiState.value.isThermalCoolingActive)

        // Cool-off occurs while parked (unplugged) with healthy battery (80% > demote floor 30%) and temperature (39°C < demote limit 41°C)
        app.batteryMonitor.updateForTesting(
            levelPct = 80,
            tempC = 39.0f,
            isCharging = false,
            powerSource = PowerSource.BATTERY
        )
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertFalse(viewModel.uiState.value.isThermalCoolingActive)
        assertTrue("Must restore Sentry mode", viewModel.uiState.value.isSentryArmed)
        assertEquals(AppMode.SENTRY, viewModel.settings.value.currentMode)
        assertEquals(SentryGuardType.OPTICAL_MOTION, viewModel.settings.value.sentryGuardType)
        assertEquals(Sensitivity.MEDIUM, app.acousticNoiseMonitor.sensitivity)
    }

    @Test
    fun testWarmAutoDim_noOpIfScreenAlreadyDimmed() {
        viewModel.setScreenDimmed(true)
        assertTrue(viewModel.uiState.value.isScreenDimmed)

        // Trigger warm trigger
        app.batteryMonitor.onWarmTrigger?.invoke(42.0f)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        // Screen is still dimmed, no crash or unexpected state change
        assertTrue(viewModel.uiState.value.isScreenDimmed)
    }

    @Test
    fun testSettingsRepository_reciprocalBoundsAndAutoAdjustment() {
        val repo = app.settingsRepository

        // 1. Clamping custom recovery and warm dim to max temp
        repo.updateSettings(
            repo.settings.value.copy(
                maxTempThresholdC = 46.0f,
                thermalRecoveryIsAuto = false,
                thermalRecoveryTargetTempC = 45.5f, // Exceeds 46 - 2 = 44
                thermalAutoDimIsAuto = false,
                thermalAutoDimTempC = 43.5f // Exceeds 46 - 4 = 42
            )
        )
        assertEquals(44.0f, repo.settings.value.thermalRecoveryTargetTempC, 0.01f)
        assertEquals(42.0f, repo.settings.value.thermalAutoDimTempC, 0.01f)

        // 2. Clamping sentry demote battery to low battery limit + 5 and sentry demote temp to max temp - 4
        repo.updateSettings(
            repo.settings.value.copy(
                maxTempThresholdC = 46.0f,
                lowBatterySafetyLimitPct = 30,
                sentryAutoDemoteBatteryPct = 25, // Less than 30 + 5 = 35
                sentryAutoDemoteTempIsAuto = false,
                sentryAutoDemoteTempC = 45.0f // Exceeds 46 - 4 = 42
            )
        )
        assertEquals(35, repo.settings.value.sentryAutoDemoteBatteryPct)
        assertEquals(42.0f, repo.settings.value.sentryAutoDemoteTempC, 0.01f)

        // 3. Auto-adjusting derivation when maxTemp changes
        repo.updateSettings(
            repo.settings.value.copy(
                thermalAutoDimIsAuto = true,
                thermalRecoveryIsAuto = true,
                sentryAutoDemoteTempIsAuto = true,
                maxTempThresholdC = 50.0f
            )
        )
        assertEquals(46.0f, repo.settings.value.thermalRecoveryTargetTempC, 0.01f)
        assertEquals(43.0f, repo.settings.value.thermalAutoDimTempC, 0.01f)
        assertEquals(46.0f, repo.settings.value.sentryAutoDemoteTempC, 0.01f)
    }

    @Test
    fun testLowPowerSleep_reTriggerNoOp_whenAlreadyInSleep() {
        app.settingsRepository.updateSettings(
            app.settingsRepository.settings.value.copy(
                lowBatterySafetyLimitPct = 15,
                maxTempThresholdC = 48.0f
            )
        )
        // Enter sleep via thermal warning
        app.batteryMonitor.onThermalWarning?.invoke(49.0f)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue(viewModel.uiState.value.isThermalCoolingActive)

        // Re-triggering thermal warning while already in sleep must be a clean no-op
        app.batteryMonitor.onThermalWarning?.invoke(52.0f)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue(viewModel.uiState.value.isThermalCoolingActive)

        // Re-triggering low battery cutoff while already in sleep must also be a clean no-op
        app.batteryMonitor.onLowBatteryCutoff?.invoke(10)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue(viewModel.uiState.value.isThermalCoolingActive)
    }

    @Test
    fun testLowPowerSleep_idleStandbyEntersSleep_andRecoversToStandby() {
        app.settingsRepository.updateSettings(
            app.settingsRepository.settings.value.copy(
                lowBatterySafetyLimitPct = 15,
                maxTempThresholdC = 48.0f,
                thermalRecoveryTargetTempC = 44.0f,
                autoRecordOnPower = false
            )
        )
        // Ensure idle Standby
        assertFalse(viewModel.uiState.value.isRecording)
        assertFalse(viewModel.uiState.value.isSentryArmed)

        // Overheat in pure idle Standby
        app.batteryMonitor.onThermalWarning?.invoke(50.0f)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertTrue("Standby must enter sleep to protect device", viewModel.uiState.value.isThermalCoolingActive)
        assertTrue(viewModel.uiState.value.isScreenDimmed)

        // Cooldown occurs
        app.batteryMonitor.updateForTesting(
            levelPct = 80,
            tempC = 40.0f,
            isCharging = false,
            powerSource = PowerSource.BATTERY
        )
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertFalse("Must exit sleep", viewModel.uiState.value.isThermalCoolingActive)
        assertFalse("Screen must un-dim for Standby", viewModel.uiState.value.isScreenDimmed)
        assertFalse(viewModel.uiState.value.isRecording)
        assertEquals(AppMode.DASHCAM, viewModel.settings.value.currentMode)
    }

    @Test
    fun testLowPowerSleep_unifiedSafeGate_requiresBothTempAndBattery() {
        app.settingsRepository.updateSettings(
            app.settingsRepository.settings.value.copy(
                lowBatterySafetyLimitPct = 10,
                maxTempThresholdC = 48.0f,
                thermalRecoveryTargetTempC = 44.0f,
                autoRecordOnPower = false
            )
        )
        // Enter sleep via thermal overheat with normal battery (50%)
        app.batteryMonitor.updateForTesting(levelPct = 50, tempC = 50.0f, isCharging = false)
        app.batteryMonitor.onThermalWarning?.invoke(50.0f)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue(viewModel.uiState.value.isThermalCoolingActive)

        // While asleep, battery drains below low-battery cutoff (8% <= 10%)
        // AND temperature cools down to 38°C (<= 44°C)
        app.batteryMonitor.updateForTesting(
            levelPct = 8,
            tempC = 38.0f,
            isCharging = false,
            powerSource = PowerSource.BATTERY
        )
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        // MUST NOT exit sleep because battery is below cutoff! Transitions to low-battery sleep
        assertFalse("Must exit thermal cooling once temperature is safe", viewModel.uiState.value.isThermalCoolingActive)
        assertTrue("Must transition to low battery sleep because battery is below cutoff", viewModel.uiState.value.isLowBatterySleepActive)
        assertEquals(DeviceSleepMode.LOW_BATTERY_STANDBY, viewModel.uiState.value.deviceSleepMode)

        // Charger connected, but battery only 9% (<= 10 + 2 = 12%)
        app.batteryMonitor.updateForTesting(
            levelPct = 9,
            tempC = 38.0f,
            isCharging = true,
            powerSource = PowerSource.AC
        )
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue("Must remain in sleep until battery exceeds safe floor (12%)", viewModel.uiState.value.isLowBatterySleepActive)

        // Battery charges to 13% (> 12%) while cool
        app.batteryMonitor.updateForTesting(
            levelPct = 13,
            tempC = 38.0f,
            isCharging = true,
            powerSource = PowerSource.AC
        )
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertFalse("Must exit sleep once BOTH temperature is cool AND battery is safely charged", viewModel.uiState.value.isLowBatterySleepActive)
        assertEquals(DeviceSleepMode.NONE, viewModel.uiState.value.deviceSleepMode)
    }

    @Test
    fun testSleepEmergencyShockClipDuration_settingPersistence() {
        val repo = app.settingsRepository
        assertEquals(30, repo.settings.value.sleepEmergencyClipDurationSeconds)

        repo.updateSettings(repo.settings.value.copy(sleepEmergencyClipDurationSeconds = 45))
        assertEquals(45, repo.settings.value.sleepEmergencyClipDurationSeconds)

        repo.updateSettings(repo.settings.value.copy(sleepEmergencyClipDurationSeconds = 60))
        assertEquals(60, repo.settings.value.sleepEmergencyClipDurationSeconds)
    }
}

