package io.github.roaxio.dashguardpro.ui.components.optimization

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.roaxio.dashguardpro.ui.theme.CyberCyan
import io.github.roaxio.dashguardpro.ui.theme.HudSurface
import io.github.roaxio.dashguardpro.ui.theme.NeonAmber
import io.github.roaxio.dashguardpro.ui.theme.NeonGreen

/**
 * Android Battery Whitelist Card (Bypasses Android Doze mode).
 */
@Composable
fun BatteryWhitelistStepCard(
    isBatteryIgnored: Boolean,
    onLaunchBatteryWhitelist: () -> Unit,
    modifier: Modifier = Modifier
) {
    OptimizationStepCard(
        icon = Icons.Default.BatteryChargingFull,
        title = "Bypass Android Battery Optimization",
        description = "Exempts DashGuard from Android Doze mode ('Unrestricted' battery). Prevents the OS from freezing video loop chunking when memory or screen is off.",
        actionLabel = if (isBatteryIgnored) "✓ Whitelisted (Unrestricted)" else "Whitelist DashGuard",
        isCompleted = isBatteryIgnored,
        onAction = onLaunchBatteryWhitelist
    )
}

/**
 * Kiosk Mode & App Pinning Card (Locks device to DashGuard).
 */
@Composable
fun KioskAppPinningStepCard(
    isAppPinned: Boolean,
    onToggleAppPinning: () -> Unit,
    onShowKioskGuide: () -> Unit,
    modifier: Modifier = Modifier
) {
    OptimizationStepCard(
        icon = Icons.Default.PushPin,
        title = "App Pinning (Kiosk Mode)",
        description = "Locks this phone to DashGuard so incoming calls, system heads-up notifications, or accidental touches won't kick you out of the camera while driving.",
        actionLabel = if (isAppPinned) "✓ Unpin DashGuard" else "📌 Pin DashGuard Now",
        secondaryActionLabel = "How to Unpin & Info",
        isCompleted = isAppPinned,
        onAction = onToggleAppPinning,
        onSecondaryAction = onShowKioskGuide
    )
}

/**
 * Step 4: Silent Cockpit (Auto Do Not Disturb) Card (Mutes incoming notifications while driving).
 */
@Composable
fun SilentCockpitDndCard(
    isDndGranted: Boolean,
    autoDndWhileRecording: Boolean,
    onLaunchDndSettings: () -> Unit,
    onToggleAutoDnd: (Boolean) -> Unit,
    onConfigureDndRules: () -> Unit,
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
                        .background(if (isDndGranted && autoDndWhileRecording) NeonGreen else CyberCyan),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsOff,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Silent Cockpit (Auto Do Not Disturb)",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Automatically mutes phone notifications, incoming calls, and banners during active recording. Restores normal sound when recording stops.",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (!isDndGranted) {
                Button(
                    onClick = onLaunchDndSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(
                        text = "Grant Do Not Disturb Access",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggleAutoDnd(!autoDndWhileRecording) }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (autoDndWhileRecording) "Auto-Silence While Recording: ENABLED" else "Auto-Silence While Recording: DISABLED",
                            color = if (autoDndWhileRecording) NeonGreen else NeonAmber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "System DND policy access granted",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = autoDndWhileRecording,
                        onCheckedChange = onToggleAutoDnd,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = NeonGreen,
                            uncheckedThumbColor = Color.LightGray,
                            uncheckedTrackColor = Color(0xFF334155)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onConfigureDndRules,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Configure System DND Rules (Allow Contacts/Alarms)", fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Step 5: OEM Background Killer Protection Card (Xiaomi, Samsung, Huawei, Oppo, Generic).
 */
@Composable
fun OemBackgroundProtectionCard(
    manufacturer: String,
    oemOptimizationCompleted: Boolean,
    onLaunchAppDetails: () -> Unit,
    onShowOemGuide: () -> Unit,
    onToggleOemOptimizationCompleted: (Boolean) -> Unit,
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
                        .background(if (oemOptimizationCompleted) NeonGreen else CyberCyan),
                    contentAlignment = Alignment.Center
                ) {
                    if (oemOptimizationCompleted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "OEM Background Protection ($manufacturer)",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Standard Battery Whitelist gives DashGuard 'Unrestricted' battery access. However, $manufacturer may enforce additional background restrictions (permission revocation if unused, sleeping apps, or aggressive task killers).",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onLaunchAppDetails,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (oemOptimizationCompleted) Color(0xFF1E293B) else CyberCyan
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Text(
                    text = if (oemOptimizationCompleted) "✓ Open App Settings (Re-check)" else "Open DashGuard App Settings",
                    color = if (oemOptimizationCompleted) NeonGreen else Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedButton(
                onClick = onShowOemGuide,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Text("View $manufacturer Specific Guide", fontSize = 12.sp)
            }

            HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 10.dp))

            // Manual verification toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleOemOptimizationCompleted(!oemOptimizationCompleted) }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (oemOptimizationCompleted) "OEM Settings: MARKED CONFIGURED" else "OEM Settings: UNVERIFIED",
                        color = if (oemOptimizationCompleted) NeonGreen else NeonAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Mark as completed once background restrictions & 'Pause app activity if unused' are turned off",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = oemOptimizationCompleted,
                    onCheckedChange = onToggleOemOptimizationCompleted,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = NeonGreen,
                        uncheckedThumbColor = Color.LightGray,
                        uncheckedTrackColor = Color(0xFF334155)
                    )
                )
            }
        }
    }
}

/**
 * Step 6: Windshield Thermal & Hardware Health Guide Card.
 */
@Composable
fun WindshieldThermalGuideCard(modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = HudSurface),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Thermostat,
                    contentDescription = null,
                    tint = NeonAmber,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Windshield Thermal & Hardware Health Guide",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "• Screen Auto-Dim: Configured in Settings, turns off display pixels and pauses preview surface rendering to reduce heat while continuing recording.\n" +
                        "• Direct Sunlight Avoidance: Mount phone behind the rearview mirror or below the windshield tint strip to block direct solar radiation.\n" +
                        "• Cabin Airflow: Position your car mount directly in the path of an A/C vent to actively cool the phone chassis.\n" +
                        "• Active Hardware Cooling: For extreme climates, consider magnetic clip-on thermoelectric (Peltier) phone coolers (commonly sold for gaming phones/dashcams) to maintain cool continuous recording indefinitely.\n" +
                        "• Remove thick cases and use 1080p 30fps resolution in Settings for optimal thermal efficiency.",
                color = Color(0xFFCBD5E1),
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}
