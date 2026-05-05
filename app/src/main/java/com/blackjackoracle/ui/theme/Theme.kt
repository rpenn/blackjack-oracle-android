package com.blackjackoracle.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val BjColorScheme = darkColorScheme(
    primary = BjColors.Accent,
    onPrimary = BjColors.BgBottom,
    secondary = BjColors.AccentSoft,
    background = BjColors.BgBottom,
    surface = BjColors.BgBottom,
    onBackground = BjColors.Neutral,
    onSurface = BjColors.Neutral,
    error = BjColors.Danger
)

@Composable
fun BlackjackOracleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BjColorScheme,
        typography = Typography,
        content = content
    )
}
