package io.github.roaxio.dashguardpro.data.settings

import androidx.camera.core.AspectRatio

enum class AppMode {
    DASHCAM,
    SENTRY
}

enum class VideoAspectRatio(val title: String, val ratioValue: Int) {
    RATIO_16_9("16:9 (Standard Widescreen)", AspectRatio.RATIO_16_9),
    RATIO_4_3("4:3 (Taller Field of View)", AspectRatio.RATIO_4_3)
}

enum class VideoQuality(val title: String, val width: Int, val height: Int) {
    SD_480P("480p SD (Storage Saver)", 720, 480),
    HD_720P("720p HD (Balanced)", 1280, 720),
    FHD_1080P("1080p FHD (High Detail)", 1920, 1080),
    UHD_4K("4K UHD (Ultra Detail)", 3840, 2160)
}

enum class SpeedUnit(val label: String, val multiplier: Float) {
    KMH("km/h", 3.6f),
    MPH("mph", 2.23694f)
}

enum class Sensitivity(
    val label: String,
    val thresholdG: Float,
    val motionDiffThreshold: Float,
    val motionPixelDelta: Int = 20,
    val motionMinClusterNeighbors: Int = 2
) {
    OFF("Disabled", 999f, 999f, 999, 999),
    VERY_LOW("Minimal (Wind / Busy Area)", 4.5f, 35f, 32, 3),
    LOW("Low (Outdoor Street)", 3.5f, 25f, 26, 2),
    MEDIUM("Medium (Standard / Driveway)", 2.4f, 15f, 20, 2),
    HIGH("High (Enclosed Garage)", 1.6f, 8f, 14, 1),
    VERY_HIGH("Highest (Ultra-Sensitive / Isolated)", 1.2f, 4f, 10, 1)
}

enum class StorageLocationType(val label: String, val shortLabel: String, val pathHint: String) {
    DEFAULT_APP_DATA("App Data Directory (Default)", "App Storage", "Standard isolated app data storage"),
    CUSTOM_DIRECTORY("Custom Directory", "Custom Storage", "User-selected folder on phone or SD card")
}

enum class AutoDimTimeout(val title: String, val seconds: Int) {
    DISABLED("Disabled", 0),
    SEC_5("5 seconds", 5),
    SEC_10("10 seconds", 10),
    SEC_15("15 seconds", 15),
    SEC_30("30 seconds", 30)
}

enum class HudDisplayMode {
    FULL,
    COMPACT
}

enum class SentryArmingTrigger(val label: String, val description: String) {
    ON_POWER_LOSS("On Car Power Loss", "Auto-arm when vehicle charger disconnects"),
    ON_RECORDING_STOP("On Recording Stop", "Auto-arm whenever dashcam recording stops"),
    MANUAL_ONLY("Manual Only", "Arm only when manually switched to Sentry Mode")
}

enum class SentryGuardType(val label: String, val description: String) {
    SHOCK_STANDBY("Tier 1: Shock & Sound Standby (Ultra Low Power)", "Camera closed; wakes on physical impact or acoustic spike (zero camera power, maximum battery life)"),
    OPTICAL_MOTION("Tier 2: Hybrid (Optical Motion + Auto-Demote)", "4 FPS motion analysis; automatically demotes to Tier 1 when battery or thermal threshold is breached")
}

enum class SentryInterruptedClipAction(val label: String, val description: String) {
    KEEP_WITH_BADGE("Keep Clip & Badge", "Keep recording and label as [Owner Return] in Gallery"),
    AUTO_DELETE("Auto-Delete Clip", "Discard recording triggered by owner re-entering the vehicle")
}

enum class SentryDeterrentMode(val label: String) {
    NONE("None (Silent Guard)"),
    TORCH_STROBE("Camera LED Strobe"),
    SIREN_CHIME("Audible Warning Tone"),
    BOTH("Strobe + Warning Tone")
}

