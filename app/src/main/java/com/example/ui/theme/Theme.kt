package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

data class LiquidGlassConfig(
  val glassIntensity: Float = 0.85f,
  val blurAlpha: Float = 0.70f,
  val accentColor: Color = CyanAccent,
  val isGlassEnabled: Boolean = true,
  val isReducedMotion: Boolean = false
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
  background = MidnightDark,
  onBackground = TextPrimary,
  surface = SurfaceGlassDark,
  onSurface = TextPrimary,
  surfaceVariant = SurfaceGlassCard,
  onSurfaceVariant = TextSecondary,
  outline = GlassBorderStroke
)

private val LightColorScheme = lightColorScheme(
  primary = ElectricBlue,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFDCEBFF),
  onPrimaryContainer = ElectricBlue,
  secondary = AzureBlue,
  onSecondary = Color.White,
  tertiary = VioletAccent,
  background = Color(0xFFF8FAFC),
  onBackground = TextPrimaryLight,
  surface = Color(0xFFFFFFFF),
  onSurface = TextPrimaryLight,
  surfaceVariant = Color(0xFFF1F5F9),
  onSurfaceVariant = TextSecondaryLight,
  outline = Color(0xFFE2E8F0)
)

@Composable
fun LiquidChatTheme(
  darkTheme: Boolean = true, // Default to Liquid Glass dark aesthetics
  glassConfig: LiquidGlassConfig = LiquidGlassConfig(),
  content: @Composable () -> Unit
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  CompositionLocalProvider(LocalLiquidGlass provides glassConfig) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      content = content
    )
  }
}

// Retain alias for compatibility
@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit
) {
  LiquidChatTheme(darkTheme = darkTheme, content = content)
}
