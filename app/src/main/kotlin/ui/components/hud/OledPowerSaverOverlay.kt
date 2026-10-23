package io.github.roaxio.dashguardpro.ui.components.hud

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.roaxio.dashguardpro.data.settings.AppMode
import io.github.roaxio.dashguardpro.sensor.BatteryInfo
import io.github.roaxio.dashguardpro.ui.theme.AutomotivePitchBlack
import io.github.roaxio.dashguardpro.ui.theme.CyberCyan
import io.github.roaxio.dashguardpro.ui.theme.NeonGreen
import io.github.roaxio.dashguardpro.ui.theme.SafetyAmber
import io.github.roaxio.dashguardpro.ui.theme.SafetyRed
import io.github.roaxio.dashguardpro.ui.theme.SafetyYellow
import io.github.roaxio.dashguardpro.viewmodel.state.DashcamUiState
import io.github.roaxio.dashguardpro.viewmodel.state.DeviceSleepMode
import io.github.roaxio.dashguardpro.viewmodel.state.PreSleepOperatingState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * True-Black OLED Stealth & Sentry Mode Overlay
 * - 99% pitch black (#000000) for OLED battery & thermal protection
 * - Pulsing single red alarm LED / deterrent ring in center
 * - UI Lock: requires 3-second long press to unlock
 * - 0.5s White Flash deterrent on bump / motion detection
 */
@Composable
fun OledPowerSaverOverlay(
    uiState: DashcamUiState,
    battery: BatteryInfo,
    mode: AppMode,
    isAutoDim: Boolean = false,
    lowBatteryLimit: Int = 10,
    onToggleLock: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    // Alarm LED Pulsing Animation
    val infiniteTransition = rememberInfiniteTransition(label = "sentryAlarmPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alarmAlpha"
    )

    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 18.dp.value,
        targetValue = 38.dp.value,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alarmRadius"
    )

    val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
    val timeStr = timeFormat.format(Date())

    // Visual Deterrent Bump Flash (0.5s white flash on motion/impact)
    var isBumpFlashVisible by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.showImpactAlert, uiState.isMotionTriggered) {
        if (uiState.showImpactAlert || uiState.isMotionTriggered) {
            isBumpFlashVisible = true
            delay(500)
            isBumpFlashVisible = false
        }
    }

    val isThermalCooling = uiState.deviceSleepMode == DeviceSleepMode.THERMAL_COOLING || uiState.isThermalCoolingActive
    val isLowBatterySleep = uiState.deviceSleepMode == DeviceSleepMode.LOW_BATTERY_STANDBY || uiState.isLowBatterySleepActive
    val isSleepMode = isThermalCooling || isLowBatterySleep
    val isSentryOrCooling = mode == AppMode.SENTRY || uiState.isSentryArmed || isSleepMode
    var isPeekVisible by remember { mutableStateOf(false) }
    var peekTimerJob by remember { mutableStateOf<Job?>(null) }
    var lastTapTimeMs by remember { mutableStateOf(0L) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AutomotivePitchBlack)
            .pointerInput(isAutoDim, isSentryOrCooling) {
                if (isSentryOrCooling) {
                    detectTapGestures(
                        onDoubleTap = {
                            onDismiss()
                        },
                        onTap = {
                            val now = System.currentTimeMillis()
                            if (now - lastTapTimeMs < 800L) {
                                // Manual double tap fallback: Tapped twice within 800ms!
                                lastTapTimeMs = 0L
                                onDismiss()
                            } else {
                                lastTapTimeMs = now
                                isPeekVisible = true
                                peekTimerJob?.cancel()
                                peekTimerJob = coroutineScope.launch {
                                    delay(5000)
                                    isPeekVisible = false
                                }
                            }
                        }
                    )
                } else if (isAutoDim) {
                    detectTapGestures(
                        onTap = { onDismiss() }
                    )
                } else {
                    detectTapGestures(
                        onDoubleTap = { onDismiss() },
                        onTap = { onDismiss() }
                    )
                }
            }
            .testTag("oled_stealth_overlay")
    ) {
        // Event Deterrent White Flash (Disabled in Sentry mode to keep pitch black OLED stealth screen)
        AnimatedVisibility(
            visible = !isSentryOrCooling && (isBumpFlashVisible || uiState.isSnapshotFlashActive),
            enter = fadeIn(tween(50)),
            exit = fadeOut(tween(450)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            )
        }

        // Center: Pulsing Sentry Alarm LED & Visual Deterrent (or Tap-to-Peek stealth HUD)
        AnimatedVisibility(
            visible = !isSentryOrCooling || isPeekVisible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Status Line: Pulsing Recording Dot + State Label + Thermals + Battery
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Pulsing Dot
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isThermalCooling -> CyberCyan.copy(alpha = pulseAlpha)
                                    isLowBatterySleep -> SafetyAmber.copy(alpha = pulseAlpha)
                                    uiState.isRecording -> SafetyRed.copy(alpha = pulseAlpha)
                                    else -> NeonGreen.copy(alpha = pulseAlpha)
                                }
                            )
                            .testTag("sentry_alarm_led_pod")
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    val statusText = when {
                        isThermalCooling -> "❄️ COOLING ACTIVE"
                        isLowBatterySleep -> "🪫 LOW BATTERY SLEEP"
                        isAutoDim -> "REC ACTIVE"
                        uiState.isRecording -> "SURVEILLANCE ACTIVE"
                        mode == AppMode.SENTRY -> "🛡️ SENTRY ARMED"
                        else -> "STANDBY"
                    }

                    Text(
                        text = statusText,
                        color = when {
                            isThermalCooling -> CyberCyan
                            isLowBatterySleep -> SafetyAmber
                            isAutoDim -> CyberCyan
                            uiState.isRecording -> SafetyRed
                            else -> Color(0xFF94A3B8)
                        },
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Icon(
                        imageVector = Icons.Default.Thermostat,
                        contentDescription = "Battery Temp",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "${battery.temperatureC.toInt()}°C",
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    if (battery.isPlugged) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Power Connected",
                            tint = CyberCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                    }
                    Text(
                        text = "${battery.levelPct}%",
                        color = if (battery.isPlugged) CyberCyan else Color(0xFFCBD5E1),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (uiState.isSegmentLocked) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = SafetyYellow,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "LOCKED",
                            color = SafetyYellow,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Vivid High-Contrast Clock Time display
                Text(
                    text = timeStr,
                    color = Color.White,
                    fontSize = 46.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                if (isThermalCooling) {
                    Spacer(modifier = Modifier.height(20.dp))
                    val targetResumeC = if (uiState.thermalCoolingTargetC > 0f) uiState.thermalCoolingTargetC.toInt() else 44
                    Text(
                        text = "Phone cooling (${battery.temperatureC.toInt()}°C → Target < ${targetResumeC}°C)\nCamera & GPS paused • Emergency Shock Armed",
                        color = CyberCyan,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    val resumeActionText = when (uiState.preSleepOrigin) {
                        PreSleepOperatingState.DRIVING_LOOP -> "Auto-resumes recording when cooled"
                        PreSleepOperatingState.SENTRY_TIER1_SHOCK, PreSleepOperatingState.SENTRY_TIER2_OPTICAL -> "Resumes Sentry Guard when cooled"
                        PreSleepOperatingState.STANDBY -> "Returns to Standby when cooled"
                    }
                    Text(
                        text = "$resumeActionText • Hold 3s to cancel",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else if (isLowBatterySleep) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Battery reached ${battery.levelPct}% (≤ ${lowBatteryLimit}% limit)\nCamera & GPS paused • Emergency Shock Armed",
                        color = SafetyAmber,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Connect vehicle power to wake device • Hold 3s to cancel",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else if (isAutoDim) {
                    Spacer(modifier = Modifier.height(28.dp))

                    // Centered High-Contrast Emergency Lock Beacon (Enlarged to 86dp)
                    Box(
                        modifier = Modifier
                            .size(86.dp)
                            .clip(CircleShape)
                            .background(
                                if (uiState.isSegmentLocked) SafetyAmber.copy(alpha = 0.45f) else Color(0xFF1E293B).copy(alpha = 0.85f)
                            )
                            .border(
                                2.5.dp,
                                if (uiState.isSegmentLocked) SafetyYellow else SafetyAmber.copy(alpha = 0.85f),
                                CircleShape
                            )
                            .clickable { onToggleLock() }
                            .testTag("dim_mode_lock_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (uiState.isSegmentLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "Lock Incident",
                            tint = if (uiState.isSegmentLocked) SafetyYellow else SafetyAmber,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (uiState.isSegmentLocked) "INCIDENT LOCKED" else "TAP BUTTON OR VOL KEY TO LOCK",
                        color = if (uiState.isSegmentLocked) SafetyYellow else SafetyAmber,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Tap anywhere to wake",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    Spacer(modifier = Modifier.height(28.dp))

                    // Sentry Lock Beacon - Directly clickable to instantly unlock!
                    Surface(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF1E293B).copy(alpha = 0.9f),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, SafetyAmber.copy(alpha = 0.8f)),
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .testTag("sentry_alarm_led_pod")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = "Sentry Lock",
                                tint = SafetyAmber,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isSleepMode) "TAP TO RESUME" else "TAP TO UNLOCK",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (mode == AppMode.SENTRY && uiState.sentryEventCount > 0) {
                        Text(
                            text = "🛡️ ${uiState.sentryEventCount} Incident(s) Recorded",
                            color = SafetyYellow,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Text(
                        text = if (isSleepMode) "Double tap anywhere to resume" else "Double tap anywhere to unlock",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
