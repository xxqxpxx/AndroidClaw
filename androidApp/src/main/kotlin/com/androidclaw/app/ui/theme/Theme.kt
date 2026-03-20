package com.androidclaw.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.androidclaw.app.settings.SettingsManager
import com.androidclaw.app.settings.ThemeMode
import org.koin.compose.koinInject

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    secondary = Color(0xFFCE93D8),
    tertiary = Color(0xFF80CBC4),
    surface = Color(0xFF1A1A2E),
    background = Color(0xFF0F0F23),
    surfaceVariant = Color(0xFF2A2A3E),
    surfaceContainerHighest = Color(0xFF252538),
    onPrimary = Color.Black,
    onSurface = Color.White,
    onBackground = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1565C0),
    secondary = Color(0xFF7B1FA2),
    tertiary = Color(0xFF00695C),
    surface = Color(0xFFFFFBFE),
    background = Color(0xFFF8F8FF),
    surfaceVariant = Color(0xFFE8E8F0),
    surfaceContainerHighest = Color(0xFFE0E0EA),
    onPrimary = Color.White,
    onSurface = Color.Black,
    onBackground = Color.Black
)

@Composable
fun AndroidClawTheme(
    settingsManager: SettingsManager = koinInject(),
    content: @Composable () -> Unit
) {
    val themeMode by settingsManager.themeMode.collectAsState()
    val dynamicColorsEnabled by settingsManager.dynamicColors.collectAsState()

    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }

    val colorScheme = when {
        dynamicColorsEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
