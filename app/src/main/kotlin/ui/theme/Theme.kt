package io.github.roaxio.dashguardpro.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = Color.Black,
    primaryContainer = HudSurfaceVariant,
    onPrimaryContainer = CyberCyan,
    secondary = NeonAmber,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF2D2310),
    onSecondaryContainer = NeonAmber,
    tertiary = NeonGreen,
    onTertiary = Color.Black,
    error = EmergencyRed,
    onError = Color.White,
    errorContainer = EmergencyRedDark,
    onErrorContainer = Color.White,
    background = HudBlack,
    onBackground = TextPrimary,
    surface = HudSurface,
    onSurface = TextPrimary,
    surfaceVariant = HudSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = HudCardBorder
)

@Composable
fun DashGuardProTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = HudBlack.toArgb()
                window.navigationBarColor = HudBlack.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
