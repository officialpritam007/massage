package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Shared design tokens for the app-wide Liquid Glass material. */
data class LiquidGlassConfig(
  val glassIntensity: Float = 0.75f,
  val blurAlpha: Float = 0.35f,
  val cornerRadiusDp: Float = 30f,
  val borderStrength: Float = 0.70f,
  val accentColor: Color = CyanAccent,
  val isGlassEnabled: Boolean = true,
  val isReducedMotion: Boolean = false,
  val isDark: Boolean = true
)

val LocalLiquidGlass = compositionLocalOf { LiquidGlassConfig() }

private val DarkColorScheme = darkColorScheme(
  primary = Color(0xFF00E39C),
  onPrimary = Color(0xFF001B14),
  primaryContainer = Color(0xFF005F49),
  onPrimaryContainer = Color(0xFFB6FFE7),
  secondary = Color(0xFF72D8FF),
  onSecondary = Color(0xFF00151C),
  tertiary = Color(0xFFA98CFF),
  background = Color(0xFF020508),
  onBackground = Color(0xFFF8FFFE),
  surface = Color(0xFF0B171D),
  onSurface = Color(0xFFF8FFFE),
  surfaceVariant = Color(0xFF172930),
  onSurfaceVariant = Color(0xFFB3C2C9),
  outline = Color(0xFF5D7A82),
  error = Color(0xFFFF8D9B),
  onError = Color(0xFF35050D)
)

private val LightColorScheme = lightColorScheme(
  primary = Color(0xFF008D70),
  onPrimary = Color.White,
  primaryContainer = Color(0xFFB9F7E8),
  onPrimaryContainer = Color(0xFF00382C),
  secondary = Color(0xFF0A7FA4),
  onSecondary = Color.White,
  tertiary = VioletAccent,
  background = Color(0xFFF3F8F8),
  onBackground = Color(0xFF0A191C),
  surface = Color(0xFFF8FCFC),
  onSurface = Color(0xFF0A191C),
  surfaceVariant = Color(0xFFE5F0F0),
  onSurfaceVariant = Color(0xFF496168),
  outline = Color(0xFF82969C)
)

@Composable
fun LiquidChatTheme(
  darkTheme: Boolean = true,
  glassConfig: LiquidGlassConfig = LiquidGlassConfig(isDark = darkTheme),
  content: @Composable () -> Unit
) {
  val base = if (darkTheme) DarkColorScheme else LightColorScheme
  val colorScheme = base.copy(
    primary = glassConfig.accentColor,
    onPrimary = if (glassConfig.accentColor.luminance() > .179f) Color.Black else Color.White
  )
  // Preserve the user's glass toggle and sanitize persisted values so malformed
  // legacy settings cannot produce broken corners, excessive blur or invisible rims.
  val resolvedConfig = glassConfig.copy(
    isDark = darkTheme,
    glassIntensity = glassConfig.glassIntensity.coerceIn(0.35f, 1f),
    blurAlpha = glassConfig.blurAlpha.coerceIn(0f, 0.65f),
    cornerRadiusDp = glassConfig.cornerRadiusDp.coerceIn(12f, 56f),
    borderStrength = glassConfig.borderStrength.coerceIn(0.20f, 1f)
  )

  CompositionLocalProvider(LocalLiquidGlass provides resolvedConfig) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      content = content
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
