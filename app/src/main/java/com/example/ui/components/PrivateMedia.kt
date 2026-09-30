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
  contentScale: ContentScale = ContentScale.Crop
) {
  var resolved by remember(model) { mutableStateOf<String?>(null) }
  var failed by remember(model) { mutableStateOf(false) }
  var imageFailed by remember(model) { mutableStateOf(false) }
  var retry by remember(model) { mutableIntStateOf(0) }

  LaunchedEffect(model, retry) {
    resolved = null
    failed = false
    imageFailed = false
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
      .clip(RoundedCornerShape(18.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)),
    contentAlignment = Alignment.Center
  ) {
    when {
      failed || imageFailed -> {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text("Photo unavailable", style = MaterialTheme.typography.labelSmall)
          TextButton(onClick = {
            model?.let { LiquidApi.invalidateMedia(it) }
            retry++
          }) {
            Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text("Retry")
          }
        }
      }
      resolved == null -> {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
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
