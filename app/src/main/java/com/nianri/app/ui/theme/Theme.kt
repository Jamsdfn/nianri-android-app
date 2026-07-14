package com.nianri.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NightColorScheme = darkColorScheme(
    primary = Violet300,
    onPrimary = Night950,
    primaryContainer = Violet500,
    onPrimaryContainer = TextPrimary,
    secondary = Violet300,
    background = Night950,
    onBackground = TextPrimary,
    surface = Night800,
    onSurface = TextPrimary,
    onSurfaceVariant = TextMuted,
)

@Composable
fun NianriTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NightColorScheme,
        content = content,
    )
}
