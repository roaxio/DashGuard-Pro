package io.github.roaxio.dashguardpro.ui.screens

import android.Manifest
import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.roaxio.dashguardpro.data.settings.AppMode
import io.github.roaxio.dashguardpro.data.settings.AutoDimTimeout
import io.github.roaxio.dashguardpro.ui.components.hud.CameraPreviewView
import io.github.roaxio.dashguardpro.ui.components.hud.LandscapeLeftCockpitDeck
import io.github.roaxio.dashguardpro.ui.components.hud.PortraitCockpitDeck
import io.github.roaxio.dashguardpro.ui.components.hud.PermissionsGuardCard
import io.github.roaxio.dashguardpro.ui.components.hud.OledPowerSaverOverlay
import io.github.roaxio.dashguardpro.ui.components.hud.TelemetryHud
import kotlinx.coroutines.delay
import io.github.roaxio.dashguardpro.ui.theme.AutomotivePitchBlack
import io.github.roaxio.dashguardpro.ui.theme.CyberCyan
import io.github.roaxio.dashguardpro.ui.theme.SafetyAmber
import io.github.roaxio.dashguardpro.ui.theme.SafetyRed
import io.github.roaxio.dashguardpro.viewmodel.DashcamViewModel
import io.github.roaxio.dashguardpro.viewmodel.state.DeviceSleepMode
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraHudScreen(
    viewModel: DashcamViewModel,
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val gForce by viewModel.gForce.collectAsStateWithLifecycle()
    val battery by viewModel.battery.collectAsStateWithLifecycle()

    var lastInteractionTimeMs by remember { mutableStateOf(System.currentTimeMillis()) }

    val isSleepActive = uiState.deviceSleepMode != DeviceSleepMode.NONE || uiState.isThermalCoolingActive || uiState.isLowBatterySleepActive
    val isSentryOrCooling = uiState.isSentryArmed || settings.currentMode == AppMode.SENTRY || isSleepActive
    val isDimmed = uiState.isScreenDimmed || settings.oledPowerSaveMode || isSentryOrCooling

    val context = LocalContext.current
    val activity = context as? Activity

    DisposableEffect(Unit) {
        onDispose {
            if (!isSentryOrCooling) {
                viewModel.setScreenDimmed(false)
            }
        }
    }

    // Auto-Dim Inactivity Timer: Runs strictly during Dashcam driving mode when recording is active
    LaunchedEffect(uiState.isRecording, uiState.isScreenDimmed, settings.autoDimTimeout, isSentryOrCooling) {
        if (isSentryOrCooling || !uiState.isRecording || settings.autoDimTimeout == AutoDimTimeout.DISABLED) {
            if (!isSentryOrCooling && !uiState.isRecording && uiState.isScreenDimmed && !settings.oledPowerSaveMode) {
                viewModel.setScreenDimmed(false)
            }
            return@LaunchedEffect
        }
        if (uiState.isScreenDimmed) {
            // Screen is already dimmed; await wake-up interaction
            return@LaunchedEffect
        }

        // Clean Entry (AGENTS.md Rule 12.3): Sanitize baseline timestamp upon recording ingress / un-dimming
        lastInteractionTimeMs = System.currentTimeMillis()

        val timeoutMs = settings.autoDimTimeout.seconds * 1000L
        while (uiState.isRecording && !uiState.isScreenDimmed && settings.autoDimTimeout != AutoDimTimeout.DISABLED && !isSentryOrCooling) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastInteractionTimeMs
            val remaining = timeoutMs - elapsed
            if (remaining <= 0) {
                viewModel.setScreenDimmed(true)
                break
            } else {
                delay(minOf(remaining, 1000L).coerceAtLeast(200L))
            }
        }
    }

    // Wake up display immediately when ANY incident lock occurs during driving loop
    LaunchedEffect(uiState.isSegmentLocked, uiState.showImpactAlert, uiState.isMotionTriggered, uiState.recentIncidentMessage, isSentryOrCooling) {
        if (!isSentryOrCooling && (uiState.isSegmentLocked || uiState.showImpactAlert || uiState.isMotionTriggered || uiState.recentIncidentMessage != null)) {
            if (uiState.isScreenDimmed) {
                viewModel.setScreenDimmed(false)
            }
            lastInteractionTimeMs = System.currentTimeMillis()
        }
    }

    // Intercept phone system hardware back button for open sheets/overlays
    androidx.activity.compose.BackHandler(
        enabled = isDimmed || uiState.showImpactAlert || uiState.unplugCountdownSeconds != null
    ) {
        if (isDimmed) {
            if (isSentryOrCooling || uiState.isSentryArmed) {
                viewModel.disarmSentryMode()
            }
            if (isSleepActive) {
                viewModel.cancelThermalCooling()
            }
            if (settings.oledPowerSaveMode) {
                viewModel.toggleOledMode()
            }
            viewModel.setScreenDimmed(false)
            lastInteractionTimeMs = System.currentTimeMillis()
        } else if (uiState.showImpactAlert) {
            viewModel.dismissImpactAlert()
        } else if (uiState.unplugCountdownSeconds != null) {
            viewModel.cancelUnplugCountdown()
        }
    }

    val requiredPermissions = remember {
        val list = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
            list.add(Manifest.permission.READ_MEDIA_VIDEO)
            list.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        list
    }

    val permissionsState = rememberMultiplePermissionsState(
        permissions = requiredPermissions
    )

    val corePermissionsGranted = permissionsState.permissions
        .filter { it.permission != Manifest.permission.POST_NOTIFICATIONS }
        .all { it.status.isGranted }

    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        }
    }

    LaunchedEffect(corePermissionsGranted) {
        viewModel.refreshSystemDiagnostics()
        viewModel.refreshStorageStats()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AutomotivePitchBlack)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val now = System.currentTimeMillis()
                        val hasDown = event.changes.any { it.changedToDown() }
                        val isHeld = event.changes.any { it.pressed } && (now - lastInteractionTimeMs >= 1000L)
                        if (hasDown || isHeld) {
                            lastInteractionTimeMs = now
                        }
                    }
                }
            }
    ) {
        if (corePermissionsGranted) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Sentry Arming Countdown Banner
                AnimatedVisibility(
                    visible = uiState.isSentryArming,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .zIndex(100f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xEE111827),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, SafetyAmber),
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = SafetyAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "🛡️ Sentry Arming in ${uiState.sentryArmingCountdownSeconds}s... (Exit Vehicle)",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Button(
                                onClick = { viewModel.cancelSentryArming() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SafetyRed,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    text = "CANCEL",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                if (isLandscape) {
                    // Landscape Layout: Left Side Cockpit Command Deck + 100% Height Camera Viewport
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left Column (adjacent to NavigationRail): All dashcam controls 100% visible
                        LandscapeLeftCockpitDeck(
                            uiState = uiState,
                            settings = settings,
                            onToggleLock = { viewModel.toggleCurrentClipLock("Emergency Guard Lock") },
                            onToggleRecord = {
                                lastInteractionTimeMs = System.currentTimeMillis()
                                viewModel.toggleRecording()
                            },
                            onToggleMic = { viewModel.toggleAudio() },
                            onToggleHudMode = { viewModel.cycleHudDisplayMode() },
                            onToggleLens = { viewModel.toggleCameraLens() },
                            onToggleSentry = {
                                if (uiState.isSentryArmed) viewModel.disarmSentryMode()
                                else if (uiState.isSentryArming) viewModel.cancelSentryArming()
                                else viewModel.armSentryMode()
                            },
                            modifier = Modifier
                                .width(78.dp)
                                .fillMaxHeight()
                        )

                        // Camera Viewport: Fills 100% vertical height and all remaining width
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(Color.Black)
                        ) {
                            CameraPreviewView(
                                isFrontCamera = settings.frontCameraSelected,
                                isSentryMode = settings.currentMode == AppMode.SENTRY || uiState.isSentryArmed,
                                sentryGuardType = settings.sentryGuardType,
                                isRecording = uiState.isRecording || uiState.isRecordingStarting,
                                isDimmed = isDimmed,
                                isThermalCooling = uiState.isThermalCoolingActive,
                                targetQuality = settings.videoQuality,
                                targetAspectRatio = settings.videoAspectRatio,
                                targetFps = settings.targetFps,
                                motionSensitivityThreshold = settings.motionSensitivity.motionDiffThreshold,
                                motionPixelDeltaThreshold = settings.motionSensitivity.motionPixelDelta,
                                motionMinClusterNeighbors = settings.motionSensitivity.motionMinClusterNeighbors,
                                motionPersistenceFrames = settings.sentryMotionPersistenceFrames,
                                onMotionDetected = { score, luma ->
                                    viewModel.handleMotionDetected(score, luma)
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Telemetry HUD overlay (strictly untouched, dynamic to camera area)
                            TelemetryHud(
                                uiState = uiState,
                                telemetry = telemetry,
                                gForce = gForce,
                                battery = battery,
                                mode = settings.currentMode,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                onAutoDismissMessage = { viewModel.clearIncidentMessage() }
                            )
                        }
                    }
                } else {
                    // Portrait Layout: Maximized Camera Viewport (~64%) + Streamlined Cockpit Deck (~36%)
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.64f)
                                .background(Color.Black)
                        ) {
                            CameraPreviewView(
                                isFrontCamera = settings.frontCameraSelected,
                                isSentryMode = settings.currentMode == AppMode.SENTRY || uiState.isSentryArmed,
                                sentryGuardType = settings.sentryGuardType,
                                isRecording = uiState.isRecording || uiState.isRecordingStarting,
                                isDimmed = isDimmed,
                                isThermalCooling = uiState.isThermalCoolingActive,
                                targetQuality = settings.videoQuality,
                                targetAspectRatio = settings.videoAspectRatio,
                                targetFps = settings.targetFps,
                                motionSensitivityThreshold = settings.motionSensitivity.motionDiffThreshold,
                                motionPixelDeltaThreshold = settings.motionSensitivity.motionPixelDelta,
                                motionMinClusterNeighbors = settings.motionSensitivity.motionMinClusterNeighbors,
                                motionPersistenceFrames = settings.sentryMotionPersistenceFrames,
                                onMotionDetected = { score, luma ->
                                    viewModel.handleMotionDetected(score, luma)
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Telemetry HUD overlay (strictly untouched, dynamic to camera area)
                            TelemetryHud(
                                uiState = uiState,
                                telemetry = telemetry,
                                gForce = gForce,
                                battery = battery,
                                mode = settings.currentMode,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                onAutoDismissMessage = { viewModel.clearIncidentMessage() }
                            )
                        }

                        // Portrait Streamlined Cockpit Deck (Bottom ~36%)
                        PortraitCockpitDeck(
                            uiState = uiState,
                            settingsMode = settings.currentMode,
                            sentryModeEnabled = settings.sentryModeEnabled,
                            micEnabled = settings.audioRecordingEnabled,
                            onToggleLock = { viewModel.toggleCurrentClipLock("Emergency Guard Lock") },
                            onToggleRecord = {
                                lastInteractionTimeMs = System.currentTimeMillis()
                                viewModel.toggleRecording()
                            },
                            onToggleMic = { viewModel.toggleAudio() },
                            onToggleHudMode = { viewModel.cycleHudDisplayMode() },
                            onToggleLens = { viewModel.toggleCameraLens() },
                            onToggleOled = { viewModel.toggleOledMode() },
                            onToggleSentry = {
                                if (uiState.isSentryArmed) viewModel.disarmSentryMode()
                                else if (uiState.isSentryArming) viewModel.cancelSentryArming()
                                else viewModel.armSentryMode()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.36f)
                        )
                    }
                }
            }

            // Snapshot Shutter White Flash
            if (uiState.isSnapshotFlashActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.85f))
                )
            }

            // True-Black OLED Stealth & Auto-Dim Overlay
            if (isDimmed) {
                OledPowerSaverOverlay(
                    uiState = uiState,
                    battery = battery,
                    mode = settings.currentMode,
                    isAutoDim = !isSentryOrCooling && !settings.oledPowerSaveMode,
                    lowBatteryLimit = settings.lowBatterySafetyLimitPct,
                    onToggleLock = {
                        viewModel.toggleCurrentClipLock("Emergency Guard Lock")
                        viewModel.setScreenDimmed(false)
                        lastInteractionTimeMs = System.currentTimeMillis()
                    },
                    onDismiss = {
                        if (isSentryOrCooling || uiState.isSentryArmed) {
                            viewModel.disarmSentryMode()
                        }
                        if (isSleepActive) {
                            viewModel.cancelThermalCooling()
                        }
                        if (settings.oledPowerSaveMode) {
                            viewModel.toggleOledMode()
                        }
                        viewModel.setScreenDimmed(false)
                        lastInteractionTimeMs = System.currentTimeMillis()
                    }
                )
            }

            // Vehicle Power Prompt on Sentry Disarm
            if (uiState.showStartDashcamPromptOnDisarm) {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissStartDashcamPrompt() },
                    title = {
                        Text(
                            text = "Vehicle Power Connected",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    },
                    text = {
                        Text(
                            text = "Sentry mode is disarmed and vehicle power is detected. Would you like to start regular dashcam loop video recording?",
                            color = Color(0xFFD1D5DB)
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.confirmStartDashcamAfterDisarm() },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                        ) {
                            Text("Start Recording", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.dismissStartDashcamPrompt() }) {
                            Text("Stay in Standby", color = Color.Gray)
                        }
                    },
                    containerColor = Color(0xFF1E293B)
                )
            }
        } else {
            // Permissions Guard Screen
            PermissionsGuardCard(
                onGrantClicked = { permissionsState.launchMultiplePermissionRequest() }
            )
        }
    }
}
