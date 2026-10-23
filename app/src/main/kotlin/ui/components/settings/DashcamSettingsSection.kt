package io.github.roaxio.dashguardpro.ui.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.roaxio.dashguardpro.data.settings.DashGuardSettings
import io.github.roaxio.dashguardpro.data.settings.Sensitivity
import io.github.roaxio.dashguardpro.data.settings.SpeedUnit
import io.github.roaxio.dashguardpro.camera.CameraHardwareProfile
import io.github.roaxio.dashguardpro.camera.CameraHardwareSpecManager
import io.github.roaxio.dashguardpro.ui.theme.CyberCyan
import io.github.roaxio.dashguardpro.ui.theme.NeonGreen

/**
 * Dashcam loop recording configuration section for [SettingsScreen].
 * Configures video segment chunk duration, post-incident safety buffer window,
 * hardware sensor capabilities, camera aspect ratios, resolutions, and target FPS.
 */
@Composable
fun DashcamSettingsSection(
    settings: DashGuardSettings,
    cameraProfile: CameraHardwareProfile,
    onSettingsChanged: (DashGuardSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCustomIncidentBufferDialog by remember { mutableStateOf(false) }

    if (showCustomIncidentBufferDialog) {
        CustomIncidentBufferDialog(
            currentSeconds = settings.incidentBufferSeconds,
            onDismiss = { showCustomIncidentBufferDialog = false },
            onConfirm = { sec ->
                onSettingsChanged(settings.copy(incidentBufferSeconds = sec))
                showCustomIncidentBufferDialog = false
            }
        )
    }

    SectionHeader("DASHCAM & LOOP BUFFER", modifier = modifier)
    SettingsCard {
        // Segment Duration
        SettingsDropdownRow(
            title = "Loop Segment Duration",
            subtitle = "Length of each continuous video segment",
            currentValue = "${settings.loopSegmentMinutes} Minutes",
            options = listOf(1, 3, 5),
            onSelect = { min ->
                onSettingsChanged(settings.copy(loopSegmentMinutes = min))
            },
            displayTransform = { "$it Minutes" }
        )

        HorizontalDivider(color = Color(0xFF1E293B))

        // Guaranteed Post-Incident Recording Buffer
        val presetBufferOptions = listOf(15, 30, 45, 60, -1)
        val isCustomBuffer = settings.incidentBufferSeconds !in listOf(15, 30, 45, 60)
        SettingsDropdownRow(
            title = "Incident Time Window Guarantee",
            subtitle = "Extends the current video file so incidents triggered near the end of a loop are captured completely in one file",
            currentValue = if (isCustomBuffer) "Custom (${settings.incidentBufferSeconds}s)" else "${settings.incidentBufferSeconds} Seconds",
            options = presetBufferOptions,
            onSelect = { sec ->
                if (sec == -1) {
                    showCustomIncidentBufferDialog = true
                } else {
                    onSettingsChanged(settings.copy(incidentBufferSeconds = sec))
                }
            },
            displayTransform = { if (it == -1) "Custom (15-300s / 5 min)..." else "$it Seconds" }
        )

        HorizontalDivider(color = Color(0xFF1E293B))

        // Hardware Sensor Specs Header Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0F172A))
                .border(1.dp, CyberCyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SENSOR: ${cameraProfile.lensFacing.uppercase()}",
                        color = CyberCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${cameraProfile.sensorMegapixels} MP Sensor Array (${cameraProfile.sensorDimensions})",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(NeonGreen.copy(alpha = 0.15f))
                        .border(0.5.dp, NeonGreen, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "HARDWARE VERIFIED",
                        color = NeonGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Dynamic Aspect Ratio, Resolution, and FPS mapping
        val currentAspect = settings.videoAspectRatio
        val availableResolutionsForAspect = remember(cameraProfile, currentAspect) {
            CameraHardwareSpecManager.getResolutionsForAspectRatio(cameraProfile, currentAspect)
        }
        val activeResOption = remember(cameraProfile, settings.videoQuality, currentAspect) {
            CameraHardwareSpecManager.getResolutionInfo(cameraProfile, settings.videoQuality, currentAspect)
        }
        val activeAspectOption = cameraProfile.supportedAspectRatios.find { it.aspectRatio == settings.videoAspectRatio }
            ?: cameraProfile.supportedAspectRatios.firstOrNull()

        // 1. Dynamic Camera Sensor Aspect Ratio derived from physical camera sensor
        SettingsDropdownRow(
            title = "Camera Sensor Aspect Ratio",
            subtitle = activeAspectOption?.description ?: "Select native sensor ratio for video recording",
            currentValue = activeAspectOption?.title ?: settings.videoAspectRatio.title,
            options = cameraProfile.supportedAspectRatios,
            onSelect = { ratioInfo ->
                val equivRes = CameraHardwareSpecManager.getEquivalentResolutionForAspectRatio(
                    cameraProfile,
                    settings.videoQuality,
                    ratioInfo.aspectRatio
                )
                val validFps = if (equivRes.supportedFps.contains(settings.targetFps)) {
                    settings.targetFps
                } else {
                    equivRes.supportedFps.maxOrNull() ?: 30
                }
                onSettingsChanged(
                    settings.copy(
                        videoAspectRatio = ratioInfo.aspectRatio,
                        videoQuality = equivRes.quality,
                        targetFps = validFps
                    )
                )
            },
            displayTransform = { it.title }
        )

        HorizontalDivider(color = Color(0xFF1E293B))

        // 2. Dynamic Video Resolution derived from physical camera sensor & filtered by Aspect Ratio
        SettingsDropdownRow(
            title = "Video Resolution",
            subtitle = activeResOption?.subtitle ?: "Resolutions tailored to ${currentAspect.title}",
            currentValue = activeResOption?.title ?: settings.videoQuality.title,
            options = availableResolutionsForAspect,
            onSelect = { resInfo ->
                val validFps = if (resInfo.supportedFps.contains(settings.targetFps)) {
                    settings.targetFps
                } else {
                    resInfo.supportedFps.maxOrNull() ?: 30
                }
                onSettingsChanged(
                    settings.copy(
                        videoQuality = resInfo.quality,
                        targetFps = validFps
                    )
                )
            },
            displayTransform = { it.title }
        )

        HorizontalDivider(color = Color(0xFF1E293B))

        // 3. Dynamic Hardware Sensor Supported FPS
        val availableFpsOptions = remember(activeResOption) {
            activeResOption?.supportedFps ?: listOf(30)
        }

        SettingsDropdownRow(
            title = "Recording Frame Rate (FPS)",
            subtitle = "Hardware sensor supported rates for ${activeResOption?.width ?: 1280}x${activeResOption?.height ?: 720} (Capped at 60 FPS max)",
            currentValue = "${settings.targetFps} FPS",
            options = availableFpsOptions,
            onSelect = { fps ->
                onSettingsChanged(settings.copy(targetFps = fps))
            },
            displayTransform = { "$it FPS" }
        )
    }
}

/**
 * Intelligent sensors and crash triggers configuration section for [SettingsScreen].
 * Manages G-force impact sensitivity thresholds and speedometer units.
 */
@Composable
fun SensorsSettingsSection(
    settings: DashGuardSettings,
    onSettingsChanged: (DashGuardSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    SectionHeader("INTELLIGENT SENSORS & IMPACTS", modifier = modifier)
    SettingsCard {
        // G-Sensor Crash Sensitivity
        SettingsDropdownRow(
            title = "G-Force Impact Sensitivity",
            subtitle = "Auto-locks current clip on sudden collision / hard brake",
            currentValue = when (settings.impactSensitivity) {
                Sensitivity.OFF -> "Disabled"
                Sensitivity.LOW -> "Low (3.5G - Major Collisions Only)"
                Sensitivity.MEDIUM -> "Medium (2.4G - Standard Crash / Hard Brake)"
                Sensitivity.HIGH -> "High (1.6G - Sensitive / Minor Bumps)"
                else -> settings.impactSensitivity.label
            },
            options = listOf(Sensitivity.OFF, Sensitivity.LOW, Sensitivity.MEDIUM, Sensitivity.HIGH),
            onSelect = { s ->
                onSettingsChanged(settings.copy(impactSensitivity = s))
            },
            displayTransform = { s ->
                when (s) {
                    Sensitivity.OFF -> "Disabled (Off)"
                    Sensitivity.LOW -> "Low (3.5G - Major Collisions Only)"
                    Sensitivity.MEDIUM -> "Medium (2.4G - Standard Crash / Hard Brake)"
                    Sensitivity.HIGH -> "High (1.6G - Sensitive / Minor Bumps)"
                    else -> s.label
                }
            }
        )

        HorizontalDivider(color = Color(0xFF1E293B))

        // Speed Unit
        SettingsDropdownRow(
            title = "Speedometer Unit",
            subtitle = "Speed unit displayed on HUD and recorded in logs",
            currentValue = settings.speedUnit.name,
            options = SpeedUnit.values().toList(),
            onSelect = { u ->
                onSettingsChanged(settings.copy(speedUnit = u))
            },
            displayTransform = { it.name }
        )
    }
}

/**
 * Recording media, telemetry watermark overlays, and advanced debug configuration section for [SettingsScreen].
 */
@Composable
fun RecordingMediaSettingsSection(
    settings: DashGuardSettings,
    onSettingsChanged: (DashGuardSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    SectionHeader("RECORDING MEDIA & OVERLAYS", modifier = modifier)
    SettingsCard {
        SettingsToggleRow(
            title = "Audio Recording",
            subtitle = "Record cabin / ambient audio with video",
            checked = settings.audioRecordingEnabled,
            onCheckedChange = { chk ->
                onSettingsChanged(settings.copy(audioRecordingEnabled = chk))
            }
        )

        HorizontalDivider(color = Color(0xFF1E293B))

        SettingsToggleRow(
            title = "Telemetry Watermark Overlay",
            subtitle = "Save synchronized GPS, speed, and timestamp telemetry (.SRT) for playback overlay",
            checked = settings.showWatermark,
            onCheckedChange = { chk ->
                onSettingsChanged(settings.copy(showWatermark = chk))
            }
        )
    }

    SectionHeader("ADVANCED & DEBUG", modifier = modifier)
    SettingsCard {
        SettingsToggleRow(
            title = "Allow Portrait Recording",
            subtitle = "[Debug] Allow video recording when phone is vertical (may cause orientation rotation issues).",
            checked = settings.debugAllowPortraitRecording,
            onCheckedChange = { chk ->
                onSettingsChanged(settings.copy(debugAllowPortraitRecording = chk))
            }
        )
    }
}
