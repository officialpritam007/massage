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
import com.example.ui.theme.MidnightDark
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LiquidBackground(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
) {
  val glassConfig = LocalLiquidGlass.current

  val infiniteTransition = rememberInfiniteTransition(label = "liquid_mesh")
  val animProgress by if (!glassConfig.isReducedMotion) {
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = (2 * Math.PI).toFloat(),
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 14000, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
      ),
      label = "mesh_progress"
    )
  } else {
    androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
  }

  val baseColor = if (glassConfig.glassIntensity > 0.5f) MidnightDark else Color(0xFF0F172A)
  val accentColor = glassConfig.accentColor

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(baseColor)
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = size.width
      val h = size.height

      // Orb 1: Primary Accent glow top-left to center
      val orb1X = w * 0.3f + sin(animProgress.toDouble()).toFloat() * (w * 0.15f)
      val orb1Y = h * 0.25f + cos(animProgress.toDouble()).toFloat() * (h * 0.12f)
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(
            accentColor.copy(alpha = 0.22f * glassConfig.glassIntensity),
            accentColor.copy(alpha = 0.08f * glassConfig.glassIntensity),
            Color.Transparent
          ),
          center = Offset(orb1X, orb1Y),
          radius = w * 0.7f
        ),
        center = Offset(orb1X, orb1Y),
        radius = w * 0.7f
      )

      // Orb 2: Deep Indigo / Violet glow mid-right
      val orb2X = w * 0.8f + cos(animProgress.toDouble()).toFloat() * (w * 0.12f)
      val orb2Y = h * 0.65f + sin(animProgress.toDouble()).toFloat() * (h * 0.14f)
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(
            Color(0xFF3A7BD5).copy(alpha = 0.18f * glassConfig.glassIntensity),
            Color(0xFF8B5CF6).copy(alpha = 0.06f * glassConfig.glassIntensity),
            Color.Transparent
          ),
          center = Offset(orb2X, orb2Y),
          radius = w * 0.75f
        ),
        center = Offset(orb2X, orb2Y),
        radius = w * 0.75f
      )

      // Orb 3: Subtle Emerald / Teal aquatic highlight near bottom
      val orb3X = w * 0.4f + sin((animProgress + 2f).toDouble()).toFloat() * (w * 0.18f)
      val orb3Y = h * 0.85f
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(
            Color(0xFF00E5FF).copy(alpha = 0.12f * glassConfig.glassIntensity),
            Color.Transparent
          ),
          center = Offset(orb3X, orb3Y),
          radius = w * 0.6f
        ),
        center = Offset(orb3X, orb3Y),
        radius = w * 0.6f
      )
    }

    content()
  }
}
