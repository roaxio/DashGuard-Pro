package io.github.roaxio.dashguardpro.ui.components.playback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.roaxio.dashguardpro.analytics.TripGroupingEngine
import io.github.roaxio.dashguardpro.model.TripSession
import io.github.roaxio.dashguardpro.model.VideoClip
import io.github.roaxio.dashguardpro.ui.theme.CyberCyan
import io.github.roaxio.dashguardpro.ui.theme.ElectricViolet
import io.github.roaxio.dashguardpro.ui.theme.EmergencyRed
import io.github.roaxio.dashguardpro.ui.theme.NeonAmber
import io.github.roaxio.dashguardpro.ui.theme.NeonGreen
import kotlin.math.max

private fun formatMmSs(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}

/**
 * Animated auto-hiding controls overlay for VideoPlayerScreen.
 * Encapsulates the top action badges, center transport controls,
 * driving break resume card, trip timeline loop strip, and timeline scrubber.
 */
@Composable
fun PlaybackControlsOverlay(
    showControls: Boolean,
    isPlaying: Boolean,
    isFullScreen: Boolean,
    isScrubbing: Boolean,
    isContinuous: Boolean,
    isAtEndOfClip: Boolean,
    currentPlaybackMs: Int,
    totalDurationMs: Int,
    totalDurationSec: Int,
    currentPlaybackSec: Int,
    scrubPositionSec: Float,
    clip: VideoClip,
    avgSpeed: Float,
    distanceKm: Float,
    trip: TripSession?,
    tripPreviousClip: VideoClip?,
    tripNextClip: VideoClip?,
    isBreakBeforeNext: Boolean,
    shouldShowBreakCard: Boolean,
    adjacencyThresholdSeconds: Int,
    onTogglePlayPause: () -> Unit,
    onReplay: () -> Unit,
    onPreviousLoop: () -> Unit,
    onNextLoop: () -> Unit,
    onResumeAfterBreak: () -> Unit,
    onToggleAutoAdvance: () -> Unit,
    onScrubValueChange: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    onToggleFullScreen: () -> Unit,
    onToggleLock: () -> Unit,
    onShare: () -> Unit,
    onSelectClip: ((VideoClip) -> Unit)?,
    onDismissControls: () -> Unit,
    onBottomControlsHeightChanged: ((Dp) -> Unit)? = null,
    speedUnit: io.github.roaxio.dashguardpro.data.settings.SpeedUnit = io.github.roaxio.dashguardpro.data.settings.SpeedUnit.KMH,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = showControls,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (!isScrubbing) {
                            onDismissControls()
                        }
                    }
                )
        ) {
            // Top Bar Inside Overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    if (isFullScreen) {
                        IconButton(
                            onClick = onToggleFullScreen,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Exit Full Screen",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // Status Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isPlaying) NeonGreen else NeonAmber)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isPlaying) "PLAYING" else "PAUSED",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    val badgeColor = when {
                        clip.triggerReason.contains("Impact") -> EmergencyRed
                        clip.triggerReason.contains("Motion") -> NeonGreen
                        clip.isLocked -> NeonAmber
                        else -> CyberCyan
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeColor.copy(alpha = 0.25f))
                            .border(0.5.dp, badgeColor, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        val displayBadgeText = if (clip.triggerReason == "Sentry (Interrupted by Power On)") "Owner Return" else clip.triggerReason
                        Text(
                            text = displayBadgeText,
                            color = badgeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Top Actions (Lock, Share in Fullscreen)
                if (isFullScreen) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onToggleLock,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (clip.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = "Lock Clip",
                                tint = if (clip.isLocked) NeonAmber else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onShare,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Center Controls (Previous Loop, Replay, Play/Pause, Next Loop, Auto-Advance)
            // Temporarily hidden while the user is actively scrubbing through the timeline
            AnimatedVisibility(
                visible = !isScrubbing,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Wing: Pushed towards center Play/Pause
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Previous Loop Button
                        IconButton(
                            onClick = onPreviousLoop,
                            enabled = tripPreviousClip != null,
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    if (tripPreviousClip != null) Color.Black.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.2f),
                                    CircleShape
                                )
                                .testTag("player_skip_previous_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = if (tripPreviousClip != null) "Previous Loop" else "No Previous Loop",
                                tint = if (tripPreviousClip != null) Color.White else Color(0xFF64748B).copy(alpha = 0.35f),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        // 2. Replay Button
                        IconButton(
                            onClick = onReplay,
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay,
                                contentDescription = "Restart",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // 3. Play / Pause Button (Strictly Centered on Screen!)
                    val isEffectivelyPlaying = isPlaying && !isAtEndOfClip
                    Button(
                        onClick = onTogglePlayPause,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        modifier = Modifier
                            .size(54.dp)
                            .testTag("player_play_pause_button"),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = if (isEffectivelyPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isEffectivelyPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Right Wing: Pushed towards center Play/Pause
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 4. Next Loop Button
                        IconButton(
                            onClick = onNextLoop,
                            enabled = tripNextClip != null,
                            modifier = Modifier
                                .size(44.dp)
                                .then(
                                    if (isBreakBeforeNext) {
                                        Modifier
                                            .border(1.dp, ElectricViolet, CircleShape)
                                            .background(ElectricViolet.copy(alpha = 0.2f), CircleShape)
                                    } else {
                                        Modifier.background(
                                            if (tripNextClip != null) Color.Black.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.2f),
                                            CircleShape
                                        )
                                    }
                                )
                                .testTag("player_skip_next_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = when {
                                    tripNextClip == null -> "No Follow-up Loop"
                                    isBreakBeforeNext   -> "Next Loop (after driving break)"
                                    else                -> "Next Loop"
                                },
                                tint = when {
                                    tripNextClip == null -> Color(0xFF64748B).copy(alpha = 0.35f)
                                    isBreakBeforeNext   -> ElectricViolet
                                    else                -> Color.White
                                },
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // 5. Auto-Advance Toggle (Only present for trips with multiple loop segments)
                        if (trip != null && trip.clips.size > 1) {
                            Spacer(modifier = Modifier.width(14.dp))
                            IconButton(
                                onClick = onToggleAutoAdvance,
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    .testTag("player_autonext_toggle_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                    contentDescription = if (isContinuous) "Auto-Advance Next Loop (ON)" else "Auto-Advance Next Loop (OFF)",
                                    tint = if (isContinuous) CyberCyan else Color(0xFF64748B),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            val density = LocalDensity.current
            // Bottom Overlay: Mini HUD, Driving Break Card, Segmented Timeline & Scrubber Slider
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { size ->
                        val heightDp = with(density) { size.height.toDp() }
                        onBottomControlsHeightChanged?.invoke(heightDp)
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Absorb touches on bottom control row to prevent accidental dismiss while scrubbing
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                // In Full-Screen: show Telemetry HUD Mini Overlay
                if (isFullScreen) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${io.github.roaxio.dashguardpro.util.FormatUtils.formatSpeedUppercase(avgSpeed, speedUnit)} • ${io.github.roaxio.dashguardpro.util.FormatUtils.formatDistanceCompact(clip.distanceMeters, speedUnit, spaceBeforeMeters = false)}",
                            color = CyberCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        if (clip.incidentGForce > 0f) {
                            Text(
                                text = "IMPACT: ${"%.1f".format(clip.incidentGForce)}G",
                                color = EmergencyRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Driving Break Resume Card
                if (shouldShowBreakCard && tripNextClip != null) {
                    val gapMs = run {
                        val prevEndMs = clip.timestamp + max(1000L, clip.durationMs.toLong())
                        (tripNextClip.timestamp - prevEndMs).coerceAtLeast(0L)
                    }
                    val gapLabel = run {
                        val totalSeconds = ((gapMs + 500L) / 1000L).coerceAtLeast(1L)
                        val hours = totalSeconds / 3600L
                        val minutes = (totalSeconds % 3600L) / 60L
                        val seconds = totalSeconds % 60L

                        when {
                            hours > 0L && minutes > 0L -> "${hours}h ${minutes}m break between clips"
                            hours > 0L -> "${hours}h break between clips"
                            minutes > 0L && seconds > 0L -> "${minutes}m ${seconds}s break between clips"
                            minutes > 0L -> "$minutes min break between clips"
                            else -> "${seconds}s break between clips"
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E293B))
                            .border(1.dp, ElectricViolet.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(ElectricViolet)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "☕  $gapLabel",
                                color = ElectricViolet,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onResumeAfterBreak,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = ElectricViolet,
                                containerColor = ElectricViolet.copy(alpha = 0.12f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ElectricViolet),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                        ) {
                            Text(
                                text = "▶ Resume",
                                color = ElectricViolet,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Segmented Trip Timeline Bar (Only displayed in fullscreen mode to avoid duplication with TripMetricsCard below the video)
                if (isFullScreen && trip != null && trip.clips.size > 1) {
                    val currentIdx = trip.clips.indexOfFirst { it.id == clip.id || it.filePath == clip.filePath }
                    val displayIdx = if (currentIdx >= 0) currentIdx + 1 else 1

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TRIP LOOPS • Loop $displayIdx of ${trip.clips.size}",
                                color = CyberCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
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
                                                .width(6.dp)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(ElectricViolet)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.width(2.dp))
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
                                            if (isCurrentSeg) Modifier.border(1.dp, Color.White, RoundedCornerShape(2.dp)) else Modifier
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
                    }
                }

                // Timeline Slider Row
                val displayPlaybackSec = if (isScrubbing) scrubPositionSec.toInt() else currentPlaybackSec
                val sliderValue = if (isScrubbing) scrubPositionSec else (currentPlaybackMs / 1000f).coerceIn(0f, totalDurationSec.toFloat())

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatMmSs(displayPlaybackSec),
                        color = CyberCyan,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )

                    val sliderInteractionSource = remember { MutableInteractionSource() }
                    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                    Slider(
                        value = sliderValue,
                        onValueChange = onScrubValueChange,
                        onValueChangeFinished = onScrubFinished,
                        valueRange = 0f..totalDurationSec.toFloat(),
                        interactionSource = sliderInteractionSource,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = CyberCyan,
                            inactiveTrackColor = Color(0xFF334155)
                        ),
                        thumb = {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .background(CyberCyan, CircleShape)
                                    .border(2.dp, Color.White, CircleShape)
                            )
                        },
                        track = { sliderState ->
                            val fraction = if (sliderState.valueRange.endInclusive > sliderState.valueRange.start) {
                                ((sliderState.value - sliderState.valueRange.start) / (sliderState.valueRange.endInclusive - sliderState.valueRange.start)).coerceIn(0f, 1f)
                            } else 0f
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                            ) {
                                val trackHeight = size.height
                                val cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                                // Inactive unplayed background track
                                drawRoundRect(
                                    color = Color(0xFF334155),
                                    size = size,
                                    cornerRadius = cornerRadius
                                )
                                // Active played progress track
                                if (fraction > 0f) {
                                    drawRoundRect(
                                        color = CyberCyan,
                                        size = Size(width = size.width * fraction, height = trackHeight),
                                        cornerRadius = cornerRadius
                                    )
                                }
                            }
                        }
                    )

                    Text(
                        text = formatMmSs(totalDurationSec),
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onToggleFullScreen,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("fullscreen_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (isFullScreen) "Exit Full Screen" else "Full Screen",
                            tint = CyberCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
