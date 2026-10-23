package io.github.roaxio.dashguardpro.ui.components.playback

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import io.github.roaxio.dashguardpro.model.VideoClip
import io.github.roaxio.dashguardpro.ui.theme.CyberCyan
import java.io.File

/**
 * Manages the video render surface for playback:
 * - Real Video Playback: AndroidView wrapping native Android VideoView with lifecycle safety.
 * - Simulated HUD Fallback: When no physical file exists on disk, renders interactive telemetry animation.
 * - Transparent Touch Interceptor: Taps on video surface toggle the HUD controls overlay.
 */
@Composable
fun VideoPlaybackSurface(
    clip: VideoClip,
    file: File,
    hasRealVideoFile: Boolean,
    isPlaying: Boolean,
    currentPlaybackMs: Int,
    lastSavedPlaybackPosMs: Int,
    totalDurationMs: Int,
    totalDurationSec: Int,
    currentPlaybackSec: Int,
    avgSpeed: Float,
    distanceKm: Float,
    isScrubbing: Boolean,
    scrubPositionSec: Float = 0f,
    isSurfacePreparing: Boolean,
    zoomState: PlaybackZoomState? = null,
    isFullScreen: Boolean = false,
    showControls: Boolean = true,
    speedUnit: io.github.roaxio.dashguardpro.data.settings.SpeedUnit = io.github.roaxio.dashguardpro.data.settings.SpeedUnit.KMH,
    onVideoViewAttached: (VideoView?) -> Unit,
    onSurfacePreparingChanged: (Boolean) -> Unit,
    onPlaybackCompleted: () -> Unit,
    onToggleControls: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.onSizeChanged { size ->
            if (zoomState != null) {
                zoomState.viewportSize = size
            }
        },
        contentAlignment = Alignment.Center
    ) {
        val videoTransformModifier = if (zoomState != null && isFullScreen) {
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .graphicsLayer {
                    scaleX = zoomState.scale
                    scaleY = zoomState.scale
                    translationX = zoomState.offset.x
                    translationY = zoomState.offset.y
                }
        } else {
            Modifier.fillMaxSize()
        }

        Box(
            modifier = videoTransformModifier,
            contentAlignment = Alignment.Center
        ) {
            if (hasRealVideoFile) {
            key(clip.id, clip.filePath) {
                AndroidView(
                    factory = { ctx ->
                        onSurfacePreparingChanged(true)
                        VideoView(ctx).apply {
                            tag = clip.filePath
                            val uri = try {
                                FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                            } catch (_: Exception) {
                                Uri.fromFile(file)
                            }
                            setVideoURI(uri)
                            setOnPreparedListener { mp ->
                                mp.isLooping = false // Never loop dashcam video clips automatically
                                if (isScrubbing) {
                                    val scrubMs = (scrubPositionSec * 1000).toInt().coerceIn(0, totalDurationMs)
                                    seekTo(scrubMs)
                                    pause()
                                } else {
                                    val targetSeek = if (lastSavedPlaybackPosMs > 0) lastSavedPlaybackPosMs else currentPlaybackMs
                                    if (targetSeek > 0) {
                                        seekTo(targetSeek)
                                    } else {
                                        seekTo(0)
                                    }
                                    if (isPlaying) {
                                        start()
                                    } else {
                                        pause()
                                    }
                                }
                                onSurfacePreparingChanged(false)
                            }
                            setOnCompletionListener {
                                onPlaybackCompleted()
                            }
                            setOnErrorListener { _, _, _ ->
                                false
                            }
                            onVideoViewAttached(this)
                        }
                    },
                    update = { vView ->
                        onVideoViewAttached(vView)
                        try {
                            if (vView.tag != clip.filePath) {
                                vView.tag = clip.filePath
                                onSurfacePreparingChanged(true)
                                val uri = try {
                                    FileProvider.getUriForFile(vView.context, "${vView.context.packageName}.fileprovider", file)
                                } catch (_: Exception) {
                                    Uri.fromFile(file)
                                }
                                vView.setVideoURI(uri)
                            }
                            if (!isScrubbing && !isSurfacePreparing) {
                                val curPos = vView.currentPosition
                                val dur = vView.duration.takeIf { it > 0 } ?: totalDurationMs
                                val reachedEnd = curPos > 1000 && (curPos >= dur - 500 || curPos >= totalDurationMs - 500)
                                if (!reachedEnd) {
                                    if (vView.isPlaying != isPlaying) {
                                        if (isPlaying) vView.start() else vView.pause()
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    },
                    onRelease = { vView ->
                        try {
                            vView.stopPlayback()
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            // Interactive Telemetry HUD Simulation Player
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF070B14))
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0F172A))
                        .border(2.dp, CyberCyan.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "${io.github.roaxio.dashguardpro.util.FormatUtils.formatSpeedUppercase(avgSpeed, speedUnit)} • ${io.github.roaxio.dashguardpro.util.FormatUtils.formatDistanceCompact(clip.distanceMeters, speedUnit, spaceBeforeMeters = true)}",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Telemetry & Video Event Log • ${clip.triggerReason}",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                val curMmSs = "%02d:%02d".format(currentPlaybackSec / 60, currentPlaybackSec % 60)
                val totMmSs = "%02d:%02d".format(totalDurationSec / 60, totalDurationSec % 60)

                Text(
                    text = "Recorded: $curMmSs / $totMmSs",
                    color = CyberCyan,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        }

        // Tappable Touch Target / Gesture Interceptor
        val isZoomGesturesActive = isFullScreen && !showControls && zoomState != null
        if (isZoomGesturesActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isFullScreen, showControls) {
                        detectTapGestures(
                            onDoubleTap = { tapOffset ->
                                zoomState?.onDoubleTap(tapOffset)
                            },
                            onTap = {
                                onToggleControls()
                            }
                        )
                    }
                    .pointerInput(isFullScreen, showControls) {
                        detectTransformGestures(panZoomLock = true) { centroid, pan, zoom, _ ->
                            zoomState?.onTransform(zoom, pan, centroid)
                        }
                    }
            )
        } else {
            // Standard click to toggle HUD controls
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleControls
                    )
            )
        }
    }
}
