package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.components.GlassButton
import com.example.ui.theme.LocalLiquidGlass

/** Legible content surfaces deliberately avoid backdrop blur in scrolling lists. */
@Composable
internal fun ScreenContentSurface(
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  content: @Composable () -> Unit
) {
  val config = LocalLiquidGlass.current
  val surface = MaterialTheme.colorScheme.surface.copy(
    alpha = if (config.isReducedTransparency) 1f else if (config.isDark) .88f else .94f
  )
  val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .28f))
  if (onClick == null) {
    Surface(modifier, shape = RoundedCornerShape(24.dp), color = surface, border = border, content = content)
  } else {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(24.dp), color = surface, border = border, content = content)
  }
}

@Composable
internal fun ScreenSectionLabel(title: String, detail: String? = null, modifier: Modifier = Modifier) {
  Row(
    modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (detail != null) Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
internal fun ScreenEmptyState(
  icon: ImageVector,
  title: String,
  description: String,
  modifier: Modifier = Modifier,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null
) {
  ScreenContentSurface(modifier.fillMaxWidth()) {
    Column(
      Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp), tint = LocalLiquidGlass.current.accentColor)
      Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
      Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
      if (actionLabel != null && onAction != null) {
        GlassButton(actionLabel, onAction, modifier = Modifier.padding(top = 8.dp))
      }
    }
  }
}