data class DashGuardSettings(
    val currentMode: AppMode = AppMode.DASHCAM,
    val hudDisplayMode: HudDisplayMode = HudDisplayMode.COMPACT,
    val loopSegmentMinutes: Int = 3, // 1, 3, 5 mins
    val maxStorageGb: Int = 10, // 2, 5, 10, 20 GB
    val videoQuality: VideoQuality = VideoQuality.HD_720P,
    val videoAspectRatio: VideoAspectRatio = VideoAspectRatio.RATIO_16_9,
    val targetFps: Int = 30,
    val debugAllowPortraitRecording: Boolean = false,
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val impactSensitivity: Sensitivity = Sensitivity.MEDIUM,
    val motionSensitivity: Sensitivity = Sensitivity.MEDIUM,
    val autoRecordOnPower: Boolean = true,
    val autoStopOnUnplugSeconds: Int = 15,
    val disableShutdownOnIncident: Boolean = true,
    val audioRecordingEnabled: Boolean = true,
    val sentrySirenEnabled: Boolean = false,
    val sentryFlashlightAlert: Boolean = true,
    val oledPowerSaveMode: Boolean = false,
    val autoDimTimeout: AutoDimTimeout = AutoDimTimeout.SEC_15,
    val showWatermark: Boolean = true,
    val lowBatterySafetyLimitPct: Int = 15,
    val maxTempThresholdC: Float = 48.0f,
    val thermalAutoDimIsAuto: Boolean = true, // Whether warm auto-dim warning temp is automatically derived from max temp cutoff
    val thermalRecoveryIsAuto: Boolean = true, // Whether cool-off recovery target is automatically derived from max temp cutoff
    val thermalAutoDimTempC: Float = 41.0f, // Temperature threshold (°C) for warm auto-dim warning
    val thermalRecoveryTargetTempC: Float = 44.0f, // Temperature target (°C) to resume recording after thermal cooling
    val frontCameraSelected: Boolean = false,
    val incidentBufferSeconds: Int = 60, // 15, 30, 45, 60 seconds post-incident guarantee
    val storageLocationType: StorageLocationType = StorageLocationType.DEFAULT_APP_DATA,
    val customStoragePath: String = "",
    val customStorageUri: String = "",
    val tripGroupingGapMinutes: Int = 3, // Inactivity gap (minutes) between loops before splitting into new Trip (supports 1, 2, 3, 5, 10 or custom value)
    val loopAdjacencyThresholdSeconds: Int = 30, // Max gap (seconds) between loops for immediate Next/Prev playback
    val autoAdvancePlayback: Boolean = true, // Automatically play next adjacent loop on completion
    val autoDndWhileRecording: Boolean = false, // Automatically engage Do Not Disturb / Priority mode during active recording
    val oemOptimizationCompleted: Boolean = false, // User-verified completion of OEM background killer / autostart settings
    val minTripDurationMinutes: Int = 10, // Minimum duration (minutes) for a loop cluster to qualify as a Trip Session (0 = disabled)
    val includeGapsInTripDuration: Boolean = false, // Whether to include inactivity gaps between loops in trip duration calculation
    val sentryModeEnabled: Boolean = false,
    val sentryArmingTrigger: SentryArmingTrigger = SentryArmingTrigger.ON_POWER_LOSS,
    val sentryGuardType: SentryGuardType = SentryGuardType.SHOCK_STANDBY,
    val sentryConfiguredGuardType: SentryGuardType = SentryGuardType.SHOCK_STANDBY,
    val sentryAutoDemoteEnabled: Boolean = true,
    val sentryAcousticTriggerSensitivity: Sensitivity = Sensitivity.MEDIUM,
    val sentryArmingDelaySeconds: Int = 60,
    val sentryIncidentDurationSeconds: Int = 45,
    val sentryDeterrentMode: SentryDeterrentMode = SentryDeterrentMode.NONE,
    val sentryInterruptedClipAction: SentryInterruptedClipAction = SentryInterruptedClipAction.KEEP_WITH_BADGE,
    val sentryVoiceAlertsEnabled: Boolean = true,
    val sentryAutoDisarmOnPower: Boolean = true,
    val sentryAutoLockEvents: Boolean = true, // Whether Sentry motion and acoustic incident clips are automatically locked
    val midDriveThermalRecoveryEnabled: Boolean = true,
    val sentryAutoDemoteBatteryPct: Int = 30, // Battery floor (%) to demote Tier 2 to Tier 1 standby (15-50%)
    val sentryAutoDemoteTempIsAuto: Boolean = true, // Whether Sentry Tier 2 demotion temp is automatically derived (maxTemp - 4°C)
    val sentryAutoDemoteTempC: Float = 44.0f, // Temperature limit (°C) to demote Tier 2 to Tier 1 standby (30-46°C)
    val sentryMotionPersistenceFrames: Int = 2, // Consecutive positive frames required to confirm motion at 4 FPS (1 = 250ms, 2 = 500ms, 3 = 750ms, 4 = 1.0s, 5 = 1.25s)
    val sleepEmergencyClipDurationSeconds: Int = 30 // Duration (seconds) of emergency shock clip recorded while in low-power sleep
)
