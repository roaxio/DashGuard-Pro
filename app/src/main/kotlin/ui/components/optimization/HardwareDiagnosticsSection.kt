package io.github.roaxio.dashguardpro.ui.components.optimization

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.roaxio.dashguardpro.data.settings.AutoDimTimeout
import io.github.roaxio.dashguardpro.data.settings.DashGuardSettings
import io.github.roaxio.dashguardpro.storage.StorageDirectoryManager
import io.github.roaxio.dashguardpro.sensor.BatteryInfo
import io.github.roaxio.dashguardpro.sensor.TemperatureStatus
import io.github.roaxio.dashguardpro.sensor.TelemetryData
import io.github.roaxio.dashguardpro.system.SystemOptimizationState
import io.github.roaxio.dashguardpro.ui.theme.CyberCyan
import io.github.roaxio.dashguardpro.ui.theme.HudSurface
import io.github.roaxio.dashguardpro.ui.theme.NeonAmber
import io.github.roaxio.dashguardpro.ui.theme.NeonGreen

/**
 * Live hardware and OS diagnostic grid displaying battery temperature, charging status,
 * storage partition health, GPS telemetry fix, and background policy states.
 */
@Composable
fun HardwareDiagnosticsSection(
    battery: BatteryInfo,
    telemetry: TelemetryData,
    settings: DashGuardSettings,
    systemOptState: SystemOptimizationState,
    storageMetrics: StorageDirectoryManager.StorageMetrics,
    onSetAutoDim10s: () -> Unit,
    onLaunchBatteryWhitelist: () -> Unit,
    onToggleAppPinning: () -> Unit,
    onLaunchDndSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "HARDWARE & SYSTEM DIAGNOSTICS",
            color = CyberCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = HudSurface,
            border = BorderStroke(1.dp, Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                // Battery Temperature
                val tempStatus = battery.temperatureStatus
                val isWarm = tempStatus == TemperatureStatus.WARM || tempStatus == TemperatureStatus.HOT || tempStatus == TemperatureStatus.CRITICAL
                val isHot = tempStatus == TemperatureStatus.HOT || tempStatus == TemperatureStatus.CRITICAL
                val tempColor = if (isHot) Color(0xFFEF4444) else if (isWarm) NeonAmber else NeonGreen

                DiagRow(
                    label = "Battery Temperature",
                    value = "${"%.1f".format(battery.temperatureC)}°C",
                    isGood = !isWarm,
                    statusText = tempStatus.label,
                    overrideStatusColor = tempColor
                )

                // If phone is getting hot on windshield and auto-dim is disabled, suggest auto-dim
                if (isWarm && settings.autoDimTimeout == AutoDimTimeout.DISABLED) {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1917)),
                        border = BorderStroke(1.dp, NeonAmber.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "⚠️ Windshield Heat Warning",
                                    color = NeonAmber,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Enable Auto-Dim Inactivity Timer to black out pixels while recording and reduce heat by up to 70%.",
                                    color = Color(0xFFD6D3D1),
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = onSetAutoDim10s,
                                colors = ButtonDefaults.buttonColors(containerColor = NeonAmber),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) {
                                Text("Set 10s Dim", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)

                // Power Source & Voltage
                val isPlugged = battery.isPlugged
                val isCharging = battery.isCharging
                val powerValue = if (isPlugged) {
                    "${battery.powerSource.name} (${battery.voltageMv}mV)"
                } else {
                    "Discharging (Battery)"
                }
                val powerStatusText = when {
                    isCharging -> "Charging"
                    isPlugged -> "Plugged In"
                    else -> "On Battery"
                }
                DiagRow(
                    label = "Power Source",
                    value = powerValue,
                    isGood = isPlugged,
                    statusText = powerStatusText
                )

                HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)

                // Storage Health & Allocation
                val hasStoragePermission = systemOptState.hasStoragePermission
                val isLowStorage = storageMetrics.physicalFreeBytes < 1_000_000_000L
                val storageStatus = if (!hasStoragePermission) "Restricted" else if (isLowStorage) "Low Space" else "Healthy"
                val storageGood = hasStoragePermission && !isLowStorage
                DiagRow(
                    label = "Storage Health & Free Quota",
                    value = "${storageMetrics.physicalFreeFormatted} Free (${storageMetrics.totalPoolFormatted} Total)",
                    isGood = storageGood,
                    statusText = storageStatus
                )

                HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)

                // GPS Telemetry Fix
                DiagRow(
                    label = "GPS Telemetry Fix",
                    value = if (telemetry.hasGpsFix) "Locked (±${"%.1f".format(telemetry.accuracyMeters)}m accuracy)" else "Searching for GPS...",
                    isGood = telemetry.hasGpsFix,
                    statusText = if (telemetry.hasGpsFix) "Active" else "Searching"
                )

                HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)

                // Battery Optimization
                DiagRow(
                    label = "Battery Optimization",
                    value = if (systemOptState.isBatteryIgnored) "Unrestricted (Won't Sleep)" else "Restricted (May sleep in background)",
                    isGood = systemOptState.isBatteryIgnored,
                    statusText = if (systemOptState.isBatteryIgnored) "Whitelisted" else "Not Whitelisted",
                    onClick = {
                        if (!systemOptState.isBatteryIgnored) {
                            onLaunchBatteryWhitelist()
                        }
                    }
                )

                HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)

                // App Pinning / Kiosk
                DiagRow(
                    label = "App Pinning / Kiosk",
                    value = if (systemOptState.isAppPinned) "Locked to DashGuard (Kiosk)" else "Standard Mode",
                    isGood = systemOptState.isAppPinned,
                    statusText = if (systemOptState.isAppPinned) "Pinned" else "Not Pinned",
                    onClick = onToggleAppPinning
                )

                HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)

                // Silent Cockpit (DND)
                DiagRow(
                    label = "Silent Cockpit (Auto Do Not Disturb)",
                    value = if (systemOptState.isDndGranted) {
                        if (settings.autoDndWhileRecording) "Auto-Silence Active" else "Ready (Disabled)"
                    } else "Permission Required",
                    isGood = systemOptState.isDndGranted,
                    statusText = if (systemOptState.isDndGranted) "Granted" else "Needed",
                    onClick = {
                        if (!systemOptState.isDndGranted) {
                            onLaunchDndSettings()
                        }
                    }
                )
            }
        }
    }
}
