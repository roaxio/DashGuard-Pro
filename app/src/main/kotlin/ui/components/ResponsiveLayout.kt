package io.github.roaxio.dashguardpro.ui.components

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Responsive Window Size Classification for Automotive & Mobile Dashcam UI.
 * Categorizes the active window width into standardized buckets to support
 * seamless, proportional layout transitions across narrow phones, foldables,
 * tablets, and landscape cockpit docks.
 */
enum class WindowSizeBucket {
    COMPACT,   // < 380dp (e.g., Galaxy S20 at 360dp, compact phones)
    STANDARD,  // 380dp - 599dp (standard modern smartphones)
    EXPANDED   // >= 600dp (tablets, foldables, and landscape cockpit modes)
}

/**
 * Real-time reactive screen metrics extracted from the runtime display environment.
 */
data class ScreenMetrics(
    val width: Dp,
    val height: Dp,
    val isLandscape: Boolean,
    val bucket: WindowSizeBucket
) {
    val isCompact: Boolean get() = bucket == WindowSizeBucket.COMPACT
    val isExpanded: Boolean get() = bucket == WindowSizeBucket.EXPANDED
}

/**
 * Extracts and remembers screen metrics in real time based on active configuration.
 */
@Composable
fun rememberScreenMetrics(): ScreenMetrics {
    val configuration = LocalConfiguration.current
    val width = configuration.screenWidthDp.dp
    val height = configuration.screenHeightDp.dp
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val bucket = when {
        isLandscape || width >= 600.dp -> WindowSizeBucket.EXPANDED
        width < 380.dp -> WindowSizeBucket.COMPACT
        else -> WindowSizeBucket.STANDARD
    }
    return remember(width, height, isLandscape) {
        ScreenMetrics(width, height, isLandscape, bucket)
    }
}

/**
 * Derives clamped fluid typography scaled proportionally from the active viewport width (Rule 22.2).
 * Formula: fontSize = clamp(minSp, viewportWidthDp * scaleFactor, maxSp).sp
 */
@Composable
fun rememberFluidFontSize(
    minSp: Float = 15f,
    scaleFactor: Float = 0.045f,
    maxSp: Float = 20f
): TextUnit {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenWidthDp, minSp, scaleFactor, maxSp) {
        val calculated = configuration.screenWidthDp * scaleFactor
        calculated.coerceIn(minSp, maxSp).sp
    }
}

