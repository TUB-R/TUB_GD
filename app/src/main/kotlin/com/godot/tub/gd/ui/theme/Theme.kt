
package com.godot.tub.gd.ui.theme

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

val BluePrimary = Color(0xFF7C9EFF)
val DarkBg = Color(0xFF0F0F11)
val DarkSurface = Color(0xFF1C1C1E)
val LightBg = Color(0xFFF5F5F5)
val LightSurface = Color(0xFFFFFFFF)

private val DarkColorScheme = darkColorScheme(
    primary = BluePrimary, background = DarkBg, surface = DarkSurface,
    onPrimary = Color.White, onBackground = Color.White, onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = BluePrimary, background = LightBg, surface = LightSurface,
    onPrimary = Color.White, onBackground = Color.Black, onSurface = Color.Black
)

@Composable
fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
