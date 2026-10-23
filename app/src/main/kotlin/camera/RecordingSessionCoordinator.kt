package io.github.roaxio.dashguardpro.camera

import android.app.Application
import androidx.camera.video.VideoRecordEvent
import io.github.roaxio.dashguardpro.data.settings.AppMode
import io.github.roaxio.dashguardpro.data.settings.DashGuardSettings
import io.github.roaxio.dashguardpro.data.settings.SentryArmingTrigger
import io.github.roaxio.dashguardpro.data.settings.Sensitivity
import io.github.roaxio.dashguardpro.data.SettingsRepository
import io.github.roaxio.dashguardpro.feedback.AlertManager
import io.github.roaxio.dashguardpro.media.TelemetryPoint
import io.github.roaxio.dashguardpro.media.TelemetrySubtitleManager
import io.github.roaxio.dashguardpro.model.VideoClip
import io.github.roaxio.dashguardpro.sensor.BatteryMonitor
import io.github.roaxio.dashguardpro.sensor.GForceData
import io.github.roaxio.dashguardpro.sensor.LocationTracker
import io.github.roaxio.dashguardpro.sensor.TelemetryData
import io.github.roaxio.dashguardpro.sensor.VehicleTelemetryPipeline
import io.github.roaxio.dashguardpro.service.DashcamForegroundService
import io.github.roaxio.dashguardpro.sentry.SentryController
import io.github.roaxio.dashguardpro.data.ClipDao
import io.github.roaxio.dashguardpro.storage.ClipRepository
import io.github.roaxio.dashguardpro.storage.LoopStorageManager
import io.github.roaxio.dashguardpro.storage.StorageDirectoryManager
import io.github.roaxio.dashguardpro.system.SystemDiagnosticsManager
import io.github.roaxio.dashguardpro.util.srtSidecarFile
import io.github.roaxio.dashguardpro.viewmodel.DashcamAlertPresenter
import io.github.roaxio.dashguardpro.viewmodel.state.ActiveSegmentData
import io.github.roaxio.dashguardpro.viewmodel.state.DashcamUiState
import io.github.roaxio.dashguardpro.viewmodel.state.RecordingOriginState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Domain coordinator managing active CameraX video recording sessions, loop chunk timer
 * and rolling rotation, pre-flight storage admission checks, Room DB persistence,
 * synchronized .srt subtitle telemetry sidecars, and emergency clip locking.
 *
 * Adheres to AGENTS.md Rule 4 (Deterministic Architecture vs Temporal Duct-Tape),
 * Rule 7 (Vertical Slice Integrity), Rule 9 (Asynchronous Teardown & Lifecycle Symmetry),
 * and Rule 10 (System Invariant Formulation & Lifecycle Enforcement).
 */
