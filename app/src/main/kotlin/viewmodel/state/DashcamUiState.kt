package io.github.roaxio.dashguardpro.viewmodel.state

import io.github.roaxio.dashguardpro.data.settings.HudDisplayMode
import io.github.roaxio.dashguardpro.storage.StorageStats

enum class RecordingOriginState {
    STANDBY,
    DRIVING_LOOP,
    SENTRY
}

data class TripStats(
    val driveSeconds: Long = 0L,
    val distanceKm: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val avgSpeedKmh: Float = 0f,
    val lockedIncidentsCount: Int = 0
)

data class DashcamUiState(
    val isRecording: Boolean = false,
    val isRecordingStarting: Boolean = false,
    
    val segmentSeconds: Int = 0,
    val loopSegmentMinutes: Int = 3,
    val targetSegmentSeconds: Int = 180,
    val totalSessionMinutes: Int = 0,
    val isSegmentLocked: Boolean = false,
    val lastTriggerReason: String = "Normal Loop",
    val isTorchOn: Boolean = false,
    val isNightExposureMode: Boolean = false,
    val isMotionTriggered: Boolean = false,
    val motionScore: Float = 0f,
    val sceneLuminance: Float = 128f,
    val showImpactAlert: Boolean = false,
    val impactG: Float = 0f,
    val unplugCountdownSeconds: Int? = null,
    val lastSnapshotPath: String? = null,
    val isSnapshotFlashActive: Boolean = false,
    val recentIncidentMessage: String? = null,
    val hudDisplayMode: HudDisplayMode = HudDisplayMode.COMPACT,
    val tripStats: TripStats = TripStats(),
    val storageStats: StorageStats = StorageStats(),
    val screenBrightnessLevel: Float = 1.0f,
    val isSentryPulsingRing: Boolean = true,
    val isSpeedLockoutAlertVisible: Boolean = false,
    val isScreenDimmed: Boolean = false,
    val isSentryArming: Boolean = false,
    val sentryArmingCountdownSeconds: Int = 0,
    val isSentryArmed: Boolean = false,
    val sentryEventCount: Int = 0,
    val deviceSleepMode: DeviceSleepMode = DeviceSleepMode.NONE,
    val preSleepOrigin: PreSleepOperatingState = PreSleepOperatingState.STANDBY,
    val thermalCoolingTempC: Float = 0f,
    val thermalCoolingTargetC: Float = 44f,
    val isAcousticAlert: Boolean = false,
    val acousticDb: Float = 0f,
    val showStartDashcamPromptOnDisarm: Boolean = false
) {
    val isThermalCoolingActive: Boolean
        get() = deviceSleepMode == DeviceSleepMode.THERMAL_COOLING

    val isLowBatterySleepActive: Boolean
        get() = deviceSleepMode == DeviceSleepMode.LOW_BATTERY_STANDBY

    val isSleeping: Boolean
        get() = deviceSleepMode != DeviceSleepMode.NONE
}
