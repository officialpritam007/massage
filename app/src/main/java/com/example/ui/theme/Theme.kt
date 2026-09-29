package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/** Shared design tokens for the Liquid Glass UI. */
data class LiquidGlassConfig(
  val glassIntensity: Float = 0.82f,
  val blurAlpha: Float = 0.70f,
  val cornerRadiusDp: Float = 32f,
  val borderStrength: Float = 0.70f,
  val accentColor: Color = CyanAccent,
  val isGlassEnabled: Boolean = true,
  val isReducedMotion: Boolean = false,
  val isDark: Boolean = true
)

val LocalLiquidGlass = compositionLocalOf { LiquidGlassConfig() }

private val DarkColorScheme = darkColorScheme(
  primary = CyanAccent,
  onPrimary = Color.Black,
  primaryContainer = DeepOcean,
  onPrimaryContainer = CyanNeon,
  secondary = AzureBlue,
  onSecondary = Color.Black,
  tertiary = VioletAccent,
  background = Color(0xFF03152D),
  onBackground = TextPrimary,
  surface = Color(0xE6102B50),
  onSurface = TextPrimary,
  surfaceVariant = SurfaceGlassDark,
  onSurfaceVariant = TextSecondary,
  outline = GlassBorderStrokeDark
)

private val LightColorScheme = lightColorScheme(
  primary = ElectricBlue,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFEAF3FF),
  onPrimaryContainer = ElectricBlue,
  secondary = AzureBlue,
  onSecondary = Color.White,
  tertiary = VioletAccent,
  background = Color(0xFFEAF4FF),
  onBackground = TextPrimaryLight,
  surface = Color(0xFF174575),
  onSurface = TextPrimary,
  surfaceVariant = Color(0xFF245D91),
  onSurfaceVariant = Color(0xFFE4F1FF),
  outline = GlassBorderStrokeLight
)

@Composable
fun LiquidChatTheme(
  darkTheme: Boolean = true,
  glassConfig: LiquidGlassConfig = LiquidGlassConfig(isDark = darkTheme),
  content: @Composable () -> Unit
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  val resolvedConfig = glassConfig.copy(isDark = darkTheme)

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
