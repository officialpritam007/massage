package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.DeletionCoordinator
import com.example.ui.theme.LocalLiquidGlass
import kotlinx.coroutines.launch
import kotlin.math.*

/** Dust fragments are pixels from the actual item, not a generic particle overlay. */
@Composable
fun DustDelete(key: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  val states by DeletionCoordinator.states.collectAsState()
  val state = states[key]
  val reduced = LocalLiquidGlass.current.isReducedMotion
  val layer = rememberGraphicsLayer()
  val progress = remember(key) { Animatable(0f) }
  var snapshot by remember(key) { mutableStateOf<ImageBitmap?>(null) }
  val scope = rememberCoroutineScope()
  LaunchedEffect(state) {
    if (state == DeletionCoordinator.Stage.DISSOLVING) {
      snapshot = if (reduced) null else runCatching { layer.toImageBitmap() }.getOrNull()
      progress.animateTo(1f, tween(if (reduced) 160 else 600))
    } else {
      progress.animateTo(0f, tween(if (reduced) 0 else 180))
      snapshot = null
    }
  }
  Column(modifier) {
    Box(Modifier
      .layout { measurable, constraints ->
        val item = measurable.measure(constraints)
        val collapse = ((progress.value - .72f) / .28f).coerceIn(0f, 1f)
        layout(item.width, (item.height * (1f - collapse)).roundToInt()) { item.placeRelative(0, 0) }
      }
      .drawWithContent {
        layer.record { this@drawWithContent.drawContent() }
        val bitmap = snapshot
        val t = (progress.value / .78f).coerceIn(0f, 1f)
        if (t <= 0f) drawLayer(layer)
        else if (bitmap != null && !reduced) {
          // Bounded tile count keeps long messages and large photos inexpensive.
          val cell = max(6, sqrt(bitmap.width.toFloat() * bitmap.height / 420f).roundToInt())
          var y = 0
          while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
              val seed = ((x * 31 + y * 17 + key.hashCode()) and 1023) / 1023f
              val local = ((t - seed * .20f) / .8f).coerceIn(0f, 1f)
              val w = min(cell, bitmap.width - x); val h = min(cell, bitmap.height - y)
              val dx = (18f + seed * 66f) * density * local
              val dy = (-38f + seed * 66f) * density * local - sin(local * PI).toFloat() * 18f * density
              drawImage(bitmap, IntOffset(x, y), IntSize(w, h),
                IntOffset((x + dx).roundToInt(), (y + dy).roundToInt()),
                IntSize(max(1, (w * (1f - local * .8f)).roundToInt()), max(1, (h * (1f - local * .8f)).roundToInt())),
                alpha = (1f - local).coerceIn(0f, 1f))
              x += cell
            }
            y += cell
          }
        } else {
          layer.alpha = 1f - t
          drawLayer(layer)
          layer.alpha = 1f
        }
      }) { content() }
    if (state == DeletionCoordinator.Stage.FAILED) {
      Row {
        Text("Couldn't delete", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        TextButton(onClick = { scope.launch { runCatching { DeletionCoordinator.retry(key) } } }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("Retry") }
      }
    }
  }
}
