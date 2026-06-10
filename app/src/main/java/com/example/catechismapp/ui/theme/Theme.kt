package com.example.catechismapp.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = LiturgicalBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6EDF5),
    onPrimaryContainer = LiturgicalBlue,
    secondary = LiturgicalGold,
    onSecondary = Color.White,
    secondaryContainer = LiturgicalGoldContainer,
    onSecondaryContainer = Color(0xFF5A4300),
    background = LightBackground,
    onBackground = Color(0xFF201D1A),
    surface = LightSurface,
    onSurface = Color(0xFF201D1A),
    surfaceVariant = Color(0xFFECE7DF),
    onSurfaceVariant = Color(0xFF4D4740),
    outline = DividerColor
)

private val DarkColorScheme = darkColorScheme(
    primary = LiturgicalBlueDark,
    onPrimary = Color(0xFF102740),
    primaryContainer = Color(0xFF1F354D),
    onPrimaryContainer = LiturgicalBlueDark,
    secondary = LiturgicalGoldDark,
    onSecondary = Color(0xFF261D00),
    secondaryContainer = LiturgicalGoldContainerDark,
    onSecondaryContainer = Color(0xFFFFEFA6),
    background = DarkBackground,
    onBackground = Color(0xFFEBE6E1),
    surface = DarkSurface,
    onSurface = Color(0xFFEBE6E1),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFCDC6BE),
    outline = DividerColorDark
)

@Composable
fun CatechismAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Force custom liturgical theme by default
    fontScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val typography = scaleTypography(Typography, fontScale)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}
