package io.github.roaxio.dashguardpro.ui.components.settings

import androidx.compose.runtime.Composable
import io.github.roaxio.dashguardpro.ui.components.common.NumericThresholdDialog

/**
 * Custom Duration Dialog enabling user to input arbitrary inactivity gap minutes (e.g. 30 mins)
 */
@Composable
fun CustomTripGapDialog(
    currentMinutes: Int,
    adjacencyThresholdSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    // Minimum allowed trip gap in minutes (ceiling so that min*60 >= adjacencyThresholdSeconds)
    val minAllowedMinutes = (adjacencyThresholdSeconds + 59) / 60

    NumericThresholdDialog(
        title = "Custom Trip Gap Timeout",
        description = "Specify the maximum allowable stopped or paused time (in minutes) between recorded loops before a new Trip session begins in your footage gallery.\n\nE.g. 30 minutes for highway rest stops or errands.",
        initialValue = currentMinutes.toString(),
        label = "Trip Gap (Minutes)",
        confirmButtonText = "Save",
        validator = { text ->
            val num = text.toIntOrNull()
            when {
                text.isEmpty() -> "Duration cannot be empty"
                num == null || num < 1 -> "Must be at least 1 minute"
                num > 720 -> "Maximum 720 minutes (12 hours)"
                num * 60 < adjacencyThresholdSeconds ->
                    "Must be ≥ $minAllowedMinutes min (Player Adjacency Window is ${adjacencyThresholdSeconds}s)"
                else -> null
            }
        },
        onDismiss = onDismiss,
        onConfirm = { text ->
            val num = text.toIntOrNull()
            if (num != null && num in 1..720) {
                onConfirm(num)
            }
        }
    )
}

@Composable
fun CustomSentryArmingDelayDialog(
    currentSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    NumericThresholdDialog(
        title = "Custom Sentry Arming Delay",
        description = "Specify how many seconds you need to exit the vehicle and close doors before Sentry sensors arm (up to 300 seconds / 5 minutes).",
        initialValue = currentSeconds.toString(),
        label = "Arming Delay (Seconds)",
        confirmButtonText = "Save",
        validator = { text ->
            val num = text.toIntOrNull()
            when {
                text.isEmpty() -> "Delay cannot be empty"
                num == null || num < 1 -> "Must be at least 1 second"
                num > 300 -> "Maximum 300 seconds (5 minutes)"
                else -> null
            }
        },
        onDismiss = onDismiss,
        onConfirm = { text ->
            val num = text.toIntOrNull()
            if (num != null && num in 1..300) {
                onConfirm(num)
            }
        }
    )
}

@Composable
fun CustomSentryIncidentDurationDialog(
    currentSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    NumericThresholdDialog(
        title = "Custom Incident Duration",
        description = "Specify how many seconds Sentry should record after an impact, loud noise, or motion event is detected (15 to 300 seconds / 5 minutes).",
        initialValue = currentSeconds.toString(),
        label = "Incident Duration (Seconds)",
        confirmButtonText = "Save",
        validator = { text ->
            val num = text.toIntOrNull()
            when {
                text.isEmpty() -> "Duration cannot be empty"
                num == null || num < 15 -> "Must be at least 15 seconds"
                num > 300 -> "Maximum 300 seconds (5 minutes)"
                else -> null
            }
        },
        onDismiss = onDismiss,
        onConfirm = { text ->
            val num = text.toIntOrNull()
            if (num != null && num in 15..300) {
                onConfirm(num)
            }
        }
    )
}

@Composable
fun CustomSentryDemoteTempDialog(
    currentTempC: Float,
    maxAllowedC: Int,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    val minTempC = 30
    val initialText = if (currentTempC > 0f) currentTempC.toInt().toString() else "40"

    NumericThresholdDialog(
        title = "Custom Demotion Temperature",
        description = "Specify custom thermal demotion limit ($minTempC°C to ${maxAllowedC}°C). Sentry optical motion automatically demotes to Tier 1 Shock Standby when device heat reaches this threshold (max allowed is 4°C below Max Cutoff).",
        initialValue = initialText,
        label = "Demotion Threshold (°C)",
        confirmButtonText = "Save",
        validator = { text ->
            val num = text.toIntOrNull()
            when {
                text.isEmpty() -> "Temperature cannot be empty"
                num == null || num < minTempC -> "Must be at least ${minTempC}°C"
                num > maxAllowedC -> "Maximum allowed is ${maxAllowedC}°C"
                else -> null
            }
        },
        onDismiss = onDismiss,
        onConfirm = { text ->
            val num = text.toIntOrNull()
            if (num != null && num in minTempC..maxAllowedC) {
                onConfirm(num.toFloat())
            }
        }
    )
}

@Composable
fun CustomIncidentBufferDialog(
    currentSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    NumericThresholdDialog(
        title = "Custom Incident Guarantee Window",
        description = "Specify guaranteed post-incident recording duration (15 to 300 seconds / 5 minutes). In Standby mode, manual emergency locks and impact recordings will record for this exact duration.",
        initialValue = currentSeconds.toString(),
        label = "Incident Guarantee Window (Seconds)",
        confirmButtonText = "Save",
        validator = { text ->
            val num = text.toIntOrNull()
            when {
                text.isEmpty() -> "Duration cannot be empty"
                num == null || num < 15 -> "Must be at least 15 seconds"
                num > 300 -> "Maximum 300 seconds (5 minutes)"
                else -> null
            }
        },
        onDismiss = onDismiss,
        onConfirm = { text ->
            val num = text.toIntOrNull()
            if (num != null && num in 15..300) {
                onConfirm(num)
            }
        }
    )
}

