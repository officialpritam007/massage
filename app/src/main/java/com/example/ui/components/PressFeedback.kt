package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

fun Modifier.liquidPressFeedback(
  pressedScale: Float = .95f,
  enabled: Boolean = true
): Modifier = composed {
  val scale = remember { Animatable(1f) }
  this
    .graphicsLayer {
      scaleX = scale.value
      scaleY = scale.value
    }
    .pointerInput(enabled, pressedScale) {
      if (!enabled) {
        scale.snapTo(1f)
        return@pointerInput
      }
      awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        scale.animateTo(
          pressedScale.coerceIn(.85f, 1f),
          spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh
          )
        )
        waitForUpOrCancellation()
        scale.animateTo(
          1f,
          spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
          )
        )
      }
    }
}
