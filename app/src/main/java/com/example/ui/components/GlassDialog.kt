package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    Color(0xFF08131E).copy(alpha = 0.70f)
  } else {
    Color(0xFFEAF4FF).copy(alpha = 0.56f)
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Box(
      Modifier.fillMaxWidth().padding(horizontal = 18.dp),
      contentAlignment = Alignment.Center
    ) {
      GlassCard(
        modifier = Modifier.fillMaxWidth().widthIn(max = 440.dp),
        shape = RoundedCornerShape(30.dp),
        backgroundColor = denseGlass,
        elevation = 18.dp
      ) {
        Column(
          Modifier
            .padding(horizontal = 15.dp, vertical = 13.dp)
            .heightIn(max = 640.dp)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              title,
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.weight(1f)
            )
            GlassIconButton(
              icon = Icons.Default.Close,
              contentDescription = "Close",
              onClick = onDismiss,
              size = 42.dp,
              backgroundColor = if (config.isDark) Color.White.copy(alpha = .07f) else Color.White.copy(alpha = .52f)
            )
          }
          content()
        }
      }
    }
  }
}
