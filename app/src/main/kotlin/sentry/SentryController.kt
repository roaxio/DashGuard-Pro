package io.github.roaxio.dashguardpro.sentry

import io.github.roaxio.dashguardpro.data.settings.SentryDeterrentMode
import io.github.roaxio.dashguardpro.data.settings.SentryGuardType
import io.github.roaxio.dashguardpro.data.settings.SentryInterruptedClipAction
import io.github.roaxio.dashguardpro.data.settings.Sensitivity
import io.github.roaxio.dashguardpro.sensor.AcousticNoiseMonitor
import io.github.roaxio.dashguardpro.feedback.AlertManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Encapsulates Sentry mode surveillance, arming countdowns, cancellation,
 * acoustic/optical sensitivity evaluation, auto-demote policies, and deterrent dispatch.
 *
 * Adheres to AGENTS.md Rule 1 (Root-Cause Resolution) and Rule 6 (Architectural Cohesion).
 */
data class SentryState(
    val isArmed: Boolean = false,
    val isArming: Boolean = false,
    val countdownSeconds: Int = 0,
    val eventCount: Int = 0
)

class SentryController(
    private val alertManager: AlertManager? = null,
    private val acousticNoiseMonitor: AcousticNoiseMonitor? = null
) {
    private val _sentryState = MutableStateFlow(SentryState())
    val sentryState: StateFlow<SentryState> = _sentryState.asStateFlow()

    private var armingJob: Job? = null
    private var incidentJob: Job? = null

    var onTriggerIncident: ((reason: String) -> Unit)? = null

    private var lastIncidentEndTimeMs: Long = 0L
    var incidentCooldownMs: Long = 4000L

    fun canTriggerIncident(now: Long = System.currentTimeMillis()): Boolean {
        return (now - lastIncidentEndTimeMs) >= incidentCooldownMs
    }

    fun markIncidentCompleted(now: Long = System.currentTimeMillis()) {
        lastIncidentEndTimeMs = now
    }

    fun resetCooldown() {
        lastIncidentEndTimeMs = 0L
    }

    val isArmed: Boolean get() = _sentryState.value.isArmed
    val isArming: Boolean get() = _sentryState.value.isArming
    val countdownSeconds: Int get() = _sentryState.value.countdownSeconds
    val eventCount: Int get() = _sentryState.value.eventCount

    /**
     * Arms Sentry mode. If delaySeconds > 0, initiates a countdown coroutine.
     */
    fun arm(
        scope: CoroutineScope,
        delaySeconds: Int,
        voiceAlertsEnabled: Boolean = true,
        onArmed: () -> Unit
    ) {
        armingJob?.cancel()
        resetCooldown()
        if (delaySeconds > 0) {
            _sentryState.update {
                it.copy(
                    isArming = true,
                    countdownSeconds = delaySeconds,
                    eventCount = 0
                )
            }
            alertManager?.speakSentryArming(delaySeconds, voiceAlertsEnabled)
            armingJob = scope.launch {
                for (sec in delaySeconds downTo 1) {
                    _sentryState.update { it.copy(countdownSeconds = sec) }
                    delay(1000L)
                }
                _sentryState.update {
                    it.copy(
                        isArming = false,
                        countdownSeconds = 0,
                        isArmed = true
                    )
                }
                onArmed()
            }
        } else {
            _sentryState.update {
                it.copy(
                    isArming = false,
                    countdownSeconds = 0,
                    isArmed = true,
                    eventCount = 0
                )
            }
            onArmed()
        }
    }

    /**
     * Disarms Sentry mode cleanly, tearing down jobs and releasing acoustic monitors.
     */
    fun disarm(
        voiceAlertsEnabled: Boolean = true,
        incidentCount: Int? = null,
        onDisarmed: ((incidents: Int) -> Unit)? = null
    ) {
        armingJob?.cancel()
        armingJob = null
        incidentJob?.cancel()
        incidentJob = null
        acousticNoiseMonitor?.stopAndRelease()
        resetCooldown()

        val incidents = incidentCount ?: _sentryState.value.eventCount
        val wasActive = _sentryState.value.isArmed || _sentryState.value.isArming

        _sentryState.update {
            it.copy(
                isArmed = false,
                isArming = false,
                countdownSeconds = 0,
                eventCount = 0
            )
        }

        if (wasActive) {
            alertManager?.speakSentryDisarmed(incidents, voiceAlertsEnabled)
            onDisarmed?.invoke(incidents)
        }
    }

    /**
     * Cancels an in-progress arming countdown without arming.
     */
    fun cancelArming() {
        armingJob?.cancel()
        armingJob = null
        _sentryState.update {
            it.copy(isArming = false, countdownSeconds = 0)
        }
    }

    fun incrementEventCount() {
        _sentryState.update { it.copy(eventCount = it.eventCount + 1) }
    }

    fun resetEventCount() {
        _sentryState.update { it.copy(eventCount = 0) }
    }

    fun triggerDeterrent(mode: SentryDeterrentMode) {
        alertManager?.triggerDeterrent(mode)
    }

    /**
     * Evaluates whether Sentry mode should auto-demote from Tier 2 (Optical Motion)
     * to Tier 1 (Shock Standby) based on battery percentage or thermal limits (AGENTS.md Rule 10 & 16).
     */
    fun evaluateAutoDemotion(
        batteryLevelPct: Int,
        batteryTempC: Float,
        demoteBatteryThresholdPct: Int,
        demoteTempThresholdC: Float,
        currentGuardType: SentryGuardType,
        autoDemoteEnabled: Boolean = true
    ): Boolean {
        if (!autoDemoteEnabled) return false
        if (currentGuardType != SentryGuardType.OPTICAL_MOTION) return false
        val batteryTooLow = demoteBatteryThresholdPct > 0 && batteryLevelPct in 1 until demoteBatteryThresholdPct
        val tempTooHigh = demoteTempThresholdC > 0f && batteryTempC > 0f && batteryTempC >= demoteTempThresholdC
        return batteryTooLow || tempTooHigh
    }

    /**
     * Evaluates whether Sentry mode should auto-promote from Tier 1 (Shock Standby)
     * back to Tier 2 (Optical Motion) when battery and thermal conditions recover.
     * Enforces deadband hysteresis (Rule 10 & Rule 16) to prevent rapid bouncing.
     */
    fun evaluateAutoPromotion(
        batteryLevelPct: Int,
        batteryTempC: Float,
        isCharging: Boolean,
        demoteBatteryThresholdPct: Int,
        demoteTempThresholdC: Float,
        configuredGuardType: SentryGuardType,
        currentGuardType: SentryGuardType,
        autoDemoteEnabled: Boolean = true
    ): Boolean {
        if (configuredGuardType != SentryGuardType.OPTICAL_MOTION) return false
        if (currentGuardType == SentryGuardType.OPTICAL_MOTION) return false
        if (!autoDemoteEnabled) return true

        // Calibrated deadband hysteresis margins (Rule 10 & Rule 16):
        // 1. Battery: either actively charging or >= demoteBatteryThresholdPct + 5%
        val batterySafe = isCharging ||
                (demoteBatteryThresholdPct > 0 && batteryLevelPct >= demoteBatteryThresholdPct + 5) ||
                (demoteBatteryThresholdPct <= 0 && batteryLevelPct > 20)

        // 2. Temperature: cooled at least 2.0°C below the demote threshold
        val tempSafe = if (demoteTempThresholdC > 0f && batteryTempC > 0f) {
            batteryTempC <= (demoteTempThresholdC - 2.0f)
        } else {
            batteryTempC <= 0f || batteryTempC <= 42.0f
        }

        return batterySafe && tempSafe
    }

    fun shouldTriggerMotion(score: Float, sensitivity: Sensitivity): Boolean {
        if (sensitivity == Sensitivity.OFF) return false
        return score >= sensitivity.motionDiffThreshold
    }

    fun shouldTriggerAcoustic(db: Float, sensitivity: Sensitivity): Boolean {
        if (sensitivity == Sensitivity.OFF) return false
        return db >= AcousticNoiseMonitor.getThresholdForSensitivity(sensitivity)
    }

    fun startIncidentTimer(
        scope: CoroutineScope,
        durationSeconds: Int,
        onComplete: () -> Unit
    ) {
        incidentJob?.cancel()
        incidentJob = scope.launch {
            delay(durationSeconds * 1000L)
            onComplete()
        }
    }

    fun cancelIncidentTimer() {
        incidentJob?.cancel()
        incidentJob = null
    }

    /**
     * Dispatches an acoustic incident trigger, coordinating deterrent actions and incident capture.
     * Returns true if alert was accepted and dispatched, false if dropped (e.g. cooldown active).
     */
    fun dispatchAcousticAlert(
        db: Float,
        deterrentMode: SentryDeterrentMode,
        autoLock: Boolean,
        isRecording: Boolean,
        onShowToast: ((String) -> Unit)? = null
    ): Boolean {
        val reason = "Sentry Acoustic (${db.toInt()} dB)"
        if (!isRecording) {
            if (!canTriggerIncident()) {
                return false
            }
            incrementEventCount()
            val lockMsg = if (autoLock) " - Incident Protected" else ""
            onShowToast?.invoke("🔊 Acoustic Alert (${db.toInt()} dB)$lockMsg")
            acousticNoiseMonitor?.stopAndRelease()
            triggerDeterrent(deterrentMode)
            onTriggerIncident?.invoke(reason)
            return true
        } else {
            onTriggerIncident?.invoke(reason)
            return true
        }
    }

    /**
     * Dispatches an optical motion incident trigger, coordinating deterrent actions and incident capture.
     * Returns true if alert was accepted and dispatched, false if dropped (e.g. cooldown active).
     */
    fun dispatchMotionAlert(
        score: Float,
        deterrentMode: SentryDeterrentMode,
        autoLock: Boolean,
        isRecording: Boolean,
        onShowToast: ((String) -> Unit)? = null
    ): Boolean {
        val reason = "Motion Sentry Trigger"
        if (!isRecording) {
            if (!canTriggerIncident()) {
                return false
            }
            incrementEventCount()
            val lockMsg = if (autoLock) " - Incident Protected" else ""
            onShowToast?.invoke("👁️ Sentry Alert: Motion Detected (${"%.0f".format(score)}%)$lockMsg")
            acousticNoiseMonitor?.stopAndRelease()
            triggerDeterrent(deterrentMode)
            onTriggerIncident?.invoke(reason)
            return true
        } else {
            onTriggerIncident?.invoke(reason)
            return true
        }
    }

    /**
     * Determines whether an interrupted sentry clip should be auto-deleted based on user configuration.
     */
    fun shouldAutoDeleteInterruptedClip(
        reason: String,
        action: SentryInterruptedClipAction
    ): Boolean {
        return reason == "Sentry (Interrupted by Power On)" && action == SentryInterruptedClipAction.AUTO_DELETE
    }

    /**
     * Evaluates whether disarming Sentry should suggest resuming normal Dashcam recording.
     */
    fun shouldPromptDashcamOnDisarm(isManual: Boolean, isPlugged: Boolean): Boolean {
        return isManual && isPlugged
    }
}
