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
    initialValue = -0.045f,
    targetValue = 0.05f,
    animationSpec = infiniteRepeatable(tween(12_000), RepeatMode.Reverse),
    label = "glass_drift_x"
  )
  val driftY by motion.animateFloat(
    initialValue = 0.035f,
    targetValue = -0.04f,
    animationSpec = infiniteRepeatable(tween(14_000), RepeatMode.Reverse),
    label = "glass_drift_y"
  )

  val base = if (dark) Color(0xFF070B11) else Color(0xFFF3F7FC)
  CompositionLocalProvider(LocalGlassBackdrop provides if (config.isGlassEnabled) state else null) {
    Box(modifier.fillMaxSize().background(base)) {
      Canvas(
        Modifier
          .fillMaxSize()
          .then(if (config.isGlassEnabled) Modifier.hazeSource(state) else Modifier)
      ) {
        val dx = if (config.isReducedMotion) 0f else driftX
        val dy = if (config.isReducedMotion) 0f else driftY

        val blue = Offset(size.width * (.86f + dx), size.height * (.10f + dy))
        drawCircle(
          brush = Brush.radialGradient(
            listOf(
              Color(0xFF6AA8FF).copy(alpha = if (dark) .18f else .22f),
              Color(0xFFB9D9FF).copy(alpha = if (dark) .05f else .09f),
              Color.Transparent
            ),
            center = blue,
            radius = size.width * .78f
          ),
          radius = size.width * .78f,
          center = blue
        )

        val violet = Offset(size.width * (.02f - dx * .6f), size.height * (.78f - dy * .7f))
        drawCircle(
          brush = Brush.radialGradient(
            listOf(
              Color(0xFF8E7CFF).copy(alpha = if (dark) .12f else .13f),
              Color(0xFFC3B9FF).copy(alpha = if (dark) .025f else .045f),
              Color.Transparent
            ),
            center = violet,
            radius = size.width * .70f
          ),
          radius = size.width * .70f,
          center = violet
        )

        val aqua = Offset(size.width * .62f, size.height * (1.04f + dy * .35f))
        drawCircle(
          brush = Brush.radialGradient(
            listOf(
              Color(0xFF61DED1).copy(alpha = if (dark) .065f else .075f),
              Color.Transparent
            ),
            center = aqua,
            radius = size.width * .82f
          ),
          radius = size.width * .82f,
          center = aqua
        )

        if (crystal) {
          val tint = if (dark) Color(0xFF7192BE) else Color(0xFFC6DDF6)
          for (i in 0..5) {
            val x = size.width * (i % 3) / 2f
            val y = size.height * (i + 1) / 8f
            val path = Path().apply {
              moveTo(x, y)
              lineTo(size.width * (1f - (i % 2) * .22f), y + size.height * .25f)
              lineTo(size.width * .50f, size.height * .50f)
              close()
            }
            drawPath(
              path,
              Brush.linearGradient(
                listOf(
                  tint.copy(alpha = if (dark) .055f else .085f),
                  Color.Transparent,
                  Color.White.copy(alpha = if (dark) .016f else .045f)
                ),
                start = Offset(x, y),
                end = Offset(size.width / 2f, size.height / 2f)
              )
            )
          }
        }

        drawRect(
          Brush.verticalGradient(
            listOf(
              if (dark) Color.Black.copy(alpha = .10f) else Color.White.copy(alpha = .08f),
              Color.Transparent,
              if (dark) Color.Black.copy(alpha = .20f) else Color(0xFFBFD4EA).copy(alpha = .06f)
            )
          )
        )
      }
      content()
    }
  }
}
