package io.github.roaxio.dashguardpro.viewmodel

import io.github.roaxio.dashguardpro.viewmodel.state.DashcamUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Presenter coordinating transient UI notification timers, countdown loops,
 * snapshot flash durations, and dialog visibility states.
 *
 * Adheres to AGENTS.md Rule 7 (Vertical Slice Integrity) and Rule 9 (Asynchronous Teardown & Lifecycle Symmetry).
 */
class DashcamAlertPresenter(
    private val scope: CoroutineScope,
    private val getCurrentUiState: () -> DashcamUiState,
    private val updateUiState: ((DashcamUiState) -> DashcamUiState) -> Unit
) {
    private var incidentMessageDismissJob: Job? = null
    private var unplugTimerJob: Job? = null
    private var snapshotFlashJob: Job? = null
    private var acousticAlertJob: Job? = null
    private var motionAlertJob: Job? = null
    private var impactAlertJob: Job? = null

    val isUnplugCountdownActive: Boolean
        get() = unplugTimerJob?.isActive == true

    fun showTransientMessage(message: String, durationMs: Long = 2500L) {
        incidentMessageDismissJob?.cancel()
        updateUiState { it.copy(recentIncidentMessage = message) }
        incidentMessageDismissJob = scope.launch {
            delay(durationMs)
            if (getCurrentUiState().recentIncidentMessage == message) {
                updateUiState { it.copy(recentIncidentMessage = null) }
            }
        }
    }

    fun clearIncidentMessage() {
        incidentMessageDismissJob?.cancel()
        incidentMessageDismissJob = null
        updateUiState { it.copy(recentIncidentMessage = null) }
    }

    fun startUnplugCountdown(
        seconds: Int,
        isRecording: () -> Boolean,
        onStopRecording: () -> Unit
    ) {
        unplugTimerJob?.cancel()
        if (!isRecording()) {
            updateUiState { it.copy(unplugCountdownSeconds = null) }
            return
        }
        updateUiState { it.copy(unplugCountdownSeconds = seconds) }
        unplugTimerJob = scope.launch {
            for (i in seconds downTo 1) {
                if (!isRecording()) {
                    updateUiState { it.copy(unplugCountdownSeconds = null) }
                    return@launch
                }
                updateUiState { it.copy(unplugCountdownSeconds = i) }
                delay(1000L)
            }
            updateUiState { it.copy(unplugCountdownSeconds = null) }
            if (isRecording()) {
                onStopRecording()
            }
        }
    }

    fun cancelUnplugCountdown() {
        unplugTimerJob?.cancel()
        unplugTimerJob = null
        updateUiState { it.copy(unplugCountdownSeconds = null) }
    }

    fun triggerSnapshotFlash(durationMs: Long = 150L) {
        snapshotFlashJob?.cancel()
        updateUiState { it.copy(isSnapshotFlashActive = true) }
        snapshotFlashJob = scope.launch {
            delay(durationMs)
            updateUiState { it.copy(isSnapshotFlashActive = false) }
        }
    }

    fun showSpeedLockoutAlert() {
        updateUiState { it.copy(isSpeedLockoutAlertVisible = true) }
    }

    fun dismissSpeedLockoutAlert() {
        updateUiState { it.copy(isSpeedLockoutAlertVisible = false) }
    }

    fun showImpactAlert(gValue: Float, isSentry: Boolean, autoDismissMs: Long = 4000L) {
        impactAlertJob?.cancel()
        updateUiState {
            it.copy(
                showImpactAlert = !isSentry,
                impactG = gValue
            )
        }
        if (!isSentry && autoDismissMs > 0L) {
            impactAlertJob = scope.launch {
                delay(autoDismissMs)
                updateUiState { it.copy(showImpactAlert = false) }
            }
        }
    }

    fun dismissImpactAlert() {
        impactAlertJob?.cancel()
        impactAlertJob = null
        updateUiState { it.copy(showImpactAlert = false) }
    }

    fun triggerAcousticAlertFeedback(durationMs: Long = 3500L) {
        acousticAlertJob?.cancel()
        acousticAlertJob = scope.launch {
            delay(durationMs)
            updateUiState { it.copy(isAcousticAlert = false) }
        }
    }

    fun triggerMotionAlertFeedback(durationMs: Long = 3500L) {
        motionAlertJob?.cancel()
        motionAlertJob = scope.launch {
            delay(durationMs)
            updateUiState { it.copy(isMotionTriggered = false) }
        }
    }

    fun resetAllAlerts() {
        incidentMessageDismissJob?.cancel()
        unplugTimerJob?.cancel()
        snapshotFlashJob?.cancel()
        acousticAlertJob?.cancel()
        motionAlertJob?.cancel()
        impactAlertJob?.cancel()
        incidentMessageDismissJob = null
        unplugTimerJob = null
        snapshotFlashJob = null
        acousticAlertJob = null
        motionAlertJob = null
        impactAlertJob = null
    }
}
