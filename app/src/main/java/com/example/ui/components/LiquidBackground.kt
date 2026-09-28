package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.theme.IceBlueBackground
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure white/black canvas with extremely subtle moving accent light behind the glass.
 * The background never becomes a colored gradient; the glass remains the visual focus.
 */
@Composable
fun LiquidBackground(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
) {
  val config = LocalLiquidGlass.current
  val transition = rememberInfiniteTransition(label = "liquid_glass_motion")
  val progress by if (!config.isReducedMotion) {
    transition.animateFloat(
      initialValue = 0f,
      targetValue = (2f * Math.PI).toFloat(),
      animationSpec = infiniteRepeatable(
        tween(16000, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
      ),
      label = "glass_orb_motion"
    )
  } else {
    androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
  }

  val base = if (config.isDark) Color.Black else IceBlueBackground
  val accent = config.accentColor
  val glowAlpha = if (config.isDark) 0.075f else 0.045f

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(base)
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = size.width
      val h = size.height
      val x1 = w * 0.18f + sin(progress.toDouble()).toFloat() * w * 0.08f
      val y1 = h * 0.16f + cos(progress.toDouble()).toFloat() * h * 0.06f
      val x2 = w * 0.84f + cos(progress.toDouble()).toFloat() * w * 0.07f
      val y2 = h * 0.62f + sin(progress.toDouble()).toFloat() * h * 0.08f

      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(accent.copy(alpha = glowAlpha), Color.Transparent),
          center = Offset(x1, y1),
          radius = w * 0.52f
        ),
        center = Offset(x1, y1),
        radius = w * 0.52f
      )
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(Color(0xFF7C5CFF).copy(alpha = glowAlpha * 0.65f), Color.Transparent),
          center = Offset(x2, y2),
          radius = w * 0.58f
        ),
        center = Offset(x2, y2),
        radius = w * 0.58f
      )
    }
    content()
  }
}
