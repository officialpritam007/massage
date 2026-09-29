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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.LocalLiquidGlass
import kotlin.math.cos
import kotlin.math.sin

/** Shared royal-blue / deep-navy backdrop behind all translucent Liquid Glass surfaces. */
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
      animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing), repeatMode = RepeatMode.Restart),
      label = "blue_glow_motion"
    )
  } else {
    remember { mutableFloatStateOf(0f) }
  }

  val backgroundBrush = if (config.isDark) {
    Brush.verticalGradient(listOf(Color(0xFF1267D8), Color(0xFF07509B), Color(0xFF03152D), Color(0xFF020812)))
  } else {
    Brush.verticalGradient(listOf(Color(0xFFDCEEFF), Color(0xFFEAF4FF), Color(0xFFF5F9FF)))
  }
  val glowAlpha = if (config.isDark) 0.22f else 0.20f

  Box(modifier = modifier.fillMaxSize().background(backgroundBrush)) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = size.width
      val h = size.height
      val x1 = w * 0.22f + sin(progress.toDouble()).toFloat() * w * 0.06f
      val y1 = h * 0.08f + cos(progress.toDouble()).toFloat() * h * 0.035f
      val x2 = w * 0.88f + cos(progress.toDouble()).toFloat() * w * 0.04f
      val y2 = h * 0.58f + sin(progress.toDouble()).toFloat() * h * 0.05f
      val blueGlow = if (config.isDark) Color(0xFF4AA8FF) else Color(0xFF72B7FF)
      drawCircle(
        brush = Brush.radialGradient(listOf(blueGlow.copy(alpha = glowAlpha), Color.Transparent), center = Offset(x1, y1), radius = w * 0.72f),
        center = Offset(x1, y1), radius = w * 0.72f
      )
      drawCircle(
        brush = Brush.radialGradient(listOf(Color(0xFF176BFF).copy(alpha = glowAlpha * 0.62f), Color.Transparent), center = Offset(x2, y2), radius = w * 0.64f),
        center = Offset(x2, y2), radius = w * 0.64f
      )
    }
    content()
  }
}
