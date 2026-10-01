package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.network.LiquidApi
import com.example.ui.theme.LocalLiquidGlass

/**
 * Private (Appwrite) image with a shimmer placeholder, retry recovery and an optional
 * intrinsic-size callback so callers can size the container to the real aspect ratio
 * instead of guessing (and leaving black gaps in media bubbles).
 */
@Composable
fun PrivateImage(
  model: String?,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  contentScale: ContentScale = ContentScale.Crop,
  cornerRadius: Dp = 18.dp,
  hidden: Boolean = false,
  onIntrinsicSize: ((Float) -> Unit)? = null
) {
  var resolved by remember(model) { mutableStateOf<String?>(null) }
  var failed by remember(model) { mutableStateOf(false) }
  var imageFailed by remember(model) { mutableStateOf(false) }
  var retry by remember(model) { mutableIntStateOf(0) }

  LaunchedEffect(model, retry, hidden) {
    resolved = null
    failed = false
    imageFailed = false
    if (hidden) {
      // Privacy gate: never resolve or download the private file until it is revealed.
      return@LaunchedEffect
    }
    if (model.isNullOrBlank()) {
      failed = true
    } else {
      runCatching { LiquidApi.resolve(model, forceRefresh = retry > 0) }
        .onSuccess { resolved = it }
        .onFailure { failed = true }
    }
  }

  Box(
    modifier = modifier
      .clip(RoundedCornerShape(cornerRadius))
      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)),
    contentAlignment = Alignment.Center
  ) {
    when {
      failed || imageFailed -> {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.padding(8.dp)
        ) {
          Icon(
            Icons.Default.BrokenImage,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(Modifier.height(4.dp))
          Text("Photo unavailable", style = MaterialTheme.typography.labelSmall)
          TextButton(onClick = {
            model?.let { LiquidApi.invalidateMedia(it) }
            retry++
          }) {
            Icon(Icons.Default.Refresh, null, Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text("Retry", style = MaterialTheme.typography.labelSmall)
          }
        }
      }
      hidden && resolved == null -> Box(
        Modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f))
      )
      resolved == null -> MediaShimmer(Modifier.fillMaxSize())
      else -> {
        AsyncImage(
          model = resolved,
          contentDescription = contentDescription,
          modifier = Modifier.fillMaxSize(),
          contentScale = contentScale,
          onSuccess = { state ->
            if (onIntrinsicSize != null) {
              val drawable = state.result.drawable
              val w = drawable.intrinsicWidth
              val h = drawable.intrinsicHeight
              if (w > 0 && h > 0) onIntrinsicSize(w.toFloat() / h.toFloat())
            }
          },
          onError = { imageFailed = true }
        )
      }
    }
  }
}

/** Soft sweeping highlight used while private media downloads. */
@Composable
fun MediaShimmer(modifier: Modifier = Modifier) {
  val config = LocalLiquidGlass.current
  val base = if (config.isDark) Color.White.copy(alpha = .045f) else Color.White.copy(alpha = .30f)
  val sheen = if (config.isDark) Color.White.copy(alpha = .10f) else Color.White.copy(alpha = .68f)
  if (config.isReducedMotion) {
    Box(modifier.background(base))
    return
  }
  val transition = rememberInfiniteTransition(label = "media_shimmer")
  val sweep by transition.animateFloat(
    initialValue = -1f,
    targetValue = 2f,
    animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
    label = "media_shimmer_sweep"
  )
  Box(
    modifier.background(
      Brush.horizontalGradient(
        colors = listOf(base, sheen, base),
        startX = sweep * 460f,
        endX = sweep * 460f + 420f
      )
    )
  )
}
