package io.github.roaxio.dashguardpro

import android.app.Application
import io.github.roaxio.dashguardpro.data.AppDatabase
import io.github.roaxio.dashguardpro.data.SettingsRepository
import io.github.roaxio.dashguardpro.camera.CameraXRecordingManager
import io.github.roaxio.dashguardpro.storage.LoopStorageManager
import io.github.roaxio.dashguardpro.feedback.AlertManager
import io.github.roaxio.dashguardpro.sensor.BatteryMonitor
import io.github.roaxio.dashguardpro.sensor.LocationTracker
import io.github.roaxio.dashguardpro.sensor.SensorMonitor

class DashGuardApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val storageManager by lazy { LoopStorageManager(this, database.clipDao(), settingsRepository) }
    val recordingManager by lazy { CameraXRecordingManager(this) }
    val sensorMonitor by lazy { SensorMonitor(this) }
    val batteryMonitor by lazy { BatteryMonitor(this) }
    val locationTracker by lazy { LocationTracker(this) }
    val alertManager by lazy { AlertManager(this) }
    val acousticNoiseMonitor by lazy { io.github.roaxio.dashguardpro.sensor.AcousticNoiseMonitor(this) }
    val sentryController by lazy { io.github.roaxio.dashguardpro.sentry.SentryController(alertManager, acousticNoiseMonitor) }
    val playbackCoordinator by lazy { io.github.roaxio.dashguardpro.playback.VideoPlaybackCoordinator() }
    val systemDiagnosticsManager by lazy { io.github.roaxio.dashguardpro.system.SystemDiagnosticsManager(this, settingsRepository) }

    override fun onCreate() {
        super.onCreate()
    }
}
