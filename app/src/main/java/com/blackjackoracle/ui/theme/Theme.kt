package com.blackjackoracle.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun BlackjackOracleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF041215),
            surface = Color(0xFF081318),
            primary = BjColors.Accent,
            secondary = BjColors.InfoBlue,
            error = BjColors.Danger,
        ),
        content = content,
    )
}
