package io.github.roaxio.dashguardpro.system

import android.app.Application
import io.github.roaxio.dashguardpro.data.settings.AppMode
import io.github.roaxio.dashguardpro.data.settings.Sensitivity
import io.github.roaxio.dashguardpro.data.settings.SentryArmingTrigger
import io.github.roaxio.dashguardpro.data.settings.SentryGuardType
import io.github.roaxio.dashguardpro.feedback.AlertManager
import io.github.roaxio.dashguardpro.sensor.AcousticNoiseMonitor
import io.github.roaxio.dashguardpro.sensor.BatteryMonitor
import io.github.roaxio.dashguardpro.sensor.LocationTracker
import io.github.roaxio.dashguardpro.sentry.SentryController
import io.github.roaxio.dashguardpro.data.SettingsRepository
import io.github.roaxio.dashguardpro.viewmodel.DashcamAlertPresenter
import io.github.roaxio.dashguardpro.viewmodel.state.DashcamUiState
import io.github.roaxio.dashguardpro.viewmodel.state.DeviceSleepMode
import io.github.roaxio.dashguardpro.viewmodel.state.PreSleepOperatingState
import io.github.roaxio.dashguardpro.viewmodel.state.SleepTriggerCause
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class PendingPostLockAction {
    NONE,
    POWER_LOSS_SHUTDOWN,
    SENTRY_ARM_ON_POWER_LOSS,
    THERMAL_COOLING_SLEEP,
    LOW_BATTERY_SLEEP
}

/**
 * Domain coordinator encapsulating AC power automation, battery safety cutoff,
 * thermal protection auto-dimming, and hysteretic sleep recovery transitions.
 *
 * Adheres to AGENTS.md Rule 4 (Deterministic Architecture vs Temporal Duct-Tape),
 * Rule 7 (Vertical Slice Integrity), Rule 9 (Asynchronous Teardown & Lifecycle Symmetry),
 * and Rule 10 (System Invariant Formulation & Lifecycle Enforcement).
 */
