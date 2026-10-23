package io.github.roaxio.dashguardpro.ui.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import io.github.roaxio.dashguardpro.data.settings.StorageLocationType
import io.github.roaxio.dashguardpro.storage.MigrationProgress
import io.github.roaxio.dashguardpro.storage.StorageDirectoryManager
import io.github.roaxio.dashguardpro.util.FormatUtils
import io.github.roaxio.dashguardpro.ui.theme.CyberCyan
import io.github.roaxio.dashguardpro.ui.theme.EmergencyRed
import io.github.roaxio.dashguardpro.ui.theme.HudBlack
import io.github.roaxio.dashguardpro.ui.theme.HudSurface
import io.github.roaxio.dashguardpro.ui.theme.NeonAmber
import io.github.roaxio.dashguardpro.ui.theme.NeonGreen
import io.github.roaxio.dashguardpro.ui.theme.SafetyAmber
import java.io.File

data class PendingStorageChange(
    val locationType: StorageLocationType,
    val customPath: String? = null,
    val customUri: String? = null,
    val targetDir: File
)

@Composable
fun StorageMigrationPromptDialog(
    sourcePath: String,
    targetPath: String,
    activeClipCount: Int,
    activeSizeBytes: Long,
    otherClipCount: Int,
    otherSizeBytes: Long,
    onMoveActiveFootage: () -> Unit,
    onConsolidateAllFootage: () -> Unit,
    onKeepInPlace: () -> Unit,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    val activeSizeFormatted = FormatUtils.formatBytesWithUnit(activeSizeBytes)
    val otherSizeFormatted = FormatUtils.formatBytesWithUnit(otherSizeBytes)
    val totalClips = activeClipCount + otherClipCount
    val totalSizeBytes = activeSizeBytes + otherSizeBytes
    val totalSizeFormatted = FormatUtils.formatBytesWithUnit(totalSizeBytes)
    val hasOtherLocations = otherClipCount > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HudSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Storage Location & Footage", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                if (!hasOtherLocations) {
                    Text(
                        text = "You have $activeClipCount recording${if (activeClipCount > 1) "s" else ""} ($activeSizeFormatted) in your current storage location.",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        text = "You have recordings across multiple storage locations on this phone:",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Current Folder:", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                Text("$activeClipCount clips ($activeSizeFormatted)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Other Past Folders:", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                Text("$otherClipCount clips ($otherSizeFormatted)", color = NeonAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            HorizontalDivider(color = Color(0xFF334155))
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Footage:", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text("$totalClips clips ($totalSizeFormatted)", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Would you like to move (cut-paste) your existing footage into the new storage folder, or keep them where they are?",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text("FROM CURRENT:", color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = sourcePath,
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("TO NEW LOCATION:", color = CyberCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = targetPath,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• Moving footage updates video files, telemetry .SRT sidecars, and Gallery index.\n• Keeping in place leaves past files in their current folders while new recordings go to the new folder.",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hasOtherLocations) {
                    Button(
                        onClick = onConsolidateAllFootage,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Consolidate ALL ($totalSizeFormatted • $totalClips clips)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onMoveActiveFootage,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Move Only Current Folder ($activeSizeFormatted)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Button(
                        onClick = onMoveActiveFootage,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Move All Footage ($activeSizeFormatted)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", fontSize = 12.sp)
                    }
                    Button(
                        onClick = onKeepInPlace,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E293B),
                            contentColor = Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Keep in Place", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        dismissButton = null
    )
}

@Composable
fun InsufficientStorageMigrationDialog(
    requiredBytes: Long,
    availableBytes: Long,
    bufferBytes: Long = StorageDirectoryManager.OS_SAFETY_BUFFER_BYTES,
    onKeepInPlaceAndSwitch: () -> Unit,
    onDismiss: () -> Unit
) {
    val reqFormatted = StorageDirectoryManager.StorageMetrics.formatBytes(requiredBytes + bufferBytes)
    val availFormatted = StorageDirectoryManager.StorageMetrics.formatBytes(availableBytes)
    val bufferFormatted = StorageDirectoryManager.StorageMetrics.formatBytes(bufferBytes)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HudSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = SafetyAmber,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Insufficient Destination Space", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Text(
                text = "The selected destination directory does not have enough free space to receive all recordings while maintaining the $bufferFormatted Android OS safety buffer.\n\n" +
                        "• Required (with $bufferFormatted buffer): $reqFormatted\n" +
                        "• Available at destination: $availFormatted\n\n" +
                        "Would you like to switch storage path without moving existing recordings?",
                color = Color(0xFFCBD5E1),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onKeepInPlaceAndSwitch,
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Switch Path (Keep Files)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cancel", fontSize = 12.sp)
            }
        }
    )
}

@Composable
fun MigrationProgressDialog(
    progress: MigrationProgress,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = { if (progress.isCompleted) onDismiss() }) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (progress.errorMessage != null) Icons.Default.Warning else if (progress.isCompleted) Icons.Default.CheckCircle else Icons.AutoMirrored.Filled.DriveFileMove,
                        contentDescription = null,
                        tint = if (progress.errorMessage != null) EmergencyRed else if (progress.isCompleted) NeonGreen else CyberCyan,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (progress.errorMessage != null) "Migration Failed" else if (progress.isCompleted) "Migration Completed" else "Migrating Footage...",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (progress.errorMessage != null) {
                    Text(
                        text = progress.errorMessage,
                        color = EmergencyRed,
                        fontSize = 13.sp,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155), contentColor = Color.White)
                    ) {
                        Text("Dismiss", fontWeight = FontWeight.Bold)
                    }
                } else if (progress.isCompleted) {
                    Text(
                        text = "Successfully moved ${progress.totalFiles} recording${if (progress.totalFiles > 1) "s" else ""} and telemetry sidecars to the new storage location.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black)
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        text = "Moving clip ${progress.currentFileIndex} of ${progress.totalFiles}:",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = progress.currentFileName,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { progress.progressPercentage },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = CyberCyan,
                        trackColor = Color(0xFF1E293B)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${(progress.progressPercentage * 100).toInt()}%",
                            color = CyberCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${StorageDirectoryManager.StorageMetrics.formatBytes(progress.bytesTransferred)} / ${StorageDirectoryManager.StorageMetrics.formatBytes(progress.totalBytes)}",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Please keep the app open until migration finishes.",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
