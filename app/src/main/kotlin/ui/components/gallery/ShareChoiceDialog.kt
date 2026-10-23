package io.github.roaxio.dashguardpro.ui.components.gallery

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.roaxio.dashguardpro.model.VideoClip
import io.github.roaxio.dashguardpro.media.TelemetryVideoFusionEngine
import io.github.roaxio.dashguardpro.util.srtSidecarFile
import io.github.roaxio.dashguardpro.util.videoFile
import io.github.roaxio.dashguardpro.ui.theme.CyberCyan
import io.github.roaxio.dashguardpro.ui.theme.NeonGreen
import io.github.roaxio.dashguardpro.ui.theme.SafetyAmber
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ShareChoiceDialog(
    clip: VideoClip,
    onDismiss: () -> Unit,
    onShareSeparate: (videoFile: File, srtFile: File?) -> Unit,
    onShareFused: (fusedFile: File) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val videoFile = remember(clip) { clip.videoFile }
    val srtFile = remember(clip) {
        val f = clip.srtSidecarFile
        if (f.exists()) f else null
    }

    val isAlreadyFused = remember(videoFile) {
        TelemetryVideoFusionEngine.isFusedVideoAvailable(videoFile)
    }

    var isFusingInProgress by remember { mutableStateOf(false) }
    var fusionProgress by remember { mutableFloatStateOf(0f) }
    var fusionError by remember { mutableStateOf<String?>(null) }

    // Intercept phone system hardware back button
    androidx.activity.compose.BackHandler(enabled = !isFusingInProgress) {
        onDismiss()
    }

    Dialog(
        onDismissRequest = {
            if (!isFusingInProgress) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = !isFusingInProgress,
            dismissOnClickOutside = !isFusingInProgress
        )
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Footage",
                            tint = CyberCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Share Video & Telemetry",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!isFusingInProgress) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = clip.fileName,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )

                HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 12.dp))

                if (isFusingInProgress) {
                    // Transcoding progress display
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Fusing Telemetry Overlay...",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Re-encoding video with burned-in speed, GPS, and timestamp",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        val displayProgress = if (fusionProgress > 0f) fusionProgress else 0.01f
                        val displayPct = (displayProgress * 100).toInt().coerceIn(1, 100)

                        LinearProgressIndicator(
                            progress = { displayProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = CyberCyan,
                            trackColor = Color(0xFF1E293B)
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (fusionProgress > 0f) "$displayPct%" else "Preparing encoder... 1%",
                            color = CyberCyan,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    // Option 1: Share Video + Subtitles (.srt) separately
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E293B).copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onShareSeparate(videoFile, srtFile)
                                onDismiss()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0D1B2A)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Subtitles,
                                    contentDescription = null,
                                    tint = CyberCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Share with .SRT Sidecar",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Shares original MP4 + separate .srt subtitle track. Universal compatibility with VLC & PC players.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Option 2: Fuse Telemetry Watermark & Re-encode
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isAlreadyFused) Color(0xFF0D2818).copy(alpha = 0.6f) else Color(0xFF0D1B2A).copy(alpha = 0.8f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isAlreadyFused) NeonGreen.copy(alpha = 0.6f) else CyberCyan.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isAlreadyFused) {
                                    val fusedFile = TelemetryVideoFusionEngine.getFusedVideoFile(videoFile)
                                    onShareFused(fusedFile)
                                    onDismiss()
                                } else {
                                    isFusingInProgress = true
                                    fusionProgress = 0f
                                    fusionError = null

                                    coroutineScope.launch {
                                        val resultFile = TelemetryVideoFusionEngine.fuseTelemetry(
                                            context = context,
                                            sourceVideo = videoFile,
                                            sourceSrt = srtFile,
                                            onProgress = { p -> fusionProgress = p }
                                        )
                                        isFusingInProgress = false

                                        if (resultFile != null) {
                                            try {
                                                val db = io.github.roaxio.dashguardpro.data.AppDatabase.getDatabase(context)
                                                val existing = db.clipDao().getClipByPath(resultFile.absolutePath)
                                                val fusedClip = VideoClip(
                                                    id = existing?.id ?: 0,
                                                    filePath = resultFile.absolutePath,
                                                    fileName = resultFile.name,
                                                    timestamp = clip.timestamp,
                                                    durationMs = clip.durationMs,
                                                    fileSizeBytes = resultFile.length(),
                                                    isLocked = true,
                                                    isSentryEvent = clip.isSentryEvent,
                                                    triggerReason = "Telemetry Fused",
                                                    speedKmh = clip.speedKmh,
                                                    avgSpeedKmh = clip.avgSpeedKmh,
                                                    distanceMeters = clip.distanceMeters,
                                                    speedSource = clip.speedSource,
                                                    latitude = clip.latitude,
                                                    longitude = clip.longitude,
                                                    startLatitude = clip.startLatitude,
                                                    startLongitude = clip.startLongitude,
                                                    endLatitude = clip.endLatitude,
                                                    endLongitude = clip.endLongitude,
                                                    incidentLatitude = clip.incidentLatitude,
                                                    incidentLongitude = clip.incidentLongitude,
                                                    incidentVideoOffsetSeconds = clip.incidentVideoOffsetSeconds,
                                                    incidentGForce = clip.incidentGForce,
                                                    batteryTempC = clip.batteryTempC
                                                )
                                                if (existing != null) {
                                                    db.clipDao().updateClip(fusedClip)
                                                } else {
                                                    db.clipDao().insertClip(fusedClip)
                                                }
                                            } catch (_: Exception) {}

                                            onShareFused(resultFile)
                                            onDismiss()
                                        } else {
                                            fusionError = "Could not re-encode video."
                                            Toast.makeText(context, "Fusion failed. Try sharing with .SRT instead.", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isAlreadyFused) NeonGreen.copy(alpha = 0.15f) else CyberCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isAlreadyFused) Icons.Default.CheckCircle else Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = if (isAlreadyFused) NeonGreen else CyberCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Fuse Telemetry into Video",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (isAlreadyFused) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "READY",
                                            color = NeonGreen,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier
                                                .background(NeonGreen.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isAlreadyFused) {
                                        "Cached fused video found on storage. Instant share without waiting for re-encoding."
                                    } else {
                                        "Burns speed, GPS, and timestamp directly into video pixels as a permanent overlay (-telemetry-fused.mp4)."
                                    },
                                    color = if (isAlreadyFused) NeonGreen.copy(alpha = 0.8f) else Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }

                    if (fusionError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = fusionError ?: "",
                            color = SafetyAmber,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
