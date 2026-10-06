package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.example.ui.theme.LocalLiquidGlass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

val LocalGlassBackdrop = staticCompositionLocalOf<HazeState?> { null }

/**
 * A quiet, static mesh behind the glass controls. No ambient animation keeps an
 * otherwise idle conversation rendering, including when reduced motion is on.
 * Brushes and optional facets are cached until the size or appearance changes.
 */
@Composable
fun LiquidBackground(
  modifier: Modifier = Modifier,
  crystal: Boolean = false,
  content: @Composable () -> Unit
) {
  val config = LocalLiquidGlass.current
  val dark = config.isDark
  val state = remember { HazeState() }
  val useBackdrop = config.isGlassEnabled && !config.isReducedTransparency
  val base = if (dark) Color(0xFF07121F) else Color(0xFFF3F7FC)

  CompositionLocalProvider(LocalGlassBackdrop provides if (useBackdrop) state else null) {
    Box(modifier.fillMaxSize().background(base)) {
      Box(
        Modifier
          .fillMaxSize()
          .then(if (useBackdrop) Modifier.hazeSource(state) else Modifier)
          .drawWithCache {
            val canvasSize = maxOf(size.width, size.height).coerceAtLeast(1f)
            val foundation = Brush.verticalGradient(
              if (dark) listOf(Color(0xFF07121F), Color(0xFF0A1C2C), Color(0xFF06131E))
              else listOf(Color(0xFFF3F7FC), Color(0xFFEDF5FA), Color(0xFFF3F9F9))
            )
            val azure = Color(0xFF3188CE)
            val teal = Color(0xFF28A695)
            val blue = Color(0xFF416CAF)
            val upperGlow = Brush.radialGradient(
              colors = listOf(azure.copy(alpha = if (dark) .17f else .10f), azure.copy(alpha = 0f)),
              center = Offset(size.width * .90f, size.height * .12f),
              radius = canvasSize * .58f
            )
            val lowerGlow = Brush.radialGradient(
              colors = listOf(teal.copy(alpha = if (dark) .13f else .08f), teal.copy(alpha = 0f)),
              center = Offset(size.width * .08f, size.height * .88f),
              radius = canvasSize * .52f
            )
            val middleGlow = Brush.radialGradient(
              colors = listOf(blue.copy(alpha = if (dark) .07f else .04f), blue.copy(alpha = 0f)),
              center = Offset(size.width * .18f, size.height * .40f),
              radius = canvasSize * .48f
            )
            val facets = if (crystal) {
              List(4) { index ->
                val y = size.height * (index + 1) / 6f
                Path().apply {
                  moveTo(0f, y)
                  lineTo(size.width, y + size.height * .12f)
                  lineTo(size.width * .64f, size.height * .54f)
                  close()
                }
              }
            } else emptyList()
            val facetColor = if (dark) Color(0xFF9BC4E0).copy(alpha = .015f)
              else Color.White.copy(alpha = .16f)

            onDrawBehind {
              drawRect(foundation)
              drawRect(upperGlow)
              drawRect(lowerGlow)
              drawRect(middleGlow)
              facets.forEach { path -> drawPath(path, facetColor) }
            }
          }
      )
      content()
    }
  }
}
