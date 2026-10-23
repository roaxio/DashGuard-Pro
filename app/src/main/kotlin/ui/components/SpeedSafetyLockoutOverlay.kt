package io.github.roaxio.dashguardpro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.roaxio.dashguardpro.ui.theme.AutomotivePitchBlack
import io.github.roaxio.dashguardpro.ui.theme.CockpitPanel
import io.github.roaxio.dashguardpro.ui.theme.SafetyAmber
import io.github.roaxio.dashguardpro.ui.theme.SafetyRed
import io.github.roaxio.dashguardpro.ui.theme.SafetyYellow

/**
 * Automotive Speed Safety Lockout Barrier
 * Actively blocks deep menu navigation, gallery footage, and video playback when vehicle speed > 10 mph (~16 km/h).
 */
@Composable
fun SpeedSafetyLockoutOverlay(
    currentSpeedKmh: Float,
    onReturnToHud: () -> Unit,
    modifier: Modifier = Modifier,
    speedUnit: io.github.roaxio.dashguardpro.data.settings.SpeedUnit = io.github.roaxio.dashguardpro.data.settings.SpeedUnit.KMH
) {
    val metrics = rememberScreenMetrics()
    val outerPadding = when {
        metrics.isExpanded -> 32.dp
        metrics.isCompact -> 14.dp
        else -> 24.dp
    }
    val cardPadding = when {
        metrics.isCompact -> 16.dp
        metrics.isExpanded -> 28.dp
        else -> 24.dp
    }
    val maxCardWidth = if (metrics.isLandscape) 540.dp else 420.dp
    val iconContainerSize = if (metrics.isCompact) 54.dp else 68.dp
    val iconSize = if (metrics.isCompact) 28.dp else 36.dp
    val titleFontSize = if (metrics.isCompact) 16.sp else 18.sp
    val bodyFontSize = if (metrics.isCompact) 12.sp else 13.sp
    val spacerSection = if (metrics.isCompact) 12.dp else 16.dp
    val spacerButton = if (metrics.isCompact) 16.dp else 24.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AutomotivePitchBlack.copy(alpha = 0.96f))
            .padding(outerPadding)
            .testTag("speed_lockout_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .widthIn(max = maxCardWidth)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(CockpitPanel)
                .border(1.5.dp, SafetyAmber, RoundedCornerShape(20.dp))
                .padding(cardPadding)
        ) {
            // Warning Icon with Alert Glow
            Box(
                modifier = Modifier
                    .size(iconContainerSize)
                    .clip(CircleShape)
                    .background(SafetyRed.copy(alpha = 0.15f))
                    .border(2.dp, SafetyRed, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = "Lockout Warning",
                    tint = SafetyRed,
                    modifier = Modifier.size(iconSize)
                )
            }

            Spacer(modifier = Modifier.height(spacerSection))

            Text(
                text = "DRIVING SAFETY LOCKOUT",
                color = Color.White,
                fontSize = titleFontSize,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Vehicle in motion (${io.github.roaxio.dashguardpro.util.FormatUtils.formatSpeed(currentSpeedKmh, speedUnit)}). Deep menus, video gallery, and playback are disabled to prevent driver distraction.",
                color = Color(0xFF94A3B8),
                fontSize = bodyFontSize,
                lineHeight = if (metrics.isCompact) 16.sp else 18.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(spacerSection))

            // Current Speed Pod
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF1E293B),
                border = androidx.compose.foundation.BorderStroke(1.dp, SafetyYellow)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Speed",
                        tint = SafetyYellow,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Current Speed: ${io.github.roaxio.dashguardpro.util.FormatUtils.formatSpeed(currentSpeedKmh, speedUnit)} (> 10 mph threshold)",
                        color = SafetyYellow,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacerButton))

            // Massive Return to HUD Button (minimum 48dp target)
            Button(
                onClick = onReturnToHud,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SafetyAmber,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("return_to_hud_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "RETURN TO DRIVING HUD",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
