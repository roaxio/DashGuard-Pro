package io.github.roaxio.dashguardpro

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.roaxio.dashguardpro.data.settings.SentryDeterrentMode
import io.github.roaxio.dashguardpro.data.settings.SentryGuardType
import io.github.roaxio.dashguardpro.data.settings.SentryInterruptedClipAction
import io.github.roaxio.dashguardpro.data.settings.Sensitivity
import io.github.roaxio.dashguardpro.sentry.SentryController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SentryControllerTest {

    private lateinit var context: Context
    private lateinit var app: DashGuardApplication
    private lateinit var sentryController: SentryController

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        app = context as DashGuardApplication
        sentryController = SentryController(app.alertManager, app.acousticNoiseMonitor)
    }

    @Test
    fun testInitialState_isClean() {
        assertFalse(sentryController.isArmed)
        assertFalse(sentryController.isArming)
        assertEquals(0, sentryController.countdownSeconds)
        assertEquals(0, sentryController.eventCount)
    }

    @Test
    fun testInstantArming_zeroDelay() = runTest {
        var armedCallbackCalled = false
        sentryController.arm(this, delaySeconds = 0) {
            armedCallbackCalled = true
        }
        assertTrue(armedCallbackCalled)
        assertTrue(sentryController.isArmed)
        assertFalse(sentryController.isArming)
        assertEquals(0, sentryController.countdownSeconds)
    }

    @Test
    fun testCountdownArming_withDelay() = runTest {
        var armedCallbackCalled = false
        sentryController.arm(this, delaySeconds = 3) {
            armedCallbackCalled = true
        }

        assertTrue(sentryController.isArming)
        assertFalse(sentryController.isArmed)
        assertEquals(3, sentryController.countdownSeconds)

        advanceTimeBy(1001L)
        assertEquals(2, sentryController.countdownSeconds)

        advanceTimeBy(1001L)
        assertEquals(1, sentryController.countdownSeconds)

        advanceTimeBy(1001L)
        assertTrue(armedCallbackCalled)
        assertTrue(sentryController.isArmed)
        assertFalse(sentryController.isArming)
        assertEquals(0, sentryController.countdownSeconds)
    }

    @Test
    fun testCancelArming_abortsPendingCountdown() = runTest {
        var armedCallbackCalled = false
        sentryController.arm(this, delaySeconds = 5) {
            armedCallbackCalled = true
        }
        assertTrue(sentryController.isArming)

        advanceTimeBy(1001L)
        sentryController.cancelArming()

        assertFalse(sentryController.isArming)
        assertEquals(0, sentryController.countdownSeconds)
        assertFalse(sentryController.isArmed)

        // Advance further and verify callback is never called
        advanceTimeBy(10000L)
        assertFalse(armedCallbackCalled)
    }

    @Test
    fun testDisarm_resetsArmedStateAndReportsIncidents() = runTest {
        sentryController.arm(this, delaySeconds = 0) {}
        assertTrue(sentryController.isArmed)

        sentryController.incrementEventCount()
        sentryController.incrementEventCount()
        assertEquals(2, sentryController.eventCount)

        var reportedIncidents = -1
        sentryController.disarm(voiceAlertsEnabled = false) { incidents ->
            reportedIncidents = incidents
        }

        assertFalse(sentryController.isArmed)
        assertFalse(sentryController.isArming)
        assertEquals(2, reportedIncidents)
        assertEquals(0, sentryController.eventCount)
    }

    @Test
    fun testDispatchMotionAlert_incrementsEventCount() {
        assertEquals(0, sentryController.eventCount)
        sentryController.dispatchMotionAlert(
            score = 30f,
            deterrentMode = SentryDeterrentMode.NONE,
            autoLock = true,
            isRecording = false
        )
        assertEquals(1, sentryController.eventCount)

        // While recording, subsequent motion should not increment incident counter
        sentryController.dispatchMotionAlert(
            score = 45f,
            deterrentMode = SentryDeterrentMode.NONE,
            autoLock = true,
            isRecording = true
        )
        assertEquals(1, sentryController.eventCount)
    }

    @Test
    fun testDispatchAcousticAlert_incrementsEventCount() {
        assertEquals(0, sentryController.eventCount)
        sentryController.dispatchAcousticAlert(
            db = 85f,
            deterrentMode = SentryDeterrentMode.NONE,
            autoLock = true,
            isRecording = false
        )
        assertEquals(1, sentryController.eventCount)
    }

    @Test
    fun testDisarm_withExplicitIncidentCountOverride() = runTest {
        sentryController.arm(this, delaySeconds = 0) {}
        var reported = -1
        sentryController.disarm(voiceAlertsEnabled = false, incidentCount = 5) { incidents ->
            reported = incidents
        }
        assertEquals(5, reported)
        assertEquals(0, sentryController.eventCount)
    }

    @Test
    fun testEvaluateAutoDemotion_underThermalAndBatteryStress() {
        // Not optical motion -> no demotion
        assertFalse(
            sentryController.evaluateAutoDemotion(
                batteryLevelPct = 20,
                batteryTempC = 45f,
                demoteBatteryThresholdPct = 30,
                demoteTempThresholdC = 44f,
                currentGuardType = SentryGuardType.SHOCK_STANDBY
            )
        )

        // Optical motion + battery below threshold -> demotes
        assertTrue(
            sentryController.evaluateAutoDemotion(
                batteryLevelPct = 25,
                batteryTempC = 30f,
                demoteBatteryThresholdPct = 30,
                demoteTempThresholdC = 44f,
                currentGuardType = SentryGuardType.OPTICAL_MOTION
            )
        )

        // Optical motion + temp above threshold -> demotes
        assertTrue(
            sentryController.evaluateAutoDemotion(
                batteryLevelPct = 80,
                batteryTempC = 45f,
                demoteBatteryThresholdPct = 30,
                demoteTempThresholdC = 44f,
                currentGuardType = SentryGuardType.OPTICAL_MOTION
            )
        )

        // Optical motion + healthy metrics -> no demotion
        assertFalse(
            sentryController.evaluateAutoDemotion(
                batteryLevelPct = 85,
                batteryTempC = 32f,
                demoteBatteryThresholdPct = 30,
                demoteTempThresholdC = 44f,
                currentGuardType = SentryGuardType.OPTICAL_MOTION
            )
        )

        // Optical motion + stress BUT autoDemoteEnabled = false -> no demotion
        assertFalse(
            sentryController.evaluateAutoDemotion(
                batteryLevelPct = 20,
                batteryTempC = 46f,
                demoteBatteryThresholdPct = 30,
                demoteTempThresholdC = 44f,
                currentGuardType = SentryGuardType.OPTICAL_MOTION,
                autoDemoteEnabled = false
            )
        )
    }

    @Test
    fun testEvaluateAutoPromotion_withDeadbandHysteresis() {
        // Configured is not optical motion -> no promotion
        assertFalse(
            sentryController.evaluateAutoPromotion(
                batteryLevelPct = 90,
                batteryTempC = 30f,
                isCharging = true,
                demoteBatteryThresholdPct = 30,
                demoteTempThresholdC = 44f,
                configuredGuardType = SentryGuardType.SHOCK_STANDBY,
                currentGuardType = SentryGuardType.SHOCK_STANDBY
            )
        )

        // Already in optical motion -> no promotion needed
        assertFalse(
            sentryController.evaluateAutoPromotion(
                batteryLevelPct = 90,
                batteryTempC = 30f,
                isCharging = true,
                demoteBatteryThresholdPct = 30,
                demoteTempThresholdC = 44f,
                configuredGuardType = SentryGuardType.OPTICAL_MOTION,
                currentGuardType = SentryGuardType.OPTICAL_MOTION
            )
        )

        // Demote enabled was turned off while demoted -> immediate promotion
        assertTrue(
            sentryController.evaluateAutoPromotion(
                batteryLevelPct = 20,
                batteryTempC = 45f,
                isCharging = false,
                demoteBatteryThresholdPct = 30,
                demoteTempThresholdC = 44f,
                configuredGuardType = SentryGuardType.OPTICAL_MOTION,
                currentGuardType = SentryGuardType.SHOCK_STANDBY,
                autoDemoteEnabled = false
            )
        )

        // Temperature still hot (43°C, within 2°C hysteresis margin of 44°C limit) -> false
        assertFalse(
            sentryController.evaluateAutoPromotion(
                batteryLevelPct = 80,
                batteryTempC = 43f,
                isCharging = false,
                demoteBatteryThresholdPct = 30,
                demoteTempThresholdC = 44f,
                configuredGuardType = SentryGuardType.OPTICAL_MOTION,
                currentGuardType = SentryGuardType.SHOCK_STANDBY,
                autoDemoteEnabled = true
            )
        )

        // Battery below recovery threshold (32%, needs 30 + 5 = 35% when not charging) -> false
        assertFalse(
            sentryController.evaluateAutoPromotion(
                batteryLevelPct = 32,
                batteryTempC = 35f,
                isCharging = false,
                demoteBatteryThresholdPct = 30,
                demoteTempThresholdC = 44f,
                configuredGuardType = SentryGuardType.OPTICAL_MOTION,
                currentGuardType = SentryGuardType.SHOCK_STANDBY,
                autoDemoteEnabled = true
            )
        )

        // Battery recovered (36% >= 35%) and temperature cooled (41.5°C <= 42°C) -> true
        assertTrue(
            sentryController.evaluateAutoPromotion(
                batteryLevelPct = 36,
                batteryTempC = 41.5f,
                isCharging = false,
                demoteBatteryThresholdPct = 30,
                demoteTempThresholdC = 44f,
                configuredGuardType = SentryGuardType.OPTICAL_MOTION,
                currentGuardType = SentryGuardType.SHOCK_STANDBY,
                autoDemoteEnabled = true
            )
        )

        // Actively charging, even with battery at 25%, as long as temp is cooled (38°C) -> true
        assertTrue(
            sentryController.evaluateAutoPromotion(
                batteryLevelPct = 25,
                batteryTempC = 38f,
                isCharging = true,
                demoteBatteryThresholdPct = 30,
                demoteTempThresholdC = 44f,
                configuredGuardType = SentryGuardType.OPTICAL_MOTION,
                currentGuardType = SentryGuardType.SHOCK_STANDBY,
                autoDemoteEnabled = true
            )
        )
    }

    @Test
    fun testSensitivityEvaluations() {
        // Motion tests - against Sensitivity.motionDiffThreshold (OFF: 999, LOW: 25, MEDIUM: 15, HIGH: 8, VERY_HIGH: 4)
        assertFalse(sentryController.shouldTriggerMotion(50f, Sensitivity.OFF))
        assertTrue(sentryController.shouldTriggerMotion(26f, Sensitivity.LOW))
        assertFalse(sentryController.shouldTriggerMotion(24f, Sensitivity.LOW))
        assertTrue(sentryController.shouldTriggerMotion(16f, Sensitivity.MEDIUM))
        assertFalse(sentryController.shouldTriggerMotion(14f, Sensitivity.MEDIUM))
        assertTrue(sentryController.shouldTriggerMotion(9f, Sensitivity.HIGH))
        assertFalse(sentryController.shouldTriggerMotion(7f, Sensitivity.HIGH))

        // Acoustic tests - against AcousticNoiseMonitor.getThresholdForSensitivity (OFF: 999, LOW: 85, MEDIUM: 75, HIGH: 65, VERY_HIGH: 60)
        assertFalse(sentryController.shouldTriggerAcoustic(100f, Sensitivity.OFF))
        assertTrue(sentryController.shouldTriggerAcoustic(86f, Sensitivity.LOW))
        assertFalse(sentryController.shouldTriggerAcoustic(84f, Sensitivity.LOW))
        assertTrue(sentryController.shouldTriggerAcoustic(76f, Sensitivity.MEDIUM))
        assertFalse(sentryController.shouldTriggerAcoustic(74f, Sensitivity.MEDIUM))
        assertTrue(sentryController.shouldTriggerAcoustic(66f, Sensitivity.HIGH))
        assertFalse(sentryController.shouldTriggerAcoustic(64f, Sensitivity.HIGH))
        assertTrue(sentryController.shouldTriggerAcoustic(61f, Sensitivity.VERY_HIGH))
        assertFalse(sentryController.shouldTriggerAcoustic(59f, Sensitivity.VERY_HIGH))
    }

    @Test
    fun testIncidentTimer_andCancellation() = runTest {
        var timerCompleted = false
        sentryController.startIncidentTimer(this, durationSeconds = 2) {
            timerCompleted = true
        }

        advanceTimeBy(1000L)
        assertFalse(timerCompleted)

        advanceTimeBy(1001L)
        assertTrue(timerCompleted)

        // Test cancel
        var cancelledTimerCompleted = false
        sentryController.startIncidentTimer(this, durationSeconds = 5) {
            cancelledTimerCompleted = true
        }
        advanceTimeBy(1000L)
        sentryController.cancelIncidentTimer()
        advanceTimeBy(10000L)
        assertFalse(cancelledTimerCompleted)
    }

    @Test
    fun testDispatchAcousticAlert() {
        var incidentTriggerReason: String? = null
        sentryController.onTriggerIncident = { reason ->
            incidentTriggerReason = reason
        }

        var toastShown: String? = null
        sentryController.dispatchAcousticAlert(
            db = 85.4f,
            deterrentMode = SentryDeterrentMode.NONE,
            autoLock = true,
            isRecording = false,
            onShowToast = { msg -> toastShown = msg }
        )

        assertEquals("Sentry Acoustic (85 dB)", incidentTriggerReason)
        assertEquals("🔊 Acoustic Alert (85 dB) - Incident Protected", toastShown)
    }

    @Test
    fun testDispatchMotionAlert() {
        var incidentTriggerReason: String? = null
        sentryController.onTriggerIncident = { reason ->
            incidentTriggerReason = reason
        }

        var toastShown: String? = null
        sentryController.dispatchMotionAlert(
            score = 42.8f,
            deterrentMode = SentryDeterrentMode.NONE,
            autoLock = false,
            isRecording = false,
            onShowToast = { msg -> toastShown = msg }
        )

        assertEquals("Motion Sentry Trigger", incidentTriggerReason)
        assertEquals("👁️ Sentry Alert: Motion Detected (43%)", toastShown)
    }

    @Test
    fun testShouldAutoDeleteInterruptedClip() {
        assertTrue(
            sentryController.shouldAutoDeleteInterruptedClip(
                reason = "Sentry (Interrupted by Power On)",
                action = SentryInterruptedClipAction.AUTO_DELETE
            )
        )
        assertFalse(
            sentryController.shouldAutoDeleteInterruptedClip(
                reason = "Sentry (Interrupted by Power On)",
                action = SentryInterruptedClipAction.KEEP_WITH_BADGE
            )
        )
        assertFalse(
            sentryController.shouldAutoDeleteInterruptedClip(
                reason = "Motion Sentry Trigger",
                action = SentryInterruptedClipAction.AUTO_DELETE
            )
        )
    }

    @Test
    fun testShouldPromptDashcamOnDisarm() {
        assertTrue(sentryController.shouldPromptDashcamOnDisarm(isManual = true, isPlugged = true))
        assertFalse(sentryController.shouldPromptDashcamOnDisarm(isManual = false, isPlugged = true))
        assertFalse(sentryController.shouldPromptDashcamOnDisarm(isManual = true, isPlugged = false))
        assertFalse(sentryController.shouldPromptDashcamOnDisarm(isManual = false, isPlugged = false))
    }

    @Test
    fun testIncidentCooldown_blocksImmediateRetrigger() {
        val now = 1000000L
        sentryController.markIncidentCompleted(now)

        // During 4000ms cooldown, canTriggerIncident is false
        assertFalse(sentryController.canTriggerIncident(now + 1000L))
        assertFalse(sentryController.canTriggerIncident(now + 3999L))

        // After cooldown expires, canTriggerIncident is true
        assertTrue(sentryController.canTriggerIncident(now + 4000L))
        assertTrue(sentryController.canTriggerIncident(now + 5000L))

        // When cooldown is active in real time:
        sentryController.markIncidentCompleted(System.currentTimeMillis())
        var triggeredReason: String? = null
        sentryController.onTriggerIncident = { triggeredReason = it }
        val accepted = sentryController.dispatchMotionAlert(
            score = 30f,
            deterrentMode = SentryDeterrentMode.NONE,
            autoLock = false,
            isRecording = false
        )
        assertFalse(accepted)
        org.junit.Assert.assertNull(triggeredReason)

        // Reset cooldown
        sentryController.markIncidentCompleted(0L)
        val acceptedAfter = sentryController.dispatchMotionAlert(
            score = 30f,
            deterrentMode = SentryDeterrentMode.NONE,
            autoLock = false,
            isRecording = false
        )
        assertTrue(acceptedAfter)
        assertEquals("Motion Sentry Trigger", triggeredReason)
    }
}
