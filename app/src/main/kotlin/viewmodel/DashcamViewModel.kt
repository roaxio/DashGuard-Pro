package io.github.roaxio.dashguardpro.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.roaxio.dashguardpro.DashGuardApplication
import io.github.roaxio.dashguardpro.system.SystemOptimizationState
import io.github.roaxio.dashguardpro.data.settings.AppMode
import io.github.roaxio.dashguardpro.data.settings.DashGuardSettings
import io.github.roaxio.dashguardpro.data.settings.HudDisplayMode
import io.github.roaxio.dashguardpro.data.settings.Sensitivity
import io.github.roaxio.dashguardpro.data.settings.SentryGuardType
import io.github.roaxio.dashguardpro.data.settings.StorageLocationType
import io.github.roaxio.dashguardpro.model.GalleryFilter
import io.github.roaxio.dashguardpro.model.GalleryNavState
import io.github.roaxio.dashguardpro.model.GalleryViewMode
import io.github.roaxio.dashguardpro.model.TripSession
import io.github.roaxio.dashguardpro.model.VideoClip
import io.github.roaxio.dashguardpro.playback.VideoPlaybackState
import io.github.roaxio.dashguardpro.analytics.TripGroupingEngine
import io.github.roaxio.dashguardpro.camera.CameraHardwareProfile
import io.github.roaxio.dashguardpro.camera.CameraHardwareSpecManager
import io.github.roaxio.dashguardpro.storage.ClipRepository
import io.github.roaxio.dashguardpro.storage.MigrationProgress
import io.github.roaxio.dashguardpro.storage.StorageDirectoryManager
import io.github.roaxio.dashguardpro.storage.StorageMigrationManager
import io.github.roaxio.dashguardpro.service.DashcamForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import java.io.File
import io.github.roaxio.dashguardpro.viewmodel.state.ActiveSegmentData
import io.github.roaxio.dashguardpro.viewmodel.state.DashcamUiState
import io.github.roaxio.dashguardpro.viewmodel.state.DeviceSleepMode
import io.github.roaxio.dashguardpro.viewmodel.state.PreSleepOperatingState
import io.github.roaxio.dashguardpro.viewmodel.state.RecordingOriginState
import io.github.roaxio.dashguardpro.viewmodel.state.SleepTriggerCause
import io.github.roaxio.dashguardpro.viewmodel.state.TripStats

class DashcamViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DashGuardApplication
    private val clipDao = app.database.clipDao()
    private val settingsRepo = app.settingsRepository
    val clipRepository = ClipRepository(clipDao, settingsRepo, viewModelScope, context = application)
    private val storageManager = app.storageManager
    private val recordingManager = app.recordingManager
    private val sensorMonitor = app.sensorMonitor
    private val batteryMonitor = app.batteryMonitor
    private val locationTracker = app.locationTracker
    val alertManager = app.alertManager
    val acousticNoiseMonitor = app.acousticNoiseMonitor
    val sentryController = app.sentryController
    val playbackCoordinator = app.playbackCoordinator
    val systemDiagnosticsManager = app.systemDiagnosticsManager

    val isAnyScreenDimmed: Boolean
        get() = _uiState.value.isScreenDimmed ||
                _uiState.value.isSentryArmed ||
                _uiState.value.isThermalCoolingActive ||
                _uiState.value.isLowBatterySleepActive ||
                _uiState.value.deviceSleepMode != DeviceSleepMode.NONE ||
                settings.value.currentMode == AppMode.SENTRY ||
                settings.value.oledPowerSaveMode

    val settings: StateFlow<DashGuardSettings> = settingsRepo.settings
    val telemetry = locationTracker.telemetryState
    val gForce = sensorMonitor.gForceState
    val battery = batteryMonitor.batteryState

    val vehicleTelemetryPipeline = io.github.roaxio.dashguardpro.sensor.VehicleTelemetryPipeline(
        telemetryState = locationTracker.telemetryState,
        gForceState = sensorMonitor.gForceState,
        scope = viewModelScope
    )
    val isDrivingLockoutActive: StateFlow<Boolean> = vehicleTelemetryPipeline.isDrivingLockoutActive

    val allClips = clipRepository.allClips
    val lockedClips = clipRepository.lockedClips
    val trips: StateFlow<List<TripSession>> = clipRepository.trips

    private val _galleryNavState = MutableStateFlow(GalleryNavState())
    val galleryNavState: StateFlow<GalleryNavState> = _galleryNavState.asStateFlow()

    private val _uiState = MutableStateFlow(
        DashcamUiState(
            loopSegmentMinutes = settingsRepo.settings.value.loopSegmentMinutes,
            targetSegmentSeconds = settingsRepo.settings.value.loopSegmentMinutes * 60,
            hudDisplayMode = settingsRepo.settings.value.hudDisplayMode
        )
    )
    val uiState: StateFlow<DashcamUiState> = _uiState.asStateFlow()

    val alertPresenter = DashcamAlertPresenter(
        scope = viewModelScope,
        getCurrentUiState = { _uiState.value },
        updateUiState = { transform -> _uiState.update(transform) }
    )

    val recordingCoordinator = io.github.roaxio.dashguardpro.camera.RecordingSessionCoordinator(
        app = app,
        scope = viewModelScope,
        storageManager = storageManager,
        recordingManager = recordingManager,
        clipRepository = clipRepository,
        clipDao = clipDao,
        settingsRepo = settingsRepo,
        alertManager = alertManager,
        alertPresenter = alertPresenter,
        locationTracker = locationTracker,
        batteryMonitor = batteryMonitor,
        acousticNoiseMonitor = acousticNoiseMonitor,
        sentryController = sentryController,
        systemDiagnosticsManager = systemDiagnosticsManager,
        vehicleTelemetryPipeline = vehicleTelemetryPipeline,
        telemetry = locationTracker.telemetryState,
        gForce = sensorMonitor.gForceState,
        getCurrentUiState = { _uiState.value },
        updateUiState = { transform -> _uiState.update(transform) },
        showTransientMessage = { msg -> showTransientMessage(msg) },
        refreshStorageStats = { refreshStorageStats() },
        armSentryMode = { delaySec -> armSentryMode(delaySec) },
        triggerThermalCoolingSleep = { temp -> powerAndThermalCoordinator.triggerThermalCoolingSleep(temp) },
        isPendingThermalSleep = { powerAndThermalCoordinator.pendingThermalSleep },
        clearPendingThermalSleep = { powerAndThermalCoordinator.clearPendingThermalSleep() },
        onClipLocked = { powerAndThermalCoordinator.onClipLocked() },
        hasPendingPostLockAction = { powerAndThermalCoordinator.hasPendingPostLockAction() },
        executePendingPostLockAction = { powerAndThermalCoordinator.executePendingPostLockAction() }
    )

    val recordingLifecycleMutex: Mutex
        get() = recordingCoordinator.recordingLifecycleMutex

    val recordingOriginState: RecordingOriginState
        get() = recordingCoordinator.recordingOriginState

    val currentActiveSegment: ActiveSegmentData?
        get() = recordingCoordinator.currentActiveSegment

    val powerAndThermalCoordinator = io.github.roaxio.dashguardpro.system.PowerAndThermalCoordinator(
        app = app,
        scope = viewModelScope,
        settingsRepo = settingsRepo,
        batteryMonitor = batteryMonitor,
        locationTracker = locationTracker,
        acousticNoiseMonitor = acousticNoiseMonitor,
        sentryController = sentryController,
        alertManager = alertManager,
        alertPresenter = alertPresenter,
        getCurrentUiState = { _uiState.value },
        updateUiState = { transform -> _uiState.update(transform) },
        getIsAnyScreenDimmed = { isAnyScreenDimmed },
        setScreenDimmed = { dimmed -> setScreenDimmed(dimmed) },
        setMode = { mode -> setMode(mode) },
        showTransientMessage = { msg -> showTransientMessage(msg) },
        isRecordingOrStarting = { _uiState.value.isRecording || _uiState.value.isRecordingStarting },
        isEmergencyLocked = { _uiState.value.isSegmentLocked || (currentActiveSegment?.isLocked == true) },
        isIncidentActive = {
            _uiState.value.isSegmentLocked ||
                    _uiState.value.showImpactAlert ||
                    _uiState.value.isMotionTriggered ||
                    (currentActiveSegment?.incidentOffsetSeconds ?: -1) >= 0
        },
        startRecording = { reason -> startRecording(reason) },
        stopRecording = { autoArm -> stopRecording(autoArm) },
        armSentryMode = { delaySec -> armSentryMode(delaySec) },
        disarmSentryMode = { manual -> disarmSentryMode(manual) },
        cancelSentryArming = { cancelSentryArming() },
        handleSentryOwnerReturnInterruption = { handleSentryOwnerReturnInterruption() }
    )

    val preSleepState: PreSleepOperatingState?
        get() = powerAndThermalCoordinator.preSleepState
    val sleepTriggerCause: SleepTriggerCause?
        get() = powerAndThermalCoordinator.sleepTriggerCause
    val pendingThermalSleep: Boolean
        get() = powerAndThermalCoordinator.pendingThermalSleep

    val systemOptState: StateFlow<SystemOptimizationState> = systemDiagnosticsManager.systemOptState

    private val _cameraProfile = MutableStateFlow(
        CameraHardwareSpecManager.getHardwareCameraProfile(
            app,
            settingsRepo.settings.value.frontCameraSelected
        )
    )
    val cameraProfile: StateFlow<CameraHardwareProfile> = _cameraProfile.asStateFlow()

    val playbackState: StateFlow<VideoPlaybackState> = playbackCoordinator.playbackState

    val migrationState: StateFlow<MigrationProgress?> = StorageMigrationManager.migrationState

    fun refreshCameraHardwareProfile() {
        _cameraProfile.value = CameraHardwareSpecManager.getHardwareCameraProfile(
            app,
            settingsRepo.settings.value.frontCameraSelected
        )
    }

    fun setGalleryViewMode(mode: GalleryViewMode) {
        _galleryNavState.update { it.copy(viewMode = mode) }
    }

    fun setGalleryFilter(filter: GalleryFilter) {
        _galleryNavState.update { it.copy(selectedFilter = filter) }
    }

    fun toggleTripExpanded(tripId: String) {
        _galleryNavState.update { state ->
            val updated = if (state.expandedTripIds.contains(tripId)) {
                state.expandedTripIds - tripId
            } else {
                state.expandedTripIds + tripId
            }
            state.copy(expandedTripIds = updated)
        }
    }

    fun setTripExpanded(tripId: String, isExpanded: Boolean) {
        _galleryNavState.update { state ->
            val updated = if (isExpanded) {
                state.expandedTripIds + tripId
            } else {
                state.expandedTripIds - tripId
            }
            state.copy(expandedTripIds = updated)
        }
    }

    fun updateGalleryScrollPosition(viewMode: GalleryViewMode, index: Int, offset: Int) {
        _galleryNavState.update { state ->
            when (viewMode) {
                GalleryViewMode.TRIP_LOOPS -> state.copy(tripScrollIndex = index, tripScrollOffset = offset)
                GalleryViewMode.ALL_CLIPS -> state.copy(allClipsScrollIndex = index, allClipsScrollOffset = offset)
            }
        }
    }

    fun consumePendingScrollToClip() {
        _galleryNavState.update { it.copy(pendingScrollToClip = null) }
    }

    fun resetGalleryNavigationMemory() {
        _galleryNavState.value = GalleryNavState()
    }

    fun openClipForPlayback(clip: VideoClip, continuous: Boolean? = null, forceRestart: Boolean = false) {
        val trip = getTripForClip(clip)
        _galleryNavState.update { state ->
            val newExpanded = if (trip != null) state.expandedTripIds + trip.id else state.expandedTripIds
            state.copy(
                lastPlayedClipFilePath = clip.filePath,
                pendingScrollToClip = clip.filePath,
                expandedTripIds = newExpanded
            )
        }
        playbackCoordinator.openClipForPlayback(clip, continuous, forceRestart)
    }

    fun getAdjacentClips(clip: VideoClip): Pair<VideoClip?, VideoClip?> {
        val trip = getTripForClip(clip) ?: return Pair(null, null)
        return TripGroupingEngine.findAdjacentClips(
            currentClip = clip,
            allClips = trip.clips,
            maxAdjacencyGapSeconds = settingsRepo.settings.value.loopAdjacencyThresholdSeconds
        )
    }

    fun getTripForClip(clip: VideoClip): TripSession? {
        return TripGroupingEngine.findTripForClip(clip, trips.value)
    }

    /**
     * Returns the chronological prev/next clip within the trip, ignoring the adjacency window.
     * Used to power trip-scoped manual Skip Prev / Skip Next buttons.
     */
    fun getTripAdjacentClips(clip: VideoClip, trip: TripSession): Pair<VideoClip?, VideoClip?> {
        return TripGroupingEngine.findTripAdjacentClips(clip, trip)
    }

    fun toggleContinuousPlayback() {
        playbackCoordinator.toggleContinuousPlayback()
    }

    fun updatePlaybackState(state: VideoPlaybackState) {
        playbackCoordinator.updatePlaybackState(state)
    }

    fun updatePlaybackState(transform: (VideoPlaybackState) -> VideoPlaybackState) {
        playbackCoordinator.updatePlaybackState(transform)
    }

    fun updateUiState(transform: (DashcamUiState) -> DashcamUiState) {
        _uiState.update(transform)
    }

    fun closePlayback() {
        playbackCoordinator.closePlayback()
    }

    fun refreshSystemDiagnostics() {
        systemDiagnosticsManager.refreshSystemDiagnostics()
    }

    fun applyAutoDndIfEnabled() {
        systemDiagnosticsManager.applyAutoDndIfEnabled()
    }

    fun restoreAutoDndIfSet() {
        systemDiagnosticsManager.restoreAutoDndIfSet()
    }

    private var sentryIncidentJob: Job? = null

    // Notification broadcast receiver
    private val emergencyLockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            lockCurrentClip("Notification Quick Lock")
        }
    }

    init {
        // Observe settings changes
        viewModelScope.launch {
            settingsRepo.settings.collect { currentSettings ->
                recordingCoordinator.onSettingsChanged(currentSettings)
                val isSentryMode = currentSettings.currentMode == AppMode.SENTRY || _uiState.value.isSentryArmed
                val targetSec = if (isSentryMode) currentSettings.sentryIncidentDurationSeconds else currentSettings.loopSegmentMinutes * 60
                _uiState.value = _uiState.value.copy(
                    loopSegmentMinutes = currentSettings.loopSegmentMinutes,
                    targetSegmentSeconds = if (!_uiState.value.isRecording) targetSec else _uiState.value.targetSegmentSeconds,
                    hudDisplayMode = currentSettings.hudDisplayMode
                )
                val currentStorageOptions = StorageDirectoryManager.getAvailableStorageOptions(
                    app,
                    currentSettings.customStoragePath,
                    currentSettings.customStorageUri
                )
                systemDiagnosticsManager.updateStorageOptions(currentStorageOptions)
                _cameraProfile.value = CameraHardwareSpecManager.getHardwareCameraProfile(
                    app,
                    currentSettings.frontCameraSelected
                )
                batteryMonitor.maxSafeTempC = currentSettings.maxTempThresholdC
                batteryMonitor.warmThresholdC = currentSettings.thermalAutoDimTempC
                batteryMonitor.lowBatterySafetyLimitPct = currentSettings.lowBatterySafetyLimitPct
                acousticNoiseMonitor.sensitivity = currentSettings.sentryAcousticTriggerSensitivity
                if (_uiState.value.isRecording) {
                    if (currentSettings.autoDndWhileRecording && !systemDiagnosticsManager.hasPreviousInterruptionFilter) {
                        applyAutoDndIfEnabled()
                    } else if (!currentSettings.autoDndWhileRecording && systemDiagnosticsManager.hasPreviousInterruptionFilter) {
                        restoreAutoDndIfSet()
                    }
                }
            }
        }
        // Wire hardware listeners
        viewModelScope.launch {
            sentryController.sentryState.collect { s ->
                _uiState.update {
                    it.copy(
                        isSentryArming = s.isArming,
                        sentryArmingCountdownSeconds = s.countdownSeconds
                    )
                }
            }
        }

        sentryController.onTriggerIncident = { reason ->
            triggerSentryIncidentCapture(reason)
        }

        acousticNoiseMonitor.sensitivity = settings.value.sentryAcousticTriggerSensitivity
        acousticNoiseMonitor.onAcousticAlert = { db ->
            handleAcousticAlert(db)
        }

        sensorMonitor.impactThresholdG = settings.value.impactSensitivity.thresholdG
        sensorMonitor.onImpactDetected = { gValue ->
            handleImpactDetected(gValue)
        }

        powerAndThermalCoordinator.initialize()
        recordingCoordinator.initialize()

        locationTracker.speedUnit = settings.value.speedUnit

        sensorMonitor.startListening()
        batteryMonitor.startListening()
        locationTracker.startTracking()

        // Reactive storage observer: whenever clips are inserted, deleted, or updated
        viewModelScope.launch {
            allClips.collect {
                refreshStorageStats()
            }
        }

        refreshStorageStats()

        // Register broadcast receiver for lock
        val filter = IntentFilter(DashcamForegroundService.ACTION_EMERGENCY_LOCK_TRIGGER)
        androidx.core.content.ContextCompat.registerReceiver(
            app,
            emergencyLockReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                clipDao.healSentryNormalLoopReasons()
            } catch (e: Exception) {
                android.util.Log.w("DashcamViewModel", "Failed to heal sentry trigger reasons: ${e.message}")
            }
        }
    }

    /**
     * Centralized MVI Intent Dispatcher (AGENTS.md Rule 8: Framework State Invariance).
     * Routes all user interactions and state modifications deterministically through a single entry point.
     */
    fun onIntent(intent: DashcamIntent) {
        when (intent) {
            is DashcamIntent.ToggleRecording -> toggleRecording()
            is DashcamIntent.ToggleCurrentClipLock -> toggleCurrentClipLock()
            is DashcamIntent.LockCurrentClip -> lockCurrentClip(intent.reason)
            is DashcamIntent.SetMode -> setMode(intent.mode)
            is DashcamIntent.ToggleOledMode -> toggleOledMode()
            is DashcamIntent.SetOledMode -> setOledMode(intent.enabled)
            is DashcamIntent.ToggleNightExposure -> toggleNightExposureMode()
            is DashcamIntent.SetScreenBrightness -> setScreenBrightnessLevel(intent.level)
            is DashcamIntent.ToggleTorch -> toggleTorch()
            is DashcamIntent.ToggleAudio -> toggleAudio()
            is DashcamIntent.ToggleCameraLens -> toggleCameraLens()
            is DashcamIntent.CycleHudDisplayMode -> cycleHudDisplayMode()
            is DashcamIntent.ArmSentryMode -> armSentryMode(intent.delaySeconds)
            is DashcamIntent.DisarmSentryMode -> disarmSentryMode(intent.isManual)
            is DashcamIntent.CancelSentryArming -> cancelSentryArming()
            is DashcamIntent.ConfirmStartDashcamAfterDisarm -> confirmStartDashcamAfterDisarm()
            is DashcamIntent.DismissStartDashcamPrompt -> dismissStartDashcamPrompt()
            is DashcamIntent.CancelThermalCooling -> cancelThermalCooling()
            is DashcamIntent.CancelUnplugShutdown -> cancelUnplugShutdown()
            is DashcamIntent.CaptureSnapshot -> captureSnapshot()
            is DashcamIntent.ClearIncidentMessage -> clearIncidentMessage()
            is DashcamIntent.DismissSpeedLockout -> dismissSpeedLockoutAlert()
            is DashcamIntent.UpdateSettings -> updateSettings(intent.newSettings)
            is DashcamIntent.MigrateFootageToNewDirectory -> migrateFootageToNewDirectory(intent.targetDir)
            is DashcamIntent.DismissMigrationProgress -> dismissMigrationProgress()
            is DashcamIntent.ResetStorageMigrationState -> resetStorageMigrationState()
            is DashcamIntent.RefreshStorageStats -> refreshStorageStats()
            is DashcamIntent.RefreshSystemDiagnostics -> refreshSystemDiagnostics()
            is DashcamIntent.SetGalleryViewMode -> setGalleryViewMode(intent.mode)
            is DashcamIntent.SetGalleryFilter -> setGalleryFilter(intent.filter)
            is DashcamIntent.ToggleTripExpanded -> toggleTripExpanded(intent.tripId)
            is DashcamIntent.SetTripExpanded -> setTripExpanded(intent.tripId, intent.expanded)
            is DashcamIntent.ResetGalleryNavigationMemory -> resetGalleryNavigationMemory()
            is DashcamIntent.SaveTripCustomTitle -> saveTripCustomTitle(intent.tripId, intent.title)
            is DashcamIntent.DeleteClip -> deleteClip(intent.clip)
            is DashcamIntent.DeleteTrip -> deleteTrip(intent.trip, intent.deleteLockedClips)
            is DashcamIntent.ToggleClipLock -> toggleClipLock(intent.clip)
            is DashcamIntent.LockTripClips -> lockTripClips(intent.trip)
            is DashcamIntent.UnlockTripClips -> unlockTripClips(intent.trip, intent.preserveIncidents)
            is DashcamIntent.OpenClipForPlayback -> openClipForPlayback(intent.clip)
            is DashcamIntent.ClosePlayback -> closePlayback()
            is DashcamIntent.ToggleContinuousPlayback -> toggleContinuousPlayback()
            is DashcamIntent.UpdatePlaybackState -> updatePlaybackState {
                it.copy(
                    currentPositionMs = intent.currentPositionMs,
                    isPlaying = intent.isPlaying
                )
            }
        }
    }

    fun onPermissionsGranted() {
        locationTracker.startTracking()
        sensorMonitor.startListening()
        refreshStorageStats()
    }

    fun onActivityResumed() {
        if (!_uiState.value.isSentryArmed &&
            !_uiState.value.isThermalCoolingActive &&
            !_uiState.value.isLowBatterySleepActive
        ) {
            locationTracker.startTracking()
        }
    }

    fun refreshStorageStats() {
        viewModelScope.launch {
            clipRepository.syncClipsFromDirectory(storageManager.videosDir)
            val stats = storageManager.getStorageStats(settings.value.maxStorageGb)
            _uiState.value = _uiState.value.copy(storageStats = stats)
        }
    }

    suspend fun syncClipsFromStorageDirect() = withContext(Dispatchers.IO) {
        clipRepository.syncClipsFromDirectory(storageManager.videosDir)
        val stats = storageManager.getStorageStats(settings.value.maxStorageGb)
        withContext(Dispatchers.Main) {
            _uiState.value = _uiState.value.copy(storageStats = stats)
        }
    }

    fun setMode(mode: AppMode) {
        settingsRepo.setMode(mode)
        val defaultTargetSeconds = if (mode == AppMode.SENTRY) {
            settings.value.sentryIncidentDurationSeconds
        } else {
            settings.value.loopSegmentMinutes * 60
        }
        if (!_uiState.value.isRecording && !_uiState.value.isRecordingStarting) {
            recordingCoordinator.currentSegmentTargetSeconds = defaultTargetSeconds
            _uiState.update { it.copy(targetSegmentSeconds = defaultTargetSeconds) }
        }
        if (mode == AppMode.DASHCAM &&
            !_uiState.value.isThermalCoolingActive &&
            !_uiState.value.isLowBatterySleepActive
        ) {
            locationTracker.startTracking()
        }
        if (_uiState.value.isRecording) {
            // Update service notification
            DashcamForegroundService.startService(app, mode.name)
        }
    }

    fun toggleOledMode() {
        val current = settings.value.oledPowerSaveMode
        settingsRepo.setOledMode(!current)
    }

    fun setOledMode(enabled: Boolean) {
        settingsRepo.setOledMode(enabled)
    }

    fun toggleNightExposureMode() {
        val next = !_uiState.value.isNightExposureMode
        _uiState.value = _uiState.value.copy(
            isNightExposureMode = next
        )
        showTransientMessage(if (next) "🌙 Night High-Contrast Exposure" else "☀️ Standard Exposure")
        alertManager.vibrateShort()
    }

    fun setScreenBrightnessLevel(level: Float) {
        _uiState.value = _uiState.value.copy(screenBrightnessLevel = level.coerceIn(0.1f, 1.0f))
    }

    fun toggleSentryPulsingRing() {
        _uiState.value = _uiState.value.copy(isSentryPulsingRing = !_uiState.value.isSentryPulsingRing)
    }

    fun isVehicleInMotion(): Boolean {
        return vehicleTelemetryPipeline.isVehicleInMotion()
    }

    fun checkOrShowSpeedLockout(): Boolean {
        if (isVehicleInMotion()) {
            alertPresenter.showSpeedLockoutAlert()
            alertManager.vibrateShort()
            return true
        }
        return false
    }

    fun dismissSpeedLockoutAlert() {
        alertPresenter.dismissSpeedLockoutAlert()
    }

    fun toggleTorch() {
        val newState = !_uiState.value.isTorchOn
        _uiState.value = _uiState.value.copy(isTorchOn = newState)
        alertManager.setTorch(newState)
    }

    fun toggleAudio() {
        settingsRepo.toggleAudio()
    }

    fun toggleCameraLens() {
        if (_uiState.value.isRecording || _uiState.value.isRecordingStarting) {
            showTransientMessage("⚠️ Lens switch deferred until recording ends")
            return
        }
        settingsRepo.toggleCamera()
    }

    fun startRecording(reason: String = "Manual"): Job? = recordingCoordinator.startRecording(reason)

    fun stopRecording(autoArmSentry: Boolean = true) {
        recordingCoordinator.stopRecording(autoArmSentry)
    }

    fun toggleRecording() {
        recordingCoordinator.toggleRecording()
    }

    private fun rotateSegment() {
        recordingCoordinator.rotateSegment()
    }

    fun toggleCurrentClipLock(reason: String = "Manual Lock") {
        recordingCoordinator.toggleCurrentClipLock(reason)
    }

    fun lockCurrentClip(reason: String = "Manual Emergency Lock", vibrate: Boolean = true) {
        recordingCoordinator.lockCurrentClip(reason, vibrate)
    }

    fun handleAcousticAlert(db: Float) {
        if (_uiState.value.isThermalCoolingActive || _uiState.value.isLowBatterySleepActive || _uiState.value.deviceSleepMode != DeviceSleepMode.NONE) return
        if (!_uiState.value.isSentryArmed && settings.value.currentMode != AppMode.SENTRY) return
        
        val isRec = _uiState.value.isRecording || recordingManager.isRecordingActive
        val initialCount = _uiState.value.sentryEventCount

        val accepted = sentryController.dispatchAcousticAlert(
            db = db,
            deterrentMode = settings.value.sentryDeterrentMode,
            autoLock = settings.value.sentryAutoLockEvents,
            isRecording = isRec,
            onShowToast = { msg ->
                showTransientMessage(msg)
                launchGpsBackfill("Sentry Acoustic (${db.toInt()} dB)")
            }
        )

        if (!accepted) return

        _uiState.value = _uiState.value.copy(
            sentryEventCount = if (isRec) initialCount else maxOf(initialCount + 1, sentryController.eventCount),
            isAcousticAlert = true,
            acousticDb = db
        )

        viewModelScope.launch {
            delay(3500)
            _uiState.value = _uiState.value.copy(isAcousticAlert = false)
        }
    }

    private fun triggerSentryIncidentCapture(reason: String) {
        val incidentDuration = settings.value.sentryIncidentDurationSeconds
        val autoLock = settings.value.sentryAutoLockEvents
        if (_uiState.value.isRecording) {
            currentActiveSegment?.let { seg ->
                if (seg.incidentOffsetSeconds < 0) {
                    seg.incidentOffsetSeconds = _uiState.value.segmentSeconds
                    seg.incidentLatitude = telemetry.value.latitude
                    seg.incidentLongitude = telemetry.value.longitude
                }
                seg.isLocked = autoLock
                seg.triggerReason = reason
            }
            val requiredTarget = _uiState.value.segmentSeconds + incidentDuration
            if (requiredTarget > recordingCoordinator.currentSegmentTargetSeconds) {
                recordingCoordinator.currentSegmentTargetSeconds = requiredTarget
                _uiState.value = _uiState.value.copy(targetSegmentSeconds = recordingCoordinator.currentSegmentTargetSeconds)
            }
            if (autoLock) {
                lockCurrentClip(reason, vibrate = false)
            }
        } else {
            startRecording(reason)
            currentActiveSegment?.isLocked = autoLock
            sentryIncidentJob?.cancel()
            sentryIncidentJob = viewModelScope.launch {
                delay(incidentDuration * 1000L)
                if (_uiState.value.isRecording && _uiState.value.isSentryArmed) {
                    stopRecording()
                    if (settings.value.sentryAcousticTriggerSensitivity != Sensitivity.OFF) {
                        acousticNoiseMonitor.startMonitoring()
                    }
                }
            }
        }
    }

    fun handleImpactDetected(gValue: Float) {
        if (settings.value.impactSensitivity == Sensitivity.OFF) return

        val isSentry = _uiState.value.isSentryArmed || settings.value.currentMode == AppMode.SENTRY
        // Do not vibrate the phone or play driving impact beep when parked in Sentry mode
        if (!isSentry) {
            alertManager.vibrateImpactPattern()
            alertManager.playImpactBeep()
        }

        currentActiveSegment?.let { seg ->
            seg.incidentGForce = maxOf(seg.incidentGForce, gValue)
        }
        _uiState.value = _uiState.value.copy(
            isSegmentLocked = true,
            lastTriggerReason = "Impact Shock (${"%.1f".format(gValue)}G)"
        )
        alertPresenter.showImpactAlert(gValue = gValue, isSentry = isSentry, autoDismissMs = 4000L)
        showTransientMessage("💥 G-Force Shock Detected (${"%.1f".format(gValue)}G) - Incident Protected")

        val isDeviceInSleep = _uiState.value.isThermalCoolingActive ||
                _uiState.value.isLowBatterySleepActive ||
                _uiState.value.deviceSleepMode != DeviceSleepMode.NONE

        if (isDeviceInSleep) {
            val shockDuration = settings.value.sleepEmergencyClipDurationSeconds
            val job = startRecording("Emergency Shock (Low-Power Sleep)")
            if (job == null) {
                _uiState.update { it.copy(isSegmentLocked = false) }
            }
            launchGpsBackfill("Emergency Shock (Low-Power Sleep)")
            viewModelScope.launch {
                delay(shockDuration * 1000L)
                val stillSleeping = _uiState.value.isThermalCoolingActive ||
                        _uiState.value.isLowBatterySleepActive ||
                        _uiState.value.deviceSleepMode != DeviceSleepMode.NONE
                if (_uiState.value.isRecording && stillSleeping) {
                    stopRecording()
                    showTransientMessage("❄️ Sleep Resumed: Incident clip (${shockDuration}s) saved. Low-power mode active.")
                }
            }
        } else if (_uiState.value.isSentryArmed) {
            if (!_uiState.value.isRecording) {
                sentryController.incrementEventCount()
                _uiState.value = _uiState.value.copy(
                    sentryEventCount = sentryController.eventCount
                )
                acousticNoiseMonitor.stopAndRelease()
                alertManager.triggerDeterrent(settings.value.sentryDeterrentMode)
                launchGpsBackfill("Sentry Impact Shock")
            } else {
                currentActiveSegment?.let { seg ->
                    seg.incidentGForce = maxOf(seg.incidentGForce, gValue)
                }
            }
            triggerSentryIncidentCapture("Impact Shock (${"%.1f".format(gValue)}G)")
        } else if (!_uiState.value.isRecording) {
            val job = startRecording("Impact Shock (${"%.1f".format(gValue)}G)")
            if (job == null) {
                _uiState.update { it.copy(isSegmentLocked = false) }
            }
        } else {
            currentActiveSegment?.let { seg ->
                if (seg.incidentOffsetSeconds < 0) {
                    seg.incidentOffsetSeconds = _uiState.value.segmentSeconds
                    seg.incidentLatitude = telemetry.value.latitude
                    seg.incidentLongitude = telemetry.value.longitude
                }
                seg.incidentGForce = maxOf(seg.incidentGForce, gValue)
                seg.isLocked = true
                seg.triggerReason = "Impact Shock (${"%.1f".format(gValue)}G)"
            }
            recordingCoordinator.guaranteePostIncidentBuffer(_uiState.value.segmentSeconds)
            lockCurrentClip("Impact Shock (${"%.1f".format(gValue)}G)")
        }
    }

    fun handleMotionDetected(score: Float, avgLuma: Float) {
        if (_uiState.value.isThermalCoolingActive || _uiState.value.isLowBatterySleepActive || _uiState.value.deviceSleepMode != DeviceSleepMode.NONE) return
        if (settings.value.currentMode != AppMode.SENTRY && !_uiState.value.isSentryArmed) return
        if (settings.value.motionSensitivity == Sensitivity.OFF) return

        val curBat = batteryMonitor.batteryState.value
        val demoteBat = settings.value.sentryAutoDemoteBatteryPct
        val demoteTemp = settings.value.sentryAutoDemoteTempC
        if (sentryController.evaluateAutoDemotion(
                batteryLevelPct = curBat.levelPct,
                batteryTempC = curBat.temperatureC,
                demoteBatteryThresholdPct = demoteBat,
                demoteTempThresholdC = demoteTemp,
                currentGuardType = settings.value.sentryGuardType,
                autoDemoteEnabled = settings.value.sentryAutoDemoteEnabled
            )
        ) {
            settingsRepo.setSentryGuardType(SentryGuardType.SHOCK_STANDBY)
            showTransientMessage("⚠️ Thermal/Battery Limit: Sentry demoted to Tier 1 Shock Standby")
            return
        }

        val isRec = _uiState.value.isRecording || recordingManager.isRecordingActive
        val initialCount = _uiState.value.sentryEventCount

        val accepted = sentryController.dispatchMotionAlert(
            score = score,
            deterrentMode = settings.value.sentryDeterrentMode,
            autoLock = settings.value.sentryAutoLockEvents,
            isRecording = isRec,
            onShowToast = { msg ->
                showTransientMessage(msg)
                launchGpsBackfill("Motion Sentry Trigger")
            }
        )

        if (!accepted) return

        _uiState.value = _uiState.value.copy(
            isMotionTriggered = true,
            motionScore = score,
            sceneLuminance = avgLuma,
            sentryEventCount = if (isRec) initialCount else maxOf(initialCount + 1, sentryController.eventCount)
        )

        viewModelScope.launch {
            delay(3500)
            _uiState.value = _uiState.value.copy(isMotionTriggered = false)
        }
    }

    fun armSentryMode(delaySeconds: Int? = null) {
        if (!settings.value.sentryModeEnabled || _uiState.value.isThermalCoolingActive || _uiState.value.isLowBatterySleepActive || _uiState.value.deviceSleepMode != DeviceSleepMode.NONE) return
        val delaySec = delaySeconds ?: settings.value.sentryArmingDelaySeconds
        if (delaySec > 0) {
            _uiState.value = _uiState.value.copy(
                isSentryArming = true,
                sentryArmingCountdownSeconds = delaySec,
                sentryEventCount = 0
            )
            sentryController.arm(viewModelScope, delaySec, settings.value.sentryVoiceAlertsEnabled) {
                engageSentryArmed()
            }
        } else {
            sentryController.arm(viewModelScope, 0, settings.value.sentryVoiceAlertsEnabled) {
                engageSentryArmed()
            }
        }
    }

    private fun engageSentryArmed() {
        if (_uiState.value.isThermalCoolingActive || _uiState.value.isLowBatterySleepActive || _uiState.value.deviceSleepMode != DeviceSleepMode.NONE) return
        if (_uiState.value.isRecording) {
            stopRecording()
        }
        setMode(AppMode.SENTRY)
        locationTracker.stopTracking()
        recordingCoordinator.currentSegmentTargetSeconds = settings.value.sentryIncidentDurationSeconds
        _uiState.value = _uiState.value.copy(
            isSentryArmed = true,
            isSentryArming = false,
            sentryArmingCountdownSeconds = 0,
            isScreenDimmed = true,
            sentryEventCount = 0,
            targetSegmentSeconds = settings.value.sentryIncidentDurationSeconds
        )
        alertManager.speakSentryActive(settings.value.sentryVoiceAlertsEnabled)
        showTransientMessage("🛡️ Sentry Active (Guard Armed)")

        if (settings.value.sentryAcousticTriggerSensitivity != Sensitivity.OFF) {
            acousticNoiseMonitor.sensitivity = settings.value.sentryAcousticTriggerSensitivity
            acousticNoiseMonitor.startMonitoring()
        }
    }

    fun disarmSentryMode(isManual: Boolean = true) {
        val wasActive = _uiState.value.isSentryArmed || _uiState.value.isSentryArming || settings.value.currentMode == AppMode.SENTRY
        val wasRecording = _uiState.value.isRecording || _uiState.value.isRecordingStarting || recordingManager.isRecordingActive
        sentryIncidentJob?.cancel()
        sentryIncidentJob = null
        val incidents = maxOf(_uiState.value.sentryEventCount, sentryController.eventCount)
        sentryController.disarm(settings.value.sentryVoiceAlertsEnabled, incidentCount = incidents)
        _uiState.value = _uiState.value.copy(
            isSentryArmed = false,
            isSentryArming = false,
            sentryArmingCountdownSeconds = 0,
            sentryEventCount = 0
        )
        if (wasRecording) {
            currentActiveSegment?.let { seg ->
                seg.triggerReason = "Sentry (Interrupted by Power On)"
                seg.isLocked = true
                seg.isSentry = true
            }
            _uiState.value = _uiState.value.copy(
                lastTriggerReason = "Sentry (Interrupted by Power On)",
                isSegmentLocked = true
            )
            stopRecording(autoArmSentry = false)
        }
        if (wasActive) {
            val msg = if (incidents > 0) "🛡️ Sentry Disarmed ($incidents incident${if (incidents == 1) "" else "s"} recorded)" else "🛡️ Sentry Disarmed"
            showTransientMessage(msg)
        }
        locationTracker.startTracking()
        setScreenDimmed(false)
        setMode(AppMode.DASHCAM)

        if (sentryController.shouldPromptDashcamOnDisarm(isManual, batteryMonitor.batteryState.value.isPlugged)) {
            _uiState.update { it.copy(showStartDashcamPromptOnDisarm = true) }
        }
    }

    fun confirmStartDashcamAfterDisarm() {
        _uiState.update { it.copy(showStartDashcamPromptOnDisarm = false) }
        startRecording("Manual")
    }

    fun dismissStartDashcamPrompt() {
        _uiState.update { it.copy(showStartDashcamPromptOnDisarm = false) }
    }

    fun cancelSentryArming() {
        sentryController.cancelArming()
        _uiState.value = _uiState.value.copy(
            isSentryArming = false,
            sentryArmingCountdownSeconds = 0
        )
        setMode(AppMode.DASHCAM)
        locationTracker.startTracking()
        showTransientMessage("🛡️ Sentry Arming Cancelled")
    }

    fun cancelThermalCooling() {
        powerAndThermalCoordinator.cancelThermalCooling()
    }

    private suspend fun handleSentryOwnerReturnInterruption() {
        recordingCoordinator.handleSentryOwnerReturnInterruption()
    }

    private fun launchGpsBackfill(reason: String) {
        recordingCoordinator.launchGpsBackfill(reason)
    }

    fun cycleHudDisplayMode() {
        val nextMode = when (_uiState.value.hudDisplayMode) {
            HudDisplayMode.FULL -> HudDisplayMode.COMPACT
            HudDisplayMode.COMPACT -> HudDisplayMode.FULL
        }
        _uiState.value = _uiState.value.copy(hudDisplayMode = nextMode)
        settingsRepo.setHudDisplayMode(nextMode)
    }

    fun resetTripStats() {
        _uiState.value = _uiState.value.copy(
            tripStats = TripStats()
        )
    }

    fun setScreenDimmed(dimmed: Boolean) {
        if (_uiState.value.isScreenDimmed != dimmed) {
            _uiState.value = _uiState.value.copy(isScreenDimmed = dimmed)
        }
    }

    fun captureSnapshot() {
        alertManager.vibrateShort()
        alertManager.playChime()
        val snapFile = storageManager.createNewSnapshotFile()
        _uiState.update {
            it.copy(lastSnapshotPath = snapFile.absolutePath)
        }
        alertPresenter.triggerSnapshotFlash()
        showTransientMessage("📸 Snapshot Captured")
    }

    fun showTransientMessage(message: String) {
        alertPresenter.showTransientMessage(message)
    }

    fun clearIncidentMessage() {
        alertPresenter.clearIncidentMessage()
    }

    private fun startUnplugCountdown(seconds: Int) {
        powerAndThermalCoordinator.startUnplugCountdown(seconds)
    }

    fun cancelUnplugShutdown() {
        powerAndThermalCoordinator.cancelUnplugShutdown()
    }

    fun cancelUnplugCountdown() = cancelUnplugShutdown()

    fun dismissImpactAlert() {
        alertPresenter.dismissImpactAlert()
    }

    fun updateSettings(newSettings: DashGuardSettings) {
        settingsRepo.updateSettings(newSettings)
        val applied = settingsRepo.settings.value
        sensorMonitor.impactThresholdG = applied.impactSensitivity.thresholdG
        batteryMonitor.maxSafeTempC = applied.maxTempThresholdC
        batteryMonitor.warmThresholdC = applied.thermalAutoDimTempC
        batteryMonitor.lowBatterySafetyLimitPct = applied.lowBatterySafetyLimitPct
        acousticNoiseMonitor.sensitivity = applied.sentryAcousticTriggerSensitivity
        locationTracker.speedUnit = applied.speedUnit
        refreshStorageStats()
    }

    fun getActiveVideosDirectoryPath(): String {
        return storageManager.videosDir.absolutePath
    }

    fun getClipsInActiveStorage(): List<VideoClip> {
        return StorageMigrationManager.getClipsInDirectory(allClips.value, storageManager.videosDir)
    }

    fun getClipsOutsideActiveStorage(): List<VideoClip> {
        return StorageMigrationManager.getClipsOutsideDirectory(allClips.value, storageManager.videosDir)
    }

    fun getAllRecordedClips(): List<VideoClip> {
        return allClips.value
    }

    fun migrateFootageToNewDirectory(
        targetDir: File,
        clipsToMigrate: List<VideoClip> = getClipsInActiveStorage(),
        onComplete: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            StorageMigrationManager.executeMigration(
                clipsToMigrate = clipsToMigrate,
                targetDir = targetDir,
                clipDao = clipDao,
                onComplete = onComplete
            )
            withContext(Dispatchers.Main) {
                refreshStorageStats()
                refreshSystemDiagnostics()
            }
        }
    }

    fun dismissMigrationProgress() {
        StorageMigrationManager.dismissMigrationProgress()
    }

    fun resetStorageMigrationState() = dismissMigrationProgress()

    fun updateStorageLocation(locationType: StorageLocationType, customPath: String? = null, customUri: String? = null) {
        val updated = settings.value.copy(
            storageLocationType = locationType,
            customStoragePath = customPath ?: settings.value.customStoragePath,
            customStorageUri = customUri ?: settings.value.customStorageUri
        )
        updateSettings(updated)
        refreshSystemDiagnostics()
        refreshStorageStats()
    }

    fun saveTripCustomTitle(tripId: String, title: String) {
        clipRepository.saveTripCustomTitle(tripId, title)
    }

    fun deleteClip(clip: VideoClip) {
        if (clip.isLocked) {
            // Strict Invariant: Locked clips cannot be deleted manually by the user!
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            storageManager.deleteClipAndSidecars(clip)
            refreshStorageStats()
        }
    }

    fun deleteTrip(trip: TripSession, deleteLockedClips: Boolean): Job {
        return viewModelScope.launch {
            var deletedCount = 0
            withContext(Dispatchers.IO) {
                trip.clips.forEach { clip ->
                    val canDelete = deleteLockedClips || !clip.isLocked
                    if (canDelete) {
                        try {
                            if (storageManager.deleteClipAndSidecars(clip, forceDeleteLocked = deleteLockedClips)) {
                                deletedCount++
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("DashcamViewModel", "Failed to delete trip clip: ${clip.filePath}", e)
                        }
                    }
                }
            }
            showTransientMessage("🗑️ Deleted $deletedCount loop(s) from Trip")
            alertManager.vibrateShort()
            refreshStorageStats()
        }
    }

    fun toggleClipLock(clip: VideoClip) {
        viewModelScope.launch {
            clipRepository.toggleClipLock(clip)
            refreshStorageStats()
        }
    }

    fun lockTripClips(trip: TripSession): Job {
        return viewModelScope.launch {
            val lockedCount = clipRepository.lockTripClips(trip)
            showTransientMessage("🔒 Trip Protected ($lockedCount loops locked)")
            alertManager.vibrateImpactPattern()
            alertManager.playChime()
            refreshStorageStats()
        }
    }

    fun unlockTripClips(trip: TripSession, preserveIncidents: Boolean): Job {
        return viewModelScope.launch {
            val unlockedCount = clipRepository.unlockTripClips(trip, preserveIncidents)
            showTransientMessage("🔓 Unlocked $unlockedCount loop(s) in Trip")
            alertManager.vibrateShort()
            refreshStorageStats()
        }
    }

    fun registerFusedClip(sourceClip: VideoClip, fusedFile: File) {
        viewModelScope.launch {
            clipRepository.registerFusedClip(sourceClip, fusedFile)
            refreshStorageStats()
        }
    }

    fun syncClipsFromStorage() {
        viewModelScope.launch {
            clipRepository.syncClipsFromDirectory(storageManager.videosDir)
            val stats = storageManager.getStorageStats(settings.value.maxStorageGb)
            _uiState.value = _uiState.value.copy(storageStats = stats)
        }
    }

    fun syncFusedClips() {
        viewModelScope.launch {
            clipRepository.syncClipsFromDirectory(storageManager.videosDir)
        }
    }

    override fun onCleared() {
        super.onCleared()
        alertPresenter.resetAllAlerts()
        powerAndThermalCoordinator.release()
        recordingCoordinator.release()
        restoreAutoDndIfSet()
        try {
            app.unregisterReceiver(emergencyLockReceiver)
        } catch (_: Exception) {}
        sensorMonitor.stopListening()
        batteryMonitor.stopListening()
        locationTracker.stopTracking()
        acousticNoiseMonitor.stopAndRelease()
        sentryIncidentJob?.cancel()
    }
}
