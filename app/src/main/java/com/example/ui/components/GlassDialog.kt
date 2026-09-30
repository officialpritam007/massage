package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.LocalLiquidGlass

@Composable
fun GlassDialog(
  title: String,
  onDismiss: () -> Unit,
  content: @Composable ColumnScope.() -> Unit
) {
  val config = LocalLiquidGlass.current
  val denseGlass = if (config.isDark) {
    Color(0xFF101820).copy(alpha = 0.92f)
  } else {
    Color(0xFFF8FBFF).copy(alpha = 0.94f)
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Box(
      Modifier.fillMaxWidth().padding(horizontal = 22.dp),
      contentAlignment = Alignment.Center
    ) {
      GlassCard(
        modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
        shape = RoundedCornerShape(30.dp),
        backgroundColor = denseGlass,
        elevation = 16.dp
      ) {
        Column(
          Modifier
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .heightIn(max = 620.dp)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
          }
          content()
        }
      }
    }
  }
}
