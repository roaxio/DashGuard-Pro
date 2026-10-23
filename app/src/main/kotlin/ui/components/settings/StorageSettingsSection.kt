package io.github.roaxio.dashguardpro.ui.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.roaxio.dashguardpro.ui.components.common.NumericThresholdDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.roaxio.dashguardpro.data.settings.DashGuardSettings
import io.github.roaxio.dashguardpro.storage.StorageDirectoryManager
import io.github.roaxio.dashguardpro.storage.StorageOptionInfo
import io.github.roaxio.dashguardpro.ui.theme.CyberCyan
import io.github.roaxio.dashguardpro.ui.theme.NeonGreen
import io.github.roaxio.dashguardpro.ui.theme.SafetyAmber
import io.github.roaxio.dashguardpro.ui.theme.SafetyRed

/**
 * Storage and video directory management section for [SettingsScreen].
 * Houses storage permission alert banner, active storage location info card,
 * and dynamic capacity quota slider derived from physical volume metrics.
 */
@Composable
fun StorageSettingsSection(
    displayedActivePath: String,
    hasStoragePermission: Boolean,
    activeOption: StorageOptionInfo?,
    settings: DashGuardSettings,
    maxDirectoryGb: Int,
    storageMetrics: StorageDirectoryManager.StorageMetrics,
    appUsedBytes: Long,
    onRequestStoragePermission: () -> Unit,
    onChangeStorageLocation: () -> Unit,
    onShowQuotaInfo: () -> Unit,
    onMaxStorageGbChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    SectionHeader("STORAGE & VIDEO DIRECTORY", modifier = modifier)
    SettingsCard {
        // Storage Permission Banner (if not granted)
        if (!hasStoragePermission) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SafetyAmber.copy(alpha = 0.12f))
                    .border(1.dp, SafetyAmber.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
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
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Permission Alert",
                            tint = SafetyAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "STORAGE PERMISSION RECOMMENDED",
                                color = SafetyAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                softWrap = true
                            )
                            Text(
                                text = "Grant storage access to read all phone directories and save to custom folders.",
                                color = Color(0xFFE2E8F0),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onRequestStoragePermission,
                        colors = ButtonDefaults.buttonColors(containerColor = SafetyAmber, contentColor = Color.Black),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("btn_grant_storage_permission")
                    ) {
                        Text("Grant", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Unified Storage Location Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0F172A))
                .border(1.dp, CyberCyan.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                .clickable { onChangeStorageLocation() }
                .padding(12.dp)
                .testTag("btn_open_storage_submenu")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CyberCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Storage Location",
                                tint = CyberCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "STORAGE LOCATION",
                                color = CyberCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = activeOption?.title ?: settings.storageLocationType.label,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                softWrap = true
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = { onChangeStorageLocation() },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                        modifier = Modifier.testTag("btn_select_storage_locations_submenu")
                    ) {
                        Text("Change", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Change Location",
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Path & Status Badge (Clean 2-row layout: full path is completely readable)
                val isWritable = activeOption?.isWritable ?: true
                val badgeColor = if (isWritable) NeonGreen else SafetyRed
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF070B12))
                        .border(0.5.dp, Color(0xFF1E293B), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ACTIVE STORAGE PATH",
                            color = Color(0xFF64748B),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(badgeColor.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (activeOption != null && activeOption.freeBytes > 0) {
                                    "${activeOption.freeSpaceFormatted} • ${if (isWritable) "Writable" else "Read-Only"}"
                                } else {
                                    if (isWritable) "Writable" else "Read-Only"
                                },
                                color = badgeColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = displayedActivePath,
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 15.sp,
                        softWrap = true
                    )
                }
            }
        }

        // Low Storage Warning or Dynamic Storage Quota
        if (maxDirectoryGb <= 0) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SafetyAmber.copy(alpha = 0.12f))
                    .border(1.dp, SafetyAmber.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Low Storage",
                        tint = SafetyAmber,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "INSUFFICIENT FREE STORAGE",
                            color = SafetyAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "This location has only ${activeOption?.freeSpaceFormatted ?: "very little space"}. Reserving the ${storageMetrics.osBufferFormatted} Android OS safety buffer leaves insufficient storage to record video segments. Please free up space or choose another storage location.",
                            color = Color(0xFFE2E8F0),
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        } else {
            HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 10.dp))

            val effectiveSelectedGb = settings.maxStorageGb.coerceIn(1, maxDirectoryGb)
            val isAtMaxDynamic = settings.maxStorageGb >= maxDirectoryGb
            var showCustomQuotaDialog by remember { mutableStateOf(false) }

            if (showCustomQuotaDialog) {
                NumericThresholdDialog(
                    title = "Custom Storage Quota",
                    description = "Specify the maximum storage space (1 GB to $maxDirectoryGb GB) allocated for DashGuard video recordings in the active directory.",
                    initialValue = effectiveSelectedGb.toString(),
                    label = "Storage Quota (1 - $maxDirectoryGb GB)",
                    confirmButtonText = "Save",
                    validator = { text ->
                        val num = text.toIntOrNull()
                        when {
                            text.isEmpty() -> "Quota cannot be empty"
                            num == null || num < 1 -> "Must be at least 1 GB"
                            num > maxDirectoryGb -> "Maximum available is $maxDirectoryGb GB"
                            else -> null
                        }
                    },
                    onDismiss = { showCustomQuotaDialog = false },
                    onConfirm = { text ->
                        val num = text.toIntOrNull()
                        if (num != null && num in 1..maxDirectoryGb) {
                            onMaxStorageGbChanged(num)
                            showCustomQuotaDialog = false
                        }
                    }
                )
            }

            val formulaSummaryText = if (appUsedBytes > 50L * StorageDirectoryManager.ONE_MB_BYTES) {
                "Max ${storageMetrics.maxAssignableGb} GB: ${storageMetrics.physicalFreeFormatted} free + ${storageMetrics.appUsedFormatted} app videos − ${storageMetrics.osBufferFormatted} buffer = ${storageMetrics.usableSafeFormatted} usable (floored to ${storageMetrics.maxAssignableGb} GB)."
            } else {
                "Max ${storageMetrics.maxAssignableGb} GB: ${storageMetrics.physicalFreeFormatted} free − ${storageMetrics.osBufferFormatted} buffer = ${storageMetrics.usableSafeFormatted} usable (floored to ${storageMetrics.maxAssignableGb} GB)."
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Max Video Storage Quota",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(
                                onClick = onShowQuotaInfo,
                                modifier = Modifier
                                    .size(22.dp)
                                    .testTag("btn_quota_help_info")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                    contentDescription = "Quota Calculation Info",
                                    tint = CyberCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(
                            text = if (isAtMaxDynamic) {
                                "⚡ Dynamic Max: Auto-capped to ${maxDirectoryGb} GB"
                            } else {
                                "🔒 Hard limit: ${effectiveSelectedGb} GB of ${maxDirectoryGb} GB max"
                            },
                            color = if (isAtMaxDynamic) CyberCyan else Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }

                    // Quota Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, CyberCyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .clickable { showCustomQuotaDialog = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("btn_custom_storage_quota")
                    ) {
                        Text(
                            text = if (isAtMaxDynamic) "MAX (${maxDirectoryGb} GB)" else "$effectiveSelectedGb GB",
                            color = CyberCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Dynamic Quota Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "1 GB",
                        color = Color(0xFF64748B),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Slider(
                        value = effectiveSelectedGb.toFloat(),
                        onValueChange = { newGb ->
                            onMaxStorageGbChanged(newGb.toInt().coerceIn(1, maxDirectoryGb))
                        },
                        valueRange = 1f..maxDirectoryGb.toFloat().coerceAtLeast(1f),
                        steps = if (maxDirectoryGb > 2) (maxDirectoryGb - 2).coerceAtMost(60) else 0,
                        colors = SliderDefaults.colors(
                            thumbColor = CyberCyan,
                            activeTrackColor = CyberCyan,
                            inactiveTrackColor = Color(0xFF1E293B)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("slider_storage_quota")
                    )

                    Button(
                        onClick = {
                            onMaxStorageGbChanged(maxDirectoryGb)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAtMaxDynamic) CyberCyan.copy(alpha = 0.2f) else Color(0xFF1E293B),
                            contentColor = if (isAtMaxDynamic) CyberCyan else Color.White
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("btn_set_max_quota")
                    ) {
                        Text("MAX", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Explanatory note with transparent arithmetic breakdown
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.7f))
                        .clickable { onShowQuotaInfo() }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = "Info",
                        tint = CyberCyan.copy(alpha = 0.8f),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formulaSummaryText,
                        color = Color(0xFF94A3B8),
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}
