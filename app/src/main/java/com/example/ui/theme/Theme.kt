package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** iOS 26 inspired glass material styles. */
enum class GlassStyle { Clear, Regular }

/**
 * Shared design tokens for the app-wide Liquid Glass material.
 *
 * Every derived token below is computed from the Appearance sliders (glassIntensity,
 * blurAlpha, borderStrength) so chrome surfaces — headers, composers, navigation bars,
 * sheets and tiles — respond to the same configuration instead of carrying literals.
 */
data class LiquidGlassConfig(
  val glassIntensity: Float = 0.85f,
  val blurAlpha: Float = 0.70f,
  val cornerRadiusDp: Float = 32f,
  val borderStrength: Float = 0.70f,
  val accentColor: Color = CyanAccent,
  val glassStyle: GlassStyle = GlassStyle.Regular,
  val isGlassEnabled: Boolean = true,
  val isReducedMotion: Boolean = false,
  val isDark: Boolean = false
) {
  val isClear: Boolean get() = glassStyle == GlassStyle.Clear

  /** Tint density multiplier shared by every derived surface (roughly 0.55–1.15). */
  private val density: Float
    get() = (0.55f + glassIntensity * 0.45f + blurAlpha * 0.25f).coerceIn(0.55f, 1.15f)

  /** Large floating chrome: chat header, composer bar, bottom navigation. */
  val chromeSurface: Color
    get() = if (isDark) {
      Color(0xFF0D1723).copy(alpha = (0.34f * density).coerceIn(0.18f, 0.54f))
    } else {
      Color.White.copy(alpha = (0.34f * density).coerceIn(0.18f, 0.54f))
    }

  /** Denser panel used by sheets and dialogs floating above content. */
  val panelSurface: Color
    get() = if (isDark) {
      Color(0xFF0D1723).copy(alpha = (0.62f * density).coerceIn(0.36f, 0.86f))
    } else {
      Color(0xFFF9FCFF).copy(alpha = (0.70f * density).coerceIn(0.42f, 0.90f))
    }

  /** Quiet inset elements inside chrome: composer field shells, tiles, reply previews. */
  val insetSurface: Color
    get() = if (isDark) {
      Color.White.copy(alpha = (0.07f * density).coerceIn(0.04f, 0.15f))
    } else {
      Color.White.copy(alpha = (0.52f * density).coerceIn(0.32f, 0.72f))
    }

  /** Long-list rows: lighter than chrome so the wallpaper stays visible. */
  val rowSurface: Color
    get() = if (isDark) {
      Color.White.copy(alpha = (0.05f * density).coerceIn(0.03f, 0.12f))
    } else {
      Color.White.copy(alpha = (0.34f * density).coerceIn(0.20f, 0.52f))
    }

  /** Border tone shared by every themed surface; scales with the border slider. */
  val chromeBorder: Color
    get() = (if (isDark) GlassBorderStrokeDark else GlassBorderStrokeLight).copy(
      alpha = ((if (isDark) 0.22f else 0.20f) * (0.5f + borderStrength)).coerceIn(0.06f, 0.60f)
    )

  /** Default card edge, kept identical in feel to the hand-tuned material. */
  val edgeBorder: Color
    get() = if (isDark) GlassBorderStrokeDark else Color.White.copy(alpha = if (isClear) 0.62f else 0.38f)

  /** Specular top highlight for borders and rims. */
  val highlightColor: Color
    get() = if (isDark) GlassHighlight.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.95f)

  /** Depth shadow; the border slider doubles as the depth control. */
  val shadowColor: Color
    get() = if (isDark) {
      Color.Black.copy(alpha = (0.20f + borderStrength * 0.34f).coerceIn(0.14f, 0.60f))
    } else {
      Color.Black.copy(alpha = (0.04f + borderStrength * 0.12f).coerceIn(0.03f, 0.20f))
    }

  val compactElevation: Dp get() = (1f + borderStrength * 4f).dp
  val panelElevation: Dp get() = (2f + borderStrength * 6f).dp
  val floatingElevation: Dp get() = (5f + borderStrength * 10f).dp

  /** Sheets and dialogs sit above everything else, so they keep a little extra depth. */
  val sheetElevation: Dp get() = floatingElevation + 4.dp

  /** Strong accent fill for primary actions; opaque enough to read on glass. */
  val solidAccent: Color
    get() = accentColor.copy(alpha = (0.78f + glassIntensity * 0.16f).coerceIn(0.72f, 0.94f))

  /** Accent fill for tinted states (upload progress, selection capsule). */
  fun accentFill(strength: Float = 1f): Color = tintFill(accentColor, strength)

  /** Theme-aware tinted fill for status surfaces (accent, error, recording). */
  fun tintFill(color: Color, strength: Float = 1f): Color = color.copy(
    alpha = ((if (isDark) 0.14f else 0.10f) * strength * density).coerceIn(0.05f, 0.36f)
  )

  /** Scrim behind sheets, dialogs and viewers. */
  val scrim: Color
    get() = Color.Black.copy(alpha = ((if (isDark) 0.30f else 0.16f) * density).coerceIn(0.10f, 0.50f))
}

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
      content = content
    )
  }
}

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit
) {
  LiquidChatTheme(darkTheme = darkTheme, glassConfig = LiquidGlassConfig(isDark = darkTheme, glassStyle = GlassStyle.Clear), content = content)
}
