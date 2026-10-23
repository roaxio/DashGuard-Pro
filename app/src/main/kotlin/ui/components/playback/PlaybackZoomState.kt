package io.github.roaxio.dashguardpro.ui.components.playback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.roaxio.dashguardpro.ui.theme.SafetyYellow

/**
 * State coordinator managing photo-gallery style pinch-to-zoom, pan, and double-tap zoom
 * for video playback surfaces in full screen mode (AGENTS.md Rule 7 & Rule 9).
 */
@Stable
class PlaybackZoomState(
    val minScale: Float = 1.0f,
    val maxScale: Float = 5.0f,
    val doubleTapScale: Float = 2.5f
) {
    var scale by mutableFloatStateOf(1.0f)
        private set

    var offset by mutableStateOf(Offset.Zero)
        private set

    var viewportSize by mutableStateOf(IntSize.Zero)

    val isZoomed: Boolean
        get() = scale > 1.05f

    fun reset() {
        scale = 1.0f
        offset = Offset.Zero
    }

    fun onTransform(zoomChange: Float, panChange: Offset, centroid: Offset) {
        val oldScale = scale
        val newScale = (oldScale * zoomChange).coerceIn(minScale, maxScale)

        if (newScale <= 1.01f) {
            reset()
            return
        }

        // Adjust translation to scale around centroid relative to center-origin graphicsLayer
        val scaleRatio = newScale / oldScale
        val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
        val centroidOffsetFromCenter = centroid - center
        val newOffset = (offset + panChange) - centroidOffsetFromCenter * (scaleRatio - 1f)

        scale = newScale
        offset = clampOffset(newOffset, newScale)
    }

    fun onDoubleTap(tapOffset: Offset) {
        if (isZoomed) {
            reset()
        } else {
            val targetScale = doubleTapScale.coerceIn(minScale, maxScale)
            val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
            val diff = (center - tapOffset) * (targetScale - 1f)
            scale = targetScale
            offset = clampOffset(diff, targetScale)
        }
    }

    fun onDrag(dragAmount: Offset) {
        if (!isZoomed) return
        offset = clampOffset(offset + dragAmount, scale)
    }

    fun clampOffset(targetOffset: Offset, currentScale: Float): Offset {
        if (currentScale <= 1.0f || viewportSize.width <= 0 || viewportSize.height <= 0) {
            return Offset.Zero
        }
        val maxPanX = (viewportSize.width * (currentScale - 1f)) / 2f
        val maxPanY = (viewportSize.height * (currentScale - 1f)) / 2f
        return Offset(
            targetOffset.x.coerceIn(-maxPanX, maxPanX),
            targetOffset.y.coerceIn(-maxPanY, maxPanY)
        )
    }
}

@Composable
fun rememberPlaybackZoomState(
    minScale: Float = 1.0f,
    maxScale: Float = 5.0f,
    doubleTapScale: Float = 2.5f
): PlaybackZoomState {
    return remember {
        PlaybackZoomState(minScale, maxScale, doubleTapScale)
    }
}

/**
 * Sleek floating badge displaying active zoom level (e.g. 2.5×) with a one-tap reset control.
 */
@Composable
fun PlaybackZoomBadge(
    zoomState: PlaybackZoomState,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = zoomState.isZoomed,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color(0xFF0F172A).copy(alpha = 0.85f))
                .border(1.dp, SafetyYellow.copy(alpha = 0.7f), CircleShape)
                .clickable { onReset() }
                .padding(horizontal = 10.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ZoomOut,
                    contentDescription = "Reset Zoom",
                    tint = SafetyYellow,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${"%.1f".format(zoomState.scale)}×",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss Zoom",
                    tint = Color.Gray,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
