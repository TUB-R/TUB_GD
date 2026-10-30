
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

// 亮色模式主色：深橄榄绿（保证在白底上的文字可读性）
val LightGreenPrimary = Color(0xFF558B2F)
// 暗色模式主色：亮黄绿（极具科技与活力感）
val DarkGreenPrimary = Color(0xFFB2FF59)

val DarkBg = Color(0xFF0F0F11)
val DarkSurface = Color(0xFF1C1C1E)
val LightBg = Color(0xFFF5F5F5)
val LightSurface = Color(0xFFFFFFFF)

private val DarkColorScheme = darkColorScheme(
    primary = DarkGreenPrimary, background = DarkBg, surface = DarkSurface,
    onPrimary = Color.Black, // 亮绿色背景上用黑色字
    onBackground = Color.White, onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = LightGreenPrimary, background = LightBg, surface = LightSurface,
    onPrimary = Color.White, // 深绿色背景上用白色字
    onBackground = Color.Black, onSurface = Color.Black
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
