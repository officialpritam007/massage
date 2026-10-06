package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Shared design tokens for the app-wide Liquid Glass material. */
@Immutable
data class LiquidGlassConfig(
  val glassIntensity: Float = 0.75f,
  val blurAlpha: Float = 0.35f,
  val cornerRadiusDp: Float = 30f,
  val borderStrength: Float = 0.70f,
  val accentColor: Color = CyanAccent,
  val isGlassEnabled: Boolean = true,
  val isReducedMotion: Boolean = false,
  val isReducedTransparency: Boolean = false,
  val isDark: Boolean = true
)

val LocalLiquidGlass = compositionLocalOf { LiquidGlassConfig() }

private val DarkColorScheme = darkColorScheme(
  primary = CyanAccent,
  onPrimary = Color(0xFF08243C),
  primaryContainer = Color(0xFF163E62),
  onPrimaryContainer = Color(0xFFD5EBFF),
  secondary = Color(0xFF53D8C4),
  onSecondary = Color(0xFF00151C),
  tertiary = Color(0xFFA98CFF),
  background = MidnightDark,
  onBackground = TextPrimary,
  surface = SlateDark,
  onSurface = TextPrimary,
  surfaceVariant = Color(0xFF1D2E46),
  onSurfaceVariant = TextSecondary,
  outline = Color(0xFF8192AB),
  error = Color(0xFFFF8D9B),
  onError = Color(0xFF35050D)
)

private val LightColorScheme = lightColorScheme(
  primary = Color(0xFF0068D9),
  onPrimary = Color.White,
  primaryContainer = Color(0xFFDCEAFF),
  onPrimaryContainer = Color(0xFF153C69),
  secondary = Color(0xFF007B70),
  onSecondary = Color.White,
  tertiary = VioletAccent,
  background = Color(0xFFF2F6FC),
  onBackground = TextPrimaryLight,
  surface = Color(0xFFFAFCFF),
  onSurface = TextPrimaryLight,
  surfaceVariant = Color(0xFFE6EDF7),
  onSurfaceVariant = TextSecondaryLight,
  outline = Color(0xFF798BA5)
)

@Composable
fun LiquidChatTheme(
  darkTheme: Boolean = true,
  glassConfig: LiquidGlassConfig = LiquidGlassConfig(
    isDark = darkTheme,
    accentColor = if (darkTheme) CyanAccent else Color(0xFF0068D9)
  ),
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
    glassIntensity = glassConfig.glassIntensity.finiteOr(.75f).coerceIn(0.35f, 1f),
    blurAlpha = glassConfig.blurAlpha.finiteOr(.35f).coerceIn(0f, 0.65f),
    cornerRadiusDp = glassConfig.cornerRadiusDp.finiteOr(30f).coerceIn(12f, 56f),
    borderStrength = glassConfig.borderStrength.finiteOr(.7f).coerceIn(0.20f, 1f)
  )

  CompositionLocalProvider(LocalLiquidGlass provides resolvedConfig) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      content = content
    )
  }
}

private fun Float.finiteOr(fallback: Float) = if (isFinite()) this else fallback

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit
) {
  LiquidChatTheme(darkTheme = darkTheme, content = content)
}
