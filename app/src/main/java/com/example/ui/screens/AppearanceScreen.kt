package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppearanceSettings
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.LiquidBackground
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.viewmodel.LiquidChatViewModel
import kotlin.math.roundToInt

@Composable
fun AppearanceScreen(viewModel: LiquidChatViewModel, onBackClick: () -> Unit) {
  val appearance by viewModel.appearance.collectAsState()
  val accents = listOf("#00E39C", "#32ADE6", "#007AFF", "#AF52DE", "#34C759")

  BackHandler(onBack = onBackClick)

  LiquidBackground(crystal = true) {
    Column(
      Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 18.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      AppearanceToggleCard(
        title = "Dark appearance",
        checked = appearance.isDarkMode,
        onCheckedChange = { viewModel.updateAppearance(appearance.copy(isDarkMode = it)) }
      )

      AppearanceSliderCard(
        title = "Backdrop blur",
        value = appearance.blurAlpha.coerceIn(0f, 1f) * 200f,
        valueRange = 0f..200f,
        valueLabel = { "${it.roundToInt()} dp" },
        onValueChange = { viewModel.updateAppearance(appearance.copy(blurAlpha = (it / 200f).coerceIn(0f, 1f))) }
      )

      AppearanceSliderCard(
        title = "Glass tint",
        value = appearance.glassIntensity.coerceIn(0f, 1f) * 100f,
        valueRange = 0f..100f,
        valueLabel = { "${it.roundToInt()}%" },
        onValueChange = { viewModel.updateAppearance(appearance.copy(glassIntensity = (it / 100f).coerceIn(0f, 1f))) }
      )

      AppearanceSliderCard(
        title = "Border highlight",
        value = appearance.borderStrength.coerceIn(0f, 1f) * 100f,
        valueRange = 0f..100f,
        valueLabel = { "${it.roundToInt()}%" },
        onValueChange = { viewModel.updateAppearance(appearance.copy(borderStrength = (it / 100f).coerceIn(0f, 1f))) }
      )

      AppearanceSliderCard(
        title = "Corner radius",
        value = appearance.cornerRadiusDp.coerceIn(0f, 200f),
        valueRange = 0f..200f,
        valueLabel = { "${it.roundToInt()} px" },
        onValueChange = { viewModel.updateAppearance(appearance.copy(cornerRadiusDp = it.coerceIn(0f, 200f))) }
      )

      AppearanceAccentCard(
        accents = accents,
        selected = appearance.accentColorHex,
        onSelect = { viewModel.updateAppearance(appearance.copy(accentColorHex = it)) }
      )

      AppearanceToggleCard(
        title = "Reduce motion",
        checked = appearance.isReducedMotion,
        onCheckedChange = { viewModel.updateAppearance(appearance.copy(isReducedMotion = it)) }
      )

      GlassButton(
        "Restore balanced glass",
        { viewModel.updateAppearance(AppearanceSettings()) },
        modifier = Modifier.fillMaxWidth(),
        isPrimary = false
      )
    }
  }
}

@Composable
private fun AppearanceGlassCard(content: @Composable ColumnScope.() -> Unit) {
  val glass = LocalLiquidGlass.current
  GlassCard(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(28.dp),
    backgroundColor = if (glass.isDark) Color(0xFF0D1F27).copy(alpha = .40f) else Color.White.copy(alpha = .52f),
    borderColor = if (glass.isDark) Color.White.copy(alpha = .16f) else Color.White.copy(alpha = .72f),
    elevation = 5.dp
  ) {
    Column(
      Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
      content = content
    )
  }
}

@Composable
private fun AppearanceToggleCard(
  title: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit
) {
  val glass = LocalLiquidGlass.current
  AppearanceGlassCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        title,
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
      )
      Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
          checkedThumbColor = Color.White,
          checkedTrackColor = glass.accentColor
        )
      )
    }
  }
}

@Composable
private fun AppearanceSliderCard(
  title: String,
  value: Float,
  valueRange: ClosedFloatingPointRange<Float>,
  valueLabel: (Float) -> String,
  onValueChange: (Float) -> Unit
) {
  val glass = LocalLiquidGlass.current
  AppearanceGlassCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        title,
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
      )
      Surface(
        shape = RoundedCornerShape(999.dp),
        color = glass.accentColor.copy(alpha = .13f),
        border = BorderStroke(1.dp, glass.accentColor.copy(alpha = .24f))
      ) {
        Text(
          valueLabel(value),
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          color = glass.accentColor,
          style = MaterialTheme.typography.labelMedium
        )
      }
    }
    Slider(
      value = value.coerceIn(valueRange.start, valueRange.endInclusive),
      onValueChange = onValueChange,
      valueRange = valueRange,
      colors = SliderDefaults.colors(
        thumbColor = glass.accentColor,
        activeTrackColor = glass.accentColor,
        inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f)
      )
    )
  }
}

@Composable
private fun AppearanceAccentCard(
  accents: List<String>,
  selected: String,
  onSelect: (String) -> Unit
) {
  AppearanceGlassCard {
    Text(
      "Accent",
      style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      accents.forEach { hex ->
        val color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Blue)
        Surface(
          onClick = { onSelect(hex) },
          shape = CircleShape,
          color = color,
          border = BorderStroke(
            if (selected.equals(hex, ignoreCase = true)) 3.dp else 1.dp,
            if (selected.equals(hex, ignoreCase = true)) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = .35f)
          ),
          modifier = Modifier.size(42.dp)
        ) {}
      }
    }
  }
}
