package io.github.roaxio.dashguardpro.ui.components.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.roaxio.dashguardpro.storage.StorageStats
import io.github.roaxio.dashguardpro.ui.theme.CyberCyan
import io.github.roaxio.dashguardpro.ui.theme.EmergencyRed
import io.github.roaxio.dashguardpro.ui.theme.NeonAmber
import io.github.roaxio.dashguardpro.ui.theme.NeonGreen
import io.github.roaxio.dashguardpro.util.FormatUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StorageBar(
    stats: StorageStats,
    onRefresh: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val appVideoBytes = stats.appVideoBytes
    val maxAllowedBytes = stats.maxAllowedBytes.coerceAtLeast(1L)
    val lockedBytes = stats.lockedVideoBytes
    val loopBytes = stats.loopBufferBytes
    val freeDeviceGb = stats.deviceFreeBytes.toFloat() / (1024f * 1024f * 1024f)

    val usedFraction = (appVideoBytes.toFloat() / maxAllowedBytes.toFloat()).coerceIn(0f, 1f)
    val usedPercentage = (usedFraction * 100f)
    val lockedFraction = (lockedBytes.toFloat() / maxAllowedBytes.toFloat()).coerceIn(0f, usedFraction)
    val loopFraction = (usedFraction - lockedFraction).coerceAtLeast(0f)

    val statusColor = when {
        usedFraction > 0.90f -> EmergencyRed
        usedFraction > 0.75f -> NeonAmber
        else -> CyberCyan
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0D131F))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PieChart,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "LOOP STORAGE BUFFER",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Percentage badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${"%.1f".format(usedPercentage)}% FULL",
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (onRefresh != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Storage",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Numbers display
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "${FormatUtils.formatBytesWithUnit(appVideoBytes)} Used",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Quota: ${FormatUtils.formatBytesWithUnit(maxAllowedBytes)}",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Progress Bar (Multi-Segment: Locked (Amber) + Loop Buffer (Cyan) + Remaining (Dark))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFF1E293B))
        ) {
            Row(modifier = Modifier.fillMaxHeight().fillMaxWidth(fraction = usedFraction)) {
                if (lockedFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(lockedFraction.coerceAtLeast(0.001f))
                            .background(NeonAmber)
                    )
                }
                if (loopFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(loopFraction.coerceAtLeast(0.001f))
                            .background(CyberCyan)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Sub-legend details (FlowRow wraps dynamically on narrow screens without ellipsis truncation)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(CyberCyan)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Loop: ${FormatUtils.formatBytesWithUnit(loopBytes)}",
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (lockedBytes > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(NeonAmber)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Locked: ${FormatUtils.formatBytesWithUnit(lockedBytes)}",
                        color = NeonAmber,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(NeonGreen)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Free: ${"%.1f".format(freeDeviceGb)} GB",
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

