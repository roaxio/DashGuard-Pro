package io.github.roaxio.dashguardpro.ui.components.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.roaxio.dashguardpro.model.GalleryFilter
import io.github.roaxio.dashguardpro.ui.theme.CyberCyan
import io.github.roaxio.dashguardpro.ui.theme.NeonAmber
import io.github.roaxio.dashguardpro.ui.theme.NeonGreen

/**
 * Filter chip row for All Clips gallery view mode.
 * Allows filtering by: ALL, LOCKED (Protected), DASHCAM, SENTRY, and FUSED_TELEMETRY.
 */
@Composable
fun GalleryFilterBar(
    selectedFilter: GalleryFilter,
    onSelectFilter: (GalleryFilter) -> Unit,
    allCount: Int,
    lockedCount: Int,
    dashCount: Int,
    sentryCount: Int,
    fusedCount: Int,
    modifier: Modifier = Modifier
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        item {
            FilterChip(
                selected = selectedFilter == GalleryFilter.ALL,
                onClick = { onSelectFilter(GalleryFilter.ALL) },
                label = { Text("All ($allCount)") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CyberCyan,
                    selectedLabelColor = Color.Black
                ),
                modifier = Modifier.testTag("gallery_filter_all")
            )
        }
        item {
            FilterChip(
                selected = selectedFilter == GalleryFilter.LOCKED,
                onClick = { onSelectFilter(GalleryFilter.LOCKED) },
                label = { Text("🔒 Protected ($lockedCount)") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NeonAmber,
                    selectedLabelColor = Color.Black
                ),
                modifier = Modifier.testTag("gallery_filter_locked")
            )
        }
        item {
            FilterChip(
                selected = selectedFilter == GalleryFilter.DASHCAM,
                onClick = { onSelectFilter(GalleryFilter.DASHCAM) },
                label = { Text("🚗 Dashcam ($dashCount)") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CyberCyan,
                    selectedLabelColor = Color.Black
                ),
                modifier = Modifier.testTag("gallery_filter_dashcam")
            )
        }
        item {
            FilterChip(
                selected = selectedFilter == GalleryFilter.SENTRY,
                onClick = { onSelectFilter(GalleryFilter.SENTRY) },
                label = { Text("🛡️ Sentry ($sentryCount)") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NeonGreen,
                    selectedLabelColor = Color.Black
                ),
                modifier = Modifier.testTag("gallery_filter_sentry")
            )
        }
        item {
            FilterChip(
                selected = selectedFilter == GalleryFilter.FUSED_TELEMETRY,
                onClick = { onSelectFilter(GalleryFilter.FUSED_TELEMETRY) },
                label = { Text("Fused Telemetry ($fusedCount)") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CyberCyan,
                    selectedLabelColor = Color.Black
                ),
                modifier = Modifier.testTag("gallery_filter_fused")
            )
        }
    }
}
