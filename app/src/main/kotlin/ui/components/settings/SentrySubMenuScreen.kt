package io.github.roaxio.dashguardpro.ui.components.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.roaxio.dashguardpro.ui.components.common.DashboardTopAppBar
import io.github.roaxio.dashguardpro.data.settings.DashGuardSettings
import io.github.roaxio.dashguardpro.data.settings.Sensitivity
import io.github.roaxio.dashguardpro.data.settings.SentryArmingTrigger
import io.github.roaxio.dashguardpro.data.settings.SentryDeterrentMode
import io.github.roaxio.dashguardpro.data.settings.SentryGuardType
import io.github.roaxio.dashguardpro.data.settings.SentryInterruptedClipAction
import io.github.roaxio.dashguardpro.ui.theme.HudBlack
import io.github.roaxio.dashguardpro.viewmodel.DashcamViewModel

/**
 * Dedicated Sub-Menu Screen for Sentry Mode (Park & Protect) Settings
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SentrySubMenuScreen(
    settings: DashGuardSettings,
    viewModel: DashcamViewModel,
    onNavigateBack: () -> Unit,
    onShowCustomArmingDialog: () -> Unit,
    onShowCustomDurationDialog: () -> Unit,
    onShowCustomDemoteTempDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val subMenuListState = rememberLazyListState()

    Scaffold(
        topBar = {
            DashboardTopAppBar(
                title = "Sentry Mode (Park & Protect)",
                onNavigateBack = onNavigateBack,
                navigationIconContentDescription = "Back to Settings",
                navigationIconTestTag = "btn_back_sentry_sub_menu"
            )
        },
        containerColor = HudBlack,
        modifier = modifier
    ) { padding ->
        LazyColumn(
            state = subMenuListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp)
                .testTag("sentry_sub_menu_lazy_column"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                SectionHeader("SENTRY MODE (PARK & PROTECT)")
                SettingsCard {
                    // Master Sentry Toggle
                    SettingsToggleRow(
                        title = "Enable Sentry Mode",
                        subtitle = "Surveillance guard while parked; captures shock, noise spikes, and visual threats",
                        checked = settings.sentryModeEnabled,
                        onCheckedChange = { chk ->
                            viewModel.updateSettings(settings.copy(sentryModeEnabled = chk))
                        }
                    )

                    if (settings.sentryModeEnabled) {
                        HorizontalDivider(color = Color(0xFF1E293B))

                        // Parked Arming Trigger
                        SettingsDropdownRow(
                            title = "Parked Detection Trigger",
                            subtitle = "How the system detects your car is parked and arms Sentry",
                            currentValue = settings.sentryArmingTrigger.label,
                            options = SentryArmingTrigger.entries,
                            onSelect = { trigger ->
                                viewModel.updateSettings(settings.copy(sentryArmingTrigger = trigger))
                            },
                            displayTransform = { it.label }
                        )

                        HorizontalDivider(color = Color(0xFF1E293B))

                        // Exit Arming Delay
                        SettingsDropdownRow(
                            title = "Exit Arming Delay",
                            subtitle = "Grace window to exit the vehicle before Sentry sensors become active",
                            currentValue = if (settings.sentryArmingDelaySeconds in listOf(5, 10, 15, 30, 60)) "${settings.sentryArmingDelaySeconds}s" else "Custom (${settings.sentryArmingDelaySeconds}s)",
                            options = listOf(5, 10, 15, 30, 60, -1),
                            onSelect = { delaySec ->
                                if (delaySec == -1) {
                                    onShowCustomArmingDialog()
                                } else {
                                    viewModel.updateSettings(settings.copy(sentryArmingDelaySeconds = delaySec))
                                }
                            },
                            displayTransform = { if (it == -1) "Custom (1–300s / 5 min)..." else "${it}s delay" }
                        )

                        HorizontalDivider(color = Color(0xFF1E293B))

                        // Sentry Guard Type
                        val isDemotedActive = settings.sentryConfiguredGuardType == SentryGuardType.OPTICAL_MOTION &&
                                settings.sentryGuardType == SentryGuardType.SHOCK_STANDBY
                        val guardDisplayValue = if (isDemotedActive) {
                            "${settings.sentryConfiguredGuardType.label} (Demoted: Tier 1 Active)"
                        } else {
                            settings.sentryConfiguredGuardType.label
                        }
                        SettingsDropdownRow(
                            title = "Sentry Guard Architecture",
                            subtitle = "Detection engine method while in parked standby",
                            currentValue = guardDisplayValue,
                            options = SentryGuardType.entries,
                            onSelect = { guardType ->
                                viewModel.updateSettings(settings.copy(sentryGuardType = guardType, sentryConfiguredGuardType = guardType))
                            },
                            displayTransform = { it.label }
                        )

                        if (settings.sentryConfiguredGuardType == SentryGuardType.OPTICAL_MOTION || settings.sentryGuardType == SentryGuardType.OPTICAL_MOTION) {
                            HorizontalDivider(color = Color(0xFF1E293B))

                            SettingsDropdownRow(
                                title = "Motion Detection Sensitivity",
                                subtitle = "Spatial cluster size and delta threshold for surveillance triggers",
                                currentValue = when (settings.motionSensitivity) {
                                    Sensitivity.OFF -> "Disabled"
                                    Sensitivity.VERY_LOW -> "Very Low (Minimal - 35% Cluster / Heavy Wind)"
                                    Sensitivity.LOW -> "Low (Outdoor Street - 25% Cluster)"
                                    Sensitivity.MEDIUM -> "Medium (Standard / Driveway - 15% Cluster)"
                                    Sensitivity.HIGH -> "High (Enclosed Garage - 8% Cluster)"
                                    Sensitivity.VERY_HIGH -> "Highest (Ultra-Sensitive - 4% Cluster)"
                                },
                                options = listOf(
                                    Sensitivity.VERY_LOW,
                                    Sensitivity.LOW,
                                    Sensitivity.MEDIUM,
                                    Sensitivity.HIGH,
                                    Sensitivity.VERY_HIGH
                                ),
                                onSelect = { s ->
                                    viewModel.updateSettings(settings.copy(motionSensitivity = s))
                                },
                                displayTransform = { s ->
                                    when (s) {
                                        Sensitivity.OFF -> "Disabled (Off)"
                                        Sensitivity.VERY_LOW -> "Very Low (Minimal - Heavy Wind / Busy Area)"
                                        Sensitivity.LOW -> "Low (Outdoor Street / Sidewalk)"
                                        Sensitivity.MEDIUM -> "Medium (Standard / Driveway - Recommended)"
                                        Sensitivity.HIGH -> "High (Enclosed Garage / Low Traffic)"
                                        Sensitivity.VERY_HIGH -> "Highest (Ultra-Sensitive / Isolated)"
                                    }
                                }
                            )

                            HorizontalDivider(color = Color(0xFF1E293B))

                            SettingsDropdownRow(
                                title = "Motion Trigger Persistence",
                                subtitle = "How long continuous movement must persist before triggering recording (sampled at 4 FPS / 250ms)",
                                currentValue = when (settings.sentryMotionPersistenceFrames) {
                                    1 -> "1 frame (250ms - Immediate)"
                                    2 -> "2 frames (500ms - Recommended)"
                                    3 -> "3 frames (750ms - Sustained)"
                                    4 -> "4 frames (1.0s - Extended)"
                                    5 -> "5 frames (1.25s - Prolonged)"
                                    else -> "${settings.sentryMotionPersistenceFrames} frames (${settings.sentryMotionPersistenceFrames * 250}ms)"
                                },
                                options = listOf(1, 2, 3, 4, 5),
                                onSelect = { frames ->
                                    viewModel.updateSettings(settings.copy(sentryMotionPersistenceFrames = frames))
                                },
                                displayTransform = { frames ->
                                    when (frames) {
                                        1 -> "1 frame (250ms - Immediate)"
                                        2 -> "2 frames (500ms - Recommended)"
                                        3 -> "3 frames (750ms - Sustained)"
                                        4 -> "4 frames (1.0s - Extended)"
                                        5 -> "5 frames (1.25s - Prolonged)"
                                        else -> "$frames frames (${frames * 250}ms)"
                                    }
                                }
                            )

                            HorizontalDivider(color = Color(0xFF1E293B))

                            SettingsToggleRow(
                                title = "Tier 2 Auto-Demote Protection",
                                subtitle = "Automatically demotes from 4 FPS optical motion to Tier 1 standby under battery drain or overheating, and auto-restores to Tier 2 when conditions recover",
                                checked = settings.sentryAutoDemoteEnabled,
                                onCheckedChange = { enabled ->
                                    val newEffective = if (!enabled) SentryGuardType.OPTICAL_MOTION else settings.sentryGuardType
                                    viewModel.updateSettings(settings.copy(sentryAutoDemoteEnabled = enabled, sentryGuardType = newEffective))
                                }
                            )

                            if (settings.sentryAutoDemoteEnabled) {
                                HorizontalDivider(color = Color(0xFF1E293B))

                                SettingsDropdownRow(
                                    title = "Tier 2 Demotion Battery Floor",
                                    subtitle = "Automatically demotes from 4 FPS optical motion to Tier 1 standby when battery drains below this level (Minimum allowed is 5% above Low Battery Cutoff)",
                                    currentValue = "${settings.sentryAutoDemoteBatteryPct}%",
                                    options = listOf(15, 20, 25, 30, 35, 40, 50).filter { it >= settings.lowBatterySafetyLimitPct + 5 },
                                    onSelect = { pct ->
                                        viewModel.updateSettings(settings.copy(sentryAutoDemoteBatteryPct = pct))
                                    },
                                    displayTransform = { "$it% battery floor" }
                                )

                                HorizontalDivider(color = Color(0xFF1E293B))

                                val maxCutoff = settings.maxTempThresholdC
                                val demoteAutoVal = if (maxCutoff > 0f) maxOf(30f, maxCutoff - 4.0f).toInt() else 44
                                val demoteDropdownOptions = listOf("AUTO", "CUSTOM")
                                val demoteCurrentDisplay = if (settings.sentryAutoDemoteTempIsAuto) {
                                    "Auto ($demoteAutoVal°C)"
                                } else {
                                    "${settings.sentryAutoDemoteTempC.toInt()}°C (Custom)"
                                }
                                SettingsDropdownRow(
                                    title = "Tier 2 Demotion Temperature Limit",
                                    subtitle = "Automatically demotes from 4 FPS optical motion to Tier 1 standby when device heat reaches this limit (Auto is derived 4°C below Max Cutoff)",
                                    currentValue = demoteCurrentDisplay,
                                    options = demoteDropdownOptions,
                                    onSelect = { option ->
                                        if (option == "AUTO") {
                                            viewModel.updateSettings(settings.copy(sentryAutoDemoteTempIsAuto = true))
                                        } else {
                                            onShowCustomDemoteTempDialog()
                                        }
                                    },
                                    displayTransform = { option ->
                                        if (option == "AUTO") {
                                            "Auto ($demoteAutoVal°C - Recommended)"
                                        } else {
                                            if (!settings.sentryAutoDemoteTempIsAuto) {
                                                "Custom (${settings.sentryAutoDemoteTempC.toInt()}°C)..."
                                            } else {
                                                "Custom..."
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        HorizontalDivider(color = Color(0xFF1E293B))

                        // Acoustic Noise Trigger Sensitivity
                        SettingsDropdownRow(
                            title = "Acoustic Noise Trigger",
                            subtitle = "Triggers emergency recording on unusual sounds (glass break, metal bang, yelling)",
                            currentValue = when (settings.sentryAcousticTriggerSensitivity) {
                                Sensitivity.OFF -> "Disabled"
                                Sensitivity.VERY_LOW -> "Minimal (90 dB - Heavy Impact)"
                                Sensitivity.LOW -> "Low (85 dB - Glass Break / Impact)"
                                Sensitivity.MEDIUM -> "Medium (75 dB - Slam / Scrape)"
                                Sensitivity.HIGH -> "High (65 dB - Knocks / Voices)"
                                Sensitivity.VERY_HIGH -> "Highest (60 dB - Whispers / Distant)"
                            },
                            options = listOf(Sensitivity.OFF, Sensitivity.VERY_LOW, Sensitivity.LOW, Sensitivity.MEDIUM, Sensitivity.HIGH, Sensitivity.VERY_HIGH),
                            onSelect = { sens ->
                                viewModel.updateSettings(settings.copy(sentryAcousticTriggerSensitivity = sens))
                            },
                            displayTransform = { sens ->
                                when (sens) {
                                    Sensitivity.OFF -> "Disabled (Off)"
                                    Sensitivity.VERY_LOW -> "Minimal (90 dB - Heavy Impact)"
                                    Sensitivity.LOW -> "Low (85 dB - Glass Break / Impact)"
                                    Sensitivity.MEDIUM -> "Medium (75 dB - Slam / Scrape)"
                                    Sensitivity.HIGH -> "High (65 dB - Knocks / Voices)"
                                    Sensitivity.VERY_HIGH -> "Highest (60 dB - Whispers / Distant)"
                                }
                            }
                        )

                        HorizontalDivider(color = Color(0xFF1E293B))

                        // Incident Clip Duration
                        SettingsDropdownRow(
                            title = "Incident Clip Duration",
                            subtitle = "Length of emergency recording captured upon motion, shock, or acoustic trigger",
                            currentValue = if (settings.sentryIncidentDurationSeconds in listOf(30, 45, 60, 90)) "${settings.sentryIncidentDurationSeconds}s" else "Custom (${settings.sentryIncidentDurationSeconds}s)",
                            options = listOf(30, 45, 60, 90, -1),
                            onSelect = { dur ->
                                if (dur == -1) {
                                    onShowCustomDurationDialog()
                                } else {
                                    viewModel.updateSettings(settings.copy(sentryIncidentDurationSeconds = dur))
                                }
                            },
                            displayTransform = { if (it == -1) "Custom (15–300s / 5 min)..." else "${it}s incident clip" }
                        )

                        HorizontalDivider(color = Color(0xFF1E293B))

                        // Deterrent Action
                        SettingsDropdownRow(
                            title = "Sentry Deterrent Action",
                            subtitle = "Visual or audible warning when a threat triggers an incident recording",
                            currentValue = settings.sentryDeterrentMode.label,
                            options = SentryDeterrentMode.entries,
                            onSelect = { mode ->
                                viewModel.updateSettings(settings.copy(sentryDeterrentMode = mode))
                            },
                            displayTransform = { it.label }
                        )

                        HorizontalDivider(color = Color(0xFF1E293B))

                        // Owner Return Interrupted Action
                        SettingsDropdownRow(
                            title = "Owner Return (Power-On Interruption)",
                            subtitle = "Action when car charger reconnects during an active Sentry incident",
                            currentValue = settings.sentryInterruptedClipAction.label,
                            options = SentryInterruptedClipAction.entries,
                            onSelect = { action ->
                                viewModel.updateSettings(settings.copy(sentryInterruptedClipAction = action))
                            },
                            displayTransform = { it.label }
                        )

                        HorizontalDivider(color = Color(0xFF1E293B))

                        // Voice Alerts
                        SettingsToggleRow(
                            title = "Voice Audio Prompts",
                            subtitle = "Spoken status cues for Sentry arming, armed, and disarmed status",
                            checked = settings.sentryVoiceAlertsEnabled,
                            onCheckedChange = { chk ->
                                viewModel.updateSettings(settings.copy(sentryVoiceAlertsEnabled = chk))
                            }
                        )

                        HorizontalDivider(color = Color(0xFF1E293B))

                        // Auto Disarm on Power
                        SettingsToggleRow(
                            title = "Auto-Disarm on Vehicle Power",
                            subtitle = "Automatically disarm Sentry and start Dashcam mode when engine starts",
                            checked = settings.sentryAutoDisarmOnPower,
                            onCheckedChange = { chk ->
                                viewModel.updateSettings(settings.copy(sentryAutoDisarmOnPower = chk))
                            }
                        )

                        HorizontalDivider(color = Color(0xFF1E293B))

                        // Auto-Lock Sentry Events
                        SettingsToggleRow(
                            title = "Auto-Lock Sentry Clips",
                            subtitle = "Automatically lock and protect clips captured by optical motion or acoustic triggers against loop overwrite",
                            checked = settings.sentryAutoLockEvents,
                            onCheckedChange = { chk ->
                                viewModel.updateSettings(settings.copy(sentryAutoLockEvents = chk))
                            }
                        )
                    }
                }
            }
        }
    }
}
