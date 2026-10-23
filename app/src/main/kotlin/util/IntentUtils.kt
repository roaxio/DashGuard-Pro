package io.github.roaxio.dashguardpro.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

/**
 * Authoritative intent launcher and factory utility for media sharing, map navigation,
 * and system settings configuration. Eliminates duplicate intent construction and permission boilerplate.
 */
object IntentUtils {

    fun getFileUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    // --- 1. Video & Subtitle Sharing ---
    fun createShareIntent(
        context: Context,
        videoFile: File,
        srtFile: File? = null,
        uriResolver: (File) -> Uri = { getFileUri(context, it) }
    ): Intent? {
        if (!videoFile.exists()) {
            return null
        }

        return try {
            val videoUri = uriResolver(videoFile)

            if (srtFile != null && srtFile.exists()) {
                val srtUri = uriResolver(srtFile)
                Intent().apply {
                    action = Intent.ACTION_SEND_MULTIPLE
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(videoUri, srtUri))
                    type = "*/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, videoUri)
                    type = "video/mp4"
                    if (videoFile.name.contains("-telemetry-fused")) {
                        putExtra(Intent.EXTRA_SUBJECT, "DashGuard Fused Video: ${videoFile.name}")
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun shareVideoFootage(context: Context, videoFile: File, srtFile: File? = null) {
        if (!videoFile.exists()) {
            Toast.makeText(context, "Video file not found on disk", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val shareIntent = createShareIntent(context, videoFile, srtFile)
            if (shareIntent == null) {
                Toast.makeText(context, "Failed to prepare footage for sharing", Toast.LENGTH_SHORT).show()
                return
            }

            val chooserTitle = if (shareIntent.action == Intent.ACTION_SEND_MULTIPLE) {
                "Share Video & .SRT Subtitles"
            } else {
                "Share Video Footage"
            }

            val chooser = Intent.createChooser(shareIntent, chooserTitle)
            if (context !is Activity) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // --- 2. Map Route & Location Launching ---
    fun launchMapCoordinates(context: Context, lat: Double, lng: Double, label: String = "Dashcam Clip Location") {
        val uriStr = String.format(Locale.US, "geo:%f,%f?q=%f,%f(%s)", lat, lng, lat, lng, label)
        try {
            val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr))
            val chooser = Intent.createChooser(mapIntent, "Open Location in Maps")
            if (context !is Activity) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Exception) {
            Toast.makeText(context, "No maps application installed", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchMapRoute(context: Context, startLat: Double, startLng: Double, endLat: Double, endLng: Double) {
        val uriStr = if (startLat != endLat || startLng != endLng) {
            String.format(
                Locale.US,
                "https://www.google.com/maps/dir/?api=1&origin=%f,%f&destination=%f,%f&travelmode=driving",
                startLat, startLng, endLat, endLng
            )
        } else {
            String.format(Locale.US, "geo:%f,%f?q=%f,%f(Trip Route)", startLat, startLng, startLat, startLng)
        }
        try {
            val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr))
            val chooser = Intent.createChooser(mapIntent, "Open Route in Maps")
            if (context !is Activity) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Exception) {
            Toast.makeText(context, "No maps application installed", Toast.LENGTH_SHORT).show()
        }
    }

    // --- 3. System Settings Intent Factories ---
    fun createBatteryOptimizationIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            } catch (_: Exception) {
                try {
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                } catch (_: Exception) {
                    Intent(Settings.ACTION_SETTINGS)
                }
            }
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
    }

    fun createDndSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
    }

    fun createAppDetailsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    fun createSecuritySettingsIntent(): Intent {
        return Intent(Settings.ACTION_SECURITY_SETTINGS)
    }

    fun launchDndRulesConfiguration(context: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS),
            Intent("android.settings.ZEN_MODE_SETTINGS"),
            Intent(Settings.ACTION_SOUND_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in intents) {
            try {
                if (context !is Activity) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }
    }

    fun toggleAppPinning(context: Context, isCurrentlyPinned: Boolean, onPinFailed: () -> Unit) {
        val activity = context as? Activity ?: return
        if (isCurrentlyPinned) {
            try {
                activity.stopLockTask()
                Toast.makeText(context, "DashGuard Unpinned", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {}
        } else {
            try {
                activity.startLockTask()
            } catch (_: Exception) {
                onPinFailed()
            }
        }
    }
}
