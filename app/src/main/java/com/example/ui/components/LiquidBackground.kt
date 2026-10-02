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

  val base = if (dark) Color(0xFF020508) else Color(0xFFF0F7F7)
  CompositionLocalProvider(LocalGlassBackdrop provides if (config.isGlassEnabled) state else null) {
    Box(modifier.fillMaxSize().background(base)) {
      Canvas(
        Modifier
          .fillMaxSize()
          .then(if (config.isGlassEnabled) Modifier.hazeSource(state) else Modifier)
      ) {
        val dx = if (config.isReducedMotion) 0f else driftX
        val dy = if (config.isReducedMotion) 0f else driftY

        val greenTop = Offset(size.width * (.12f + dx), size.height * (.06f + dy))
        drawCircle(
          brush = Brush.radialGradient(
            listOf(
              Color(0xFF00D58D).copy(alpha = if (dark) .34f else .24f),
              Color(0xFF00A878).copy(alpha = if (dark) .13f else .09f),
              Color.Transparent
            ),
            center = greenTop,
            radius = size.width * .74f
          ),
          radius = size.width * .74f,
          center = greenTop
        )

        val blueTop = Offset(size.width * (.86f + dx), size.height * (.08f + dy))
        drawCircle(
          brush = Brush.radialGradient(
            listOf(
              Color(0xFF006DFF).copy(alpha = if (dark) .23f else .15f),
              Color(0xFF2E98FF).copy(alpha = if (dark) .08f else .05f),
              Color.Transparent
            ),
            center = blueTop,
            radius = size.width * .82f
          ),
          radius = size.width * .82f,
          center = blueTop
        )

        val aquaBottom = Offset(size.width * (.32f - dx * .7f), size.height * (.94f + dy * .28f))
        drawCircle(
          brush = Brush.radialGradient(
            listOf(
              Color(0xFF00DFA0).copy(alpha = if (dark) .24f else .15f),
              Color(0xFF00A3CB).copy(alpha = if (dark) .09f else .05f),
              Color.Transparent
            ),
            center = aquaBottom,
            radius = size.width * .80f
          ),
          radius = size.width * .80f,
          center = aquaBottom
        )

        val violetRight = Offset(size.width * (.92f - dx * .3f), size.height * (.68f - dy * .4f))
        drawCircle(
          brush = Brush.radialGradient(
            listOf(
              Color(0xFFAA36FF).copy(alpha = if (dark) .18f else .10f),
              Color(0xFF3C78FF).copy(alpha = if (dark) .10f else .05f),
              Color.Transparent
            ),
            center = violetRight,
            radius = size.width * .60f
          ),
          radius = size.width * .60f,
          center = violetRight
        )

        val redRight = Offset(size.width * (.84f + dx * .4f), size.height * (.43f + dy * .4f))
        drawCircle(
          brush = Brush.radialGradient(
            listOf(Color(0xFFFF316F).copy(alpha = if (dark) .12f else .06f), Color.Transparent),
            center = redRight,
            radius = size.width * .40f
          ),
          radius = size.width * .40f,
          center = redRight
        )

        if (crystal) {
          val tint = if (dark) Color(0xFF74A8B3) else Color(0xFFB7D5D7)
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
                  tint.copy(alpha = if (dark) .055f else .08f),
                  Color.Transparent,
                  Color.White.copy(alpha = if (dark) .018f else .035f)
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
              if (dark) Color.Black.copy(alpha = .10f) else Color.White.copy(alpha = .04f),
              Color.Transparent,
              if (dark) Color.Black.copy(alpha = .24f) else Color(0xFF6E989B).copy(alpha = .08f)
            )
          )
        )
      }
      content()
    }
  }
}
