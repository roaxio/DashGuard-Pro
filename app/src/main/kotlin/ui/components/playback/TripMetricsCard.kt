package io.github.roaxio.dashguardpro.ui.components.playback

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.roaxio.dashguardpro.analytics.TripGroupingEngine
import io.github.roaxio.dashguardpro.model.TripSession
import io.github.roaxio.dashguardpro.model.VideoClip
import io.github.roaxio.dashguardpro.ui.theme.CyberCyan
import io.github.roaxio.dashguardpro.ui.theme.ElectricViolet
import io.github.roaxio.dashguardpro.ui.theme.EmergencyRed
import io.github.roaxio.dashguardpro.ui.theme.HudSurface
import io.github.roaxio.dashguardpro.ui.theme.NeonAmber
import io.github.roaxio.dashguardpro.ui.theme.NeonGreen
import io.github.roaxio.dashguardpro.util.FormatUtils
import io.github.roaxio.dashguardpro.util.IntentUtils
import io.github.roaxio.dashguardpro.util.effectiveEndCoordinates
import io.github.roaxio.dashguardpro.util.effectiveStartCoordinates
import kotlin.math.max

/**
 * Bottom scrollable telemetry record and actions for VideoPlayerScreen in non-fullscreen mode.
 * Encapsulates the trip continuous timeline, incident telemetry table, map navigation buttons,
 * and lock/share controls.
 */
