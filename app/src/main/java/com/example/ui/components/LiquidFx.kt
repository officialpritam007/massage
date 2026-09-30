package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * iOS 26 "Liquid Glass" FX engine.
 *
 * - [Modifier.frostEdges]    : progressive frost rails above/below scrollable content (scroll-edge effect)
 * - [Modifier.liquidSheen]   : animated light sweep used on primary buttons
 * - [Modifier.lensEdge]      : specular refraction ring + inner glow on the glass edge
 * - [Modifier.lensHighlight] : diagonal wet highlight blob on the surface
 * - [Modifier.pressGlow]     : press-scale + springy settle
 * - [rememberLiquidHaptics]  : consistent haptic vocabulary
 */

/** Haptics: one shared vocabulary so every surface feels the same. */
class LiquidHaptics(private val perform: (HapticFeedbackType) -> Unit) {
  fun tap() = perform(HapticFeedbackType.TextHandleMove)
  fun confirm() = perform(HapticFeedbackType.LongPress)
  fun toggle() = perform(HapticFeedbackType.TextHandleMove)
}

@Composable
fun rememberLiquidHaptics(): LiquidHaptics {
  val h = LocalHapticFeedback.current
  return remember(h) { LiquidHaptics { type -> runCatching { h.performHapticFeedback(type) } } }
}

/** Frost rails: content freezes under a soft veil at list edges (iOS 26 scroll-edge effect). */
fun Modifier.frostEdges(
  height: Dp = 30.dp,
  dark: Boolean = false
): Modifier = drawWithContent {
  drawContent()
  val h = height.toPx()
  if (h <= 0f) return@drawWithContent
  val veil = if (dark) Color(0xFF0A1220) else Color(0xFFF4F8FF)
  drawRect(
    brush = Brush.verticalGradient(
      colors = listOf(veil.copy(alpha = .50f), veil.copy(alpha = .18f), Color.Transparent),
      startY = 0f,
      endY = h
    ),
    size = Size(size.width, h)
  )
  drawRect(
    brush = Brush.verticalGradient(
      colors = listOf(Color.Transparent, veil.copy(alpha = .18f), veil.copy(alpha = .50f)),
      startY = size.height - h,
      endY = size.height
    ),
    topLeft = Offset(0f, size.height - h),
    size = Size(size.width, h)
  )
}

/** Sheen sweep: a specular band that periodically glides across the surface. */
fun Modifier.liquidSheen(enabled: Boolean = true, dark: Boolean = false): Modifier =
  if (!enabled) {
    this
  } else {
    composed {
      val transition = rememberInfiniteTransition(label = "liquid_sheen")
      val sweep by transition.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "sheen_progress"
      )
      drawWithContent {
        drawContent()
        val band = size.width * 0.34f
        val x = sweep * size.width
        drawRect(
          brush = Brush.horizontalGradient(
            colors = listOf(
              Color.Transparent,
              Color.White.copy(alpha = if (dark) .09f else .30f),
              Color.Transparent
            ),
            startX = x - band,
            endX = x + band
          )
        )
      }
    }
  }

/** Specular edge: bright rim on the top/left, soft refraction glow inside the rim (lensing cue). */
fun Modifier.lensEdge(
  shape: Shape,
  dark: Boolean = false,
  strength: Float = 1f
): Modifier = drawWithCache {
  val outline = shape.createOutline(size, layoutDirection, this)
  val outlinePath: Path = when (val o = outline) {
    is Outline.Generic -> o.path
    is Outline.Rounded -> Path().apply { addRoundRect(o.roundRect) }
    is Outline.Rectangle -> Path().apply { addRect(o.rect) }
  }
  val rim = Color.White.copy(alpha = (if (dark) .55f else .92f) * strength)
  val innerGlow = Color.White.copy(alpha = (if (dark) .12f else .45f) * strength)
  val refraction = Color.White.copy(alpha = (if (dark) .05f else .16f) * strength)
  onDrawWithContent {
    drawContent()
    drawPath(outlinePath, brush = Brush.verticalGradient(listOf(rim, Color.Transparent, rim.copy(alpha = rim.alpha * .55f))), style = Stroke(width = 1.dp.toPx()))
    drawPath(outlinePath, brush = Brush.verticalGradient(listOf(innerGlow, Color.Transparent)), style = Stroke(width = 2.5f.dp.toPx()))
    drawPath(outlinePath, brush = Brush.verticalGradient(listOf(refraction, Color.Transparent)), style = Stroke(width = 5.5f.dp.toPx()))
  }
}

/** Wet diagonal highlight on the upper-left of a glass surface. */
fun Modifier.lensHighlight(
  dark: Boolean = false,
  strength: Float = 1f
): Modifier = drawWithContent {
  drawContent()
  val radius = size.minDimension * 0.9f
  drawCircle(
    brush = Brush.radialGradient(
      colors = listOf(
        Color.White.copy(alpha = (if (dark) .07f else .30f) * strength),
        Color.Transparent
      ),
      center = Offset(size.width * 0.18f, size.height * 0.10f),
      radius = radius
    ),
    center = Offset(size.width * 0.18f, size.height * 0.10f),
    radius = radius
  )
}

/** Press feedback: spring scale settle used across the glass system. */
@Composable
fun Modifier.pressGlow(pressed: Boolean, reducedMotion: Boolean): Modifier {
  val scale by animateFloatAsState(
    targetValue = if (pressed && !reducedMotion) 0.965f else 1f,
    animationSpec = spring(dampingRatio = 0.55f, stiffness = 520f),
    label = "press_glow_scale"
  )
  return this.graphicsLayer { scaleX = scale; scaleY = scale }
}
