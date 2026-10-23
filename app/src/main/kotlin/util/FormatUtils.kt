package io.github.roaxio.dashguardpro.util

import io.github.roaxio.dashguardpro.data.settings.SpeedUnit
import java.util.Locale

/**
 * Authoritative formatting utility for storage bytes, durations, distances, speed, and GPS coordinates.
 * Eliminates ad-hoc division and inconsistent string formatting across all UI and domain tiers.
 */
object FormatUtils {
    // --- Byte Formatting ---
    fun formatBytesCompact(bytes: Long): String {
        val sizeMb = bytes.toFloat() / (1024f * 1024f)
        return if (sizeMb >= 1024f) {
            String.format(Locale.US, "%.2f GB", sizeMb / 1024f)
        } else {
            String.format(Locale.US, "%.1f MB", sizeMb)
        }
    }

    fun formatBytesWithUnit(bytes: Long): String {
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return when {
            mb < 1.0 -> "${bytes / 1024} KB"
            mb < 1024.0 -> String.format(Locale.US, "%.1f MB", mb)
            else -> String.format(Locale.US, "%.2f GB", mb / 1024.0)
        }
    }

    fun formatStorageCapacityFree(bytes: Long): String {
        val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 1.0) {
            String.format(Locale.US, "%.1f GB free", gb)
        } else {
            val mb = bytes.toDouble() / (1024.0 * 1024.0)
            String.format(Locale.US, "%.0f MB free", mb)
        }
    }

    fun formatStorageCapacityTotal(bytes: Long): String {
        val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f GB total", gb)
    }

    fun formatFileSizeMb(bytes: Long): String {
        return String.format(Locale.US, "%.2f MB", bytes / (1024f * 1024f))
    }

    // --- Duration Formatting ---
    fun formatDuration(seconds: Long): String {
        val min = seconds / 60
        val sec = seconds % 60
        return if (min > 0) "${min}m ${sec}s" else "${sec}s"
    }

    fun formatDurationDetailed(seconds: Long): String {
        val min = seconds / 60
        val sec = seconds % 60
        return if (min > 0) "${seconds}s (${min}m ${sec}s)" else "${seconds}s"
    }

    fun formatTripDuration(durationMs: Long): String {
        val totalSec = (durationMs / 1000).toInt()
        val hrs = totalSec / 3600
        val mins = (totalSec % 3600) / 60
        val secs = totalSec % 60
        return if (hrs > 0) "${hrs}h ${mins}m ${secs}s" else "${mins}m ${secs}s"
    }

    // --- Distance & Speed Formatting ---
    fun convertSpeed(speedKmh: Float, speedUnit: SpeedUnit = SpeedUnit.KMH): Float {
        return if (speedUnit == SpeedUnit.MPH) speedKmh * 0.621371f else speedKmh
    }

    fun formatSpeed(speedKmh: Float, speedUnit: SpeedUnit): String {
        return "${convertSpeed(speedKmh, speedUnit).toInt()} ${speedUnit.label}"
    }

    fun formatSpeedUppercase(speedKmh: Float, speedUnit: SpeedUnit = SpeedUnit.KMH): String {
        return "${convertSpeed(speedKmh, speedUnit).toInt()} ${speedUnit.label.uppercase()}"
    }

    fun formatSpeedDetailed(speedKmh: Float, speedUnit: SpeedUnit = SpeedUnit.KMH): String {
        return String.format(Locale.US, "%.1f %s", convertSpeed(speedKmh, speedUnit), speedUnit.label)
    }

    fun formatDistance(distanceMeters: Float, speedUnit: SpeedUnit): String {
        if (speedUnit == SpeedUnit.MPH) {
            val miles = distanceMeters / 1609.344f
            val feet = (distanceMeters * 3.28084f).toInt()
            return if (miles >= 0.1f) {
                String.format(Locale.US, "%.2f mi (%d ft)", miles, feet)
            } else {
                "$feet ft"
            }
        }
        return formatDistance(distanceMeters)
    }

    fun formatDistanceMetersOrKm(distanceMeters: Float, speedUnit: SpeedUnit): String {
        if (speedUnit == SpeedUnit.MPH) {
            val miles = distanceMeters / 1609.344f
            return if (miles >= 0.1f) {
                String.format(Locale.US, "%.2f mi", miles)
            } else {
                "${(distanceMeters * 3.28084f).toInt()} ft"
            }
        }
        return formatDistanceMetersOrKm(distanceMeters)
    }

    fun formatTripDistance(distanceMeters: Float, speedUnit: SpeedUnit): String? {
        if (speedUnit == SpeedUnit.MPH) {
            val miles = distanceMeters / 1609.344f
            return when {
                miles >= 0.1f -> String.format(Locale.US, "%.2f mi", miles)
                distanceMeters > 0f -> "${(distanceMeters * 3.28084f).toInt()} ft"
                else -> null
            }
        }
        return formatTripDistance(distanceMeters)
    }

    fun formatDistanceCompact(distanceMeters: Float, speedUnit: SpeedUnit = SpeedUnit.KMH, spaceBeforeMeters: Boolean = false): String {
        if (speedUnit == SpeedUnit.MPH) {
            val miles = distanceMeters / 1609.344f
            return if (miles >= 0.1f) {
                String.format(Locale.US, "%.2f mi", miles)
            } else {
                val ft = (distanceMeters * 3.28084f).toInt()
                if (spaceBeforeMeters) "$ft ft" else "${ft}ft"
            }
        }
        return if (distanceMeters >= 1000f) {
            String.format(Locale.US, "%.2f km", distanceMeters / 1000f)
        } else {
            if (spaceBeforeMeters) "${distanceMeters.toInt()} m" else "${distanceMeters.toInt()}m"
        }
    }
    fun formatDistance(distanceMeters: Float): String {
        return if (distanceMeters >= 1000f) {
            String.format(Locale.US, "%.2f km (%d m)", distanceMeters / 1000f, distanceMeters.toInt())
        } else {
            "${distanceMeters.toInt()} m"
        }
    }

    fun formatDistanceMetersOrKm(distanceMeters: Float): String {
        return if (distanceMeters >= 1000f) {
            String.format(Locale.US, "%.2f km", distanceMeters / 1000f)
        } else {
            "${distanceMeters.toInt()} meters"
        }
    }

    fun formatTripDistance(distanceMeters: Float): String? {
        return when {
            distanceMeters >= 1000f -> String.format(Locale.US, "%.2f km", distanceMeters / 1000f)
            distanceMeters > 0f -> "${distanceMeters.toInt()} m"
            else -> null
        }
    }

    fun formatSpeed(speedKmh: Float, unitLabel: String = "km/h"): String {
        return "${speedKmh.toInt()} $unitLabel"
    }

    fun formatCoordinates(lat: Double, lng: Double): String {
        return String.format(Locale.US, "%.4f, %.4f", lat, lng)
    }
}