@Composable
fun TripMetricsCard(
    clip: VideoClip,
    formattedTime: String,
    trip: TripSession?,
    isContinuous: Boolean,
    adjacencyThresholdSeconds: Int,
    onSelectClip: ((VideoClip) -> Unit)?,
    onToggleLock: () -> Unit,
    onShare: () -> Unit,
    speedUnit: io.github.roaxio.dashguardpro.data.settings.SpeedUnit = io.github.roaxio.dashguardpro.data.settings.SpeedUnit.KMH,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val avgSpeed = if (clip.avgSpeedKmh > 0f) clip.avgSpeedKmh else clip.speedKmh

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // 1. Trip Continuous Timeline Card (When viewing a clip from a multi-loop ride)
        if (trip != null && trip.clips.size > 1) {
            val currentIdx = trip.clips.indexOfFirst { it.id == clip.id || it.filePath == clip.filePath }
            val displayIdx = if (currentIdx >= 0) currentIdx + 1 else 1

            Text(
                text = "TRIP CONTINUOUS TIMELINE",
                color = CyberCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = HudSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Playing Loop $displayIdx of ${trip.clips.size}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${trip.totalDurationMs / 60000} mins • ${"%.1f".format(trip.totalDistanceMeters / 1000f)} km",
                            color = CyberCyan,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val hasBreaksInTrip = remember(trip.clips, adjacencyThresholdSeconds) {
                        trip.clips.zipWithNext().any { (prev, next) ->
                            TripGroupingEngine.isBreakBetween(prev, next, adjacencyThresholdSeconds)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0F172A))
                    ) {
                        trip.clips.forEachIndexed { idx, segClip ->
                            if (idx > 0) {
                                val prevSeg = trip.clips[idx - 1]
                                val isBreak = TripGroupingEngine.isBreakBetween(
                                    prevClip = prevSeg,
                                    nextClip = segClip,
                                    adjacencyThresholdSeconds = adjacencyThresholdSeconds
                                )
                                if (isBreak) {
                                    Box(
                                        modifier = Modifier
                                            .width(8.dp)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(ElectricViolet)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.width(3.dp))
                                }
                            }

                            val isCurrentSeg = segClip.id == clip.id || segClip.filePath == clip.filePath
                            val segWeight = max(1000L, segClip.durationMs).toFloat()
                            val isIncident = TripGroupingEngine.isIncidentClip(segClip)
                            val baseColor = when {
                                isIncident -> EmergencyRed
                                segClip.isLocked -> NeonAmber
                                segClip.triggerReason.contains("Motion") -> NeonGreen
                                else -> CyberCyan
                            }
                            val segColor = if (isCurrentSeg) {
                                baseColor
                            } else {
                                baseColor.copy(alpha = if (isIncident || segClip.isLocked) 0.8f else 0.35f)
                            }

                            Box(
                                modifier = Modifier
                                    .weight(segWeight)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(2.dp))
                                    .then(
                                        if (isCurrentSeg) Modifier.border(1.5.dp, Color.White, RoundedCornerShape(2.dp)) else Modifier
                                    )
                                    .background(segColor)
                                    .clickable {
                                        if (!isCurrentSeg && onSelectClip != null) {
                                            onSelectClip(segClip)
                                        }
                                    }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(CyberCyan))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Current", color = Color(0xFF94A3B8), fontSize = 10.sp)

                            Spacer(modifier = Modifier.width(10.dp))
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(NeonAmber))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Locked", color = Color(0xFF94A3B8), fontSize = 10.sp)

                            Spacer(modifier = Modifier.width(10.dp))
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(EmergencyRed))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Incident", color = Color(0xFF94A3B8), fontSize = 10.sp)

                            if (hasBreaksInTrip) {
                                Spacer(modifier = Modifier.width(10.dp))
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(ElectricViolet))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Break", color = Color(0xFF94A3B8), fontSize = 10.sp)
                            }
                        }

                        Text(
                            text = if (isContinuous) "Auto-Advance: ON" else "Auto-Advance: OFF",
                            color = if (isContinuous) CyberCyan else Color(0xFF64748B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // 2. Incident Telemetry Record
        Text(
            text = "INCIDENT TELEMETRY RECORD",
            color = CyberCyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = HudSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                TelemetryRow("Trigger Reason", clip.triggerReason)
                TelemetryRow("Recorded At", formattedTime)
                val avgText = if (speedUnit == io.github.roaxio.dashguardpro.data.settings.SpeedUnit.MPH) {
                    "${(avgSpeed * 0.621371f).toInt()} mph (${avgSpeed.toInt()} km/h)"
                } else {
                    "${avgSpeed.toInt()} km/h (${(avgSpeed * 0.621371f).toInt()} mph)"
                }
                TelemetryRow("Average Speed", avgText)
                TelemetryRow(
                    "Distance Traveled",
                    FormatUtils.formatDistanceMetersOrKm(clip.distanceMeters, speedUnit)
                )
                TelemetryRow("Speed Source", clip.speedSource)

                if (clip.startLatitude != 0.0 && clip.startLongitude != 0.0) {
                    TelemetryRow(
                        "Start GPS",
                        "${"%.5f".format(clip.startLatitude)}, ${"%.5f".format(clip.startLongitude)}"
                    )
                }
                if (clip.endLatitude != 0.0 && clip.endLongitude != 0.0) {
                    TelemetryRow(
                        "End GPS",
                        "${"%.5f".format(clip.endLatitude)}, ${"%.5f".format(clip.endLongitude)}"
                    )
                }
                if (clip.incidentLatitude != 0.0 && clip.incidentLongitude != 0.0) {
                    TelemetryRow(
                        "Incident GPS",
                        "${"%.5f".format(clip.incidentLatitude)}, ${"%.5f".format(clip.incidentLongitude)}"
                    )
                } else if (clip.latitude != 0.0 && clip.startLatitude == 0.0) {
                    TelemetryRow(
                        "GPS Coordinates",
                        "${"%.5f".format(clip.latitude)}, ${"%.5f".format(clip.longitude)}"
                    )
                }

                if (clip.incidentGForce > 0f) {
                    TelemetryRow("Incident Peak Impact", "${"%.1f".format(clip.incidentGForce)}G Shock")
                }

                TelemetryRow("Battery Temp", "${"%.1f".format(clip.batteryTempC)}°C")
                TelemetryRow("Duration", "${clip.durationMs / 1000} seconds")
                TelemetryRow("File Size", FormatUtils.formatFileSizeMb(clip.fileSizeBytes))
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Map Route Coordinates Determination
        val tripStart = trip?.startCoordinates ?: clip.effectiveStartCoordinates
        val tripEnd = trip?.endCoordinates ?: clip.effectiveEndCoordinates
        val videoStart = clip.effectiveStartCoordinates
        val videoEnd = clip.effectiveEndCoordinates

        val hasIncidentLocation = clip.incidentLatitude != 0.0 && clip.incidentLongitude != 0.0

        val isPartOfTrip = trip != null && trip.clips.size > 1

        // Row 1: Map Route Buttons (Whole Trip Map & Current Video Path when in a trip; Video Path only for solo clips)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isPartOfTrip) {
                OutlinedButton(
                    onClick = {
                        launchMapRouteHelper(
                            context = context,
                            origin = tripStart,
                            destination = tripEnd,
                            fallbackLabel = "Trip+Route",
                            missingCoordsMessage = "No GPS coordinates recorded for trip"
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp)
                        .testTag("player_whole_trip_map_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Whole Trip Map",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    launchMapRouteHelper(
                        context = context,
                        origin = videoStart,
                        destination = videoEnd,
                        fallbackLabel = "Video+Path",
                        missingCoordsMessage = "No GPS coordinates recorded for this video"
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 44.dp)
                    .testTag("player_video_path_button"),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Timeline,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Video Path",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Row 2: Dedicated Incident Location Button
        if (hasIncidentLocation) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    val label = if (clip.incidentGForce > 0f) {
                        "DashGuard+Incident+${"%.1f".format(clip.incidentGForce)}G"
                    } else {
                        "DashGuard+Incident+Location"
                    }
                    IntentUtils.launchMapCoordinates(context, clip.incidentLatitude, clip.incidentLongitude, label)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp)
                    .testTag("player_incident_location_button"),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, EmergencyRed.copy(alpha = 0.8f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = EmergencyRed.copy(alpha = 0.12f),
                    contentColor = EmergencyRed
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Explore,
                    contentDescription = null,
                    tint = EmergencyRed,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "View Incident Location on Maps",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Action Buttons (Lock / Share)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onToggleLock,
                modifier = Modifier
                    .weight(1f)
                    .testTag("player_toggle_lock_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = if (clip.isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (clip.isLocked) NeonAmber else Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (clip.isLocked) "Unlock" else "Lock Clip",
                    color = Color.White
                )
            }

            Button(
                onClick = onShare,
                modifier = Modifier
                    .weight(1f)
                    .testTag("player_share_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Share Video", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun TelemetryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFF94A3B8),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun launchMapRouteHelper(
    context: Context,
    origin: Pair<Double, Double>?,
    destination: Pair<Double, Double>?,
    fallbackLabel: String,
    missingCoordsMessage: String
) {
    if (origin == null && destination == null) {
        Toast.makeText(context, missingCoordsMessage, Toast.LENGTH_SHORT).show()
        return
    }
    val orig = origin ?: destination!!
    val dest = destination ?: origin!!
    if (origin != null && destination != null && origin != destination) {
        IntentUtils.launchMapRoute(context, orig.first, orig.second, dest.first, dest.second)
    } else {
        IntentUtils.launchMapCoordinates(context, orig.first, orig.second, fallbackLabel)
    }
}