class PowerAndThermalCoordinator(
    private val app: Application,
    private val scope: CoroutineScope,
    private val settingsRepo: SettingsRepository,
    private val batteryMonitor: BatteryMonitor,
    private val locationTracker: LocationTracker,
    private val acousticNoiseMonitor: AcousticNoiseMonitor,
    private val sentryController: SentryController,
    private val alertManager: AlertManager,
    private val alertPresenter: DashcamAlertPresenter,
    private val getCurrentUiState: () -> DashcamUiState,
    private val updateUiState: ((DashcamUiState) -> DashcamUiState) -> Unit,
    private val getIsAnyScreenDimmed: () -> Boolean,
    private val setScreenDimmed: (Boolean) -> Unit,
    private val setMode: (AppMode) -> Unit,
    private val showTransientMessage: (String) -> Unit,
    private val isRecordingOrStarting: () -> Boolean,
    private val isEmergencyLocked: () -> Boolean,
    private val isIncidentActive: () -> Boolean,
    private val startRecording: (reason: String) -> Job?,
    private val stopRecording: (autoArmSentry: Boolean) -> Unit,
    private val armSentryMode: (delaySeconds: Int?) -> Unit,
    private val disarmSentryMode: (isManual: Boolean) -> Unit,
    private val cancelSentryArming: () -> Unit,
    private val handleSentryOwnerReturnInterruption: suspend () -> Unit
) {
    var preSleepState: PreSleepOperatingState? = null
        private set
    var sleepTriggerCause: SleepTriggerCause? = null
        private set
    var pendingThermalSleep: Boolean = false
        internal set

    var pendingPostLockAction: PendingPostLockAction = PendingPostLockAction.NONE
        private set

    fun setPendingPostLockPowerLoss() {
        val settings = settingsRepo.settings.value
        pendingPostLockAction = if (settings.sentryModeEnabled && settings.sentryArmingTrigger == SentryArmingTrigger.ON_POWER_LOSS) {
            PendingPostLockAction.SENTRY_ARM_ON_POWER_LOSS
        } else {
            PendingPostLockAction.POWER_LOSS_SHUTDOWN
        }
        alertPresenter.cancelUnplugCountdown()
    }

    fun clearPendingPostLockPowerLoss() {
        if (pendingPostLockAction == PendingPostLockAction.POWER_LOSS_SHUTDOWN ||
            pendingPostLockAction == PendingPostLockAction.SENTRY_ARM_ON_POWER_LOSS
        ) {
            pendingPostLockAction = PendingPostLockAction.NONE
        }
    }

    fun hasPendingPostLockAction(): Boolean = pendingPostLockAction != PendingPostLockAction.NONE

    fun onClipLocked() {
        alertPresenter.cancelUnplugCountdown()
        val curBat = batteryMonitor.batteryState.value
        if (!curBat.isPlugged) {
            setPendingPostLockPowerLoss()
        }
    }

    fun executePendingPostLockAction() {
        val action = pendingPostLockAction
        pendingPostLockAction = PendingPostLockAction.NONE
        when (action) {
            PendingPostLockAction.POWER_LOSS_SHUTDOWN -> {
                stopRecording(false)
                showTransientMessage("🔌 Power-Loss Safeguard: Incident segment finished. Stopped recording.")
            }
            PendingPostLockAction.SENTRY_ARM_ON_POWER_LOSS -> {
                stopRecording(false)
                armSentryMode(null)
                showTransientMessage("🛡️ Sentry Safeguard: Incident segment finished. Sentry armed on power-loss.")
            }
            PendingPostLockAction.THERMAL_COOLING_SLEEP -> {
                val curTemp = batteryMonitor.batteryState.value.temperatureC
                clearPendingThermalSleep()
                stopRecording(false)
                triggerThermalCoolingSleep(curTemp)
            }
            PendingPostLockAction.LOW_BATTERY_SLEEP -> {
                val curPct = batteryMonitor.batteryState.value.levelPct
                stopRecording(false)
                triggerLowBatterySleep(curPct)
            }
            PendingPostLockAction.NONE -> {}
        }
    }

    private var powerConnectionJob: Job? = null
    private var hystereticRecoveryJob: Job? = null

    val isPendingThermalSleep: Boolean
        get() = pendingThermalSleep

    fun clearPendingThermalSleep() {
        pendingThermalSleep = false
    }

    fun initialize() {
        val initialSettings = settingsRepo.settings.value
        batteryMonitor.maxSafeTempC = initialSettings.maxTempThresholdC
        batteryMonitor.warmThresholdC = initialSettings.thermalAutoDimTempC
        batteryMonitor.lowBatterySafetyLimitPct = initialSettings.lowBatterySafetyLimitPct

        batteryMonitor.onLowBatteryCutoff = { pct ->
            val ui = getCurrentUiState()
            if (ui.deviceSleepMode == DeviceSleepMode.NONE) {
                val isLocked = isEmergencyLocked() || ui.isSegmentLocked || isIncidentActive()
                if (isRecordingOrStarting() && isLocked) {
                    if (!getIsAnyScreenDimmed()) {
                        setScreenDimmed(true)
                    }
                    pendingPostLockAction = PendingPostLockAction.LOW_BATTERY_SLEEP
                    showTransientMessage("🛡️ Battery Safeguard: Battery at $pct%. Screen dimmed. Incident clip will complete before sleep.")
                } else {
                    triggerLowBatterySleep(pct)
                }
            }
        }

        batteryMonitor.onPowerConnected = {
            alertPresenter.cancelUnplugCountdown()
            clearPendingPostLockPowerLoss()

            // Android can deliver POWER_CONNECTED and a confirming BATTERY_CHANGED event
            // for the same physical transition. Serialize the owner-return transaction so
            // duplicate broadcasts cannot issue a second stop/disarm/start sequence.
            if (powerConnectionJob?.isActive != true) {
                powerConnectionJob = scope.launch {
                    try {
                        val ui = getCurrentUiState()
                        val settings = settingsRepo.settings.value
                        val wasSentry = ui.isSentryArmed || ui.isSentryArming || settings.currentMode == AppMode.SENTRY
                        if (wasSentry) {
                            if (settings.sentryAutoDisarmOnPower) {
                                cancelSentryArming()
                                val isRecording = isRecordingOrStarting()
                                if (isRecording) {
                                    handleSentryOwnerReturnInterruption()
                                }
                                disarmSentryMode(false)
                            }
                        }

                        val hasCamera = androidx.core.content.ContextCompat.checkSelfPermission(
                            app,
                            android.Manifest.permission.CAMERA
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        val curBat = batteryMonitor.batteryState.value
                        val isBatterySafe = curBat.levelPct > settings.lowBatterySafetyLimitPct
                        if (hasCamera && settings.autoRecordOnPower && !getCurrentUiState().isRecording && !getCurrentUiState().isThermalCoolingActive && isBatterySafe && getCurrentUiState().deviceSleepMode != DeviceSleepMode.LOW_BATTERY_STANDBY) {
                            // Keep this transaction open until the replacement recording has
                            // completed its asynchronous startup. A second power broadcast must
                            // not observe the pre-start gap and launch another recording.
                            startRecording("Auto Power-On")?.join()
                        }
                    } finally {
                        powerConnectionJob = null
                    }
                }
            }
        }

        batteryMonitor.onPowerDisconnected = {
            val ui = getCurrentUiState()
            val settings = settingsRepo.settings.value
            val isIncident = isIncidentActive() || isEmergencyLocked() || ui.isSegmentLocked

            if (ui.isRecording || isRecordingOrStarting()) {
                if (isIncident) {
                    showTransientMessage("🛡️ Incident Safeguard: Power disconnected, keeping recording active on battery until clip finishes")
                    setPendingPostLockPowerLoss()
                } else if (settings.autoStopOnUnplugSeconds > 0) {
                    startUnplugCountdown(settings.autoStopOnUnplugSeconds)
                } else {
                    stopRecording(true)
                    if (settings.sentryModeEnabled && settings.sentryArmingTrigger == SentryArmingTrigger.ON_POWER_LOSS) {
                        armSentryMode(null)
                    }
                }
            } else {
                if (settings.sentryModeEnabled && settings.sentryArmingTrigger == SentryArmingTrigger.ON_POWER_LOSS) {
                    armSentryMode(null)
                }
            }
        }

        batteryMonitor.onThermalWarning = { temp ->
            val ui = getCurrentUiState()
            val settings = settingsRepo.settings.value
            if (ui.deviceSleepMode == DeviceSleepMode.NONE && !ui.isThermalCoolingActive) {
                if (settings.maxTempThresholdC > 0f) {
                    val isLocked = isEmergencyLocked() || ui.isSegmentLocked || isIncidentActive()
                    if (isRecordingOrStarting() && isLocked) {
                        // Invariant: Do not abruptly abort emergency locked recording to preserve crash evidence.
                        // Proactively dim the screen to shed display/GPU heat and flag pending sleep (AGENTS.md Rule 7).
                        if (!getIsAnyScreenDimmed()) {
                            setScreenDimmed(true)
                        }
                        pendingThermalSleep = true
                        pendingPostLockAction = PendingPostLockAction.THERMAL_COOLING_SLEEP
                        showTransientMessage("🛡️ Thermal Shield: Battery at ${"%.1f".format(temp)}°C. Screen dimmed. Incident clip will complete before cooling sleep.")
                    } else {
                        triggerThermalCoolingSleep(temp)
                    }
                } else {
                    showTransientMessage("⚠️ Thermal Warning: Battery at ${"%.1f".format(temp)}°C. Dimming HUD recommended.")
                }
            }
        }

        batteryMonitor.onWarmTrigger = { temp ->
            // Invariant: Warm auto-dim is a complete no-op if screen is already dimmed for ANY reason
            if (isRecordingOrStarting() && !getIsAnyScreenDimmed()) {
                setScreenDimmed(true)
                showTransientMessage("🛡️ Thermal Shield: Battery reached ${"%.1f".format(temp)}°C. Auto-dimming to cool device.")
            }
        }

        // Hysteretic sleep recovery observer (Formal Decoupled Temp & Battery State Transitions)
        hystereticRecoveryJob = scope.launch {
            batteryMonitor.batteryState.collect { curBat ->
                val currentMode = getCurrentUiState().deviceSleepMode
                val isThermalSleep = currentMode == DeviceSleepMode.THERMAL_COOLING
                val isBatterySleep = currentMode == DeviceSleepMode.LOW_BATTERY_STANDBY
                val settings = settingsRepo.settings.value

                if (!isThermalSleep && !isBatterySleep) {
                    // Not in any sleep mode; fall through to Sentry Tier 2 auto-demotion check
                } else if (isThermalSleep) {
                    val targetTemp = settings.thermalRecoveryTargetTempC.coerceAtMost(settings.maxTempThresholdC - 2.0f)
                    updateUiState {
                        it.copy(
                            thermalCoolingTempC = curBat.temperatureC,
                            thermalCoolingTargetC = targetTemp
                        )
                    }

                    val isTempSafe = settings.maxTempThresholdC <= 0f ||
                            (curBat.temperatureC > 0f && curBat.temperatureC <= targetTemp)

                    if (isTempSafe) {
                        val isBatteryLow = !curBat.isPlugged && settings.lowBatterySafetyLimitPct > 0 &&
                                curBat.levelPct in 1..settings.lowBatterySafetyLimitPct

                        if (isBatteryLow) {
                            // Truthful state transition: Device has cooled down, but cannot resume because battery is low.
                            sleepTriggerCause = SleepTriggerCause.LOW_BATTERY
                            updateUiState {
                                it.copy(
                                    deviceSleepMode = DeviceSleepMode.LOW_BATTERY_STANDBY,
                                    isScreenDimmed = true
                                )
                            }
                            showTransientMessage("🪫 Cooled to <${"%.0f".format(targetTemp)}°C. Battery low (${curBat.levelPct}%). Entering Low Battery Sleep.")
                        } else {
                            val savedPreSleep = preSleepState ?: getCurrentUiState().preSleepOrigin
                            preSleepState = null
                            sleepTriggerCause = null

                            locationTracker.startTracking()
                            alertManager.speakThermalCoolingResumed(settings.sentryVoiceAlertsEnabled)

                            val isSentryPreSleep = savedPreSleep == PreSleepOperatingState.SENTRY_TIER1_SHOCK ||
                                    savedPreSleep == PreSleepOperatingState.SENTRY_TIER2_OPTICAL

                            if (isSentryPreSleep && (!curBat.isPlugged || !settings.sentryAutoDisarmOnPower)) {
                                val demoteBat = settings.sentryAutoDemoteBatteryPct
                                val demoteTemp = settings.sentryAutoDemoteTempC
                                val autoDemoteEnabled = settings.sentryAutoDemoteEnabled
                                val configuredGuard = settings.sentryConfiguredGuardType

                                val targetGuard = if (configuredGuard == SentryGuardType.OPTICAL_MOTION) {
                                    val safeForTier2 = !autoDemoteEnabled || sentryController.evaluateAutoPromotion(
                                        batteryLevelPct = curBat.levelPct,
                                        batteryTempC = curBat.temperatureC,
                                        isCharging = curBat.isPlugged,
                                        demoteBatteryThresholdPct = demoteBat,
                                        demoteTempThresholdC = demoteTemp,
                                        configuredGuardType = configuredGuard,
                                        currentGuardType = SentryGuardType.SHOCK_STANDBY,
                                        autoDemoteEnabled = autoDemoteEnabled
                                    )
                                    if (safeForTier2) SentryGuardType.OPTICAL_MOTION else SentryGuardType.SHOCK_STANDBY
                                } else {
                                    SentryGuardType.SHOCK_STANDBY
                                }
                                settingsRepo.setSentryGuardType(targetGuard)

                                updateUiState {
                                    it.copy(
                                        deviceSleepMode = DeviceSleepMode.NONE,
                                        isSentryArmed = true,
                                        isScreenDimmed = true,
                                        isSegmentLocked = false,
                                        lastTriggerReason = "Normal Loop"
                                    )
                                }
                                setMode(AppMode.SENTRY)
                                showTransientMessage("❄️ Sleep Recovery: Device safe (${"%.1f".format(curBat.temperatureC)}°C, ${curBat.levelPct}%). Resuming Sentry ${if (targetGuard == SentryGuardType.OPTICAL_MOTION) "Tier 2" else "Tier 1"} Standby.")

                                if (settings.sentryAcousticTriggerSensitivity != Sensitivity.OFF) {
                                    acousticNoiseMonitor.sensitivity = settings.sentryAcousticTriggerSensitivity
                                    acousticNoiseMonitor.startMonitoring()
                                }
                            } else if (savedPreSleep == PreSleepOperatingState.DRIVING_LOOP) {
                                if (settings.midDriveThermalRecoveryEnabled) {
                                    updateUiState {
                                        it.copy(
                                            deviceSleepMode = DeviceSleepMode.NONE,
                                            isSentryArmed = false,
                                            isSegmentLocked = false,
                                            lastTriggerReason = "Normal Loop"
                                        )
                                    }
                                    setMode(AppMode.DASHCAM)
                                    showTransientMessage("❄️ Sleep Recovery: Device safe (${"%.1f".format(curBat.temperatureC)}°C, ${curBat.levelPct}%). Resuming Dashcam.")
                                    startRecording("Thermal Recovery Auto-Resume")
                                } else {
                                    updateUiState {
                                        it.copy(
                                            deviceSleepMode = DeviceSleepMode.NONE,
                                            isSentryArmed = false,
                                            isScreenDimmed = false
                                        )
                                    }
                                    setMode(AppMode.DASHCAM)
                                    showTransientMessage("❄️ Sleep Recovery: Cooled to <${"%.0f".format(targetTemp)}°C. Returning to Standby.")
                                }
                            } else {
                                // Standby Origin
                                updateUiState {
                                    it.copy(
                                        deviceSleepMode = DeviceSleepMode.NONE,
                                        isSentryArmed = false,
                                        isScreenDimmed = false
                                    )
                                }
                                setMode(AppMode.DASHCAM)
                                showTransientMessage("❄️ Sleep Recovery: Cooled to <${"%.0f".format(targetTemp)}°C. Standby active.")
                            }
                        }
                    }
                } else if (isBatterySleep) {
                    val isBatterySafe = curBat.levelPct > (settings.lowBatterySafetyLimitPct + 2)
                    if (isBatterySafe) {
                        val savedPreSleep = preSleepState ?: getCurrentUiState().preSleepOrigin
                        preSleepState = null
                        sleepTriggerCause = null
                        locationTracker.startTracking()

                        val isSentryPreSleep = savedPreSleep == PreSleepOperatingState.SENTRY_TIER1_SHOCK ||
                                savedPreSleep == PreSleepOperatingState.SENTRY_TIER2_OPTICAL

                        if (isSentryPreSleep) {
                            val demoteBat = settings.sentryAutoDemoteBatteryPct
                            val demoteTemp = settings.sentryAutoDemoteTempC
                            val autoDemoteEnabled = settings.sentryAutoDemoteEnabled
                            val configuredGuard = settings.sentryConfiguredGuardType

                            val targetGuard = if (configuredGuard == SentryGuardType.OPTICAL_MOTION) {
                                val safeForTier2 = !autoDemoteEnabled || sentryController.evaluateAutoPromotion(
                                    batteryLevelPct = curBat.levelPct,
                                    batteryTempC = curBat.temperatureC,
                                    isCharging = curBat.isPlugged,
                                    demoteBatteryThresholdPct = demoteBat,
                                    demoteTempThresholdC = demoteTemp,
                                    configuredGuardType = configuredGuard,
                                    currentGuardType = SentryGuardType.SHOCK_STANDBY,
                                    autoDemoteEnabled = autoDemoteEnabled
                                )
                                if (safeForTier2) SentryGuardType.OPTICAL_MOTION else SentryGuardType.SHOCK_STANDBY
                            } else {
                                SentryGuardType.SHOCK_STANDBY
                            }
                            settingsRepo.setSentryGuardType(targetGuard)

                            updateUiState {
                                it.copy(
                                    deviceSleepMode = DeviceSleepMode.NONE,
                                    isSentryArmed = true,
                                    isScreenDimmed = true,
                                    isSegmentLocked = false,
                                    lastTriggerReason = "Normal Loop"
                                )
                            }
                            setMode(AppMode.SENTRY)
                            showTransientMessage("🔋 Battery Recovery (${curBat.levelPct}%): Resuming Sentry ${if (targetGuard == SentryGuardType.OPTICAL_MOTION) "Tier 2" else "Tier 1"} Standby.")
                            if (settings.sentryAcousticTriggerSensitivity != Sensitivity.OFF) {
                                acousticNoiseMonitor.sensitivity = settings.sentryAcousticTriggerSensitivity
                                acousticNoiseMonitor.startMonitoring()
                            }
                        } else if (savedPreSleep == PreSleepOperatingState.DRIVING_LOOP) {
                            updateUiState {
                                it.copy(
                                    deviceSleepMode = DeviceSleepMode.NONE,
                                    isSentryArmed = false,
                                    isScreenDimmed = false,
                                    isSegmentLocked = false,
                                    lastTriggerReason = "Normal Loop"
                                )
                            }
                            setMode(AppMode.DASHCAM)
                            showTransientMessage("🔋 Battery Recovery (${curBat.levelPct}%): Resuming Dashcam Recording.")
                            startRecording("Battery Recovery Auto-Resume")
                        } else {
                            updateUiState {
                                it.copy(
                                    deviceSleepMode = DeviceSleepMode.NONE,
                                    isSentryArmed = false,
                                    isScreenDimmed = false,
                                    isSegmentLocked = false,
                                    lastTriggerReason = "Normal Loop"
                                )
                            }
                            setMode(AppMode.DASHCAM)
                            showTransientMessage("🔋 Battery Recovery (${curBat.levelPct}%): Standby active.")
                        }
                    }
                }

                // Sentry Tier 2 Auto-Demotion & Auto-Promotion Observer:
                // Actively catch background battery drain or heat spikes, and restore Tier 2 when conditions recover
                if (settings.currentMode == AppMode.SENTRY || getCurrentUiState().isSentryArmed) {
                    val demoteBat = settings.sentryAutoDemoteBatteryPct
                    val demoteTemp = settings.sentryAutoDemoteTempC
                    val autoDemoteEnabled = settings.sentryAutoDemoteEnabled
                    val configuredGuard = settings.sentryConfiguredGuardType
                    val activeGuard = settings.sentryGuardType

                    if (activeGuard == SentryGuardType.OPTICAL_MOTION) {
                        if (sentryController.evaluateAutoDemotion(
                                batteryLevelPct = curBat.levelPct,
                                batteryTempC = curBat.temperatureC,
                                demoteBatteryThresholdPct = demoteBat,
                                demoteTempThresholdC = demoteTemp,
                                currentGuardType = activeGuard,
                                autoDemoteEnabled = autoDemoteEnabled
                            )
                        ) {
                            settingsRepo.setSentryGuardType(SentryGuardType.SHOCK_STANDBY)
                            showTransientMessage("⚠️ Thermal/Battery Limit: Sentry demoted to Tier 1 Shock Standby")
                        }
                    } else if (configuredGuard == SentryGuardType.OPTICAL_MOTION) {
                        if (sentryController.evaluateAutoPromotion(
                                batteryLevelPct = curBat.levelPct,
                                batteryTempC = curBat.temperatureC,
                                isCharging = curBat.isPlugged,
                                demoteBatteryThresholdPct = demoteBat,
                                demoteTempThresholdC = demoteTemp,
                                configuredGuardType = configuredGuard,
                                currentGuardType = activeGuard,
                                autoDemoteEnabled = autoDemoteEnabled
                            )
                        ) {
                            settingsRepo.setSentryGuardType(SentryGuardType.OPTICAL_MOTION)
                            showTransientMessage("⚡ Battery/Thermal Restored: Sentry promoted to Tier 2 Optical Motion")
                        }
                    }
                }
            }
        }
    }

    fun triggerThermalCoolingSleep(temp: Float) {
        val ui = getCurrentUiState()
        val settings = settingsRepo.settings.value
        val isSentry = ui.isSentryArmed || settings.currentMode == AppMode.SENTRY
        val origin = when {
            isSentry && settings.sentryGuardType == SentryGuardType.OPTICAL_MOTION -> PreSleepOperatingState.SENTRY_TIER2_OPTICAL
            isSentry -> PreSleepOperatingState.SENTRY_TIER1_SHOCK
            isRecordingOrStarting() -> PreSleepOperatingState.DRIVING_LOOP
            else -> PreSleepOperatingState.STANDBY
        }
        preSleepState = origin
        sleepTriggerCause = SleepTriggerCause.THERMAL_OVERHEAT

        alertManager.playThermalCutoffAlert()
        val target = settings.thermalRecoveryTargetTempC.coerceAtMost(settings.maxTempThresholdC - 2.0f)
        updateUiState {
            it.copy(
                deviceSleepMode = DeviceSleepMode.THERMAL_COOLING,
                preSleepOrigin = origin,
                thermalCoolingTempC = temp,
                thermalCoolingTargetC = target,
                isScreenDimmed = true,
                isSentryArmed = false,
                isSentryArming = false
            )
        }
        acousticNoiseMonitor.stopAndRelease()
        if (isRecordingOrStarting()) stopRecording(false)
        locationTracker.stopTracking()
        showTransientMessage("❄️ Thermal Sleep: Cooling to <${"%.0f".format(target)}°C. Emergency Shock Armed.")
    }

    fun triggerLowBatterySleep(pct: Int) {
        val ui = getCurrentUiState()
        val settings = settingsRepo.settings.value
        val isSentry = ui.isSentryArmed || settings.currentMode == AppMode.SENTRY
        val origin = when {
            isSentry && settings.sentryGuardType == SentryGuardType.OPTICAL_MOTION -> PreSleepOperatingState.SENTRY_TIER2_OPTICAL
            isSentry -> PreSleepOperatingState.SENTRY_TIER1_SHOCK
            isRecordingOrStarting() -> PreSleepOperatingState.DRIVING_LOOP
            else -> PreSleepOperatingState.STANDBY
        }
        preSleepState = origin
        sleepTriggerCause = SleepTriggerCause.LOW_BATTERY

        updateUiState {
            it.copy(
                deviceSleepMode = DeviceSleepMode.LOW_BATTERY_STANDBY,
                preSleepOrigin = origin,
                isScreenDimmed = true,
                isSentryArmed = false,
                isSentryArming = false
            )
        }
        acousticNoiseMonitor.stopAndRelease()
        if (isRecordingOrStarting()) stopRecording(false)
        locationTracker.stopTracking()
        showTransientMessage("🪫 Low Battery Sleep ($pct%): Suspended to conserve power. Tap to wake.")
    }

    fun cancelThermalCooling() {
        val ui = getCurrentUiState()
        if (ui.isThermalCoolingActive || ui.isLowBatterySleepActive || ui.deviceSleepMode != DeviceSleepMode.NONE) {
            val wasLowBat = ui.deviceSleepMode == DeviceSleepMode.LOW_BATTERY_STANDBY || ui.isLowBatterySleepActive || sleepTriggerCause == SleepTriggerCause.LOW_BATTERY
            val savedPreSleep = preSleepState ?: ui.preSleepOrigin
            preSleepState = null
            sleepTriggerCause = null
            batteryMonitor.resetBatteryLevelBaseline()
            locationTracker.startTracking()

            when (savedPreSleep) {
                PreSleepOperatingState.SENTRY_TIER1_SHOCK,
                PreSleepOperatingState.SENTRY_TIER2_OPTICAL -> {
                    val targetGuard = if (savedPreSleep == PreSleepOperatingState.SENTRY_TIER2_OPTICAL) {
                        SentryGuardType.OPTICAL_MOTION
                    } else {
                        SentryGuardType.SHOCK_STANDBY
                    }
                    settingsRepo.setSentryGuardType(targetGuard)
                    updateUiState {
                        it.copy(
                            deviceSleepMode = DeviceSleepMode.NONE,
                            isSentryArmed = true,
                            isScreenDimmed = true,
                            isSegmentLocked = false,
                            lastTriggerReason = "Normal Loop"
                        )
                    }
                    setMode(AppMode.SENTRY)
                    showTransientMessage("Resumed Sentry Mode")
                    val settings = settingsRepo.settings.value
                    if (settings.sentryAcousticTriggerSensitivity != Sensitivity.OFF) {
                        acousticNoiseMonitor.sensitivity = settings.sentryAcousticTriggerSensitivity
                        acousticNoiseMonitor.startMonitoring()
                    }
                }
                PreSleepOperatingState.DRIVING_LOOP -> {
                    updateUiState {
                        it.copy(
                            deviceSleepMode = DeviceSleepMode.NONE,
                            isSentryArmed = false,
                            isScreenDimmed = false,
                            isSegmentLocked = false,
                            lastTriggerReason = "Normal Loop"
                        )
                    }
                    setMode(AppMode.DASHCAM)
                    showTransientMessage("Resumed Dashcam Recording")
                    startRecording("User Wake-Up Resume")
                }
                else -> {
                    updateUiState {
                        it.copy(
                            deviceSleepMode = DeviceSleepMode.NONE,
                            isSentryArmed = false,
                            isScreenDimmed = false,
                            isSegmentLocked = false,
                            lastTriggerReason = "Normal Loop"
                        )
                    }
                    setMode(AppMode.DASHCAM)
                    if (wasLowBat) {
                        showTransientMessage("Low battery standby cancelled")
                    } else {
                        showTransientMessage("Thermal cooling standby cancelled")
                    }
                }
            }
        }
    }

    fun startUnplugCountdown(seconds: Int) {
        alertPresenter.startUnplugCountdown(
            seconds = seconds,
            isRecording = { getCurrentUiState().isRecording },
            onStopRecording = { stopRecording(true) }
        )
    }

    fun cancelUnplugShutdown() {
        alertPresenter.cancelUnplugCountdown()
    }

    fun release() {
        powerConnectionJob?.cancel()
        powerConnectionJob = null
        hystereticRecoveryJob?.cancel()
        hystereticRecoveryJob = null
    }
}
