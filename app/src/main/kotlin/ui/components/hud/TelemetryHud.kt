package io.github.roaxio.dashguardpro.ui.components.hud

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.roaxio.dashguardpro.data.settings.AppMode
import io.github.roaxio.dashguardpro.sensor.BatteryInfo
import io.github.roaxio.dashguardpro.sensor.TemperatureStatus
import io.github.roaxio.dashguardpro.sensor.GForceData
import io.github.roaxio.dashguardpro.sensor.TelemetryData
import io.github.roaxio.dashguardpro.ui.theme.CyberCyan
import io.github.roaxio.dashguardpro.ui.theme.NeonGreen
import io.github.roaxio.dashguardpro.ui.theme.SafetyAmber
import io.github.roaxio.dashguardpro.ui.theme.SafetyRed
import io.github.roaxio.dashguardpro.ui.theme.SafetyYellow
import io.github.roaxio.dashguardpro.viewmodel.state.DashcamUiState
import io.github.roaxio.dashguardpro.data.settings.HudDisplayMode
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Automotive HUD Overlay
 * Container backgrounds of all HUDs (in both FULL and COMPACT modes) are styled at 75%-80% opacity (0xCC)
 * for a sleek smoked-glass aesthetic, while all text, digits, icons, and indicators remain 100% opaque
 * for crystalline optical clarity against the live camera stream.
 *
 * FULL Mode: Full telemetry (Top status bar with power & GPS, middle speedometer with compass/altitude, G-Force/Sentry radar pod, bottom watermark).
 * COMPACT Mode: Combined Recording & Speed HUD in the top left corner; minimal distraction with road view unobstructed.
 */
