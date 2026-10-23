package io.github.roaxio.dashguardpro.ui.components.optimization

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.roaxio.dashguardpro.data.settings.AutoDimTimeout
import io.github.roaxio.dashguardpro.data.settings.DashGuardSettings
import io.github.roaxio.dashguardpro.ui.theme.CyberCyan
import io.github.roaxio.dashguardpro.ui.theme.EmergencyRed
import io.github.roaxio.dashguardpro.ui.theme.HudSurface
import io.github.roaxio.dashguardpro.ui.theme.NeonGreen

/**
 * Consolidated Vehicle Power & Thermal Automation Card configuring automotive ignition detection,
 * automatic shutdown countdowns, screen auto-dimming, battery cutoff protection, and thermal recovery loops.
 */
@Composable
fun VehiclePowerAutomationSection(
    settings: DashGuardSettings,
    onUpdateSettings: (DashGuardSettings) -> Unit,
    onShowCustomLowBattery: () -> Unit,
    onShowCustomWarmDim: () -> Unit,
    onShowCustomThermalRecovery: () -> Unit,
    onShowCustomSleepEmergency: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = HudSurface),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (settings.autoRecordOnPower) NeonGreen else CyberCyan),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Vehicle Power & Thermal Automation",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Complete vehicle ignition, shutoff, screen dimming, and thermal protection controls for autonomous dashcam operation.",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Auto-record on charger connect
            OptimizationToggleRow(
                title = "Auto-Start on Power Connect",
                subtitle = "Automatically begins recording the moment vehicle ignition powers on",
                checked = settings.autoRecordOnPower,
                onCheckedChange = { chk ->
                    onUpdateSettings(settings.copy(autoRecordOnPower = chk))
                }
            )

            HorizontalDivider(color = Color(0xFF1E293B))

            // Auto-stop countdown on disconnect
            OptimizationDropdownRow(
                title = "Auto-Stop After Unplug",
                subtitle = "Safely saves video loop and stops when car turns off",
                currentValue = if (settings.autoStopOnUnplugSeconds == 0) "Immediate" else "${settings.autoStopOnUnplugSeconds}s",
                options = listOf(0, 10, 15, 30, 60),
                onSelect = { s ->
                    onUpdateSettings(settings.copy(autoStopOnUnplugSeconds = s))
                },
                displayTransform = { if (it == 0) "Immediate" else "${it}s countdown" }
            )

            HorizontalDivider(color = Color(0xFF1E293B))

            // Intelligent Auto-Dim Inactivity Timer
            OptimizationDropdownRow(
                title = "Auto-Dim Inactivity Timer",
                subtitle = "Blacks out display pixels while driving to cut heat generation by up to 70%",
                currentValue = settings.autoDimTimeout.title,
                options = AutoDimTimeout.values().toList(),
                onSelect = { timeout ->
                    onUpdateSettings(settings.copy(autoDimTimeout = timeout))
                },
                displayTransform = { it.title }
            )

            HorizontalDivider(color = Color(0xFF1E293B))

            // Keep Recording on Incident
            OptimizationToggleRow(
                title = "Keep Recording on Power-Loss if Incident",
                subtitle = "Bypasses auto-shutdown and records on battery if a collision or shock is detected",
                checked = settings.disableShutdownOnIncident,
                onCheckedChange = { chk ->
                    onUpdateSettings(settings.copy(disableShutdownOnIncident = chk))
                }
            )

            HorizontalDivider(color = Color(0xFF1E293B))

            // Low Battery Safety Cutoff
            val lowBatOptions = listOf(0, 5, 10, 15, 20, -1)
            val currentLowBatDisplay = when {
                settings.lowBatterySafetyLimitPct <= 0 -> "Disabled"
                listOf(5, 10, 15, 20).contains(settings.lowBatterySafetyLimitPct) -> "${settings.lowBatterySafetyLimitPct}%"
                else -> "${settings.lowBatterySafetyLimitPct}% (Custom)"
            }
            OptimizationDropdownRow(
                title = "Low Battery Safety Cutoff",
                subtitle = "Halts recording and enters low-power sleep if battery drains below this level to preserve device health",
                currentValue = currentLowBatDisplay,
                options = lowBatOptions,
                onSelect = { pct ->
                    if (pct == -1) {
                        onShowCustomLowBattery()
                    } else {
                        onUpdateSettings(settings.copy(lowBatterySafetyLimitPct = pct))
                    }
                },
                displayTransform = {
                    when (it) {
                        -1 -> "Custom %..."
                        0 -> "Disabled (No cutoff)"
                        else -> "$it% remaining"
                    }
                }
            )

            HorizontalDivider(color = Color(0xFF1E293B))

            // Max Thermal Cutoff
            OptimizationDropdownRow(
                title = "Max Temperature Thermal Cutoff",
                subtitle = "Safely finalizes footage and halts recording if windshield heat exceeds this limit",
                currentValue = if (settings.maxTempThresholdC <= 0f) "Disabled" else "${settings.maxTempThresholdC.toInt()}°C",
                options = listOf(0.0f, 44.0f, 46.0f, 48.0f, 50.0f, 52.0f),
                onSelect = { temp ->
                    onUpdateSettings(settings.copy(maxTempThresholdC = temp))
                },
                displayTransform = { if (it <= 0f) "Disabled (No thermal cutoff)" else "${it.toInt()}°C limit" }
            )

            // Warning banner if Max Thermal Cutoff is disabled
            if (settings.maxTempThresholdC <= 0f) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1515)),
                    border = BorderStroke(1.dp, EmergencyRed.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("max_temp_disabled_warning")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Thermal Warning",
                            tint = EmergencyRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "⚠️ Thermal Safety Cutoff Disabled",
                                color = EmergencyRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Recording will not halt under extreme windshield heat. Device hardware may overheat, throttle, or degrade battery in direct sunlight.",
                                color = Color(0xFFE2E8F0),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF1E293B))

            // Warm Warning Auto-Dim Temperature
            val maxCutoff = settings.maxTempThresholdC
            val warmAutoVal = if (maxCutoff > 0f) maxOf(30f, maxCutoff - 7.0f).toInt() else 41
            val warmDropdownOptions = listOf("AUTO", "CUSTOM")
            val warmCurrentDisplay = if (settings.thermalAutoDimIsAuto) {
                "Auto ($warmAutoVal°C)"
            } else {
                "${settings.thermalAutoDimTempC.toInt()}°C (Custom)"
            }
            OptimizationDropdownRow(
                title = "Warm Auto-Dim Warning Temp",
                subtitle = "Proactively dims the screen to the driving power-saver overlay when warming up (no-op if already dimmed)",
                currentValue = warmCurrentDisplay,
                options = warmDropdownOptions,
                onSelect = { option ->
                    if (option == "AUTO") {
                        onUpdateSettings(settings.copy(thermalAutoDimIsAuto = true))
                    } else {
                        onShowCustomWarmDim()
                    }
                },
                displayTransform = { option ->
                    if (option == "AUTO") {
                        "Auto ($warmAutoVal°C - Recommended)"
                    } else {
                        if (!settings.thermalAutoDimIsAuto) {
                            "Custom (${settings.thermalAutoDimTempC.toInt()}°C)..."
                        } else {
                            "Custom..."
                        }
                    }
                }
            )

            HorizontalDivider(color = Color(0xFF1E293B))

            // Cool-Off Resume Target
            val recoveryAutoVal = if (maxCutoff > 0f) maxOf(30f, maxCutoff - 4.0f).toInt() else 44
            val recoveryDropdownOptions = listOf("AUTO", "CUSTOM")
            val recoveryCurrentDisplay = if (settings.thermalRecoveryIsAuto) {
                "Auto ($recoveryAutoVal°C)"
            } else {
                "${settings.thermalRecoveryTargetTempC.toInt()}°C (Custom)"
            }
            OptimizationDropdownRow(
                title = "Cool-Off Recovery Target",
                subtitle = "Target temperature for device to reach before automatically resuming driving loop recording",
                currentValue = recoveryCurrentDisplay,
                options = recoveryDropdownOptions,
                onSelect = { option ->
                    if (option == "AUTO") {
                        onUpdateSettings(settings.copy(thermalRecoveryIsAuto = true))
                    } else {
                        onShowCustomThermalRecovery()
                    }
                },
                displayTransform = { option ->
                    if (option == "AUTO") {
                        "Auto ($recoveryAutoVal°C - Recommended)"
                    } else {
                        if (!settings.thermalRecoveryIsAuto) {
                            "Custom (${settings.thermalRecoveryTargetTempC.toInt()}°C)..."
                        } else {
                            "Custom..."
                        }
                    }
                }
            )

            HorizontalDivider(color = Color(0xFF1E293B))

            val recoverySubtitle = if (settings.maxTempThresholdC > 0f) {
                "Auto-sleeps screen & camera to cool down while keeping emergency shock armed; auto-resumes recording when cooled below ${settings.thermalRecoveryTargetTempC.toInt()}°C"
            } else {
                "⚠️ Inactive: Max Temperature Cutoff is disabled. Enable a thermal cutoff limit above to activate auto-cooling and recovery."
            }

            OptimizationToggleRow(
                title = "Mid-Drive Thermal Recovery Guard",
                subtitle = recoverySubtitle,
                checked = settings.midDriveThermalRecoveryEnabled,
                onCheckedChange = { chk ->
                    onUpdateSettings(settings.copy(midDriveThermalRecoveryEnabled = chk))
                }
            )

            HorizontalDivider(color = Color(0xFF1E293B))

            val sleepDurationOptions = listOf(15, 30, 45, 60, -1)
            val sleepDurationCurrentDisplay = when (settings.sleepEmergencyClipDurationSeconds) {
                15 -> "15 seconds"
                30 -> "30 seconds (Default)"
                45 -> "45 seconds"
                60 -> "60 seconds"
                else -> "${settings.sleepEmergencyClipDurationSeconds} seconds (Custom)"
            }

            OptimizationDropdownRow(
                title = "Sleep Emergency Clip Duration",
                subtitle = "Length of high-priority emergency recording if a vehicle shock is detected while in Low-Power Sleep",
                currentValue = sleepDurationCurrentDisplay,
                options = sleepDurationOptions,
                onSelect = { sec ->
                    if (sec == -1) {
                        onShowCustomSleepEmergency()
                    } else {
                        onUpdateSettings(settings.copy(sleepEmergencyClipDurationSeconds = sec))
                    }
                },
                displayTransform = { sec ->
                    when (sec) {
                        15 -> "15 seconds"
                        30 -> "30 seconds (Default)"
                        45 -> "45 seconds"
                        60 -> "60 seconds"
                        -1 -> if (settings.sleepEmergencyClipDurationSeconds !in listOf(15, 30, 45, 60)) {
                            "Custom (${settings.sleepEmergencyClipDurationSeconds}s)..."
                        } else {
                            "Custom..."
                        }
                        else -> "$sec seconds"
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Thermal Sleep Sensor Info Badge
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Thermostat,
                        contentDescription = "Thermal Guard",
                        tint = CyberCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Low-Power Sleep: Shock Sensors Active (<0.1mA) • Camera & Mic Powered Down",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}
