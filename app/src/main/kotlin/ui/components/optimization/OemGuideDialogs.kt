package io.github.roaxio.dashguardpro.ui.components.optimization

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.roaxio.dashguardpro.ui.theme.CyberCyan

/**
 * Modal guide dialog walking users through Android App Pinning (Kiosk Mode).
 */
@Composable
fun KioskGuideDialog(
    onDismiss: () -> Unit,
    onOpenSecuritySettings: () -> Unit,
    onTryPinNow: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0F172A),
            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "App Pinning (Kiosk Mode)",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 10.dp))

                Text(
                    text = "App Pinning locks DashGuard on screen so other apps, incoming calls, or accidental touches won't kick you out of the dashcam while driving.",
                    color = Color(0xFFE2E8F0),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                GuideStepRow("1", "Tap 'Pin DashGuard Now' below to lock the screen to DashGuard.")
                GuideStepRow("2", "To Exit / Unpin anytime: Swipe up and hold (or press Back + Overview buttons together).")
                GuideStepRow("3", "(Optional on older Android versions): App Pinning can also be enabled in Android Security Settings.")

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        onDismiss()
                        onTryPinNow()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("📌 Pin DashGuard Now", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenSecuritySettings()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Open Android Security Settings (Older Devices)", fontSize = 11.sp)
                }
            }
        }
    }
}

/**
 * Modal guide dialog with custom OEM-specific instructions (Xiaomi, Samsung, Huawei, Oppo, Generic)
 * to prevent background process killing and autostart blocking.
 */
@Composable
fun OemGuideDialog(
    manufacturer: String,
    isXiaomi: Boolean,
    isSamsung: Boolean,
    isHuawei: Boolean,
    isOppo: Boolean,
    onDismiss: () -> Unit,
    onOpenAppDetails: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0F172A),
            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$manufacturer Optimization",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 10.dp))

                Text(
                    text = "Phone manufacturers use proprietary background cleaners. Configure these settings in App Info to ensure uninterrupted dashcam recording:",
                    color = Color(0xFFE2E8F0),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (isXiaomi) {
                    GuideStepRow("1", "Autostart: Toggle ON to allow DashGuard to start on power connection.")
                    GuideStepRow("2", "Battery Saver: Select 'No restrictions' (avoids freezing camera loops).")
                    GuideStepRow("3", "Permissions: Disable 'Pause app activity if unused'.")
                    GuideStepRow("4", "Recents: Lock DashGuard in recent apps screen (padlock icon).")
                } else if (isSamsung) {
                    GuideStepRow("1", "Battery: Set to 'Unrestricted' (prevents OneUI sleeping apps).")
                    GuideStepRow("2", "Permissions: Turn OFF 'Remove permissions if app is unused'.")
                    GuideStepRow("3", "Device Care: Remove DashGuard from 'Sleeping apps' or 'Deep sleeping apps'.")
                    GuideStepRow("4", "Recents: Tap DashGuard icon in Recents and choose 'Keep open'.")
                } else if (isHuawei) {
                    GuideStepRow("1", "App Launch: Set to 'Manage manually' and enable Auto-launch & Secondary launch.")
                    GuideStepRow("2", "Battery: Turn OFF 'Power-intensive prompt'.")
                    GuideStepRow("3", "Permissions: Turn OFF 'Remove permissions if unused'.")
                } else if (isOppo) {
                    GuideStepRow("1", "Battery: Enable 'Allow background activity' and 'Allow auto-launch'.")
                    GuideStepRow("2", "Permissions: Disable 'Pause app activity if unused'.")
                    GuideStepRow("3", "Recents: Lock DashGuard in task switcher.")
                } else {
                    GuideStepRow("1", "App Battery Usage: Select 'Unrestricted' mode.")
                    GuideStepRow("2", "Permissions: Turn OFF 'Pause app activity if unused'.")
                    GuideStepRow("3", "Notifications: Ensure DashGuard background service notifications are enabled.")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        onDismiss()
                        onOpenAppDetails()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Open DashGuard App Settings", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