class RecordingSessionCoordinator(
    private val app: Application,
    private val scope: CoroutineScope,
    private val storageManager: LoopStorageManager,
    private val recordingManager: CameraXRecordingManager,
    private val clipRepository: ClipRepository,
    private val clipDao: ClipDao,
    private val settingsRepo: SettingsRepository,
    private val alertManager: AlertManager,
    private val alertPresenter: DashcamAlertPresenter,
    private val locationTracker: LocationTracker,
    private val batteryMonitor: BatteryMonitor,
    private val acousticNoiseMonitor: io.github.roaxio.dashguardpro.sensor.AcousticNoiseMonitor,
    private val sentryController: SentryController,
    private val systemDiagnosticsManager: SystemDiagnosticsManager,
    private val vehicleTelemetryPipeline: VehicleTelemetryPipeline,
    private val telemetry: StateFlow<TelemetryData>,
    private val gForce: StateFlow<GForceData>,
    private val getCurrentUiState: () -> DashcamUiState,
    private val updateUiState: ((DashcamUiState) -> DashcamUiState) -> Unit,
    private val showTransientMessage: (String) -> Unit,
    private val refreshStorageStats: () -> Unit,
    private val armSentryMode: (delaySeconds: Int?) -> Unit,
    private val triggerThermalCoolingSleep: (temp: Float) -> Unit,
    private val isPendingThermalSleep: () -> Boolean,
    private val clearPendingThermalSleep: () -> Unit,
    private val onClipLocked: () -> Unit = {},
    private val hasPendingPostLockAction: () -> Boolean = { false },
    private val executePendingPostLockAction: () -> Unit = {}
) {
    val recordingLifecycleMutex = Mutex()
    private var startRecordingJob: Job? = null
    private var recordingGeneration: Long = 0L

    var recordingOriginState: RecordingOriginState = RecordingOriginState.STANDBY
        internal set

    private var recordingTimerJob: Job? = null
    private var videoCaptureStartTimeMs: Long = 0L
    private var gpsBackfillJob: Job? = null
    var currentActiveSegment: ActiveSegmentData? = null
    val activeSegments = ConcurrentHashMap<String, ActiveSegmentData>()
    val segmentFinalizationCompletions = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    var currentSegmentTargetSeconds: Int = settingsRepo.settings.value.loopSegmentMinutes * 60

    fun initialize() {
        recordingManager.onSegmentFinalized = { file, durationMs, hasError, cause ->
            handleSegmentFinalized(file, durationMs, hasError, cause)
        }

        recordingManager.onRecordEvent = { event ->
            handleRecordEvent(event)
        }

        recordingManager.onStartNextChunk = { reason ->
            initializeNextChunkAndStart(reason)
        }
    }

    fun isNormalLoopTrigger(reason: String): Boolean {
        return reason == "Manual" ||
                reason == "Auto Power-On" ||
                reason == "Normal Loop" ||
                reason == "Auto Recovery" ||
                reason == "Thermal Recovery Auto-Resume" ||
                reason == "Battery Recovery Auto-Resume" ||
                reason == "User Wake-Up Resume"
    }

    fun onSettingsChanged(currentSettings: DashGuardSettings) {
        val isSentryMode = currentSettings.currentMode == AppMode.SENTRY || getCurrentUiState().isSentryArmed
        val targetSec = if (isSentryMode) currentSettings.sentryIncidentDurationSeconds else currentSettings.loopSegmentMinutes * 60
        if (!getCurrentUiState().isRecording) {
            currentSegmentTargetSeconds = targetSec
        }
    }

    fun startRecording(reason: String = "Manual"): Job? {
        val ui = getCurrentUiState()
        if (ui.isRecording || ui.isRecordingStarting) return null

        val isLandscape = app.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        if (!isLandscape && !settingsRepo.settings.value.debugAllowPortraitRecording) {
            android.widget.Toast.makeText(app, "Recording disabled in Portrait. Rotate to Landscape or enable Debug Mode.", android.widget.Toast.LENGTH_LONG).show()
            android.util.Log.w("RecordingSessionCoordinator", "Portrait recording prevented.")
            return null
        }

        // Pre-flight Storage Check: Ensure directory is writable
        val targetDir = storageManager.videosDir
        if (!StorageDirectoryManager.isStorageWritable(targetDir)) {
            showTransientMessage("Cannot record: Storage directory is not writable")
            android.widget.Toast.makeText(app, "Storage directory is not writable. Check permissions or folder path.", android.widget.Toast.LENGTH_LONG).show()
            alertManager.playChime()
            return null
        }

        // Pre-flight Physical & Quota Check:
        // 1. Device free space must be strictly above the dynamic OS safety buffer
        val effectiveAvailable = StorageDirectoryManager.getAvailableBytes(targetDir)
        val safetyBuffer = StorageDirectoryManager.getOsSafetyBufferBytes(targetDir)
        if (effectiveAvailable in 1..safetyBuffer) {
            val bufferFormatted = StorageDirectoryManager.StorageMetrics.formatBytes(safetyBuffer)
            showTransientMessage("Cannot record: Storage full (< $bufferFormatted OS buffer remaining)")
            android.widget.Toast.makeText(app, "Cannot record: Less than $bufferFormatted free space available on device.", android.widget.Toast.LENGTH_LONG).show()
            alertManager.playChime()
            return null
        }

        val startGeneration = ++recordingGeneration
        val isSentry = settingsRepo.settings.value.currentMode == AppMode.SENTRY || ui.isSentryArmed
        // Unconditionally ensure location tracking is active during all recording sessions (AGENTS.md Rule 7 & 9)
        locationTracker.startTracking()
        recordingOriginState = when {
            isSentry -> RecordingOriginState.SENTRY
            isNormalLoopTrigger(reason) -> RecordingOriginState.DRIVING_LOOP
            else -> RecordingOriginState.STANDBY
        }
        val initialLock = if (isSentry && !settingsRepo.settings.value.sentryAutoLockEvents && !reason.contains("Impact", ignoreCase = true) && !reason.contains("Shock", ignoreCase = true)) {
            false
        } else {
            (!isNormalLoopTrigger(reason)) || ui.isSegmentLocked
        }
        val targetSec = when {
            isSentry -> settingsRepo.settings.value.sentryIncidentDurationSeconds
            recordingOriginState == RecordingOriginState.STANDBY -> settingsRepo.settings.value.incidentBufferSeconds
            else -> settingsRepo.settings.value.loopSegmentMinutes * 60
        }
        currentSegmentTargetSeconds = targetSec
        updateUiState {
            it.copy(
                isRecording = false,
                isRecordingStarting = true,
                targetSegmentSeconds = targetSec,
                isSegmentLocked = initialLock,
                lastTriggerReason = if (!isNormalLoopTrigger(reason)) reason else it.lastTriggerReason
            )
        }
        val job = scope.launch {
            recordingLifecycleMutex.withLock {
                // Symmetrical cancellation check
                if (startGeneration != recordingGeneration) {
                    updateUiState { it.copy(isRecording = false, isRecordingStarting = false) }
                    return@withLock
                }

                // 2. Pre-emptively verify User Quota & pre-clean headroom before opening camera stream
                val spaceOk = withContext(Dispatchers.IO) {
                    storageManager.performLoopCleanupIfNeeded(settingsRepo.settings.value.maxStorageGb)
                }
                refreshStorageStats()
                if (!spaceOk) {
                    showTransientMessage("Cannot record: Storage quota reached (all saved clips are locked)")
                    android.widget.Toast.makeText(app, "Storage quota reached. All remaining clips are locked.", android.widget.Toast.LENGTH_LONG).show()
                    alertManager.playChime()
                    updateUiState { it.copy(isRecording = false, isRecordingStarting = false) }
                    return@withLock
                }

                if (recordingManager.isRecordingActive || recordingManager.isStopping) {
                    // Previous recording stream is finalizing; await its completion barrier deterministically (AGENTS.md Rule 4)
                    val barrier = segmentFinalizationCompletions.values.firstOrNull()
                    barrier?.await()
                    if (startGeneration != recordingGeneration) {
                        updateUiState { it.copy(isRecording = false, isRecordingStarting = false) }
                        return@withLock
                    }
                }

                // A stop/power transition may have happened while quota checks were running.
                val stateNow = getCurrentUiState()
                if (startGeneration != recordingGeneration || (!stateNow.isRecordingStarting && !stateNow.isRecording)) {
                    updateUiState { it.copy(isRecording = false, isRecordingStarting = false) }
                    return@withLock
                }

                acousticNoiseMonitor.stopAndRelease()
                val isSentrySession = settingsRepo.settings.value.currentMode == AppMode.SENTRY || stateNow.isSentryArmed
                val (newFile, startTimeMs) = storageManager.createNewVideoFileWithTimestamp(
                    if (isSentrySession) "SENTRY" else "DASH"
                )
                val isLocked = if (isSentrySession && !settingsRepo.settings.value.sentryAutoLockEvents && !reason.contains("Impact", ignoreCase = true) && !reason.contains("Shock", ignoreCase = true)) {
                    false
                } else {
                    (!isNormalLoopTrigger(reason)) || stateNow.isSegmentLocked
                }
                val effectiveReason = when {
                    !isNormalLoopTrigger(reason) -> reason
                    reason == "Thermal Recovery Auto-Resume" ||
                            reason == "Battery Recovery Auto-Resume" ||
                            reason == "User Wake-Up Resume" -> "Normal Loop"
                    stateNow.isSegmentLocked && stateNow.lastTriggerReason.isNotEmpty() && !isNormalLoopTrigger(stateNow.lastTriggerReason) -> stateNow.lastTriggerReason
                    else -> reason
                }
                val curTelem = telemetry.value
                val newSegment = ActiveSegmentData(
                    file = newFile,
                    startTimeMs = startTimeMs,
                    isLocked = isLocked,
                    triggerReason = effectiveReason,
                    isSentry = isSentrySession,
                    startLatitude = curTelem.latitude,
                    startLongitude = curTelem.longitude,
                    peakSpeed = curTelem.speedKmh,
                    incidentLatitude = if (isLocked) curTelem.latitude else 0.0,
                    incidentLongitude = if (isLocked) curTelem.longitude else 0.0,
                    incidentOffsetSeconds = if (isLocked) 0 else -1
                )
                currentActiveSegment = newSegment
                activeSegments[newFile.absolutePath] = newSegment
                segmentFinalizationCompletions[newFile.absolutePath] = CompletableDeferred()
                currentSegmentTargetSeconds = if (isSentrySession) {
                    settingsRepo.settings.value.sentryIncidentDurationSeconds
                } else if (recordingOriginState == RecordingOriginState.STANDBY) {
                    settingsRepo.settings.value.incidentBufferSeconds
                } else {
                    settingsRepo.settings.value.loopSegmentMinutes * 60
                }

                updateUiState {
                    it.copy(
                        isRecording = false,
                        isRecordingStarting = true,
                        segmentSeconds = 0,
                        loopSegmentMinutes = settingsRepo.settings.value.loopSegmentMinutes,
                        targetSegmentSeconds = currentSegmentTargetSeconds,
                        isSegmentLocked = isLocked,
                        lastTriggerReason = effectiveReason
                    )
                }
                if (reason != "Manual") {
                    showTransientMessage("Started: $reason")
                }

                // Tier 1 Sentry standby deliberately unbinds CameraX. Starting a Recording while
                // that rebind is still pending leaves CameraX in a device-dependent pre-Start
                // state. Wait for the binding lifecycle event rather than guessing with a delay.
                if (!recordingManager.awaitCameraBinding()) {
                    android.util.Log.e("RecordingSessionCoordinator", "Camera binding failed before recording start")
                    discardUnstartedSegment(newFile)
                    updateUiState { it.copy(isRecording = false, isRecordingStarting = false) }
                    showTransientMessage("Camera is not ready. Recording was not started.")
                    return@launch
                }

                // Power-on can interrupt a Sentry incident during the startup window. Do not
                // create a CameraX recording after the lifecycle has already moved to stopped.
                val curState = getCurrentUiState()
                if (startGeneration != recordingGeneration ||
                    (!curState.isRecording && !curState.isRecordingStarting) ||
                    currentActiveSegment?.file?.absolutePath != newFile.absolutePath
                ) {
                    discardUnstartedSegment(newFile)
                    updateUiState { it.copy(isRecording = false, isRecordingStarting = false) }
                    return@withLock
                }
                try {
                    var retryCount = 0
                    var recording: androidx.camera.video.Recording? = null
                    while (retryCount < 3 && recording == null &&
                        startGeneration == recordingGeneration &&
                        (getCurrentUiState().isRecording || getCurrentUiState().isRecordingStarting)
                    ) {
                        recording = recordingManager.startRecording(newFile, settingsRepo.settings.value.audioRecordingEnabled)
                        if (recording == null) {
                            retryCount++
                            val cur = getCurrentUiState()
                            if (startGeneration != recordingGeneration ||
                                (!cur.isRecording && !cur.isRecordingStarting)
                            ) break
                            delay(400)
                        }
                    }
                    if (recording == null) {
                        android.util.Log.e("RecordingSessionCoordinator", "Failed to start CameraX recording after retries")
                        discardUnstartedSegment(newFile)
                        updateUiState { it.copy(isRecording = false, isRecordingStarting = false) }
                        showTransientMessage("Recording failed to start. Camera is reinitializing...")
                        return@withLock
                    }
                } catch (e: Exception) {
                    android.util.Log.e("RecordingSessionCoordinator", "Failed to start CameraX recording: ${e.message}", e)
                    discardUnstartedSegment(newFile)
                    updateUiState { it.copy(isRecording = false, isRecordingStarting = false) }
                    return@withLock
                }

                systemDiagnosticsManager.applyAutoDndIfEnabled()
                DashcamForegroundService.startService(app, settingsRepo.settings.value.currentMode.name)
                val isSentryMode = settingsRepo.settings.value.currentMode == AppMode.SENTRY || getCurrentUiState().isSentryArmed
                if (!isSentryMode) {
                    if (initialLock || !isNormalLoopTrigger(reason)) {
                        alertManager.playLockSound()
                    } else {
                        alertManager.playRecordStartSound()
                    }
                }
            }
        }
        startRecordingJob = job
        return job
    }

    fun stopRecording(autoArmSentry: Boolean = true) {
        val ui = getCurrentUiState()
        if (!ui.isRecording && !ui.isRecordingStarting && !recordingManager.isRecordingActive) return

        // Invalidate and cancel any start coroutine currently in flight (AGENTS.md Rule 7: Symmetrical Teardown)
        recordingGeneration++
        startRecordingJob?.cancel()
        startRecordingJob = null

        recordingTimerJob?.cancel()
        recordingTimerJob = null
        alertPresenter.cancelUnplugCountdown()
        recordingOriginState = RecordingOriginState.STANDBY
        clearPendingThermalSleep()

        // Stop CameraX ActiveRecording. VideoRecordEvent.Finalize callback handles saveFinalizedSegmentToDb.
        // Only finalize manually if recordingManager is not active.
        if (recordingManager.isRecordingActive) {
            recordingManager.stopRecording()
        } else {
            val segment = currentActiveSegment
            if (segment?.file?.exists() == true && segment.file.length() > 0L) {
                finalizeCurrentSegment()
            } else {
                segment?.let { discardUnstartedSegment(it.file) }
            }
        }

        val settings = settingsRepo.settings.value
        val isSentryMode = settings.currentMode == AppMode.SENTRY || ui.isSentryArmed
        val defaultTargetSeconds = if (isSentryMode) {
            settings.sentryIncidentDurationSeconds
        } else {
            settings.loopSegmentMinutes * 60
        }
        currentSegmentTargetSeconds = defaultTargetSeconds

        updateUiState {
            it.copy(
                isRecording = false,
                isRecordingStarting = false,
                segmentSeconds = 0,
                targetSegmentSeconds = defaultTargetSeconds,
                isSegmentLocked = false,
                unplugCountdownSeconds = null
            )
        }

        systemDiagnosticsManager.restoreAutoDndIfSet()
        DashcamForegroundService.stopService(app)
        if (!isSentryMode) {
            alertManager.playRecordStopSound()
        } else if (getCurrentUiState().isSentryArmed) {
            sentryController.markIncidentCompleted()
        }
        refreshStorageStats()

        if (getCurrentUiState().isSentryArmed && settings.sentryAcousticTriggerSensitivity != Sensitivity.OFF) {
            acousticNoiseMonitor.sensitivity = settings.sentryAcousticTriggerSensitivity
            acousticNoiseMonitor.startMonitoring()
        }

        val isOwnerPowerInterruption = ui.lastTriggerReason == "Sentry (Interrupted by Power On)"
        val isVehiclePowered = batteryMonitor.batteryState.value.isPlugged
        val shouldArmSentry = autoArmSentry &&
                settings.sentryModeEnabled &&
                !isOwnerPowerInterruption &&
                !ui.isThermalCoolingActive &&
                (settings.sentryArmingTrigger == SentryArmingTrigger.ON_RECORDING_STOP ||
                        (settings.sentryArmingTrigger == SentryArmingTrigger.ON_POWER_LOSS && !isVehiclePowered))

        if (shouldArmSentry && !getCurrentUiState().isSentryArmed && !getCurrentUiState().isSentryArming) {
            armSentryMode(null)
        }
    }

    fun toggleRecording() {
        val ui = getCurrentUiState()
        if (ui.isRecording || ui.isRecordingStarting) {
            stopRecording()
        } else {
            startRecording("Manual")
        }
    }

    private fun startSegmentTimer() {
        recordingTimerJob?.cancel()
        recordingTimerJob = scope.launch {
            while (getCurrentUiState().isRecording) {
                delay(1000L)
                if (!getCurrentUiState().isRecording) break

                val captureStartMs = videoCaptureStartTimeMs
                if (captureStartMs <= 0L) break
                val currentSec = ((System.currentTimeMillis() - captureStartMs) / 1000L).toInt()
                val totalMin = getCurrentUiState().totalSessionMinutes + (if (currentSec % 60 == 0) 1 else 0)

                // Update segment running telemetry samples
                val curSpeed = telemetry.value.speedKmh
                val curTelem = telemetry.value
                val curG = gForce.value.totalG
                val ui = getCurrentUiState()
                currentActiveSegment?.let { seg ->
                    seg.speedSamples.add(curSpeed)
                    seg.peakSpeed = maxOf(seg.peakSpeed, curSpeed)
                    seg.distanceMeters += (curSpeed / 3.6f) // meters in 1 second

                    seg.telemetryPoints.add(
                        TelemetryPoint(
                            offsetSeconds = currentSec,
                            wallClockTimeMs = System.currentTimeMillis(),
                            speedDisplay = curTelem.speedDisplay,
                            speedUnit = curTelem.speedUnitLabel,
                            latitude = curTelem.latitude,
                            longitude = curTelem.longitude,
                            altitudeMeters = curTelem.altitudeMeters,
                            headingDegrees = curTelem.headingDegrees,
                            headingCardinal = curTelem.headingCardinal,
                            gForce = curG,
                            isLocked = ui.isSegmentLocked
                        )
                    )
                }

                // Update session trip stats via VehicleTelemetryPipeline
                val updatedTripStats = vehicleTelemetryPipeline.calculateTripStats(
                    prevStats = ui.tripStats,
                    curSpeedKmh = curSpeed,
                    isSegmentLocked = ui.isSegmentLocked
                )

                // Real-time in-flight storage safety & user quota monitoring (checked every 2 seconds)
                if (currentSec % 2 == 0) {
                    val spaceOk = withContext(Dispatchers.IO) {
                        storageManager.performLoopCleanupIfNeeded(settingsRepo.settings.value.maxStorageGb)
                    }
                    if (!spaceOk) {
                        stopRecording()
                        showTransientMessage("Recording stopped: Storage limit reached (all saved clips are locked)")
                        alertManager.playChime()
                        break
                    }
                }

                if (currentSec >= currentSegmentTargetSeconds) {
                    if (hasPendingPostLockAction()) {
                        stopRecording(autoArmSentry = false)
                        executePendingPostLockAction()
                        break
                    }

                    val curTemp = batteryMonitor.batteryState.value.temperatureC
                    val maxTemp = settingsRepo.settings.value.maxTempThresholdC
                    val isOverheating = (maxTemp > 0f && curTemp >= maxTemp) || isPendingThermalSleep()

                    if (isOverheating) {
                        clearPendingThermalSleep()
                        stopRecording(autoArmSentry = false)
                        triggerThermalCoolingSleep(curTemp)
                        break
                    } else if (recordingOriginState == RecordingOriginState.STANDBY) {
                        stopRecording(autoArmSentry = false)
                        showTransientMessage("💾 Emergency clip saved. Returning to Standby.")
                        break
                    } else if (recordingOriginState == RecordingOriginState.SENTRY || settingsRepo.settings.value.currentMode == AppMode.SENTRY) {
                        stopRecording(autoArmSentry = true)
                        break
                    } else if (settingsRepo.settings.value.currentMode == AppMode.DASHCAM) {
                        updateUiState {
                            it.copy(
                                segmentSeconds = 0,
                                totalSessionMinutes = totalMin,
                                tripStats = updatedTripStats
                            )
                        }
                        rotateSegment()
                    }
                } else {
                    updateUiState {
                        it.copy(
                            segmentSeconds = currentSec,
                            totalSessionMinutes = totalMin,
                            tripStats = updatedTripStats
                        )
                    }
                }
            }
        }
    }

    /**
     * Rotates rolling video chunk.
     * Strict Lifecycle Handling:
     * Calls stop() on ActiveRecording and WAITS for VideoRecordEvent.Finalize before initializing the next chunk file.
     */
    fun rotateSegment() {
        val curTemp = batteryMonitor.batteryState.value.temperatureC
        val maxTemp = settingsRepo.settings.value.maxTempThresholdC
        if ((maxTemp > 0f && curTemp >= maxTemp) || isPendingThermalSleep()) {
            clearPendingThermalSleep()
            stopRecording(autoArmSentry = false)
            triggerThermalCoolingSleep(curTemp)
            return
        }
        if (recordingOriginState == RecordingOriginState.STANDBY) {
            stopRecording(autoArmSentry = false)
            return
        }
        val nextChunkReason = "Normal Loop"

        if (recordingManager.isRecordingActive) {
            recordingManager.rotateToNextChunk(nextChunkReason)
        } else {
            finalizeCurrentSegment()
            initializeNextChunkAndStart(nextChunkReason)
        }
    }

    /**
     * Initializes the next video chunk file ONLY after the previous stream has completed VideoRecordEvent.Finalize.
     */
    fun initializeNextChunkAndStart(reason: String = "Normal Loop") {
        val isSentry = settingsRepo.settings.value.currentMode == AppMode.SENTRY
        val (newFile, startTimeMs) = storageManager.createNewVideoFileWithTimestamp(
            if (isSentry) "SENTRY" else "DASH"
        )
        val isLocked = (!isNormalLoopTrigger(reason))
        val curTelem = telemetry.value
        val newSegment = ActiveSegmentData(
            file = newFile,
            startTimeMs = startTimeMs,
            isLocked = isLocked,
            triggerReason = reason,
            isSentry = isSentry,
            startLatitude = curTelem.latitude,
            startLongitude = curTelem.longitude,
            peakSpeed = curTelem.speedKmh,
            incidentLatitude = if (isLocked) curTelem.latitude else 0.0,
            incidentLongitude = if (isLocked) curTelem.longitude else 0.0,
            incidentOffsetSeconds = if (isLocked) 0 else -1
        )
        currentActiveSegment = newSegment
        activeSegments[newFile.absolutePath] = newSegment
        segmentFinalizationCompletions[newFile.absolutePath] = CompletableDeferred()
        currentSegmentTargetSeconds = if (isSentry) {
            settingsRepo.settings.value.sentryIncidentDurationSeconds
        } else {
            settingsRepo.settings.value.loopSegmentMinutes * 60
        }

        updateUiState {
            it.copy(
                segmentSeconds = 0,
                targetSegmentSeconds = currentSegmentTargetSeconds,
                isSegmentLocked = isLocked,
                lastTriggerReason = reason
            )
        }

        scope.launch {
            try {
                var retryCount = 0
                var recording: androidx.camera.video.Recording? = null
                while (retryCount < 3 && recording == null) {
                    recording = recordingManager.startRecording(newFile, settingsRepo.settings.value.audioRecordingEnabled)
                    if (recording == null) {
                        retryCount++
                        delay(500)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("RecordingSessionCoordinator", "Failed to start next chunk recording: ${e.message}", e)
            }
        }

        scope.launch(Dispatchers.IO) {
            val spaceOk = storageManager.performLoopCleanupIfNeeded(settingsRepo.settings.value.maxStorageGb)
            refreshStorageStats()
            if (!spaceOk) {
                withContext(Dispatchers.Main) {
                    stopRecording()
                    showTransientMessage("Recording stopped: Storage quota reached (all saved clips are locked)")
                    alertManager.playChime()
                }
            }
        }
    }

    fun finalizeCurrentSegment() {
        val segment = currentActiveSegment ?: return
        val file = segment.file
        val durationMs = (System.currentTimeMillis() - segment.startTimeMs).coerceAtLeast(1000L)
        val saveJob = saveFinalizedSegmentToDb(file, durationMs)
        segmentFinalizationCompletions.remove(file.absolutePath)?.let { completion ->
            scope.launch {
                try {
                    saveJob.join()
                } finally {
                    completion.complete(Unit)
                }
            }
        }
    }

    /** Removes a segment whose CameraX recording never reached a valid media stream. */
    fun discardUnstartedSegment(file: File) {
        activeSegments.remove(file.absolutePath)
        if (currentActiveSegment?.file?.absolutePath == file.absolutePath) {
            currentActiveSegment = null
        }
        try {
            if (file.exists() && file.length() == 0L) file.delete()
        } catch (_: Exception) {}
        segmentFinalizationCompletions.remove(file.absolutePath)?.complete(Unit)
    }

    fun saveFinalizedSegmentToDb(file: File, finalizedDurationMs: Long): Job {
        val segment = activeSegments.remove(file.absolutePath) ?: currentActiveSegment
        if (currentActiveSegment?.file?.absolutePath == file.absolutePath) {
            currentActiveSegment = null
        }
        val segmentStartMs = segment?.startTimeMs ?: System.currentTimeMillis()
        val durationMs = if (finalizedDurationMs > 0) finalizedDurationMs else (System.currentTimeMillis() - segmentStartMs).coerceAtLeast(1000L)
        val curTelemetry = telemetry.value
        val curBattery = batteryMonitor.batteryState.value
        val ui = getCurrentUiState()
        val settings = settingsRepo.settings.value
        val effectiveReason = when {
            segment?.triggerReason != null && segment.triggerReason != "Normal Loop" && segment.triggerReason != "Thermal Recovery Auto-Resume" -> segment.triggerReason
            ui.lastTriggerReason.startsWith("Sentry") -> ui.lastTriggerReason
            else -> segment?.triggerReason ?: ui.lastTriggerReason
        }
        val isSentry = (segment?.isSentry == true) || (settings.currentMode == AppMode.SENTRY) || effectiveReason.startsWith("Sentry") || file.name.startsWith("VID_SENTRY_")
        val reason = if (isSentry && (effectiveReason == "Normal Loop" || effectiveReason.isEmpty())) {
            if (ui.lastTriggerReason.isNotEmpty() && ui.lastTriggerReason != "Normal Loop") {
                ui.lastTriggerReason
            } else {
                "Motion Sentry Trigger"
            }
        } else {
            effectiveReason
        }
        val isLocked = (segment?.isLocked == true) || ui.isSegmentLocked || reason.startsWith("Sentry") || isSentry

        android.util.Log.d("SentryFix", "saveFinalizedSegmentToDb: file=${file.name}, reason=$reason, isLocked=$isLocked, isSentry=$isSentry, sentryInterruptedAction=${settings.sentryInterruptedClipAction}")

        if (sentryController.shouldAutoDeleteInterruptedClip(reason, settings.sentryInterruptedClipAction)) {
            showTransientMessage("🗑️ Auto-Deleted Sentry clip interrupted by vehicle start")
            return scope.launch(Dispatchers.IO) {
                try {
                    file.delete()
                    val srt = file.srtSidecarFile
                    if (srt.exists()) srt.delete()
                } catch (_: Exception) {}
                refreshStorageStats()
            }
        }

        // Compute segment-specific metrics
        val calculatedAvgSpeed = if (segment?.speedSamples?.isNotEmpty() == true) {
            segment.speedSamples.average().toFloat()
        } else {
            curTelemetry.speedKmh
        }
        val calculatedDistance = if (segment != null && segment.distanceMeters > 0f) {
            segment.distanceMeters
        } else {
            (calculatedAvgSpeed / 3.6f) * (durationMs / 1000f)
        }
        val speedSource = "GPS"
        val startLat = segment?.startLatitude ?: 0.0
        val startLng = segment?.startLongitude ?: 0.0
        val endLat = curTelemetry.latitude
        val endLng = curTelemetry.longitude
        val incLat = segment?.incidentLatitude ?: 0.0
        val incLng = segment?.incidentLongitude ?: 0.0
        val incOffset = segment?.incidentOffsetSeconds ?: -1
        val incG = segment?.incidentGForce ?: 0f

        return scope.launch(Dispatchers.IO) {
            if (!file.exists() || file.length() == 0L) {
                try { file.delete() } catch (_: Exception) {}
                android.util.Log.e("RecordingSessionCoordinator", "Aborted saving 0-byte finalized segment: ${file.name}")
                return@launch
            }

            // Check if clip record already exists for this file
            val existing = clipDao.getClipByPath(file.absolutePath)
            val fileSize = file.length()
            val clip = VideoClip(
                id = existing?.id ?: 0,
                filePath = file.absolutePath,
                fileName = file.name,
                timestamp = existing?.timestamp ?: segmentStartMs,
                durationMs = durationMs,
                fileSizeBytes = fileSize,
                isLocked = isLocked || (existing?.isLocked == true),
                isSentryEvent = isSentry || (existing?.isSentryEvent == true),
                triggerReason = if (isLocked && reason != "Normal Loop") reason else if (isSentry) "Motion Sentry Trigger" else (existing?.triggerReason ?: reason),
                speedKmh = calculatedAvgSpeed,
                avgSpeedKmh = calculatedAvgSpeed,
                distanceMeters = calculatedDistance,
                speedSource = speedSource,
                latitude = if (incLat != 0.0) incLat else if (startLat != 0.0) startLat else endLat,
                longitude = if (incLng != 0.0) incLng else if (startLng != 0.0) startLng else endLng,
                startLatitude = startLat,
                startLongitude = startLng,
                endLatitude = endLat,
                endLongitude = endLng,
                incidentLatitude = incLat,
                incidentLongitude = incLng,
                incidentVideoOffsetSeconds = incOffset,
                incidentGForce = incG,
                batteryTempC = curBattery.temperatureC
            )
            clipRepository.insertOrUpdateClip(clip)

            // Generate synchronized .srt subtitle telemetry sidecar file
            val pointsToSave = segment?.telemetryPoints?.toList() ?: emptyList()
            if (pointsToSave.isNotEmpty() && settings.showWatermark) {
                val srtFile = file.srtSidecarFile
                TelemetrySubtitleManager.writeSubtitleFile(srtFile, pointsToSave)
            }

            refreshStorageStats()
        }
    }

    fun guaranteePostIncidentBuffer(triggerOffsetSec: Int) {
        val bufferSec = settingsRepo.settings.value.incidentBufferSeconds
        val requiredTarget = triggerOffsetSec + bufferSec
        if (requiredTarget > currentSegmentTargetSeconds) {
            currentSegmentTargetSeconds = requiredTarget
            updateUiState { it.copy(targetSegmentSeconds = currentSegmentTargetSeconds) }
        }
    }

    fun toggleCurrentClipLock(reason: String = "Manual Lock") {
        val ui = getCurrentUiState()
        if (ui.isRecording || ui.isRecordingStarting) {
            val currentlyLocked = ui.isSegmentLocked
            val newLockState = !currentlyLocked
            if (newLockState) {
                currentActiveSegment?.let { seg ->
                    if (seg.incidentOffsetSeconds < 0) {
                        seg.incidentOffsetSeconds = ui.segmentSeconds
                        seg.incidentLatitude = telemetry.value.latitude
                        seg.incidentLongitude = telemetry.value.longitude
                    }
                    seg.isLocked = true
                    seg.triggerReason = reason
                }
                guaranteePostIncidentBuffer(ui.segmentSeconds)
                alertPresenter.cancelUnplugCountdown()
                onClipLocked()
            } else {
                currentActiveSegment?.let { seg ->
                    seg.isLocked = false
                    seg.triggerReason = "Normal Loop"
                }
            }
            updateUiState {
                it.copy(
                    isSegmentLocked = newLockState,
                    lastTriggerReason = if (newLockState) reason else "Normal Loop"
                )
            }
            showTransientMessage(if (newLockState) "🔒 Current Recording Protected" else "🔓 Current Recording Unlocked")
            val isSentry = ui.isSentryArmed || settingsRepo.settings.value.currentMode == AppMode.SENTRY
            if (newLockState) {
                if (!isSentry) {
                    alertManager.vibrateImpactPattern()
                    alertManager.playLockSound()
                }
            } else {
                if (!isSentry) {
                    alertManager.vibrateShort()
                }
            }
        } else {
            // In Standby mode:
            if (ui.isSegmentLocked) {
                // Symmetrical toggle: If already in a locked state while standing by, clear it cleanly (AGENTS.md Rule 9)
                updateUiState {
                    it.copy(
                        isSegmentLocked = false,
                        lastTriggerReason = "Normal Loop"
                    )
                }
                alertPresenter.dismissImpactAlert()
                showTransientMessage("🔓 Guard Lock Cleared")
                alertManager.vibrateShort()
            } else {
                // Otherwise, pressing the lock button attempts to start a protected manual emergency recording
                val job = startRecording("Manual Emergency Lock")
                if (job == null) {
                    updateUiState { it.copy(isSegmentLocked = false) }
                }
            }
        }
    }

    fun lockCurrentClip(reason: String = "Manual Emergency Lock", vibrate: Boolean = true) {
        val ui = getCurrentUiState()
        val isSentry = ui.isSentryArmed || settingsRepo.settings.value.currentMode == AppMode.SENTRY
        val shouldVibrate = vibrate && !isSentry
        if (ui.isRecording || ui.isRecordingStarting) {
            currentActiveSegment?.let { seg ->
                if (seg.incidentOffsetSeconds < 0) {
                    seg.incidentOffsetSeconds = ui.segmentSeconds
                    seg.incidentLatitude = telemetry.value.latitude
                    seg.incidentLongitude = telemetry.value.longitude
                }
                seg.isLocked = true
                seg.triggerReason = reason
            }
            guaranteePostIncidentBuffer(ui.segmentSeconds)
            updateUiState {
                it.copy(
                    isSegmentLocked = true,
                    lastTriggerReason = reason
                )
            }
            alertPresenter.cancelUnplugCountdown()
            onClipLocked()
            showTransientMessage("🔒 Clip Protected: $reason")
            if (shouldVibrate) {
                alertManager.vibrateImpactPattern()
            }
            if (!isSentry) {
                alertManager.playLockSound()
            }
        } else {
            val job = startRecording(reason)
            if (job == null) {
                updateUiState { it.copy(isSegmentLocked = false) }
            }
        }
    }

    suspend fun handleSentryOwnerReturnInterruption() {
        val interruptedFilePath = currentActiveSegment?.file?.absolutePath
        currentActiveSegment?.let { seg ->
            seg.triggerReason = "Sentry (Interrupted by Power On)"
            seg.isLocked = true
            seg.isSentry = true
            android.util.Log.d("SentryFix", "handleSentryOwnerReturnInterruption: seg=${seg.file.name}, isSentry=${seg.isSentry}, isLocked=${seg.isLocked}")
        }
        updateUiState {
            it.copy(
                lastTriggerReason = "Sentry (Interrupted by Power On)",
                isSegmentLocked = true
            )
        }
        stopRecording()
        interruptedFilePath?.let { path ->
            segmentFinalizationCompletions[path]?.await()
        }
    }

    fun launchGpsBackfill(reason: String) {
        gpsBackfillJob?.cancel()
        locationTracker.startTracking()
        gpsBackfillJob = scope.launch(Dispatchers.IO) {
            val validTelemetry = withTimeoutOrNull(45_000L) {
                telemetry.first { it.latitude != 0.0 && it.longitude != 0.0 }
            }

            if (validTelemetry != null) {
                val fixedLat = validTelemetry.latitude
                val fixedLng = validTelemetry.longitude
                val seg = currentActiveSegment
                if (seg != null) {
                    seg.incidentLatitude = fixedLat
                    seg.incidentLongitude = fixedLng
                }
                clipRepository.updateLatestClipIncidentLocation(fixedLat, fixedLng)
                android.util.Log.d("RecordingSessionCoordinator", "GPS Backfill successfully updated coordinates ($fixedLat, $fixedLng) for $reason")
            }
        }
    }

    private fun handleSegmentFinalized(file: File?, durationMs: Long, hasError: Boolean, cause: Throwable?) {
        val finalization = file?.absolutePath?.let { segmentFinalizationCompletions[it] }
        if (file != null && file.exists() && file.length() > 0L) {
            val saveJob = saveFinalizedSegmentToDb(file, durationMs)
            scope.launch {
                try {
                    saveJob.join()
                } finally {
                    finalization?.let {
                        it.complete(Unit)
                        segmentFinalizationCompletions.remove(file.absolutePath, it)
                    }
                }
            }
        } else {
            finalization?.let {
                it.complete(Unit)
                file?.absolutePath?.let { path -> segmentFinalizationCompletions.remove(path, it) }
            }
        }

        if (hasError && getCurrentUiState().isRecording && !recordingManager.isRotating) {
            val errorMsg = cause?.message ?: ""
            val targetDir = storageManager.videosDir
            val measuredFree = StorageDirectoryManager.getAvailableBytes(targetDir)
            val currentFree = if (measuredFree > 0L) measuredFree else Long.MAX_VALUE
            val safetyBuffer = StorageDirectoryManager.getOsSafetyBufferBytes(targetDir)
            val isOutOfSpace = currentFree <= safetyBuffer
            val isStorageError = isOutOfSpace ||
                    errorMsg.contains("storage", ignoreCase = true) ||
                    errorMsg.contains("space", ignoreCase = true) ||
                    errorMsg.contains("ENOSPC", ignoreCase = true)

            if (isStorageError) {
                stopRecording()
                showTransientMessage("Recording stopped: Storage space exhausted")
                alertManager.playChime()
            } else {
                android.util.Log.e("RecordingSessionCoordinator", "CameraX recording finalized unexpectedly (error: ${cause?.message})")
                scope.launch {
                    delay(500)
                    if (getCurrentUiState().isRecording && !recordingManager.isRecordingActive) {
                        initializeNextChunkAndStart("Auto Recovery")
                    }
                }
            }
        }
    }

    private fun handleRecordEvent(event: VideoRecordEvent) {
        if (event is VideoRecordEvent.Start) {
            videoCaptureStartTimeMs = System.currentTimeMillis()
            updateUiState { it.copy(isRecording = true, isRecordingStarting = false) }
            android.util.Log.d("RecordingSessionCoordinator", "VideoCapture clock started")
            startSegmentTimer()
        }
    }

    fun release() {
        startRecordingJob?.cancel()
        startRecordingJob = null
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        gpsBackfillJob?.cancel()
        gpsBackfillJob = null
    }
}
