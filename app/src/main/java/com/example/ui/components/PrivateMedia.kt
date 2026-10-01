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
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import java.io.File
import kotlinx.coroutines.CancellationException
import com.example.data.network.LiquidApi

@Composable
fun PrivateImage(
  model: String?,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  contentScale: ContentScale = ContentScale.Crop
) {
  val context = LocalContext.current
  val versions by LiquidApi.mediaVersions.collectAsState()
  val version = versions[model] ?: 0
  var resolved by remember(model, version) { mutableStateOf<File?>(model?.let { LiquidApi.peekCachedMedia(it) }) }
  var failed by remember(model, version) { mutableStateOf(false) }
  var imageFailed by remember(model, version) { mutableStateOf(false) }
  var retry by remember(model) { mutableIntStateOf(0) }
  LaunchedEffect(model, retry, version) {
    failed = false
    imageFailed = false
    if (model.isNullOrBlank()) failed = true else {
      try { resolved = LiquidApi.cachedPrivateMedia(model, forceRefresh = retry > 0) }
      catch (e: CancellationException) { throw e }
      catch (_: Exception) { failed = true }
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
          model = ImageRequest.Builder(context).data(resolved)
            .memoryCacheKey(model?.let(LiquidApi::mediaCacheKey))
            .diskCacheKey(model?.let(LiquidApi::mediaCacheKey))
            .crossfade(false).build(),
          contentDescription = contentDescription,
          modifier = Modifier.fillMaxSize(),
          contentScale = contentScale,
          onError = { imageFailed = true }
        )
      }
    }
  }
}
