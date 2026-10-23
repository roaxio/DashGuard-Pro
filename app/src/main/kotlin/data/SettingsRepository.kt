package io.github.roaxio.dashguardpro.data

import android.content.Context
import android.content.SharedPreferences
import io.github.roaxio.dashguardpro.data.settings.AppMode
import io.github.roaxio.dashguardpro.data.settings.AutoDimTimeout
import io.github.roaxio.dashguardpro.data.settings.DashGuardSettings
import io.github.roaxio.dashguardpro.data.settings.HudDisplayMode
import io.github.roaxio.dashguardpro.data.settings.Sensitivity
import io.github.roaxio.dashguardpro.data.settings.SentryArmingTrigger
import io.github.roaxio.dashguardpro.data.settings.SentryDeterrentMode
import io.github.roaxio.dashguardpro.data.settings.SentryGuardType
import io.github.roaxio.dashguardpro.data.settings.SentryInterruptedClipAction
import io.github.roaxio.dashguardpro.data.settings.SpeedUnit
import io.github.roaxio.dashguardpro.data.settings.StorageLocationType
import io.github.roaxio.dashguardpro.data.settings.VideoAspectRatio
import io.github.roaxio.dashguardpro.data.settings.VideoQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("dashguard_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<DashGuardSettings> = _settings.asStateFlow()

    private fun loadSettings(): DashGuardSettings {
        val modeStr = prefs.getString("current_mode", AppMode.DASHCAM.name) ?: AppMode.DASHCAM.name
        val qualityStr = prefs.getString("video_quality", VideoQuality.HD_720P.name) ?: VideoQuality.HD_720P.name
        val aspectStr = prefs.getString("video_aspect_ratio", VideoAspectRatio.RATIO_16_9.name) ?: VideoAspectRatio.RATIO_16_9.name
        val speedStr = prefs.getString("speed_unit", SpeedUnit.KMH.name) ?: SpeedUnit.KMH.name
        val impactStr = prefs.getString("impact_sensitivity", Sensitivity.MEDIUM.name) ?: Sensitivity.MEDIUM.name
        val motionStr = prefs.getString("motion_sensitivity", Sensitivity.MEDIUM.name) ?: Sensitivity.MEDIUM.name

        val storageLocStr = prefs.getString("storage_location_type", StorageLocationType.DEFAULT_APP_DATA.name) ?: StorageLocationType.DEFAULT_APP_DATA.name

        val resolvedStorageType = try {
            StorageLocationType.valueOf(storageLocStr)
        } catch (_: Exception) {
            if (storageLocStr == "CUSTOM_FOLDER") StorageLocationType.CUSTOM_DIRECTORY else StorageLocationType.DEFAULT_APP_DATA
        }

        val autoDimStr = prefs.getString("auto_dim_timeout", AutoDimTimeout.SEC_15.name) ?: AutoDimTimeout.SEC_15.name
        val hudModeStr = prefs.getString("hud_display_mode", HudDisplayMode.COMPACT.name) ?: HudDisplayMode.COMPACT.name
        val sentryArmingStr = prefs.getString("sentry_arming_trigger", SentryArmingTrigger.ON_POWER_LOSS.name) ?: SentryArmingTrigger.ON_POWER_LOSS.name
        val sentryGuardStr = prefs.getString("sentry_guard_type", SentryGuardType.SHOCK_STANDBY.name) ?: SentryGuardType.SHOCK_STANDBY.name
        val sentryConfiguredStr = prefs.getString("sentry_configured_guard_type", null)
        val resolvedConfiguredGuardType = if (sentryConfiguredStr != null) {
            try { SentryGuardType.valueOf(sentryConfiguredStr) } catch (_: Exception) { SentryGuardType.SHOCK_STANDBY }
        } else {
            try { SentryGuardType.valueOf(sentryGuardStr) } catch (_: Exception) { SentryGuardType.SHOCK_STANDBY }
        }
        val sentryAcousticStr = prefs.getString("sentry_acoustic_sensitivity", Sensitivity.MEDIUM.name) ?: Sensitivity.MEDIUM.name
        val sentryDeterrentStr = prefs.getString("sentry_deterrent_mode", SentryDeterrentMode.NONE.name) ?: SentryDeterrentMode.NONE.name
        val sentryInterruptedStr = prefs.getString("sentry_interrupted_action", SentryInterruptedClipAction.KEEP_WITH_BADGE.name) ?: SentryInterruptedClipAction.KEEP_WITH_BADGE.name

        return DashGuardSettings(
            currentMode = try { AppMode.valueOf(modeStr) } catch (_: Exception) { AppMode.DASHCAM },
            hudDisplayMode = try { HudDisplayMode.valueOf(hudModeStr) } catch (_: Exception) { HudDisplayMode.COMPACT },
            loopSegmentMinutes = prefs.getInt("loop_segment_minutes", 3),
            maxStorageGb = prefs.getInt("max_storage_gb", 10),
            videoQuality = try { VideoQuality.valueOf(qualityStr) } catch (_: Exception) { VideoQuality.HD_720P },
            videoAspectRatio = try { VideoAspectRatio.valueOf(aspectStr) } catch (_: Exception) { VideoAspectRatio.RATIO_16_9 },
            targetFps = prefs.getInt("target_fps", 30),
            debugAllowPortraitRecording = prefs.getBoolean("debug_allow_portrait_recording", false),
            speedUnit = try { SpeedUnit.valueOf(speedStr) } catch (_: Exception) { SpeedUnit.KMH },
            impactSensitivity = try { Sensitivity.valueOf(impactStr) } catch (_: Exception) { Sensitivity.MEDIUM },
            motionSensitivity = try { Sensitivity.valueOf(motionStr) } catch (_: Exception) { Sensitivity.MEDIUM },
            autoRecordOnPower = prefs.getBoolean("auto_record_on_power", true),
            autoStopOnUnplugSeconds = prefs.getInt("auto_stop_unplug_seconds", 15),
            disableShutdownOnIncident = prefs.getBoolean("disable_shutdown_on_incident", true),
            audioRecordingEnabled = prefs.getBoolean("audio_recording", true),
            sentrySirenEnabled = prefs.getBoolean("sentry_siren", false),
            sentryFlashlightAlert = prefs.getBoolean("sentry_flash", true),
            oledPowerSaveMode = prefs.getBoolean("oled_power_save", false),
            autoDimTimeout = try { AutoDimTimeout.valueOf(autoDimStr) } catch (_: Exception) { AutoDimTimeout.SEC_15 },
            showWatermark = prefs.getBoolean("show_watermark", true),
            lowBatterySafetyLimitPct = prefs.getInt("low_battery_limit", 15),
            maxTempThresholdC = prefs.getFloat("max_temp_c", 48.0f),
            thermalAutoDimIsAuto = prefs.getBoolean("thermal_auto_dim_is_auto", true),
            thermalRecoveryIsAuto = prefs.getBoolean("thermal_recovery_is_auto", true),
            thermalAutoDimTempC = prefs.getFloat("thermal_auto_dim_temp_c", 41.0f),
            thermalRecoveryTargetTempC = prefs.getFloat("thermal_recovery_target_temp_c", 44.0f),
            frontCameraSelected = prefs.getBoolean("front_cam", false),
            incidentBufferSeconds = prefs.getInt("incident_buffer_seconds", 60),
            storageLocationType = resolvedStorageType,
            customStoragePath = prefs.getString("custom_storage_path", "") ?: "",
            customStorageUri = prefs.getString("custom_storage_uri", "") ?: "",
            tripGroupingGapMinutes = prefs.getInt("trip_grouping_gap_minutes", 3),
            loopAdjacencyThresholdSeconds = prefs.getInt("loop_adjacency_threshold_seconds", 30),
            autoAdvancePlayback = prefs.getBoolean("auto_advance_playback", true),
            autoDndWhileRecording = prefs.getBoolean("auto_dnd_while_recording", false),
            oemOptimizationCompleted = prefs.getBoolean("oem_opt_completed", false),
            minTripDurationMinutes = prefs.getInt("min_trip_duration_minutes", 10),
            includeGapsInTripDuration = prefs.getBoolean("include_gaps_in_trip_duration", false),
            sentryModeEnabled = prefs.getBoolean("sentry_mode_enabled", false),
            sentryArmingTrigger = try { SentryArmingTrigger.valueOf(sentryArmingStr) } catch (_: Exception) { SentryArmingTrigger.ON_POWER_LOSS },
            sentryGuardType = try { SentryGuardType.valueOf(sentryGuardStr) } catch (_: Exception) { SentryGuardType.SHOCK_STANDBY },
            sentryConfiguredGuardType = resolvedConfiguredGuardType,
            sentryAutoDemoteEnabled = prefs.getBoolean("sentry_auto_demote_enabled", true),
            sentryAcousticTriggerSensitivity = try { Sensitivity.valueOf(sentryAcousticStr) } catch (_: Exception) { Sensitivity.MEDIUM },
            sentryArmingDelaySeconds = prefs.getInt("sentry_arming_delay_seconds", 60),
            sentryIncidentDurationSeconds = prefs.getInt("sentry_incident_duration_seconds", 45),
            sentryDeterrentMode = try { SentryDeterrentMode.valueOf(sentryDeterrentStr) } catch (_: Exception) { SentryDeterrentMode.NONE },
            sentryInterruptedClipAction = try { SentryInterruptedClipAction.valueOf(sentryInterruptedStr) } catch (_: Exception) { SentryInterruptedClipAction.KEEP_WITH_BADGE },
            sentryVoiceAlertsEnabled = prefs.getBoolean("sentry_voice_alerts", true),
            sentryAutoDisarmOnPower = prefs.getBoolean("sentry_auto_disarm_power", true),
            sentryAutoLockEvents = prefs.getBoolean("sentry_auto_lock_events", true),
            midDriveThermalRecoveryEnabled = prefs.getBoolean("mid_drive_thermal_recovery", true),
            sentryAutoDemoteBatteryPct = prefs.getInt("sentry_auto_demote_battery_pct", 30),
            sentryAutoDemoteTempIsAuto = prefs.getBoolean("sentry_auto_demote_temp_is_auto", true),
            sentryAutoDemoteTempC = if (prefs.getBoolean("sentry_auto_demote_temp_is_auto", true)) {
                val maxT = prefs.getFloat("max_temp_c", 48.0f)
                if (maxT > 0f) (maxT - 4.0f).coerceAtLeast(30.0f) else 44.0f
            } else {
                prefs.getFloat("sentry_auto_demote_temp_c", 44.0f)
            },
            sentryMotionPersistenceFrames = prefs.getInt("sentry_motion_persistence_frames", 2),
            sleepEmergencyClipDurationSeconds = prefs.getInt("sleep_emergency_clip_duration_seconds", 30)
        )
    }

    fun updateSettings(newSettings: DashGuardSettings) {
        val maxTemp = newSettings.maxTempThresholdC
        val lowBat = newSettings.lowBatterySafetyLimitPct

        // Invariant 1: Sentry battery demote floor must strictly exceed low battery cutoff (>= lowBat + 5%)
        val clampedSentryDemoteBat = if (lowBat > 0) {
            newSettings.sentryAutoDemoteBatteryPct.coerceAtLeast(lowBat + 5).coerceIn(10, 50)
        } else {
            newSettings.sentryAutoDemoteBatteryPct.coerceIn(10, 50)
        }

        // Invariant 2: Sentry temp demote ceiling: if Auto, derived (maxTemp - 4°C); if Custom, clamped <= maxTemp - 4°C
        val resolvedSentryDemoteTemp = if (newSettings.sentryAutoDemoteTempIsAuto) {
            if (maxTemp > 0f) (maxTemp - 4.0f).coerceAtLeast(30.0f) else 44.0f
        } else {
            if (maxTemp > 0f) newSettings.sentryAutoDemoteTempC.coerceAtMost(maxTemp - 4.0f).coerceAtLeast(30.0f)
            else newSettings.sentryAutoDemoteTempC
        }

        // Invariant 3: Cool-off recovery target: if Auto, derived (maxTemp - 4°C); if Custom, clamped <= maxTemp - 2°C
        val resolvedRecoveryTarget = if (newSettings.thermalRecoveryIsAuto) {
            if (maxTemp > 0f) (maxTemp - 4.0f).coerceAtLeast(36.0f) else 44.0f
        } else {
            if (maxTemp > 0f) newSettings.thermalRecoveryTargetTempC.coerceAtMost(maxTemp - 2.0f).coerceAtLeast(30.0f)
            else newSettings.thermalRecoveryTargetTempC
        }

        // Invariant 4: Warm auto-dim warning: if Auto, derived (maxTemp - 7°C); if Custom, clamped <= maxTemp - 4°C
        val resolvedAutoDimTemp = if (newSettings.thermalAutoDimIsAuto) {
            if (maxTemp > 0f) (maxTemp - 7.0f).coerceAtLeast(38.0f) else 41.0f
        } else {
            if (maxTemp > 0f) newSettings.thermalAutoDimTempC.coerceAtMost(maxTemp - 4.0f).coerceAtLeast(30.0f)
            else newSettings.thermalAutoDimTempC
        }

        // Invariant 5: Sentry configured vs effective guard type
        val resolvedConfiguredGuard = if (newSettings.sentryConfiguredGuardType == SentryGuardType.SHOCK_STANDBY && newSettings.sentryGuardType == SentryGuardType.SHOCK_STANDBY) {
            SentryGuardType.SHOCK_STANDBY
        } else if (newSettings.sentryConfiguredGuardType == SentryGuardType.OPTICAL_MOTION || newSettings.sentryGuardType == SentryGuardType.OPTICAL_MOTION) {
            SentryGuardType.OPTICAL_MOTION
        } else {
            newSettings.sentryConfiguredGuardType
        }

        val resolvedEffectiveGuard = if (!newSettings.sentryAutoDemoteEnabled && resolvedConfiguredGuard == SentryGuardType.OPTICAL_MOTION) {
            SentryGuardType.OPTICAL_MOTION
        } else {
            newSettings.sentryGuardType
        }

        val effectiveSettings = newSettings.copy(
            sentryConfiguredGuardType = resolvedConfiguredGuard,
            sentryGuardType = resolvedEffectiveGuard,
            sentryAutoDemoteBatteryPct = clampedSentryDemoteBat,
            sentryAutoDemoteTempC = resolvedSentryDemoteTemp,
            thermalRecoveryTargetTempC = resolvedRecoveryTarget,
            thermalAutoDimTempC = resolvedAutoDimTemp
        )

        prefs.edit().apply {
            putString("current_mode", effectiveSettings.currentMode.name)
            putString("hud_display_mode", effectiveSettings.hudDisplayMode.name)
            putInt("loop_segment_minutes", effectiveSettings.loopSegmentMinutes)
            putInt("max_storage_gb", effectiveSettings.maxStorageGb)
            putString("video_quality", effectiveSettings.videoQuality.name)
            putString("video_aspect_ratio", effectiveSettings.videoAspectRatio.name)
            putInt("target_fps", effectiveSettings.targetFps)
            putBoolean("debug_allow_portrait_recording", effectiveSettings.debugAllowPortraitRecording)
            putString("speed_unit", effectiveSettings.speedUnit.name)
            putString("impact_sensitivity", effectiveSettings.impactSensitivity.name)
            putString("motion_sensitivity", effectiveSettings.motionSensitivity.name)
            putBoolean("auto_record_on_power", effectiveSettings.autoRecordOnPower)
            putInt("auto_stop_unplug_seconds", effectiveSettings.autoStopOnUnplugSeconds)
            putBoolean("disable_shutdown_on_incident", effectiveSettings.disableShutdownOnIncident)
            putBoolean("audio_recording", effectiveSettings.audioRecordingEnabled)
            putBoolean("sentry_siren", effectiveSettings.sentrySirenEnabled)
            putBoolean("sentry_flash", effectiveSettings.sentryFlashlightAlert)
            putBoolean("oled_power_save", effectiveSettings.oledPowerSaveMode)
            putString("auto_dim_timeout", effectiveSettings.autoDimTimeout.name)
            putBoolean("show_watermark", effectiveSettings.showWatermark)
            putInt("low_battery_limit", effectiveSettings.lowBatterySafetyLimitPct)
            putFloat("max_temp_c", effectiveSettings.maxTempThresholdC)
            putBoolean("thermal_auto_dim_is_auto", effectiveSettings.thermalAutoDimIsAuto)
            putBoolean("thermal_recovery_is_auto", effectiveSettings.thermalRecoveryIsAuto)
            putFloat("thermal_auto_dim_temp_c", effectiveSettings.thermalAutoDimTempC)
            putFloat("thermal_recovery_target_temp_c", effectiveSettings.thermalRecoveryTargetTempC)
            putBoolean("front_cam", effectiveSettings.frontCameraSelected)
            putInt("incident_buffer_seconds", effectiveSettings.incidentBufferSeconds)
            putString("storage_location_type", effectiveSettings.storageLocationType.name)
            putString("custom_storage_path", effectiveSettings.customStoragePath)
            putString("custom_storage_uri", effectiveSettings.customStorageUri)
            putInt("trip_grouping_gap_minutes", effectiveSettings.tripGroupingGapMinutes)
            putInt("loop_adjacency_threshold_seconds", effectiveSettings.loopAdjacencyThresholdSeconds)
            putBoolean("auto_advance_playback", effectiveSettings.autoAdvancePlayback)
            putBoolean("auto_dnd_while_recording", effectiveSettings.autoDndWhileRecording)
            putBoolean("oem_opt_completed", effectiveSettings.oemOptimizationCompleted)
            putInt("min_trip_duration_minutes", effectiveSettings.minTripDurationMinutes)
            putBoolean("include_gaps_in_trip_duration", effectiveSettings.includeGapsInTripDuration)
            putBoolean("sentry_mode_enabled", effectiveSettings.sentryModeEnabled)
            putString("sentry_arming_trigger", effectiveSettings.sentryArmingTrigger.name)
            putString("sentry_guard_type", effectiveSettings.sentryGuardType.name)
            putString("sentry_configured_guard_type", effectiveSettings.sentryConfiguredGuardType.name)
            putBoolean("sentry_auto_demote_enabled", effectiveSettings.sentryAutoDemoteEnabled)
            putString("sentry_acoustic_sensitivity", effectiveSettings.sentryAcousticTriggerSensitivity.name)
            putInt("sentry_arming_delay_seconds", effectiveSettings.sentryArmingDelaySeconds)
            putInt("sentry_incident_duration_seconds", effectiveSettings.sentryIncidentDurationSeconds)
            putString("sentry_deterrent_mode", effectiveSettings.sentryDeterrentMode.name)
            putString("sentry_interrupted_action", effectiveSettings.sentryInterruptedClipAction.name)
            putBoolean("sentry_voice_alerts", effectiveSettings.sentryVoiceAlertsEnabled)
            putBoolean("sentry_auto_disarm_power", effectiveSettings.sentryAutoDisarmOnPower)
            putBoolean("sentry_auto_lock_events", effectiveSettings.sentryAutoLockEvents)
            putBoolean("mid_drive_thermal_recovery", effectiveSettings.midDriveThermalRecoveryEnabled)
            putBoolean("sentry_auto_demote_temp_is_auto", effectiveSettings.sentryAutoDemoteTempIsAuto)
            putInt("sentry_auto_demote_battery_pct", effectiveSettings.sentryAutoDemoteBatteryPct)
            putFloat("sentry_auto_demote_temp_c", effectiveSettings.sentryAutoDemoteTempC)
            putInt("sentry_motion_persistence_frames", effectiveSettings.sentryMotionPersistenceFrames)
            putInt("sleep_emergency_clip_duration_seconds", effectiveSettings.sleepEmergencyClipDurationSeconds)
            apply()
        }
        _settings.value = effectiveSettings
    }

    fun setMode(mode: AppMode) {
        updateSettings(_settings.value.copy(currentMode = mode))
    }

    fun setOledMode(enabled: Boolean) {
        updateSettings(_settings.value.copy(oledPowerSaveMode = enabled))
    }

    fun toggleCamera() {
        updateSettings(_settings.value.copy(frontCameraSelected = !_settings.value.frontCameraSelected))
    }

    fun toggleAudio() {
        updateSettings(_settings.value.copy(audioRecordingEnabled = !_settings.value.audioRecordingEnabled))
    }

    fun setHudDisplayMode(mode: HudDisplayMode) {
        updateSettings(_settings.value.copy(hudDisplayMode = mode))
    }

    fun setSentryGuardType(type: SentryGuardType) {
        updateSettings(_settings.value.copy(sentryGuardType = type))
    }

    fun setConfiguredSentryGuardType(type: SentryGuardType) {
        updateSettings(_settings.value.copy(sentryGuardType = type, sentryConfiguredGuardType = type))
    }
}
