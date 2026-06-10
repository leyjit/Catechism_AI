package com.example.catechismapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle

fun scaleTypography(base: Typography, scale: Float): Typography {
    val clampedScale = scale.coerceIn(0.9f, 1.3f)
    return Typography(
        displayLarge = base.displayLarge.scaled(clampedScale),
        displayMedium = base.displayMedium.scaled(clampedScale),
        displaySmall = base.displaySmall.scaled(clampedScale),
        headlineLarge = base.headlineLarge.scaled(clampedScale),
        headlineMedium = base.headlineMedium.scaled(clampedScale),
        headlineSmall = base.headlineSmall.scaled(clampedScale),
        titleLarge = base.titleLarge.scaled(clampedScale),
        titleMedium = base.titleMedium.scaled(clampedScale),
        titleSmall = base.titleSmall.scaled(clampedScale),
        bodyLarge = base.bodyLarge.scaled(clampedScale),
        bodyMedium = base.bodyMedium.scaled(clampedScale),
        bodySmall = base.bodySmall.scaled(clampedScale),
        labelLarge = base.labelLarge.scaled(clampedScale),
        labelMedium = base.labelMedium.scaled(clampedScale),
        labelSmall = base.labelSmall.scaled(clampedScale)
    )
}

private fun TextStyle.scaled(scale: Float): TextStyle {
    return copy(
        fontSize = fontSize * scale,
        lineHeight = lineHeight * scale
    )
}
