package io.github.roaxio.dashguardpro.ui.components.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.roaxio.dashguardpro.ui.components.common.DashboardTopAppBar
import io.github.roaxio.dashguardpro.data.settings.DashGuardSettings
import io.github.roaxio.dashguardpro.ui.theme.HudBlack
import io.github.roaxio.dashguardpro.viewmodel.DashcamViewModel

@Composable
fun SmartPlaybackSubMenuScreen(
    settings: DashGuardSettings,
    viewModel: DashcamViewModel,
    onNavigateBack: () -> Unit,
    onShowCustomTripGapDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val subMenuListState = rememberLazyListState()

    Scaffold(
        topBar = {
            DashboardTopAppBar(
                title = "Trip Continuity & Playback",
                onNavigateBack = onNavigateBack,
                navigationIconContentDescription = "Back to Settings",
                navigationIconTestTag = "btn_back_smart_playback_sub_menu"
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
                .testTag("smart_playback_sub_menu_lazy_column"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                SectionHeader("TRIP CONTINUITY & GROUPING")
                SettingsCard {
                    // 1. Trip Inactivity Gap (Presets + Custom)
                    val presetGapOptions = listOf(1, 2, 3, 5, 10, -1) // -1 signifies Custom...
                    val currentGapDisplay = if (listOf(1, 2, 3, 5, 10).contains(settings.tripGroupingGapMinutes)) {
                        "${settings.tripGroupingGapMinutes} Minutes"
                    } else {
                        "${settings.tripGroupingGapMinutes} Min (Custom)"
                    }

                    SettingsDropdownRow(
                        title = "Trip Inactivity Gap",
                        subtitle = "Time threshold between recordings before splitting into a new Trip session in footage gallery",
                        currentValue = currentGapDisplay,
                        // Filter presets: trip gap (in seconds) must be >= adjacency window (in seconds).
                        // Custom (-1) is always shown but will be validated in the dialog.
                        options = presetGapOptions.filter { min ->
                            min == -1 || (min * 60) >= settings.loopAdjacencyThresholdSeconds
                        },
                        onSelect = { gapMin ->
                            if (gapMin == -1) {
                                onShowCustomTripGapDialog()
                            } else {
                                viewModel.updateSettings(settings.copy(tripGroupingGapMinutes = gapMin))
                            }
                        },
                        displayTransform = { min ->
                            if (min == -1) "Custom Duration..." else "$min Minutes"
                        }
                    )

                    HorizontalDivider(color = Color(0xFF1E293B))

                    // 2. Loop Adjacency Threshold (Next / Prev Window)
                    // Invariant: adjacency window (seconds) must never exceed trip gap (also in seconds).
                    // Options that would violate this are filtered out so the illegal state is unreachable.
                    val tripGapSeconds = settings.tripGroupingGapMinutes * 60
                    SettingsDropdownRow(
                        title = "Player Loop Adjacency Window",
                        subtitle = "Max gap between clips to enable instant Next / Prev loop playback in the video player",
                        currentValue = "${settings.loopAdjacencyThresholdSeconds} Seconds",
                        options = listOf(15, 30, 45, 60).filter { it <= tripGapSeconds },
                        onSelect = { sec ->
                            viewModel.updateSettings(settings.copy(loopAdjacencyThresholdSeconds = sec))
                        },
                        displayTransform = { "$it Seconds" }
                    )

                    HorizontalDivider(color = Color(0xFF1E293B))

                    // 3. Auto-Advance Playback Toggle
                    SettingsToggleRow(
                        title = "Auto-Advance Trip Loops",
                        subtitle = "Automatically starts playing the next adjacent loop of the trip when current video finishes",
                        checked = settings.autoAdvancePlayback,
                        onCheckedChange = { autoAdv ->
                            viewModel.updateSettings(settings.copy(autoAdvancePlayback = autoAdv))
                        }
                    )

                    HorizontalDivider(color = Color(0xFF1E293B))

                    // 4. Minimum Trip Qualification Duration
                    val tripDurationOptions = listOf(0, 5, 10, 15, 20, 30)
                    val currentTripDurationDisplay = if (settings.minTripDurationMinutes == 0) {
                        "None (All Trips)"
                    } else {
                        "${settings.minTripDurationMinutes} Minutes"
                    }

                    SettingsDropdownRow(
                        title = "Minimum Trip Duration",
                        subtitle = "Continuous recordings must reach this total duration to qualify as a full Trip Session in footage gallery",
                        currentValue = currentTripDurationDisplay,
                        options = tripDurationOptions,
                        onSelect = { min ->
                            viewModel.updateSettings(settings.copy(minTripDurationMinutes = min))
                        },
                        displayTransform = { min ->
                            if (min == 0) "None (All Trips)" else "$min Minutes"
                        }
                    )

                    HorizontalDivider(color = Color(0xFF1E293B))

                    // 5. Include Inactivity Gaps in Trip Duration
                    SettingsToggleRow(
                        title = "Include Gaps in Trip Duration",
                        subtitle = "Count inactivity pauses between recorded clips towards the minimum trip qualification duration",
                        checked = settings.includeGapsInTripDuration,
                        onCheckedChange = { inc ->
                            viewModel.updateSettings(settings.copy(includeGapsInTripDuration = inc))
                        }
                    )
                }
            }
        }
    }
}
