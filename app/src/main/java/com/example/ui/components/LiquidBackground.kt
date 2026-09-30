package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.example.ui.theme.LocalLiquidGlass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

val LocalGlassBackdrop = staticCompositionLocalOf<HazeState?> { null }

@Composable
fun LiquidBackground(
  modifier: Modifier = Modifier,
  crystal: Boolean = false,
  content: @Composable () -> Unit
) {
  val config = LocalLiquidGlass.current
  val dark = config.isDark
  val state = remember { HazeState() }
  val motion = rememberInfiniteTransition(label = "liquid_background")
  val driftX by motion.animateFloat(
    initialValue = -0.06f,
    targetValue = 0.07f,
    animationSpec = infiniteRepeatable(tween(9000), RepeatMode.Reverse),
    label = "glass_drift_x"
  )
  val driftY by motion.animateFloat(
    initialValue = 0.04f,
    targetValue = -0.05f,
    animationSpec = infiniteRepeatable(tween(11000), RepeatMode.Reverse),
    label = "glass_drift_y"
  )

  CompositionLocalProvider(LocalGlassBackdrop provides if (config.isGlassEnabled) state else null) {
    Box(
      modifier
        .fillMaxSize()
        .background(if (dark) Color(0xFF090D14) else Color(0xFFF5F8FC))
    ) {
      Canvas(
        Modifier
          .fillMaxSize()
          .then(if (config.isGlassEnabled) Modifier.hazeSource(state) else Modifier)
      ) {
        val dx = if (config.isReducedMotion) 0f else driftX
        val dy = if (config.isReducedMotion) 0f else driftY

        val glowA = Offset(size.width * (0.82f + dx), size.height * (0.16f + dy))
        drawCircle(
          brush = Brush.radialGradient(
            listOf(
              Color(0xFF6EA8FF).copy(alpha = if (dark) .16f else .25f),
              Color(0xFF8DC7FF).copy(alpha = if (dark) .05f else .09f),
              Color.Transparent
            ),
            center = glowA,
            radius = size.width * .72f
          ),
          radius = size.width * .72f,
          center = glowA
        )

        val glowB = Offset(size.width * (0.12f - dx * .7f), size.height * (0.72f - dy * .8f))
        drawCircle(
          brush = Brush.radialGradient(
            listOf(
              Color(0xFF8D7CFF).copy(alpha = if (dark) .10f else .15f),
              Color.Transparent
            ),
            center = glowB,
            radius = size.width * .64f
          ),
          radius = size.width * .64f,
          center = glowB
        )

        val glowC = Offset(size.width * .58f, size.height * (1.02f + dy * .4f))
        drawCircle(
          brush = Brush.radialGradient(
            listOf(
              Color(0xFF68E1D4).copy(alpha = if (dark) .055f else .08f),
              Color.Transparent
            ),
            center = glowC,
            radius = size.width * .75f
          ),
          radius = size.width * .75f,
          center = glowC
        )

        if (crystal) {
          val tint = if (dark) Color(0xFF6D8BB4) else Color(0xFFC5DDF8)
          for (i in 0..7) {
            val x = size.width * (i % 4) / 3f
            val y = size.height * i / 8f
            val path = Path().apply {
              moveTo(x, y)
              lineTo(size.width * (1f - (i % 3) / 3f), y + size.height * .30f)
              lineTo(size.width * .49f, size.height * .50f)
              close()
            }
            drawPath(
              path,
              Brush.linearGradient(
                listOf(
                  tint.copy(alpha = if (dark) .11f else .16f),
                  Color.Transparent,
                  Color.White.copy(alpha = if (dark) .035f else .10f)
                ),
                start = Offset(x, y),
                end = Offset(size.width / 2, size.height / 2)
              )
            )
          }
        }
      }
      content()
    }
  }
}
