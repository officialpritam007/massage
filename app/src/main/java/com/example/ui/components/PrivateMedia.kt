package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.network.LiquidApi

@Composable
fun PrivateImage(
  model: String?,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  contentScale: ContentScale = ContentScale.Crop,
  fallbackText: String? = null,
  preview: Boolean = false
) {
  var resolved by remember(model) { mutableStateOf<Any?>(LiquidApi.peekCachedPrivateMedia(model)) }
  var failed by remember(model) { mutableStateOf(false) }
  var imageFailed by remember(model) { mutableStateOf(false) }
  var retry by remember(model) { mutableIntStateOf(0) }

  LaunchedEffect(model, retry) {
    failed = false
    imageFailed = false
    if (model.isNullOrBlank()) {
      resolved = null
      failed = true
    } else if (resolved == null || retry > 0) {
      runCatching { LiquidApi.cachedPrivateMedia(model, forceRefresh = retry > 0) }
        .onSuccess { resolved = it; imageFailed = false; failed = false }
        .onFailure { failed = true }
    }
  }

  Box(
    modifier = modifier
      .then(if (preview) Modifier.height(if (failed || imageFailed) 92.dp else if (resolved == null) 88.dp else 190.dp) else Modifier)
      .clip(RoundedCornerShape(18.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)),
    contentAlignment = Alignment.Center
  ) {
    when {
      failed || imageFailed -> {
        if (fallbackText != null) {
          Text(fallbackText, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
        } else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(10.dp)) {
          Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
          Text("Photo unavailable", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelSmall)
          if (LiquidApi.isSupportedMedia(model)) TextButton(onClick = { retry++ }) {
            Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text("Retry")
          }
        }
      }
      resolved == null -> {
        if (fallbackText != null) Text(fallbackText, color = MaterialTheme.colorScheme.onSurface)
        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
          CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
          Text("Loading photo…", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
      }
      else -> {
        AsyncImage(
          model = resolved,
          contentDescription = contentDescription,
          modifier = Modifier.fillMaxSize(),
          contentScale = contentScale,
          onError = { imageFailed = true }
        )
      }
    }
  }
}
