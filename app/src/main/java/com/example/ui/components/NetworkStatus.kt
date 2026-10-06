package com.example.ui.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun rememberValidatedNetwork(): Boolean {
  val context = LocalContext.current
  val manager = remember(context) {
    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
  }

  fun connected(): Boolean {
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
      capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
  }

  var online by remember(manager) { mutableStateOf(connected()) }

  DisposableEffect(manager) {
    val callback = object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) { online = connected() }
      override fun onLost(network: Network) { online = connected() }
      override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
        online = connected()
      }
    }
    runCatching { manager.registerDefaultNetworkCallback(callback) }
    online = connected()
    onDispose { runCatching { manager.unregisterNetworkCallback(callback) } }
  }

  return online
}

@Composable
fun NetworkStatusBanner(modifier: Modifier = Modifier, online: Boolean = rememberValidatedNetwork()) {
  val reducedMotion = com.example.ui.theme.LocalLiquidGlass.current.isReducedMotion
  AnimatedVisibility(
    visible = !online,
    modifier = modifier,
    enter = if (reducedMotion) androidx.compose.animation.EnterTransition.None else fadeIn() + slideInVertically { -it / 2 },
    exit = if (reducedMotion) androidx.compose.animation.ExitTransition.None else fadeOut() + slideOutVertically { -it / 2 }
  ) {
    GlassCard {
      Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        Text(
          "Offline · messages will retry automatically",
          style = MaterialTheme.typography.labelMedium
        )
      }
    }
  }
}
