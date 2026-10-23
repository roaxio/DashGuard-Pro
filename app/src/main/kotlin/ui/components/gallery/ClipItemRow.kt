package io.github.roaxio.dashguardpro.ui.components.gallery

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.roaxio.dashguardpro.model.VideoClip
import io.github.roaxio.dashguardpro.analytics.TripGroupingEngine

import io.github.roaxio.dashguardpro.ui.theme.CyberCyan
import io.github.roaxio.dashguardpro.ui.theme.EmergencyRed
import io.github.roaxio.dashguardpro.ui.theme.NeonAmber
import io.github.roaxio.dashguardpro.ui.theme.NeonGreen
import io.github.roaxio.dashguardpro.util.effectiveCoordinates
import io.github.roaxio.dashguardpro.util.FormatUtils
import io.github.roaxio.dashguardpro.util.IntentUtils
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders an individual video loop card with thumbnail preview, telemetry badge,
 * speed/g-force stats, and action controls (play, lock/unlock, share, delete, route map).
 */
@Composable
fun ClipItemRow(
    clip: VideoClip,
    onPlay: () -> Unit,
    onToggleLock: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    isFocused: Boolean = false,
    speedUnit: io.github.roaxio.dashguardpro.data.settings.SpeedUnit = io.github.roaxio.dashguardpro.data.settings.SpeedUnit.KMH
) {
    var isExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val fileExists = remember(clip.filePath) { File(clip.filePath).exists() }
    val dateOnlyFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }
    val timeOnlyFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    val fullDateTimeFormat = remember { SimpleDateFormat("EEEE, MMMM dd, yyyy  HH:mm:ss", Locale.US) }

    val dateStr = remember(clip.timestamp) { dateOnlyFormat.format(Date(clip.timestamp)) }
    val timeOnlyStr = remember(clip.timestamp) { timeOnlyFormat.format(Date(clip.timestamp)) }
    val fullDateTimeStr = remember(clip.timestamp) { fullDateTimeFormat.format(Date(clip.timestamp)) }
    val durationSec = clip.durationMs / 1000

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D131F)),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when {
                !fileExists -> EmergencyRed.copy(alpha = 0.6f)
                clip.isLocked -> NeonAmber
                else -> Color(0xFF1E293B)
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .animateContentSize()
            .testTag("video_clip_card_${clip.id}")
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            val cardWidth = maxWidth
            var videoAr by remember(clip.filePath) {
                mutableStateOf(VideoThumbnailCache.getAspectRatio(clip.filePath) ?: (16f / 9f))
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Video Thumbnail Box (Scaled to true video aspect ratio, utilizing card height)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(videoAr.coerceIn(0.75f, 2.4f), matchHeightConstraintsFirst = true)
                            .widthIn(min = 84.dp, max = cardWidth * 0.42f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (fileExists) Color(0xFF161F32) else Color(0xFF261818))
                            .border(
                                if (isFocused) 2.dp else 1.dp,
                                when {
                                    isFocused -> CyberCyan
                                    fileExists -> Color(0xFF334155)
                                    else -> EmergencyRed.copy(alpha = 0.5f)
                                },
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                if (fileExists) {
                                    onPlay()
                                } else {
                                    Toast.makeText(context, "Storage folder or SD card is disconnected.", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .testTag("video_clip_preview_${clip.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (fileExists) {
                            VideoThumbnailView(
                                filePath = clip.filePath,
                                modifier = Modifier.fillMaxSize(),
                                onAspectRatioLoaded = { ar -> videoAr = ar }
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.35f))
                            )
                        }

                        Icon(
                            imageVector = if (fileExists) Icons.Default.PlayArrow else Icons.Default.VideocamOff,
                            contentDescription = if (fileExists) "Play Video" else "Storage Disconnected",
                            tint = if (fileExists) CyberCyan else EmergencyRed,
                            modifier = Modifier.size(24.dp)
                        )

                        // Duration Overlay (YouTube / Dashcam standard)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(3.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xCC000000))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "${durationSec}s",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // Top-Right Lock Indicator
                        if (clip.isLocked) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(3.dp)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(NeonAmber),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Locked",
                                    tint = Color.Black,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Details Column: Clean non-truncating multi-row layout
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Sub-row 1: Date on left, Event Badge on right
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dateStr,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            Spacer(modifier = Modifier.width(4.dp))

                            // Event Trigger Badge
                            val isFused = clip.fileName.contains("-telemetry-fused")
                            val badgeColor = when {
                                !fileExists -> EmergencyRed
                                isFused -> CyberCyan
                                TripGroupingEngine.isIncidentClip(clip) -> EmergencyRed
                                clip.isSentryEvent || TripGroupingEngine.isSentrySurveillanceClip(clip) -> NeonGreen
                                else -> CyberCyan
                            }
                            val badgeText = when {
                                !fileExists -> "Disconnected"
                                isFused -> "Fused"
                                clip.triggerReason == "Sentry (Interrupted by Power On)" -> "Owner Return"
                                else -> clip.triggerReason
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(badgeColor.copy(alpha = 0.2f))
                                    .border(0.5.dp, badgeColor, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.5.dp)
                            ) {
                                Text(
                                    text = badgeText,
                                    color = badgeColor,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }

                        // Sub-row 2: Time & File Size
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = timeOnlyStr,
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                            Text(
                                text = FormatUtils.formatBytesCompact(clip.fileSizeBytes),
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }

                        // Sub-row 3: Telemetry (Speed & Distance)
                        val displayAvg = if (clip.avgSpeedKmh > 0f) clip.avgSpeedKmh else clip.speedKmh
                        val displayDistance = FormatUtils.formatTripDistance(clip.distanceMeters, speedUnit)

                        val statsStr = buildString {
                            if (displayAvg > 0f) append(FormatUtils.formatSpeed(displayAvg, speedUnit))
                            if (displayAvg > 0f && displayDistance != null) append(" • ")
                            if (displayDistance != null) append(displayDistance)
                        }

                        if (statsStr.isNotEmpty()) {
                            Text(
                                text = statsStr,
                                color = CyberCyan,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Sub-row 4: Folder on left, Quick Actions on right
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val parentFolderName = remember(clip.filePath) { File(clip.filePath).parentFile?.name ?: "Storage" }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = parentFolderName,
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Quick Actions: Lock, Share, Delete
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                IconButton(
                                    onClick = onToggleLock,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (clip.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = "Lock",
                                        tint = if (clip.isLocked) NeonAmber else Color(0xFF64748B),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }

                                IconButton(
                                    onClick = onShare,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        if (clip.isLocked) {
                                            Toast.makeText(
                                                context,
                                                "Locked footage is protected from deletion. Unlock the clip first.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            onDelete()
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = if (clip.isLocked) "Delete (Protected)" else "Delete",
                                        tint = if (clip.isLocked) Color(0xFF475569) else Color(0xFFEF4444),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Expandable details accordion content (Comprehensive full-text metadata upon single tap)
                if (isExpanded) {
                    Spacer(modifier = Modifier.height(10.dp))
                    androidx.compose.material3.HorizontalDivider(color = Color(0xFF1E293B))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CLIP DETAILS & METADATA",
                            color = CyberCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = if (clip.isLocked) "🔒 Protected (Locked)" else "🔓 Normal Retention",
                            color = if (clip.isLocked) NeonAmber else Color(0xFF64748B),
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Full Date & Time
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(
                            text = "RECORDED TIMESTAMP",
                            color = Color(0xFF64748B),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = fullDateTimeStr,
                            color = Color(0xFFE2E8F0),
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp
                        )
                    }

                    // Trigger Event
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(
                            text = "TRIGGER EVENT",
                            color = Color(0xFF64748B),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = clip.triggerReason + (if (clip.isSentryEvent) " [Sentry Active]" else ""),
                            color = if (clip.isLocked) NeonAmber else CyberCyan,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Duration & Size
                    val durFormatted = FormatUtils.formatDurationDetailed(durationSec)
                    val bytesFormatted = NumberFormat.getIntegerInstance().format(clip.fileSizeBytes)
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(
                            text = "DURATION & FILE SIZE",
                            color = Color(0xFF64748B),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "$durFormatted • ${FormatUtils.formatBytesCompact(clip.fileSizeBytes)} ($bytesFormatted bytes)",
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Telemetry (Speed & Distance)
                    val detailsAvg = if (clip.avgSpeedKmh > 0f) clip.avgSpeedKmh else clip.speedKmh
                    if (detailsAvg > 0f || clip.distanceMeters > 0f) {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(
                                text = "DRIVING TELEMETRY",
                                color = Color(0xFF64748B),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp
                            )
                            val telemText = buildString {
                                if (detailsAvg > 0f) append("Average Speed: ${FormatUtils.formatSpeedDetailed(detailsAvg, speedUnit)}")
                                if (clip.distanceMeters > 0f) {
                                    if (isNotEmpty()) append(" • ")
                                    append("Distance: ")
                                    append(FormatUtils.formatDistance(clip.distanceMeters, speedUnit))
                                }
                            }
                            Text(
                                text = telemText,
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    if (clip.incidentGForce > 0f) {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(
                                text = "PEAK IMPACT SHOCK",
                                color = Color(0xFF64748B),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "${"%.2f".format(clip.incidentGForce)}G Force Detected",
                                color = EmergencyRed,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // File Path
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(
                            text = "FILE LOCATION",
                            color = Color(0xFF64748B),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = clip.filePath,
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp,
                            softWrap = true
                        )
                    }

                    val coords = clip.effectiveCoordinates
                    if (coords != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                IntentUtils.launchMapCoordinates(context, coords.first, coords.second)
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Explore,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "View Location on Map (${FormatUtils.formatCoordinates(coords.first, coords.second)})",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onPlay,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Watch Video Footage", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
