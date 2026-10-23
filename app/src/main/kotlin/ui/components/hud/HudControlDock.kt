package io.github.roaxio.dashguardpro.ui.components.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.roaxio.dashguardpro.data.settings.AppMode
import io.github.roaxio.dashguardpro.data.settings.DashGuardSettings
import io.github.roaxio.dashguardpro.ui.theme.CockpitBorder
import io.github.roaxio.dashguardpro.ui.theme.CockpitPanel
import io.github.roaxio.dashguardpro.ui.theme.CyberCyan
import io.github.roaxio.dashguardpro.ui.theme.SafetyAmber
import io.github.roaxio.dashguardpro.ui.theme.SafetyRed
import io.github.roaxio.dashguardpro.ui.theme.SafetyYellow
import io.github.roaxio.dashguardpro.viewmodel.state.DashcamUiState
import io.github.roaxio.dashguardpro.data.settings.HudDisplayMode

/**
 * Landscape Left Cockpit Deck
 * High-density vertical control column placed on the left side of the screen (adjacent to NavigationRail).
 * Keeps all controls permanently visible and thumb-accessible (AGENTS.md Rule 19).
 */
@Composable
fun LandscapeLeftCockpitDeck(
    uiState: DashcamUiState,
    settings: DashGuardSettings,
    onToggleLock: () -> Unit,
    onToggleRecord: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleHudMode: () -> Unit,
    onToggleLens: () -> Unit,
    onToggleSentry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(CockpitPanel)
            .border(1.dp, CockpitBorder)
            .padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        // 1. High-visibility Emergency LOCK button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clickable { onToggleLock() }
                .testTag("emergency_lock_button")
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        if (uiState.isSegmentLocked)
                            Brush.radialGradient(listOf(SafetyAmber, Color(0xFFD97706)))
                        else
                            Brush.radialGradient(listOf(SafetyAmber.copy(alpha = 0.35f), Color(0xFF1A2234)))
                    )
                    .border(2.dp, if (uiState.isSegmentLocked) SafetyYellow else SafetyAmber, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (uiState.isSegmentLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = "Lock Incident",
                    tint = if (uiState.isSegmentLocked) Color.Black else SafetyYellow,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (uiState.isSegmentLocked) "LOCKED" else "LOCK",
                color = if (uiState.isSegmentLocked) SafetyYellow else SafetyAmber,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
        }

        // 2. Primary Record Pill
        val isRecordingActive = uiState.isRecording || uiState.isRecordingStarting
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (isRecordingActive) SafetyRed.copy(alpha = 0.25f) else Color(0xFF161E2E))
                .border(1.dp, if (isRecordingActive) SafetyRed else CyberCyan, RoundedCornerShape(18.dp))
                .clickable { onToggleRecord() }
                .padding(horizontal = 8.dp)
                .testTag("primary_record_button")
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(if (isRecordingActive) RoundedCornerShape(2.dp) else CircleShape)
                    .background(if (isRecordingActive) SafetyRed else CyberCyan)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (uiState.isRecording) "REC" else if (uiState.isRecordingStarting) "REC..." else "START",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
        }

        // 3. Cabin Mic Toggle
        IconButton(
            onClick = onToggleMic,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (settings.audioRecordingEnabled) Color(0xFF161E2E) else Color(0xFF7F1D1D))
                .border(1.dp, if (settings.audioRecordingEnabled) CockpitBorder else SafetyRed, CircleShape)
                .testTag("mic_toggle_button")
        ) {
            Icon(
                imageVector = if (settings.audioRecordingEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                contentDescription = "Cabin Mic",
                tint = if (settings.audioRecordingEnabled) CyberCyan else Color.White,
                modifier = Modifier.size(18.dp)
            )
        }

        // 4. Camera Lens Switch
        IconButton(
            onClick = onToggleLens,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFF161E2E))
                .border(1.dp, CockpitBorder, CircleShape)
                .testTag("switch_lens_button")
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = "Switch Camera Lens",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }

        // 5. HUD Mode Toggle (Full / Compact)
        IconButton(
            onClick = onToggleHudMode,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (uiState.hudDisplayMode == HudDisplayMode.FULL) CyberCyan.copy(alpha = 0.2f) else Color(0xFF161E2E))
                .border(1.dp, if (uiState.hudDisplayMode == HudDisplayMode.FULL) CyberCyan else CockpitBorder, CircleShape)
                .testTag("hud_mode_toggle_button")
        ) {
            Icon(
                imageVector = Icons.Default.Layers,
                contentDescription = "Toggle HUD Mode",
                tint = if (uiState.hudDisplayMode == HudDisplayMode.FULL) CyberCyan else Color(0xFF94A3B8),
                modifier = Modifier.size(18.dp)
            )
        }

        // 6. Sentry Mode Guard Toggle
        if (settings.sentryModeEnabled) {
            IconButton(
                onClick = onToggleSentry,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (uiState.isSentryArmed) SafetyAmber.copy(alpha = 0.25f)
                        else if (uiState.isSentryArming) SafetyRed.copy(alpha = 0.25f)
                        else Color(0xFF161E2E)
                    )
                    .border(
                        1.dp,
                        if (uiState.isSentryArmed) SafetyAmber
                        else if (uiState.isSentryArming) SafetyRed
                        else CockpitBorder,
                        CircleShape
                    )
                    .testTag("sentry_arm_toggle_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Sentry Guard",
                    tint = if (uiState.isSentryArmed) SafetyAmber
                    else if (uiState.isSentryArming) SafetyRed
                    else Color(0xFF94A3B8),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Portrait Layout Cockpit Deck
 * Features a massive circular "LOCK INCIDENT" button in the center and a clean 2x2 grid of secondary toggles.
 */
@Composable
fun PortraitCockpitDeck(
    uiState: DashcamUiState,
    settingsMode: AppMode,
    micEnabled: Boolean,
    onToggleLock: () -> Unit,
    onToggleRecord: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleHudMode: () -> Unit,
    onToggleLens: () -> Unit,
    onToggleOled: () -> Unit,
    sentryModeEnabled: Boolean = false,
    onToggleSentry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(CockpitPanel)
            .border(
                1.dp,
                CockpitBorder,
                RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            )
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // TOP ROW: Primary Reactor Record & Guard Status Indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.size(48.dp))

            // Record / Stop Hero Button
            val isRecordingActive = uiState.isRecording || uiState.isRecordingStarting
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (isRecordingActive) SafetyRed.copy(alpha = 0.25f) else Color(0xFF161E2E))
                    .border(1.dp, if (isRecordingActive) SafetyRed else CyberCyan, RoundedCornerShape(24.dp))
                    .clickable { onToggleRecord() }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .testTag("primary_record_button")
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(if (isRecordingActive) RoundedCornerShape(2.dp) else CircleShape)
                        .background(if (isRecordingActive) SafetyRed else CyberCyan)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.isRecording) "RECORDING" else if (uiState.isRecordingStarting) "STARTING..." else "START RECORD",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }

            // Lens Switch
            IconButton(
                onClick = onToggleLens,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF161E2E))
                    .border(1.dp, CockpitBorder, CircleShape)
                    .testTag("switch_lens_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Switch Camera Lens",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // CENTER HERO: Massive circular EMERGENCY LOCK INCIDENT button
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(
                    if (uiState.isSegmentLocked)
                        Brush.radialGradient(listOf(SafetyAmber, Color(0xFFB45309)))
                    else
                        Brush.radialGradient(listOf(SafetyAmber.copy(alpha = 0.35f), Color(0xFF161E2E)))
                )
                .border(3.dp, if (uiState.isSegmentLocked) SafetyYellow else SafetyAmber, CircleShape)
                .clickable { onToggleLock() }
                .testTag("emergency_lock_button"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (uiState.isSegmentLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = "Emergency Guard Lock",
                    tint = if (uiState.isSegmentLocked) Color.Black else SafetyYellow,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (uiState.isSegmentLocked) "LOCKED" else "LOCK",
                    color = if (uiState.isSegmentLocked) Color.Black else SafetyYellow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }

        // BOTTOM ROW: 2 or 3 secondary utility actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cabin Mic Toggle
            Button(
                onClick = onToggleMic,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (micEnabled) Color(0xFF161E2E) else Color(0xFF7F1D1D),
                    contentColor = if (micEnabled) CyberCyan else Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("mic_toggle_button")
            ) {
                Icon(
                    imageVector = if (micEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (micEnabled) "MIC ON" else "MIC OFF",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // HUD Density Toggle (Full / Compact)
            Button(
                onClick = onToggleHudMode,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.hudDisplayMode == HudDisplayMode.FULL) CyberCyan.copy(alpha = 0.2f) else Color(0xFF161E2E),
                    contentColor = if (uiState.hudDisplayMode == HudDisplayMode.FULL) CyberCyan else Color(0xFF94A3B8)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("hud_mode_toggle_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (uiState.hudDisplayMode == HudDisplayMode.FULL) "HUD FULL" else "HUD COMPACT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Sentry Mode Guard Toggle (when master setting enabled)
            if (sentryModeEnabled) {
                Button(
                    onClick = onToggleSentry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isSentryArmed) SafetyAmber.copy(alpha = 0.25f)
                            else if (uiState.isSentryArming) SafetyRed.copy(alpha = 0.25f)
                            else Color(0xFF161E2E),
                        contentColor = if (uiState.isSentryArmed) SafetyAmber
                            else if (uiState.isSentryArming) SafetyRed
                            else Color(0xFF94A3B8)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("sentry_arm_toggle_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (uiState.isSentryArmed) "ARMED"
                            else if (uiState.isSentryArming) "${uiState.sentryArmingCountdownSeconds}s"
                            else "SENTRY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionsGuardCard(onGrantClicked: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(CockpitPanel)
                .border(1.dp, CockpitBorder, RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "Permissions Required",
                tint = CyberCyan,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "DASHGUARD PERMISSIONS REQUIRED",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Camera, Audio, GPS, and Storage access are required to record loop dashcam footage and save clips to your chosen directory.",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onGrantClicked,
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text("GRANT PERMISSIONS", fontWeight = FontWeight.Bold)
            }
        }
    }
}
