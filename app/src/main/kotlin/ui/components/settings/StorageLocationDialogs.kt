package io.github.roaxio.dashguardpro.ui.components.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.roaxio.dashguardpro.data.settings.StorageLocationType
import io.github.roaxio.dashguardpro.storage.StorageDirectoryManager
import io.github.roaxio.dashguardpro.storage.StorageOptionInfo
import io.github.roaxio.dashguardpro.ui.theme.CyberCyan
import io.github.roaxio.dashguardpro.ui.theme.HudBlack
import io.github.roaxio.dashguardpro.ui.theme.NeonGreen
import io.github.roaxio.dashguardpro.ui.theme.SafetyAmber

/**
 * Sub-Menu Dialog for selecting storage locations on this phone
 */
@Composable
fun StorageLocationSubMenuDialog(
    availableOptions: List<StorageOptionInfo>,
    selectedType: StorageLocationType,
    onDismiss: () -> Unit,
    onSelectLocation: (StorageOptionInfo) -> Unit,
    onOpenViewfinder: () -> Unit,
    onLaunchSafPicker: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
            color = HudBlack
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(CyberCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = "Storage",
                                tint = CyberCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "STORAGE LOCATIONS",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                softWrap = true
                            )
                            Text(
                                text = "Select where dashcam videos are saved",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                softWrap = true
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 10.dp))

                // List of Storage Options
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(availableOptions) { opt ->
                        val isSelected = selectedType == opt.type
                        StorageLocationItemRow(
                            option = opt,
                            isSelected = isSelected,
                            onSelect = { onSelectLocation(opt) },
                            onConfigureCustom = onOpenViewfinder
                        )
                    }

                    if (selectedType == StorageLocationType.CUSTOM_DIRECTORY) {
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onOpenViewfinder,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Viewfinder",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Viewfinder Explorer", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = onLaunchSafPicker,
                                    modifier = Modifier.weight(0.9f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCBD5E1)),
                                    border = BorderStroke(1.dp, Color(0xFF334155))
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                                        contentDescription = "System Picker",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("SAF Picker", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 10.dp))

                // Bottom Done Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black)
                ) {
                    Text("Done", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Explanatory Dialog for Storage Quota Calculation and OS Safety Buffers
 */
@Composable
fun StorageQuotaInfoDialog(
    metrics: StorageDirectoryManager.StorageMetrics,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, CyberCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
            color = HudBlack
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(CyberCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = "Help",
                                tint = CyberCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Storage Quota Calculation",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 12.dp))

                // Current reading card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Physical Free Storage:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            Text(metrics.physicalFreeFormatted, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        if (metrics.appUsedBytes > 0L) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("App Video Buffer (Reclaimable):", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                Text("+ ${metrics.appUsedFormatted}", color = NeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Assignable Storage Pool:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                Text(metrics.totalPoolFormatted, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("OS Safety Buffer:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            Text("− ${metrics.osBufferFormatted}", color = SafetyAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Usable Safe Storage:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            Text(metrics.usableSafeFormatted, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Calculated Safe MAX Quota:", color = CyberCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("${metrics.maxAssignableGb} GB", color = CyberCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Why is MAX floored to whole GB?",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Point 1: Dynamic Safety buffer
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("🛡️ ", fontSize = 12.sp)
                    Column {
                        Text("Android OS Safety Buffer (${metrics.osBufferFormatted})", color = CyberCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "DashGuard dynamically preserves an OS safety buffer (${metrics.osBufferFormatted}) on your device at all times (proportional 2% of partition capacity, clamped between 500 MB and 5 GB). This prevents the Android OS from crashing or running out of memory for camera feeds and essential system processes.",
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Point 2: Downward rounding
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("🔢 ", fontSize = 12.sp)
                    Column {
                        Text("Stable Assignable Pool & Whole-GB Flooring", color = CyberCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Storage space already occupied by DashGuard videos is counted as part of your available quota pool because older clips are automatically recycled on a loop. Usable storage is floored to the nearest integer (${metrics.usableSafeFormatted} usable → ${metrics.maxAssignableGb} GB maximum).",
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Point 3: Hardcoded limit behavior
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("🔒 ", fontSize = 12.sp)
                    Column {
                        Text("Hardcoded Custom Limit", color = CyberCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "If you choose a quota lower than MAX (such as 2 GB or 5 GB), the app strictly enforces this limit and automatically prunes oldest unlocked clips once reached.",
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black)
                ) {
                    Text("Got It", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Individual storage location preset card displaying dynamic path and available disk space
 */
@Composable
fun StorageLocationItemRow(
    option: StorageOptionInfo,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onConfigureCustom: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Color(0xFF0D1B2A) else Color(0xFF0F172A))
            .border(
                1.dp,
                if (isSelected) CyberCyan.copy(alpha = 0.6f) else Color(0xFF1E293B),
                RoundedCornerShape(10.dp)
            )
            .clickable { onSelect() }
            .animateContentSize()
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(
                    selectedColor = CyberCyan,
                    unselectedColor = Color(0xFF64748B)
                ),
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = option.title,
                        color = if (isSelected) Color.White else Color(0xFFE2E8F0),
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        softWrap = true,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (option.freeBytes > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = option.freeSpaceFormatted,
                            color = if (isSelected) CyberCyan else Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = option.resolvedPath,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 15.sp,
                    softWrap = true
                )

                Text(
                    text = option.description,
                    color = Color(0xFF64748B),
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    softWrap = true
                )
            }

            if (option.type == StorageLocationType.CUSTOM_DIRECTORY && isSelected) {
                IconButton(
                    onClick = onConfigureCustom,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Custom Directory",
                        tint = CyberCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
