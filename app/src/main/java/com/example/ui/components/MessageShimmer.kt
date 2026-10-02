package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalLiquidGlass

/** Fixed footprint while the initial encrypted page is being decoded off the UI thread. */
@Composable
fun MessageShimmer() {
  val reduced = LocalLiquidGlass.current.isReducedMotion
  val transition = rememberInfiniteTransition(label = "message-decryption")
  val alpha by transition.animateFloat(.15f, .35f,
    infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "shimmer-alpha")
  Box(Modifier.width(140.dp).height(36.dp).clip(RoundedCornerShape(18.dp))
    .background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = if (reduced) .2f else alpha),
      MaterialTheme.colorScheme.primary.copy(alpha = .08f), Color.White.copy(alpha = .2f))))
    .semantics { contentDescription = "Decrypting messages" })
}
