package io.github.roaxio.dashguardpro.ui.components.optimization

import androidx.compose.runtime.Composable
import io.github.roaxio.dashguardpro.ui.components.common.NumericThresholdDialog

/**
 * Numeric threshold dialog allowing the user to set a custom low-battery safety cutoff percentage.
 */
@Composable
fun CustomLowBatteryDialog(
    currentPct: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val initialText = if (currentPct > 0) currentPct.toString() else "10"

    NumericThresholdDialog(
        title = "Custom Battery Safety Cutoff",
        description = "Specify custom battery percentage floor (1% to 50%). If battery drains below this level when unplugged, DashGuard safely saves recordings and enters Low-Power Sleep to preserve phone battery life.",
        initialValue = initialText,
        label = "Cutoff Level (%)",
        confirmButtonText = "Apply",
        validator = { text ->
            val num = text.toIntOrNull()
            when {
                text.isEmpty() -> "Percentage cannot be empty"
                num == null || num < 1 -> "Must be at least 1%"
                num > 50 -> "Maximum 50%"
                else -> null
            }
        },
        onDismiss = onDismiss,
        onConfirm = { text ->
            val num = text.toIntOrNull()
            if (num != null && num in 1..50) {
                onConfirm(num)
            }
        }
    )
}

/**
 * Numeric threshold dialog for custom warm auto-dim warning and cool-off recovery target temperatures.
 */
@Composable
fun CustomThermalTempDialog(
    title: String,
    description: String,
    currentTempC: Float,
    minTempC: Int = 30,
    maxTempC: Int,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    val initialText = if (currentTempC > 0f) currentTempC.toInt().toString() else "40"

    NumericThresholdDialog(
        title = title,
        description = description,
        initialValue = initialText,
        label = "Threshold (°C)",
        confirmButtonText = "Apply",
        validator = { text ->
            val num = text.toIntOrNull()
            when {
                text.isEmpty() -> "Temperature cannot be empty"
                num == null || num < minTempC -> "Must be at least ${minTempC}°C"
                num > maxTempC -> "Maximum allowed is ${maxTempC}°C"
                else -> null
            }
        },
        onDismiss = onDismiss,
        onConfirm = { text ->
            val num = text.toIntOrNull()
            if (num != null && num in minTempC..maxTempC) {
                onConfirm(num.toFloat())
            }
        }
    )
}

/**
 * Numeric threshold dialog for emergency clip duration when vehicle shock triggers in Low-Power Sleep.
 */
@Composable
fun CustomSleepEmergencyDurationDialog(
    currentDurationSeconds: Int,
    minSeconds: Int = 10,
    maxSeconds: Int = 180,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val initialText = currentDurationSeconds.toString()

    NumericThresholdDialog(
        title = "Custom Sleep Shock Duration",
        description = "Specify custom emergency clip duration in seconds ($minSeconds to $maxSeconds seconds). The camera will wake up, record for this duration, save the emergency file, and safely return to low-power sleep.",
        initialValue = initialText,
        label = "Duration (Seconds)",
        confirmButtonText = "Apply",
        validator = { text ->
            val num = text.toIntOrNull()
            when {
                text.isEmpty() -> "Duration cannot be empty"
                num == null || num < minSeconds -> "Must be at least ${minSeconds}s"
                num > maxSeconds -> "Maximum allowed is ${maxSeconds}s"
                else -> null
            }
        },
        onDismiss = onDismiss,
        onConfirm = { text ->
            val num = text.toIntOrNull()
            if (num != null && num in minSeconds..maxSeconds) {
                onConfirm(num)
            }
        }
    )
}
