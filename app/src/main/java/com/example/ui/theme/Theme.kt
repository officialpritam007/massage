package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/** Shared design tokens for the app-wide Liquid Glass material. */
data class LiquidGlassConfig(
  val glassIntensity: Float = 0.85f,
  val blurAlpha: Float = 0.70f,
  val cornerRadiusDp: Float = 32f,
  val borderStrength: Float = 0.70f,
  val accentColor: Color = CyanAccent,
  val isGlassEnabled: Boolean = true,
  val isReducedMotion: Boolean = false,
  val isDark: Boolean = false
)

val LocalLiquidGlass = compositionLocalOf { LiquidGlassConfig() }

private val DarkColorScheme = darkColorScheme(
  primary = Color(0xFF72B7FF),
  onPrimary = Color(0xFF04111E),
  primaryContainer = Color(0xFF14385D),
  onPrimaryContainer = Color(0xFFDDEEFF),
  secondary = Color(0xFF9CCBFF),
  onSecondary = Color(0xFF07121C),
  tertiary = Color(0xFFC8B8FF),
  background = Color(0xFF090D14),
  onBackground = Color(0xFFF4F8FF),
  surface = Color(0xFF101722),
  onSurface = Color(0xFFF4F8FF),
  surfaceVariant = Color(0xFF1A2432),
  onSurfaceVariant = Color(0xFFC5D1DF),
  outline = Color(0xFF6A7E95),
  error = Color(0xFFFF8A8A),
  onError = Color(0xFF330606)
)

private val LightColorScheme = lightColorScheme(
  primary = ElectricBlue,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFE4F0FF),
  onPrimaryContainer = Color(0xFF083A78),
  secondary = AzureBlue,
  onSecondary = Color.White,
  tertiary = VioletAccent,
  background = Color(0xFFF5F8FC),
  onBackground = Color(0xFF111827),
  surface = Color(0xFFF8FBFF),
  onSurface = Color(0xFF111827),
  surfaceVariant = Color(0xFFEAF0F7),
  onSurfaceVariant = Color(0xFF465568),
  outline = Color(0xFF8795A7)
)

@Composable
fun LiquidChatTheme(
  darkTheme: Boolean = false,
  glassConfig: LiquidGlassConfig = LiquidGlassConfig(isDark = darkTheme),
  content: @Composable () -> Unit
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  val resolvedConfig = glassConfig.copy(isDark = darkTheme, isGlassEnabled = true)

  CompositionLocalProvider(LocalLiquidGlass provides resolvedConfig) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      content = { CompositionLocalProvider(LocalContentColor provides colorScheme.onSurface) { content() } }
    )
  }
}

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit
) {
  LiquidChatTheme(darkTheme = darkTheme, content = content)
}
