package io.github.roaxio.dashguardpro.ui.components.gallery

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.roaxio.dashguardpro.model.TripSession
import io.github.roaxio.dashguardpro.model.VideoClip
import io.github.roaxio.dashguardpro.analytics.TripGroupingEngine
import io.github.roaxio.dashguardpro.ui.theme.CyberCyan
import io.github.roaxio.dashguardpro.ui.theme.EmergencyRed
import io.github.roaxio.dashguardpro.ui.theme.NeonAmber
import io.github.roaxio.dashguardpro.ui.theme.NeonGreen
import io.github.roaxio.dashguardpro.util.FormatUtils
import io.github.roaxio.dashguardpro.util.IntentUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders a complete Trip Session container, displaying session metrics,
 * quick-play full trip action, lock/unlock all clips, rename, map route,
 * delete trip action, and an expandable list of constituent loops using [ClipItemRow].
 */
@Composable
fun TripSessionCard(
    trip: TripSession,
    tripIndex: Int,
    totalTrips: Int,
    onPlayFullTrip: () -> Unit,
    onPlayClip: (VideoClip) -> Unit,
    onToggleLock: (VideoClip) -> Unit,
    onRenameTrip: () -> Unit,
    onShare: (VideoClip) -> Unit,
    onDelete: (VideoClip) -> Unit,
    onLockTrip: (TripSession) -> Unit,
    onUnlockTrip: (TripSession) -> Unit,
    onDeleteTrip: (TripSession) -> Unit,
    modifier: Modifier = Modifier,
    isExpanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    focusedClipFilePath: String? = null,
    speedUnit: io.github.roaxio.dashguardpro.data.settings.SpeedUnit = io.github.roaxio.dashguardpro.data.settings.SpeedUnit.KMH
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    val startDateStr = dateFormat.format(Date(trip.startTimeMs))
    val startTimeStr = timeFormat.format(Date(trip.startTimeMs))
    val endTimeStr = timeFormat.format(Date(trip.endTimeMs))

    val durationStr = FormatUtils.formatTripDuration(trip.totalDurationMs)
    val distanceStr = FormatUtils.formatTripDistance(trip.totalDistanceMeters, speedUnit)

    val avgSpeedStr = if (trip.avgSpeedKmh > 0f) FormatUtils.formatSpeed(trip.avgSpeedKmh, speedUnit) else null
    val maxSpeedStr = if (trip.maxSpeedKmh > 0f) FormatUtils.formatSpeed(trip.maxSpeedKmh, speedUnit) else null

    Card(
        onClick = onPlayFullTrip,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D131F)),
        border = BorderStroke(
            1.dp,
            when {
                trip.incidentCount > 0 -> EmergencyRed.copy(alpha = 0.8f)
                trip.lockedCount > 0 -> NeonAmber.copy(alpha = 0.7f)
                else -> Color(0xFF1E293B)
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("trip_card_${trip.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Session title, Date, Duration badge and Status Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (trip.isSentry) NeonGreen.copy(alpha = 0.15f) else CyberCyan.copy(alpha = 0.15f))
                            .border(1.dp, if (trip.isSentry) NeonGreen.copy(alpha = 0.4f) else CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (trip.isSentry) Icons.Default.Shield else Icons.Default.Timeline,
                            contentDescription = null,
                            tint = if (trip.isSentry) NeonGreen else CyberCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val prefix = if (trip.tripNumber > 0) "#${trip.tripNumber}" else ""
                            val titleText = when {
                                !trip.customTitle.isNullOrBlank() -> if (prefix.isNotEmpty()) "$prefix ${trip.customTitle}" else trip.customTitle!!
                                trip.isSentry -> if (prefix.isNotEmpty()) "$prefix Sentry Incident Session" else "Sentry Incident Session"
                                else -> if (prefix.isNotEmpty()) "$prefix Ride" else "Ride"
                            }
                            Text(
                                text = titleText,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f, fill = false),
                                softWrap = true
                            )
                            IconButton(
                                onClick = onRenameTrip,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Rename Trip",
                                    tint = CyberCyan,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$startDateStr • $startTimeStr – $endTimeStr",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 14.sp,
                            softWrap = true
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Right header block: Duration & Loop pill + Incident / Lock Badges
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF161F32))
                            .border(0.5.dp, Color(0xFF334155), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "$durationStr • ${trip.loopCount} ${if (trip.loopCount == 1) "loop" else "loops"}",
                            color = CyberCyan,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (trip.incidentCount > 0 || trip.lockedCount > 0) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (trip.incidentCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(EmergencyRed.copy(alpha = 0.2f))
                                        .border(0.5.dp, EmergencyRed, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "⚠️ ${trip.incidentCount} ${if (trip.incidentCount == 1) "Incident" else "Incidents"}",
                                        color = EmergencyRed,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (trip.lockedCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(NeonAmber.copy(alpha = 0.2f))
                                        .border(0.5.dp, NeonAmber, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "🔒 ${trip.lockedCount} Locked",
                                        color = NeonAmber,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Secondary Stats & Action Bar: Distance, Avg Speed, and Trip Lock/Unlock Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (distanceStr != null) {
                        Text(
                            text = "Dist:\u00A0$distanceStr",
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }

                    if (avgSpeedStr != null) {
                        Text(
                            text = "Avg:\u00A0$avgSpeedStr",
                            color = CyberCyan,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }

                // Trip Lock / Unlock Action Button
                val isFullyLocked = trip.lockedCount == trip.loopCount && trip.loopCount > 0
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isFullyLocked) NeonAmber.copy(alpha = 0.2f) else Color(0xFF161F32)
                        )
                        .border(
                            1.dp,
                            if (isFullyLocked) NeonAmber else Color(0xFF334155),
                            RoundedCornerShape(6.dp)
                        )
                        .clickable {
                            if (isFullyLocked) onUnlockTrip(trip) else onLockTrip(trip)
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isFullyLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = if (isFullyLocked) "Unlock Entire Trip" else "Lock Entire Trip",
                            tint = if (isFullyLocked) NeonAmber else CyberCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (isFullyLocked) "Trip Locked" else "Lock Trip",
                            color = if (isFullyLocked) NeonAmber else CyberCyan,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Mini Segmented Timeline Strip
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TRIP LOOP CONTINUITY",
                        color = Color(0xFF64748B),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Tap segment to play from loop",
                        color = Color(0xFF475569),
                        fontSize = 9.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF090D16))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(6.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    trip.clips.forEachIndexed { index, clip ->
                        val hasIncident = TripGroupingEngine.isIncidentClip(clip)
                        val isSegmentFocused = clip.filePath == focusedClipFilePath
                        val segmentColor = when {
                            hasIncident -> EmergencyRed
                            clip.isLocked -> NeonAmber
                            clip.isSentryEvent -> NeonGreen
                            else -> CyberCyan
                        }
                        val weight = (clip.durationMs.coerceAtLeast(1000L)).toFloat()

                        Box(
                            modifier = Modifier
                                .weight(weight)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(segmentColor.copy(alpha = 0.85f))
                                .border(
                                    width = if (isSegmentFocused) 2.dp else if (clip.isLocked || hasIncident) 1.dp else 0.5.dp,
                                    color = if (isSegmentFocused) Color.White else if (hasIncident) EmergencyRed else if (clip.isLocked) NeonAmber else segmentColor,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .clickable { onPlayClip(clip) }
                                .testTag("trip_segment_${clip.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (trip.clips.size <= 8) {
                                Text(
                                    text = "${index + 1}",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            // Action Buttons: Expand Loops Drawer, Map Route, Delete Trip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Loops Expand / Collapse Button (Primary action in row)
                OutlinedButton(
                    onClick = onToggleExpand,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isExpanded) CyberCyan else Color(0xFF334155)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isExpanded) CyberCyan else Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("expand_trip_drawer_button_${trip.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = if (isExpanded) CyberCyan else Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Loops (${trip.loopCount})",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse loops" else "Expand loops",
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Map Route Button (Only if start or end coordinates exist)
                val startCoords = trip.startCoordinates
                val endCoords = trip.endCoordinates
                if (startCoords != null || endCoords != null) {
                    OutlinedButton(
                        onClick = {
                            val origin = startCoords ?: endCoords!!
                            val destination = endCoords ?: startCoords!!
                            IntentUtils.launchMapRoute(context, origin.first, origin.second, destination.first, destination.second)
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("view_trip_map_button_${trip.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = "View Route on Map",
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                OutlinedButton(
                    onClick = { onDeleteTrip(trip) },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, EmergencyRed.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EmergencyRed),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("delete_trip_button_${trip.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Entire Trip",
                        tint = EmergencyRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Expandable Constituent Loops Drawer
            if (isExpanded) {
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = Color(0xFF1E293B))
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "INDIVIDUAL LOOPS IN THIS SESSION",
                    color = CyberCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    trip.clips.forEachIndexed { index, clip ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Loop ${index + 1} of ${trip.loopCount}",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                            )
                            ClipItemRow(
                                clip = clip,
                                isFocused = (clip.filePath == focusedClipFilePath),
                                onPlay = { onPlayClip(clip) },
                                onToggleLock = { onToggleLock(clip) },
                                onShare = { onShare(clip) },
                                onDelete = { onDelete(clip) },
                                speedUnit = speedUnit
                            )
                        }
                    }
                }
            }
        }
    }
}
