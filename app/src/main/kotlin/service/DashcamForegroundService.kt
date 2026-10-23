package io.github.roaxio.dashguardpro.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.roaxio.dashguardpro.MainActivity
import io.github.roaxio.dashguardpro.R

class DashcamForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "dashguard_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START_DASHGUARD"
        const val ACTION_STOP = "ACTION_STOP_DASHGUARD"
        const val ACTION_EMERGENCY_LOCK = "ACTION_EMERGENCY_LOCK"
        const val ACTION_EMERGENCY_LOCK_TRIGGER = "io.github.roaxio.dashguardpro.EMERGENCY_LOCK_TRIGGER"
        const val EXTRA_MODE = "EXTRA_MODE"

        fun startService(context: Context, mode: String = "DASHCAM") {
            try {
                val intent = Intent(context, DashcamForegroundService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_MODE, mode)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {}
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, DashcamForegroundService::class.java).apply {
                    action = ACTION_STOP
                }
                context.stopService(intent)
            } catch (_: Exception) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        // Immediately fulfill Android OS startForeground contract to prevent ForegroundServiceDidNotStartInTimeException
        try {
            val notification = buildNotification("DASHCAM", isRecording = false)
            val serviceType = computeForegroundServiceType()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && serviceType != 0) {
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w("DashcamForegroundService", "Initial onCreate startForeground note: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                try {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } catch (_: Exception) {}
                return START_NOT_STICKY
            }
            ACTION_EMERGENCY_LOCK -> {
                val lockBroadcast = Intent(ACTION_EMERGENCY_LOCK_TRIGGER).apply {
                    setPackage(packageName)
                }
                sendBroadcast(lockBroadcast)
            }
            else -> {
                val mode = intent?.getStringExtra(EXTRA_MODE) ?: "DASHCAM"
                val notification = buildNotification(mode, isRecording = true)
                val serviceType = computeForegroundServiceType()

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && serviceType != 0) {
                        startForeground(NOTIFICATION_ID, notification, serviceType)
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } catch (e: Exception) {
                    Log.e("DashcamForegroundService", "Failed to start foreground service", e)
                    // Fallback to basic startForeground if specific types fail due to race conditions
                    try {
                        startForeground(NOTIFICATION_ID, notification)
                    } catch (fallbackEx: Exception) {
                        Log.e("DashcamForegroundService", "Fallback startForeground failed", fallbackEx)
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "DashGuard::SurveillanceWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L) // 12 hours max safety
            }
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DashGuard Camera Active Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Dashcam / Sentry recording actively in background with process protection."
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(mode: String, isRecording: Boolean): Notification {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingAppIntent = PendingIntent.getActivity(
            this,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val lockIntent = Intent(this, DashcamForegroundService::class.java).apply {
            action = ACTION_EMERGENCY_LOCK
        }
        val pendingLockIntent = PendingIntent.getService(
            this,
            1,
            lockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, DashcamForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStopIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (mode == "SENTRY") "🛡️ Sentry Security Armed" else "🔴 DashGuard Pro Recording"
        val text = if (mode == "SENTRY") "Motion & shock sentry guard active" else "Continuous loop telemetry active"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pendingAppIntent)
            .addAction(android.R.drawable.ic_lock_lock, "🔒 Lock Clip", pendingLockIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingStopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Exception) {}
    }

    private fun computeForegroundServiceType(): Int {
        val hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        var serviceType = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (hasCamera) serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            if (hasLocation) serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        return serviceType
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
