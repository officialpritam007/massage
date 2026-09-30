package com.example.ui.components

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.data.network.LiquidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PrivateVideoThumbnail(
  mediaUrl: String,
  modifier: Modifier = Modifier
) {
  var frame by remember(mediaUrl) { mutableStateOf<Bitmap?>(null) }
  var error by remember(mediaUrl) { mutableStateOf(false) }
  var retry by remember(mediaUrl) { mutableIntStateOf(0) }

  LaunchedEffect(mediaUrl, retry) {
    error = false
    frame?.recycle()
    frame = null
    runCatching {
      withContext(Dispatchers.IO) {
        val resolved = LiquidApi.resolve(mediaUrl, forceRefresh = retry > 0)
        val retriever = MediaMetadataRetriever()
        try {
          retriever.setDataSource(resolved, emptyMap())
          retriever.getFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.frameAtTime
            ?: error("Unable to create video preview")
        } finally {
          retriever.release()
        }
      }
    }.onSuccess { frame = it }
      .onFailure { error = true }
  }

  DisposableEffect(mediaUrl) {
    onDispose { frame?.recycle() }
  }

  Box(
    modifier
      .clip(RoundedCornerShape(16.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .34f)),
    contentAlignment = Alignment.Center
  ) {
    when {
      error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Video preview unavailable", style = MaterialTheme.typography.labelSmall)
        TextButton(onClick = {
          LiquidApi.invalidateMedia(mediaUrl)
          retry++
        }) {
          Icon(Icons.Default.Refresh, null, Modifier.size(15.dp))
          Spacer(Modifier.width(4.dp))
          Text("Retry")
        }
      }
      frame == null -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
      else -> {
        Image(
          frame!!.asImageBitmap(),
          contentDescription = "Video preview",
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop
        )
        Surface(
          shape = androidx.compose.foundation.shape.CircleShape,
          color = MaterialTheme.colorScheme.surface.copy(alpha = .72f)
        ) {
          Icon(Icons.Default.PlayArrow, "Play video", Modifier.padding(9.dp).size(28.dp))
        }
      }
    }
  }
}