@Composable
fun TelemetryHud(
    uiState: DashcamUiState,
    telemetry: TelemetryData,
    gForce: GForceData,
    battery: BatteryInfo,
    mode: AppMode,
    modifier: Modifier = Modifier,
    onAutoDismissMessage: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hudPulse")
    val recAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recAlpha"
    )

    val radarSweep by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarSweep"
    )

    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    val timeString = dateFormat.format(Date())
    val isFullMode = uiState.hudDisplayMode == HudDisplayMode.FULL

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val parentWidth = maxWidth
        val parentHeight = maxHeight

        val hudHorizontalPadding = (parentWidth * 0.025f).coerceIn(6.dp, 16.dp)
        val hudVerticalPadding = (parentHeight * 0.015f).coerceIn(4.dp, 12.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = hudHorizontalPadding, vertical = hudVerticalPadding),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP SECTION: STATUS STRIP & INCIDENT ALERT BANNER
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isFullMode) {
                    AutomotiveFullTopHudBar(
                        uiState = uiState,
                        battery = battery,
                        mode = mode,
                        telemetry = telemetry,
                        recAlpha = recAlpha,
                        parentWidth = parentWidth,
                        parentHeight = parentHeight
                    )
                } else {
                    // COMPACT MODE: Speed HUD is combined directly with the Recording HUD in the top left
                    AutomotiveCompactTopCombinedHudBar(
                        uiState = uiState,
                        telemetry = telemetry,
                        mode = mode,
                        recAlpha = recAlpha,
                        parentWidth = parentWidth,
                        parentHeight = parentHeight
                    )
                }

                // Transient auto-dismissing notifications banner
                IncidentAlertBanner(
                    uiState = uiState,
                    parentWidth = parentWidth,
                    onAutoDismiss = onAutoDismissMessage
                )
            }

            // MIDDLE SECTION: SPEED & SENSOR DYNAMICS POD (Visible in FULL mode only)
            if (isFullMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = (parentHeight * 0.01f).coerceIn(2.dp, 6.dp)),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Speedometer Pod with Compass and Altitude
                    AutomotiveSpeedPod(
                        telemetry = telemetry,
                        maxSpeed = uiState.tripStats.maxSpeedKmh,
                        parentWidth = parentWidth,
                        parentHeight = parentHeight
                    )

                    // Right Pod (G-Force / Sentry Radar)
                    if (mode == AppMode.SENTRY) {
                        CompactSentryRadar(
                            uiState = uiState,
                            radarSweep = radarSweep,
                            parentWidth = parentWidth,
                            parentHeight = parentHeight
                        )
                    } else {
                        CompactGForcePod(
                            gForce = gForce,
                            parentWidth = parentWidth,
                            parentHeight = parentHeight
                        )
                    }
                }
            } else {
                // Spacer to keep layout balanced in COMPACT mode
                Spacer(modifier = Modifier.weight(1f, fill = false))
            }

            // BOTTOM SECTION: WATERMARK TIMESTAMP & GPS COORDINATES
            if (isFullMode) {
                BottomWatermarkBar(
                    telemetry = telemetry,
                    timeString = timeString,
                    parentWidth = parentWidth,
                    parentHeight = parentHeight
                )
            } else {
                // Smoked-glass 75-80% opacity legal timestamp in compact mode (with 100% crisp text)
                val timeCapsuleRadius = (parentWidth * 0.015f).coerceIn(4.dp, 8.dp)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(timeCapsuleRadius))
                        .background(Color(0xCC05080E)) // 80% opacity smoked dark background
                        .border(0.8.dp, Color(0x3338BDF8), RoundedCornerShape(timeCapsuleRadius))
                        .padding(
                            horizontal = (parentWidth * 0.02f).coerceIn(6.dp, 10.dp),
                            vertical = (parentHeight * 0.008f).coerceIn(2.dp, 5.dp)
                        )
                ) {
                    Text(
                        text = timeString,
                        color = Color.White,
                        fontSize = (parentWidth.value * 0.024f).coerceIn(8.5f, 11f).sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * FULL Mode Top HUD Bar (75%-80% opacity smoked-glass background, 100% crisp text/icons)
 * Shows Recording Indicator Dot, Elapsed Time, GPS Fix status, Battery Temp/Power, and Loop Segment Gauge.
 */
@Composable
private fun AutomotiveFullTopHudBar(
    uiState: DashcamUiState,
    battery: BatteryInfo,
    mode: AppMode,
    telemetry: TelemetryData,
    recAlpha: Float,
    parentWidth: Dp,
    parentHeight: Dp
) {
    val cornerRadius = (parentWidth * 0.035f).coerceIn(10.dp, 20.dp)
    val padH = (parentWidth * 0.025f).coerceIn(8.dp, 16.dp)
    val padV = (parentHeight * 0.012f).coerceIn(4.dp, 8.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color(0x66050A14)) // Translucent tactical HUD glass
            .border(
                0.8.dp,
                Brush.horizontalGradient(
                    listOf(
                        CyberCyan.copy(alpha = 0.5f),
                        Color(0x2238BDF8),
                        CyberCyan.copy(alpha = 0.15f)
                    )
                ),
                RoundedCornerShape(cornerRadius)
            )
            .padding(horizontal = padH, vertical = padV)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: REC DOT & SEGMENT ELAPSED TIME
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (uiState.isRecording) {
                    val dotSize = (parentWidth * 0.026f).coerceIn(9.dp, 13.dp)
                    Box(
                        modifier = Modifier
                            .size(dotSize)
                            .alpha(recAlpha)
                            .clip(CircleShape)
                            .background(SafetyRed)
                            .testTag("recording_indicator_dot")
                    )
                    Spacer(modifier = Modifier.width((parentWidth * 0.015f).coerceIn(4.dp, 8.dp)))
                    Text(
                        text = "REC",
                        color = SafetyRed,
                        fontWeight = FontWeight.Black,
                        fontSize = (parentWidth.value * 0.028f).coerceIn(10f, 13f).sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width((parentWidth * 0.015f).coerceIn(4.dp, 8.dp)))
                    val minutes = uiState.segmentSeconds / 60
                    val seconds = uiState.segmentSeconds % 60
                    val targetMinutes = uiState.targetSegmentSeconds / 60
                    val targetSeconds = uiState.targetSegmentSeconds % 60
                    Text(
                        text = "%02d:%02d / %02d:%02d".format(minutes, seconds, targetMinutes, targetSeconds),
                        color = Color.White,
                        fontSize = (parentWidth.value * 0.030f).coerceIn(10.5f, 14f).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    val dotSize = (parentWidth * 0.024f).coerceIn(8.dp, 12.dp)
                    Box(
                        modifier = Modifier
                            .size(dotSize)
                            .clip(CircleShape)
                            .background(if (mode == AppMode.SENTRY) NeonGreen else CyberCyan)
                    )
                    Spacer(modifier = Modifier.width((parentWidth * 0.015f).coerceIn(4.dp, 8.dp)))
                    Text(
                        text = if (mode == AppMode.SENTRY) "SENTRY ARMED" else "STANDBY",
                        color = if (mode == AppMode.SENTRY) NeonGreen else CyberCyan,
                        fontSize = (parentWidth.value * 0.028f).coerceIn(10f, 13f).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (uiState.isSegmentLocked) {
                    Spacer(modifier = Modifier.width((parentWidth * 0.02f).coerceIn(6.dp, 10.dp)))
                    Surface(
                        shape = RoundedCornerShape((parentWidth * 0.012f).coerceIn(4.dp, 8.dp)),
                        color = SafetyAmber.copy(alpha = 0.85f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(
                                horizontal = (parentWidth * 0.015f).coerceIn(4.dp, 8.dp),
                                vertical = (parentHeight * 0.005f).coerceIn(2.dp, 4.dp)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked",
                                tint = Color.Black,
                                modifier = Modifier.size((parentWidth * 0.025f).coerceIn(9.dp, 12.dp))
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "LOCKED",
                                color = Color.Black,
                                fontSize = (parentWidth.value * 0.022f).coerceIn(8.5f, 11f).sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Right: Sensor Readouts (GPS, Battery Temp, Power)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((parentWidth * 0.02f).coerceIn(6.dp, 12.dp))
            ) {
                // GPS Fix Indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (telemetry.hasGpsFix) Icons.Default.GpsFixed else Icons.Default.GpsNotFixed,
                        contentDescription = "GPS",
                        tint = if (telemetry.hasGpsFix) CyberCyan else SafetyAmber,
                        modifier = Modifier.size((parentWidth * 0.032f).coerceIn(11.dp, 16.dp))
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (telemetry.hasGpsFix) "GPS 3D" else "NO GPS",
                        color = if (telemetry.hasGpsFix) CyberCyan else SafetyAmber,
                        fontSize = (parentWidth.value * 0.024f).coerceIn(8.5f, 11f).sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Battery Temp
                val tempColor = when (battery.temperatureStatus) {
                    TemperatureStatus.HOT, TemperatureStatus.CRITICAL -> SafetyRed
                    TemperatureStatus.WARM -> SafetyAmber
                    TemperatureStatus.COOL, TemperatureStatus.NORMAL -> Color(0xFF94A3B8)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Thermostat,
                        contentDescription = "Battery Temp",
                        tint = tempColor,
                        modifier = Modifier.size((parentWidth * 0.028f).coerceIn(10.dp, 14.dp))
                    )
                    Text(
                        text = "${battery.temperatureC.toInt()}°C",
                        color = tempColor,
                        fontSize = (parentWidth.value * 0.025f).coerceIn(9f, 11.5f).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Battery Level
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (battery.isPlugged) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Power Connected",
                            tint = NeonGreen,
                            modifier = Modifier.size((parentWidth * 0.028f).coerceIn(10.dp, 14.dp))
                        )
                    }
                    Text(
                        text = "${battery.levelPct}%",
                        color = if (battery.levelPct < 20) SafetyRed else Color.White,
                        fontSize = (parentWidth.value * 0.025f).coerceIn(9f, 11.5f).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Loop Segment Progress Gauge
        if (uiState.isRecording) {
            Spacer(modifier = Modifier.height((parentHeight * 0.008f).coerceIn(3.dp, 6.dp)))
            val segSec = uiState.segmentSeconds.toFloat()
            val totalSegSec = maxOf(1f, uiState.targetSegmentSeconds.toFloat())
            val progress = (segSec / totalSegSec).coerceIn(0f, 1f)
            val barHeight = (parentHeight * 0.005f).coerceIn(2.dp, 3.5.dp)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .clip(RoundedCornerShape(barHeight / 2f))
                    .background(Color(0x661E293B))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(barHeight)
                        .clip(RoundedCornerShape(barHeight / 2f))
                        .background(if (uiState.isSegmentLocked) SafetyAmber else CyberCyan)
                )
            }
        }
    }
}

/**
 * COMPACT Mode Combined Recording & Speed HUD Bar (Top Left)
 * Seamlessly integrates the recording state (REC dot, elapsed time) with real-time Speed telemetry.
 * Styled with an 80% opacity smoked-glass cockpit pill container with 100% crisp digits and text.
 */
@Composable
private fun AutomotiveCompactTopCombinedHudBar(
    uiState: DashcamUiState,
    telemetry: TelemetryData,
    mode: AppMode,
    recAlpha: Float,
    parentWidth: Dp,
    parentHeight: Dp
) {
    val animatedSpeed by animateFloatAsState(
        targetValue = telemetry.speedDisplay,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "animatedSpeedCompact"
    )

    val speedColor = when {
        animatedSpeed > 120f -> SafetyRed
        animatedSpeed > 90f -> SafetyAmber
        else -> Color.White
    }

    val cornerRadius = (parentWidth * 0.03f).coerceIn(8.dp, 16.dp)

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color(0xCC070C16)) // Sleek smoked cockpit glass
            .border(
                1.dp,
                Brush.horizontalGradient(
                    listOf(CyberCyan.copy(alpha = 0.45f), Color(0x2238BDF8))
                ),
                RoundedCornerShape(cornerRadius)
            )
            .padding(
                horizontal = (parentWidth * 0.025f).coerceIn(8.dp, 14.dp),
                vertical = (parentHeight * 0.01f).coerceIn(4.dp, 8.dp)
            )
            .testTag("speedometer_hud_card"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // --- SECTION 1: RECORDING STATUS ---
        if (uiState.isRecording) {
            val dotSize = (parentWidth * 0.024f).coerceIn(8.dp, 12.dp)
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .alpha(recAlpha)
                    .clip(CircleShape)
                    .background(SafetyRed)
                    .testTag("recording_indicator_dot")
            )
            Spacer(modifier = Modifier.width((parentWidth * 0.012f).coerceIn(4.dp, 8.dp)))
            Text(
                text = "REC",
                color = SafetyRed,
                fontWeight = FontWeight.Black,
                fontSize = (parentWidth.value * 0.028f).coerceIn(9.5f, 12.5f).sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width((parentWidth * 0.012f).coerceIn(4.dp, 8.dp)))
            val minutes = uiState.segmentSeconds / 60
            val seconds = uiState.segmentSeconds % 60
            Text(
                text = "%02d:%02d".format(minutes, seconds),
                color = Color.White,
                fontSize = (parentWidth.value * 0.030f).coerceIn(10f, 13.5f).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        } else {
            val dotSize = (parentWidth * 0.022f).coerceIn(7.dp, 10.dp)
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(if (mode == AppMode.SENTRY) NeonGreen else CyberCyan)
            )
            Spacer(modifier = Modifier.width((parentWidth * 0.012f).coerceIn(4.dp, 8.dp)))
            Text(
                text = if (mode == AppMode.SENTRY) "SENTRY" else "STANDBY",
                color = if (mode == AppMode.SENTRY) NeonGreen else CyberCyan,
                fontSize = (parentWidth.value * 0.026f).coerceIn(9f, 12f).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        // --- SUBTLE VERTICAL DIVIDER ---
        Spacer(modifier = Modifier.width((parentWidth * 0.02f).coerceIn(6.dp, 12.dp)))
        Box(
            modifier = Modifier
                .width(1.dp)
                .height((parentHeight * 0.025f).coerceIn(12.dp, 20.dp))
                .background(Color(0x4464748B))
        )
        Spacer(modifier = Modifier.width((parentWidth * 0.02f).coerceIn(6.dp, 12.dp)))

        // --- SECTION 2: SPEED HUD INFORMATION ---
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(bottom = 1.dp)
        ) {
            val speedSp = (parentWidth.value * 0.045f).coerceIn(16f, 24f).sp
            Text(
                text = "%02d".format(animatedSpeed.toInt()),
                color = speedColor,
                fontSize = speedSp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                lineHeight = speedSp
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = telemetry.speedUnitLabel.uppercase(),
                color = Color(0xFF94A3B8),
                fontSize = (parentWidth.value * 0.022f).coerceIn(8.5f, 11f).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 1.dp)
            )
        }

        // --- SECTION 3: LOCK STATUS BADGE (IF LOCKED) ---
        if (uiState.isSegmentLocked) {
            Spacer(modifier = Modifier.width((parentWidth * 0.02f).coerceIn(6.dp, 10.dp)))
            Surface(
                shape = RoundedCornerShape((parentWidth * 0.012f).coerceIn(4.dp, 8.dp)),
                color = SafetyAmber.copy(alpha = 0.85f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = Color.Black,
                        modifier = Modifier.size((parentWidth * 0.025f).coerceIn(9.dp, 12.dp))
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "LOCK",
                        color = Color.Black,
                        fontSize = (parentWidth.value * 0.020f).coerceIn(8f, 10f).sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/**
 * Transient Notification Pop-Up (75-80% opacity background, 100% crisp text)
 * Auto-dismisses after 2.5 seconds with smooth fade and slide animations.
 */
@Composable
private fun IncidentAlertBanner(
    uiState: DashcamUiState,
    parentWidth: Dp = 360.dp,
    onAutoDismiss: () -> Unit = {}
) {
    LaunchedEffect(uiState.recentIncidentMessage) {
        if (uiState.recentIncidentMessage != null) {
            delay(2500L)
            onAutoDismiss()
        }
    }

    AnimatedVisibility(
        visible = uiState.recentIncidentMessage != null || uiState.unplugCountdownSeconds != null,
        enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { -it },
        exit = fadeOut(tween(350)) + slideOutVertically(tween(350)) { -it }
    ) {
        val bannerCorner = (parentWidth * 0.028f).coerceIn(8.dp, 16.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(bannerCorner))
                .background(
                    if (uiState.showImpactAlert || uiState.isMotionTriggered)
                        Brush.horizontalGradient(
                            listOf(
                                SafetyRed.copy(alpha = 0.80f),
                                Color(0xFF7F1D1D).copy(alpha = 0.75f)
                            )
                        )
                    else
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF1E293B).copy(alpha = 0.80f),
                                Color(0xFF0F172A).copy(alpha = 0.75f)
                            )
                        )
                )
                .border(
                    1.dp,
                    if (uiState.showImpactAlert || uiState.isMotionTriggered) SafetyRed else SafetyAmber,
                    RoundedCornerShape(bannerCorner)
                )
                .padding(horizontal = (parentWidth * 0.028f).coerceIn(8.dp, 14.dp), vertical = 6.dp)
        ) {
            val alertText = when {
                uiState.unplugCountdownSeconds != null ->
                    "🔌 Power Disconnected: Auto shutdown in ${uiState.unplugCountdownSeconds}s"
                else -> uiState.recentIncidentMessage ?: ""
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (uiState.showImpactAlert) Icons.Default.Warning else Icons.Default.Shield,
                    contentDescription = "Alert",
                    tint = Color.White,
                    modifier = Modifier.size((parentWidth * 0.035f).coerceIn(12.dp, 18.dp))
                )
                Spacer(modifier = Modifier.width(6.dp))
                val alertSp = (parentWidth.value * 0.028f).coerceIn(10f, 13f).sp
                Text(
                    text = alertText,
                    color = Color.White,
                    fontSize = alertSp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = alertSp * 1.35f,
                    softWrap = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Frameless Jet Avionics Speedometer HUD Projection (Option 1)
 * Eliminates opaque bounding boxes in favor of projected digital avionics.
 * Features tactical corner brackets, glowing monospace speed digits, and integrated heading/altitude.
 * Sizing and typography are strictly proportional to container dimensions (AGENTS.md Rule 22).
 */
@Composable
private fun AutomotiveSpeedPod(
    telemetry: TelemetryData,
    maxSpeed: Float,
    parentWidth: Dp,
    parentHeight: Dp
) {
    val animatedSpeed by animateFloatAsState(
        targetValue = telemetry.speedDisplay,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "animatedSpeedFull"
    )

    val speedColor = when {
        animatedSpeed > 120f -> SafetyRed
        animatedSpeed > 90f -> SafetyAmber
        else -> CyberCyan
    }

    val bracketLen = (parentWidth * 0.038f).coerceIn(12.dp, 18.dp)
    val strokeWidth = 2.dp

    Box(
        modifier = Modifier
            .drawBehind {
                val bLenPx = bracketLen.toPx()
                val sWidthPx = strokeWidth.toPx()
                val bracketColor = CyberCyan.copy(alpha = 0.85f)

                // Top-Left Tactical Bracket ⌜
                drawLine(bracketColor, Offset(0f, 0f), Offset(bLenPx, 0f), sWidthPx)
                drawLine(bracketColor, Offset(0f, 0f), Offset(0f, bLenPx), sWidthPx)

                // Bottom-Left Tactical Bracket ⌞
                drawLine(bracketColor, Offset(0f, size.height), Offset(bLenPx, size.height), sWidthPx)
                drawLine(bracketColor, Offset(0f, size.height), Offset(0f, size.height - bLenPx), sWidthPx)
            }
            .padding(
                start = (parentWidth * 0.024f).coerceIn(8.dp, 14.dp),
                top = 2.dp,
                bottom = 2.dp,
                end = (parentWidth * 0.02f).coerceIn(6.dp, 12.dp)
            )
            .testTag("speedometer_hud_card")
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            val speedFontSize = (parentWidth.value * 0.115f).coerceIn(38f, 60f).sp
            val speedShadow = Shadow(
                color = speedColor.copy(alpha = 0.65f),
                offset = Offset(0f, 0f),
                blurRadius = 16f
            )

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "%02d".format(animatedSpeed.toInt()),
                    color = speedColor,
                    fontSize = speedFontSize,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = speedFontSize,
                    style = LocalTextStyle.current.copy(shadow = speedShadow)
                )
                Spacer(modifier = Modifier.width((parentWidth * 0.015f).coerceIn(4.dp, 8.dp)))
                Text(
                    text = telemetry.speedUnitLabel.uppercase(),
                    color = CyberCyan.copy(alpha = 0.9f),
                    fontSize = (parentWidth.value * 0.028f).coerceIn(10f, 13f).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    style = LocalTextStyle.current.copy(shadow = Shadow(CyberCyan.copy(alpha = 0.5f), blurRadius = 8f)),
                    modifier = Modifier.padding(bottom = (speedFontSize.value * 0.12f).coerceIn(4f, 8f).dp)
                )
            }

            // Cardinal Compass, Heading Degrees, Altitude, Max Speed
            val subTextShadow = Shadow(Color.Black.copy(alpha = 0.85f), Offset(0f, 2f), blurRadius = 4f)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = "Heading",
                    tint = SafetyYellow,
                    modifier = Modifier
                        .size((parentWidth * 0.028f).coerceIn(10.dp, 14.dp))
                        .rotate(telemetry.headingDegrees)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "${telemetry.headingCardinal} ${telemetry.headingDegrees.toInt()}°",
                    color = SafetyYellow,
                    fontSize = (parentWidth.value * 0.026f).coerceIn(9.5f, 12f).sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    style = LocalTextStyle.current.copy(shadow = subTextShadow)
                )
                if (telemetry.altitudeMeters != 0.0) {
                    Spacer(modifier = Modifier.width((parentWidth * 0.018f).coerceIn(5.dp, 9.dp)))
                    Text(
                        text = "ALT ${telemetry.altitudeMeters.toInt()}m",
                        color = Color(0xFFCBD5E1),
                        fontSize = (parentWidth.value * 0.024f).coerceIn(8.5f, 11f).sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        style = LocalTextStyle.current.copy(shadow = subTextShadow)
                    )
                }
                if (maxSpeed > 0f) {
                    Spacer(modifier = Modifier.width((parentWidth * 0.018f).coerceIn(5.dp, 9.dp)))
                    Text(
                        text = "MAX ${io.github.roaxio.dashguardpro.util.FormatUtils.convertSpeed(maxSpeed, telemetry.speedUnit).toInt()}",
                        color = Color(0xFF94A3B8),
                        fontSize = (parentWidth.value * 0.024f).coerceIn(8.5f, 11f).sp,
                        fontFamily = FontFamily.Monospace,
                        style = LocalTextStyle.current.copy(shadow = subTextShadow)
                    )
                }
            }
        }
    }
}

/**
 * Real-time G-Force Dynamics Radar Reticle (Option 1: Frameless Jet Avionics)
 * Frameless tactical crosshair reticle with animated G-force tracking pip.
 * Zero opaque bounding boxes or cards.
 */
@Composable
private fun CompactGForcePod(
    gForce: GForceData,
    parentWidth: Dp,
    parentHeight: Dp
) {
    val gTotal = gForce.totalG
    val gColor = when {
        gTotal > 2.2f -> SafetyRed
        gTotal > 1.5f -> SafetyAmber
        else -> CyberCyan
    }

    val podSize = (parentWidth * 0.17f).coerceIn(54.dp, 82.dp)

    Box(
        modifier = Modifier
            .size(podSize)
            .testTag("gforce_radar_pod"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = (size.minDimension / 2f - 4.dp.toPx()).coerceAtLeast(0f)

            if (outerRadius > 0f) {
                val tickLen = 6.dp.toPx()
                val reticleColor = CyberCyan.copy(alpha = 0.45f)
                val reticleSubColor = CyberCyan.copy(alpha = 0.20f)
                val stroke1 = 1.2.dp.toPx()

                // Outer segmented circle
                drawCircle(
                    color = reticleColor,
                    radius = outerRadius,
                    center = center,
                    style = Stroke(
                        width = stroke1,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )
                )

                // Inner reference circle (1.0G reference)
                drawCircle(
                    color = reticleSubColor,
                    radius = outerRadius * 0.5f,
                    center = center,
                    style = Stroke(width = stroke1)
                )

                // Tactical 4-Axis Crosshair Ticks (12, 3, 6, 9 o'clock)
                drawLine(reticleColor, Offset(center.x, center.y - outerRadius), Offset(center.x, center.y - outerRadius + tickLen), stroke1 * 1.5f)
                drawLine(reticleColor, Offset(center.x, center.y + outerRadius - tickLen), Offset(center.x, center.y + outerRadius), stroke1 * 1.5f)
                drawLine(reticleColor, Offset(center.x - outerRadius, center.y), Offset(center.x - outerRadius + tickLen, center.y), stroke1 * 1.5f)
                drawLine(reticleColor, Offset(center.x + outerRadius - tickLen, center.y), Offset(center.x + outerRadius, center.y), stroke1 * 1.5f)

                // Dynamic G-Force Position Pip
                val maxBound = outerRadius * 0.82f
                val offsetX = if (maxBound > 0f) (gForce.x * outerRadius * 0.45f).coerceIn(-maxBound, maxBound) else 0f
                val offsetY = if (maxBound > 0f) (-gForce.y * outerRadius * 0.45f).coerceIn(-maxBound, maxBound) else 0f
                val gPoint = Offset(center.x + offsetX, center.y + offsetY)

                // Soft glow halo
                drawCircle(
                    color = gColor.copy(alpha = 0.35f),
                    radius = 5.dp.toPx(),
                    center = gPoint
                )
                // Solid core dot
                drawCircle(
                    color = gColor,
                    radius = 2.5.dp.toPx(),
                    center = gPoint
                )
            }
        }

        // Digital G-Force Readout
        val gFontSize = (podSize.value * 0.15f).coerceIn(9f, 12f).sp
        Text(
            text = "${"%.2f".format(gTotal)}G",
            color = gColor,
            fontSize = gFontSize,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            style = LocalTextStyle.current.copy(
                shadow = Shadow(color = gColor.copy(alpha = 0.7f), blurRadius = 8f)
            ),
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * Sentry Mode Radar Sweep (Option 1: Frameless Jet Avionics)
 */
@Composable
private fun CompactSentryRadar(
    uiState: DashcamUiState,
    radarSweep: Float,
    parentWidth: Dp,
    parentHeight: Dp
) {
    val motionActive = uiState.isMotionTriggered
    val podSize = (parentWidth * 0.17f).coerceIn(54.dp, 82.dp)
    val accentColor = if (motionActive) SafetyRed else NeonGreen

    Box(
        modifier = Modifier
            .size(podSize)
            .testTag("sentry_radar_pod"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = (size.minDimension / 2f - 4.dp.toPx()).coerceAtLeast(0f)

            if (outerRadius > 0f) {
                val tickLen = 6.dp.toPx()
                val stroke1 = 1.2.dp.toPx()

                // Outer segmented circle
                drawCircle(
                    color = accentColor.copy(alpha = 0.5f),
                    radius = outerRadius,
                    center = center,
                    style = Stroke(
                        width = stroke1,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )
                )
                drawCircle(
                    color = accentColor.copy(alpha = 0.2f),
                    radius = outerRadius * 0.5f,
                    center = center,
                    style = Stroke(width = stroke1)
                )

                // Tactical 4-Axis Crosshair Ticks
                drawLine(accentColor, Offset(center.x, center.y - outerRadius), Offset(center.x, center.y - outerRadius + tickLen), stroke1 * 1.5f)
                drawLine(accentColor, Offset(center.x, center.y + outerRadius - tickLen), Offset(center.x, center.y + outerRadius), stroke1 * 1.5f)
                drawLine(accentColor, Offset(center.x - outerRadius, center.y), Offset(center.x - outerRadius + tickLen, center.y), stroke1 * 1.5f)
                drawLine(accentColor, Offset(center.x + outerRadius - tickLen, center.y), Offset(center.x + outerRadius, center.y), stroke1 * 1.5f)

                // Radar sweep beam
                val rad = Math.toRadians(radarSweep.toDouble())
                val lineEnd = Offset(
                    (center.x + outerRadius * cos(rad)).toFloat(),
                    (center.y + outerRadius * sin(rad)).toFloat()
                )
                drawLine(
                    color = accentColor,
                    start = center,
                    end = lineEnd,
                    strokeWidth = 1.5.dp.toPx()
                )
            }
        }

        val alertFontSize = (podSize.value * 0.14f).coerceIn(8.5f, 11.5f).sp
        Text(
            text = if (motionActive) "ALERT" else "SCAN",
            color = if (motionActive) Color.White else NeonGreen,
            fontSize = alertFontSize,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            style = LocalTextStyle.current.copy(
                shadow = Shadow(color = accentColor.copy(alpha = 0.8f), blurRadius = 8f)
            ),
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * Bottom Watermark Bar: GPS Lat/Long & Legal Timestamp
 * Styled as a sleek floating micro-capsule with proportional padding and typography.
 */
@Composable
private fun BottomWatermarkBar(
    telemetry: TelemetryData,
    timeString: String,
    parentWidth: Dp,
    parentHeight: Dp
) {
    val cornerRadius = (parentWidth * 0.025f).coerceIn(6.dp, 12.dp)
    val padH = (parentWidth * 0.025f).coerceIn(6.dp, 14.dp)
    val padV = (parentHeight * 0.01f).coerceIn(3.dp, 6.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color(0x66050A14)) // Translucent tactical HUD background
            .border(0.6.dp, Color(0x3300E5FF), RoundedCornerShape(cornerRadius))
            .padding(horizontal = padH, vertical = padV),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // GPS Coordinates (100% crisp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Location",
                tint = if (telemetry.hasGpsFix || telemetry.latitude != 0.0) CyberCyan else Color(0xFF64748B),
                modifier = Modifier.size((parentWidth * 0.026f).coerceIn(10.dp, 14.dp))
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = if (telemetry.hasGpsFix || (telemetry.latitude != 0.0 && telemetry.longitude != 0.0))
                    "${"%.4f".format(telemetry.latitude)}, ${"%.4f".format(telemetry.longitude)}"
                else "GPS Acquiring...",
                color = Color(0xFFCBD5E1),
                fontSize = (parentWidth.value * 0.024f).coerceIn(8.5f, 11f).sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
        }

        // Legal Timestamp (100% crisp)
        Text(
            text = timeString,
            color = Color(0xFFCBD5E1),
            fontSize = (parentWidth.value * 0.024f).coerceIn(8.5f, 11f).sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}
