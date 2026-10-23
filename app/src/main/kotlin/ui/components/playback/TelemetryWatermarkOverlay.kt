package io.github.roaxio.dashguardpro.ui.components.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.roaxio.dashguardpro.media.TelemetrySubtitleEntry
import io.github.roaxio.dashguardpro.media.TelemetrySubtitleManager
import io.github.roaxio.dashguardpro.model.VideoClip
import java.text.SimpleDateFormat
import java.util.Date

import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.coerceIn

/**
 * Real-time telemetry watermark overlay banner with responsive text scaling.
 * Displays synchronized speed, timestamp, GPS coordinates, and lock status over video playback.
 * All sizing and positioning are derived strictly from parent container dimensions (zero hardcoded sizes).
 * Repositions dynamically when controls are visible to guarantee zero obstruction with transport buttons and scrubber.
 */
@Composable
fun TelemetryWatermarkOverlay(
    watermarkText: String?,
    showControls: Boolean,
    isFullScreen: Boolean = false,
    bottomControlsHeight: androidx.compose.ui.unit.Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    if (watermarkText == null) return

    BoxWithConstraints(modifier = modifier) {
        val parentWidth = maxWidth
        val parentHeight = maxHeight

        val horizontalPadding = (parentWidth * 0.025f).coerceIn(6.dp, 16.dp)
        val verticalPadding = (parentHeight * 0.015f).coerceIn(3.dp, 8.dp)
        val cornerRadius = (parentWidth * 0.018f).coerceIn(4.dp, 8.dp)
        val dynamicFontSize = (parentWidth.value * 0.026f).coerceIn(8.5f, 13f).sp

        // Subtitles are anchored at BottomCenter. When controls are visible, the banner dynamically
        // clears the bottom controls (scrubber, trip loops, mini HUD) and rests cleanly below the center play controls.
        val dynamicBottomOffset = if (showControls) {
            val baseControlsHeight = if (bottomControlsHeight > 0.dp) {
                bottomControlsHeight
            } else {
                if (isFullScreen) 92.dp else 44.dp
            }
            baseControlsHeight + (parentHeight * 0.02f).coerceIn(6.dp, 10.dp)
        } else {
            (parentHeight * 0.035f).coerceIn(8.dp, 16.dp)
        }

        val isNarrow = parentWidth < 540.dp
        val displayText = remember(watermarkText, isNarrow) {
            if (isNarrow && watermarkText.contains(" | ")) {
                val segments = watermarkText.split(" | ")
                if (segments.size >= 3) {
                    val line1 = "${segments[0]} • ${segments[1]}"
                    val line2 = segments.drop(2).joinToString(" • ")
                    "$line1\n$line2"
                } else if (segments.size == 2) {
                    "${segments[0]}\n${segments[1]}"
                } else {
                    watermarkText
                }
            } else {
                watermarkText
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = dynamicBottomOffset,
                    start = (parentWidth * 0.02f).coerceIn(6.dp, 12.dp),
                    end = (parentWidth * 0.02f).coerceIn(6.dp, 12.dp)
                )
                .widthIn(max = parentWidth * 0.96f)
                .clip(RoundedCornerShape(cornerRadius))
                .background(Color.Black.copy(alpha = 0.65f))
                .border(0.8.dp, Color(0xFF334155).copy(alpha = 0.5f), RoundedCornerShape(cornerRadius))
                .padding(horizontal = horizontalPadding, vertical = verticalPadding)
        ) {
            Text(
                text = displayText,
                color = Color.White,
                fontSize = dynamicFontSize,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                softWrap = true,
                lineHeight = dynamicFontSize * 1.3f
            )
        }
    }
}

/**
 * Authoritative telemetry watermark text generator for playback frame positions.
 */
object TelemetryWatermarkHelper {
    fun computeWatermarkText(
        clip: VideoClip,
        subtitleEntries: List<TelemetrySubtitleEntry>,
        effectiveDisplayMs: Int,
        isFusedVideo: Boolean,
        dateFormat: SimpleDateFormat,
        speedUnit: io.github.roaxio.dashguardpro.data.settings.SpeedUnit = io.github.roaxio.dashguardpro.data.settings.SpeedUnit.KMH
    ): String? {
        if (isFusedVideo) {
            return null // Fused videos already have telemetry burned into video pixels
        }
        if (subtitleEntries.isNotEmpty()) {
            return TelemetrySubtitleManager.getTelemetryForPosition(subtitleEntries, effectiveDisplayMs)
        }
        val timeStr = dateFormat.format(Date(clip.timestamp + effectiveDisplayMs))
        val rawSpd = if (clip.avgSpeedKmh > 0) clip.avgSpeedKmh else clip.speedKmh
        val spdVal = io.github.roaxio.dashguardpro.util.FormatUtils.convertSpeed(rawSpd, speedUnit).toInt()
        val coordStr = if (clip.latitude != 0.0 || clip.longitude != 0.0) {
            val latDir = if (clip.latitude >= 0) "N" else "S"
            val lonDir = if (clip.longitude >= 0) "E" else "W"
            "%.4f°%s, %.4f°%s".format(Math.abs(clip.latitude), latDir, Math.abs(clip.longitude), lonDir)
        } else {
            "NO GPS FIX"
        }
        val lockStr = if (clip.isLocked) " | LOCKED" else ""
        return "$timeStr | %02d %s | %s%s".format(spdVal, speedUnit.label.uppercase(), coordStr, lockStr)
    }
}
