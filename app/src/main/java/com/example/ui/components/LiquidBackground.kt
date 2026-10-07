package com.example.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.LocalLiquidGlass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

val LocalGlassBackdrop = staticCompositionLocalOf<HazeState?> { null }

/** A quiet, cached backdrop: chatting does not need a continuously running shader. */
@Composable
fun LiquidBackground(
  modifier: Modifier = Modifier,
  crystal: Boolean = false,
  content: @Composable () -> Unit
) {
  val config = LocalLiquidGlass.current
  val state = remember { HazeState() }
  val base = MaterialTheme.colorScheme.background
  val accent = config.accentColor
  val dark = config.isDark
  val showTint = !config.isReducedTransparency
  val useBackdrop = config.isGlassEnabled && showTint &&
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

  CompositionLocalProvider(LocalGlassBackdrop provides if (useBackdrop) state else null) {
    Box(modifier.fillMaxSize().background(base)) {
      Box(
        Modifier
          .fillMaxSize()
          .then(if (useBackdrop) Modifier.hazeSource(state) else Modifier)
          .drawWithCache {
            val tint = Brush.radialGradient(
              colors = listOf(
                accent.copy(alpha = if (dark) .10f else .06f),
                Color.Transparent
              ),
              center = Offset(size.width * .85f, 0f),
              radius = size.maxDimension
            )
            val lowerTint = Brush.verticalGradient(
              listOf(Color.Transparent, accent.copy(alpha = if (dark) .025f else .015f))
            )
            onDrawBehind {
              if (showTint) {
                drawRect(tint)
                if (crystal) drawRect(lowerTint)
              }
            }
          }
      )
      content()
    }
  }
}
