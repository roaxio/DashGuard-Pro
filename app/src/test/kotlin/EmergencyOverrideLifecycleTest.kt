package io.github.roaxio.dashguardpro

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.roaxio.dashguardpro.viewmodel.DashcamViewModel
import io.github.roaxio.dashguardpro.viewmodel.state.RecordingOriginState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmergencyOverrideLifecycleTest {

    private lateinit var context: Context
    private lateinit var app: DashGuardApplication
    private lateinit var viewModel: DashcamViewModel

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        app = context as DashGuardApplication
        app.settingsRepository.updateSettings(
            app.settingsRepository.settings.value.copy(
                debugAllowPortraitRecording = true
            )
        )
        viewModel = DashcamViewModel(app)
    }

    @After
    fun tearDown() {
        viewModel.stopRecording()
    }

    @Test
    fun testStandbyOrigin_setsOriginAndTargetCorrectly() {
        // Initially in Standby
        assertFalse("App must start in Standby (not recording)", viewModel.uiState.value.isRecording)
        assertEquals(RecordingOriginState.STANDBY, viewModel.recordingOriginState)

        // Trigger impact shock while in Standby
        viewModel.handleImpactDetected(5.2f)

        // Must recognize origin was Standby
        assertEquals(RecordingOriginState.STANDBY, viewModel.recordingOriginState)
        assertTrue("Clip must be locked", viewModel.uiState.value.isSegmentLocked)

        // Target seconds must be bounded to incident buffer (e.g. >= 30s) rather than full 3m driving loop
        assertTrue("Target seconds must be >= 30s incident buffer", viewModel.uiState.value.targetSegmentSeconds >= 30)
    }

    @Test
    fun testInFlightThermalWarning_autoDimsScreenAndDefersThermalSleep() {
        assertFalse(viewModel.uiState.value.isRecording)
        viewModel.startRecording("Impact Shock (5.2G)")

        // Clip is locked as an emergency incident
        assertTrue(viewModel.uiState.value.isSegmentLocked)
        assertFalse(viewModel.pendingThermalSleep)

        // Thermal warning (49°C) arrives while emergency recording is in flight
        app.batteryMonitor.onThermalWarning?.invoke(49.0f)

        // Invariant: Evidence is preserved (recording continues), but screen is proactively dimmed to shed heat
        assertTrue("Screen must be proactively dimmed during in-flight thermal warning", viewModel.isAnyScreenDimmed)
        assertTrue("Pending thermal sleep flag must be armed", viewModel.pendingThermalSleep)

        // When segment completes / rotates, pending thermal sleep must transition system to Thermal Cooling Sleep
        val method = DashcamViewModel::class.java.getDeclaredMethod("rotateSegment")
        method.isAccessible = true
        method.invoke(viewModel)

        assertTrue("System must enter Thermal Cooling Sleep upon segment completion", viewModel.uiState.value.isThermalCoolingActive)
        assertFalse("Recording must stop upon entering Thermal Cooling Sleep", viewModel.uiState.value.isRecording)
        assertFalse("Pending flag must reset", viewModel.pendingThermalSleep)
    }

    @Test
    fun testStandbyEmergencySegment_returnsToStandbyWithoutLooping() {
        viewModel.startRecording("Impact Shock (5.2G)")
        assertEquals(RecordingOriginState.STANDBY, viewModel.recordingOriginState)

        // Rotate segment invoked at segment completion
        val method = DashcamViewModel::class.java.getDeclaredMethod("rotateSegment")
        method.isAccessible = true
        method.invoke(viewModel)

        // Must return cleanly to Standby, NOT start another loop chunk
        assertFalse("System must return to Standby (not recording)", viewModel.uiState.value.isRecording)
        assertEquals(RecordingOriginState.STANDBY, viewModel.recordingOriginState)
    }

    @Test
    fun testStandbyLockButton_startsManualEmergencyRecording() {
        assertFalse("App starts in standby", viewModel.uiState.value.isRecording)
        assertEquals(RecordingOriginState.STANDBY, viewModel.recordingOriginState)

        // User taps the HUD lock button in Standby mode
        viewModel.toggleCurrentClipLock()

        assertTrue("Emergency recording must be starting or active",
            viewModel.uiState.value.isRecordingStarting || viewModel.uiState.value.isRecording
        )
        assertTrue("Recording must be protected/locked", viewModel.uiState.value.isSegmentLocked)
        assertEquals("Manual Emergency Lock", viewModel.uiState.value.lastTriggerReason)
        assertEquals(RecordingOriginState.STANDBY, viewModel.recordingOriginState)
    }

    @Test
    fun testPowerLossDuringLockedRecording_cancelsCountdownAndDefersShutdown() {
        app.batteryMonitor.updateForTesting(levelPct = 80, tempC = 30f, isCharging = true)
        viewModel.updateUiState { it.copy(isRecording = true, isSegmentLocked = true) }
        assertTrue(viewModel.uiState.value.isSegmentLocked)

        // Disconnect power while clip is locked
        app.batteryMonitor.updateForTesting(levelPct = 80, tempC = 30f, isCharging = false)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        // Unplug countdown must NOT be active; action must be queued
        assertFalse("Countdown must not be active for locked incident", viewModel.alertPresenter.isUnplugCountdownActive)
        assertTrue("Pending post-lock action must be queued", viewModel.powerAndThermalCoordinator.hasPendingPostLockAction())

        // Executing the pending action after clip finishes must stop recording
        viewModel.powerAndThermalCoordinator.executePendingPostLockAction()
        assertFalse("Recording must stop when post-lock action executes", viewModel.uiState.value.isRecording)
    }

    @Test
    fun testUnplugCountdownActive_lockingClipCancelsCountdownAndQueuesPowerLoss() {
        app.settingsRepository.updateSettings(
            app.settingsRepository.settings.value.copy(autoStopOnUnplugSeconds = 10)
        )
        // Set initial charging state
        app.batteryMonitor.updateForTesting(levelPct = 80, tempC = 30f, isCharging = true)
        viewModel.updateUiState { it.copy(isRecording = true, isSegmentLocked = false) }
        assertFalse(viewModel.uiState.value.isSegmentLocked)

        // Disconnect power -> triggers unplug countdown
        app.batteryMonitor.updateForTesting(levelPct = 80, tempC = 30f, isCharging = false)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue("Countdown must start on unplug", viewModel.alertPresenter.isUnplugCountdownActive)

        // User locks clip while countdown is in-flight
        viewModel.toggleCurrentClipLock("Manual Incident Lock")
        assertTrue(viewModel.uiState.value.isSegmentLocked)
        assertFalse("Countdown must be cancelled immediately when clip is locked", viewModel.alertPresenter.isUnplugCountdownActive)
        assertTrue("Power-loss action must be queued for segment completion", viewModel.powerAndThermalCoordinator.hasPendingPostLockAction())
    }

    @Test
    fun testBatteryCutoff_decreasedLevelTriggersSleepRegardlessOfCharging() {
        app.settingsRepository.updateSettings(
            app.settingsRepository.settings.value.copy(lowBatterySafetyLimitPct = 30)
        )

        // Device operating with slow charger at 26%
        app.batteryMonitor.updateForTesting(levelPct = 26, tempC = 30f, isCharging = true)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        // Reset baseline as when user manually operates or resumes app
        viewModel.powerAndThermalCoordinator.cancelThermalCooling()
        assertFalse(viewModel.uiState.value.isLowBatterySleepActive)

        // Subsequent check: battery stays at 26% -> Must NOT trigger sleep
        app.batteryMonitor.updateForTesting(levelPct = 26, tempC = 30f, isCharging = true)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertFalse("Same level must not trigger sleep", viewModel.uiState.value.isLowBatterySleepActive)

        // Battery increases to 27% -> Must NOT trigger sleep
        app.batteryMonitor.updateForTesting(levelPct = 27, tempC = 30f, isCharging = true)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertFalse("Increasing level must not trigger sleep", viewModel.uiState.value.isLowBatterySleepActive)

        // Battery decreases to 25% (draw exceeds slow charger) -> Must trigger sleep regardless of isCharging
        app.batteryMonitor.updateForTesting(levelPct = 25, tempC = 30f, isCharging = true)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue("Decreased level below threshold must trigger sleep even while charging", viewModel.uiState.value.isLowBatterySleepActive)

        // Recovery: battery level recovers to 33% (> 30 + 2 = 32%) even if unplugged -> Exits sleep
        app.batteryMonitor.updateForTesting(levelPct = 33, tempC = 30f, isCharging = false)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertFalse("Battery recovery must exit sleep based on level alone", viewModel.uiState.value.isLowBatterySleepActive)
    }

    @Test
    fun testSleepRecovery_restoresPriorDrivingLoopState() {
        // App is recording driving loop
        viewModel.updateUiState { it.copy(isRecording = true) }
        assertTrue(viewModel.uiState.value.isRecording)

        // Low battery cutoff triggers
        app.settingsRepository.updateSettings(
            app.settingsRepository.settings.value.copy(lowBatterySafetyLimitPct = 20)
        )
        app.batteryMonitor.updateForTesting(levelPct = 15, tempC = 30f, isCharging = false)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertTrue(viewModel.uiState.value.isLowBatterySleepActive)
        assertFalse(viewModel.uiState.value.isRecording)

        // Battery recovers to 25% (> 20 + 2 = 22%)
        app.batteryMonitor.updateForTesting(levelPct = 25, tempC = 30f, isCharging = false)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        // Must recover into DRIVING_LOOP and resume recording!
        assertFalse("Sleep mode must be exited", viewModel.uiState.value.isLowBatterySleepActive)
        assertTrue("Recording must automatically resume into previous driving loop state",
            viewModel.uiState.value.isRecording || viewModel.uiState.value.isRecordingStarting
        )
        assertFalse("Resumed loop recording must NOT be locked", viewModel.uiState.value.isSegmentLocked)
        assertEquals("Normal Loop", viewModel.uiState.value.lastTriggerReason)
    }

    @Test
    fun testManualWakeup_restoresPriorDrivingLoopState() {
        // App is recording driving loop
        viewModel.updateUiState { it.copy(isRecording = true) }
        assertTrue(viewModel.uiState.value.isRecording)

        // Enters low battery sleep
        app.settingsRepository.updateSettings(
            app.settingsRepository.settings.value.copy(lowBatterySafetyLimitPct = 20)
        )
        app.batteryMonitor.updateForTesting(levelPct = 15, tempC = 30f, isCharging = false)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue(viewModel.uiState.value.isLowBatterySleepActive)

        // User manually wakes up (taps screen)
        viewModel.powerAndThermalCoordinator.cancelThermalCooling()

        assertFalse("Sleep mode must be exited", viewModel.uiState.value.isLowBatterySleepActive)
        assertTrue("Recording must resume into previous driving loop state on manual wake",
            viewModel.uiState.value.isRecording || viewModel.uiState.value.isRecordingStarting
        )
        assertFalse("Resumed loop recording must NOT be locked on manual wake", viewModel.uiState.value.isSegmentLocked)
        assertEquals("Normal Loop", viewModel.uiState.value.lastTriggerReason)
    }

    @Test
    fun testSleepExit_sentryRestoresAcousticNoiseMonitorAndGuardType() {
        app.settingsRepository.updateSettings(
            app.settingsRepository.settings.value.copy(
                sentryModeEnabled = true,
                sentryGuardType = io.github.roaxio.dashguardpro.data.settings.SentryGuardType.SHOCK_STANDBY,
                sentryAcousticTriggerSensitivity = io.github.roaxio.dashguardpro.data.settings.Sensitivity.HIGH,
                lowBatterySafetyLimitPct = 20
            )
        )
        viewModel.armSentryMode(delaySeconds = 0)
        assertTrue(viewModel.uiState.value.isSentryArmed)

        // Low battery triggers sleep
        app.batteryMonitor.updateForTesting(levelPct = 15, tempC = 30f, isCharging = false)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue(viewModel.uiState.value.isLowBatterySleepActive)

        // Battery recovers to 25% (> 22%)
        app.batteryMonitor.updateForTesting(levelPct = 25, tempC = 30f, isCharging = false)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        // Sentry must be restored with acoustic noise monitor sensitivity intact
        assertFalse(viewModel.uiState.value.isLowBatterySleepActive)
        assertTrue("Sentry must be armed upon recovery", viewModel.uiState.value.isSentryArmed)
        assertEquals(io.github.roaxio.dashguardpro.data.settings.AppMode.SENTRY, viewModel.settings.value.currentMode)
        assertEquals(io.github.roaxio.dashguardpro.data.settings.Sensitivity.HIGH, app.acousticNoiseMonitor.sensitivity)
        assertFalse("Segment lock must be cleared on Sentry restore", viewModel.uiState.value.isSegmentLocked)
    }

    @Test
    fun testManualWakeup_sentryRestoresAcousticNoiseMonitorAndGuardType() {
        app.settingsRepository.updateSettings(
            app.settingsRepository.settings.value.copy(
                sentryModeEnabled = true,
                sentryGuardType = io.github.roaxio.dashguardpro.data.settings.SentryGuardType.OPTICAL_MOTION,
                sentryAcousticTriggerSensitivity = io.github.roaxio.dashguardpro.data.settings.Sensitivity.VERY_HIGH,
                lowBatterySafetyLimitPct = 20
            )
        )
        viewModel.armSentryMode(delaySeconds = 0)
        assertTrue(viewModel.uiState.value.isSentryArmed)

        // Low battery triggers sleep
        app.batteryMonitor.updateForTesting(levelPct = 15, tempC = 30f, isCharging = false)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue(viewModel.uiState.value.isLowBatterySleepActive)

        // Manual wake up (tap screen)
        viewModel.powerAndThermalCoordinator.cancelThermalCooling()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        // Sentry must be restored with acoustic noise monitor sensitivity intact
        assertFalse(viewModel.uiState.value.isLowBatterySleepActive)
        assertTrue("Sentry must be armed upon manual wake", viewModel.uiState.value.isSentryArmed)
        assertEquals(io.github.roaxio.dashguardpro.data.settings.AppMode.SENTRY, viewModel.settings.value.currentMode)
        assertEquals(io.github.roaxio.dashguardpro.data.settings.Sensitivity.VERY_HIGH, app.acousticNoiseMonitor.sensitivity)
        assertFalse("Segment lock must be cleared on Sentry manual wake", viewModel.uiState.value.isSegmentLocked)
    }

    @Test
    fun testIncidentBufferGuarantee_customDurationSetsTargetSeconds() {
        // User configures custom 120s incident guarantee window
        app.settingsRepository.updateSettings(
            app.settingsRepository.settings.value.copy(incidentBufferSeconds = 120)
        )

        // Tap Lock in Standby
        viewModel.toggleCurrentClipLock()

        assertEquals("Target seconds must match the configured 120s incident guarantee window",
            120, viewModel.uiState.value.targetSegmentSeconds
        )
    }

    @Test
    fun testDistinctAudioFeedbackMethods() {
        // Must execute cleanly without exception
        app.alertManager.playRecordStartSound()
        app.alertManager.playRecordStopSound()
        app.alertManager.playLockSound()
        app.alertManager.playChime()
    }

    @Test
    fun testStandby_PortraitWithPortraitDisabled_ImpactShockDoesNotLeaveStuckLockState() {
        // Disallow portrait recording in settings
        app.settingsRepository.updateSettings(
            app.settingsRepository.settings.value.copy(
                debugAllowPortraitRecording = false
            )
        )
        assertFalse("Device must be in Standby", viewModel.uiState.value.isRecording)

        // Trigger impact shock while in portrait and portrait recording is disallowed
        viewModel.handleImpactDetected(5.5f)

        // Recording could not start due to admission failure
        assertFalse("Recording must not have started", viewModel.uiState.value.isRecording)
        assertFalse("Phantom lock flag must not be retained when recording cannot start", viewModel.uiState.value.isSegmentLocked)
    }

    @Test
    fun testStandby_WhenLocked_ToggleCurrentClipLockClearsLockSymmetrically() {
        // Disallow portrait recording
        app.settingsRepository.updateSettings(
            app.settingsRepository.settings.value.copy(
                debugAllowPortraitRecording = false
            )
        )
        assertFalse("Device must be in Standby", viewModel.uiState.value.isRecording)

        // Force an emergency lock state in Standby (simulating transient lock flag)
        val updateMethod = DashcamViewModel::class.java.getDeclaredMethod("updateUiState", kotlin.jvm.functions.Function1::class.java)
        updateMethod.isAccessible = true
        val transform: (io.github.roaxio.dashguardpro.viewmodel.state.DashcamUiState) -> io.github.roaxio.dashguardpro.viewmodel.state.DashcamUiState = {
            it.copy(isSegmentLocked = true, lastTriggerReason = "Simulated Impact")
        }
        updateMethod.invoke(viewModel, transform)
        assertTrue("Pre-condition: state is locked in Standby", viewModel.uiState.value.isSegmentLocked)

        // User taps the emergency lock button to get out of the locked state
        viewModel.toggleCurrentClipLock()

        // Symmetrical toggle: lock state is cleared
        assertFalse("Lock state must be cleared after toggle in Standby", viewModel.uiState.value.isSegmentLocked)
        assertEquals("Normal Loop", viewModel.uiState.value.lastTriggerReason)
    }
}
