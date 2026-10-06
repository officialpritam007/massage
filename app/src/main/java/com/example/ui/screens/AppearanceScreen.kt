package com.example.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.ui.components.GlassHeader
import com.example.ui.components.LiquidBackground
import com.example.ui.components.liquidRoundedShape
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.viewmodel.LiquidChatViewModel
import kotlin.math.abs
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics

private data class AppearancePreset(
  val label: String,
  val value: Float
)

private val blurPresets = listOf(
  AppearancePreset("Normal", 0.20f),
  AppearancePreset("Balanced", 0.35f),
  AppearancePreset("Extra", 0.55f)
)

private val tintPresets = listOf(
  AppearancePreset("Normal", 0.55f),
  AppearancePreset("Balanced", 0.75f),
  AppearancePreset("Extra", 0.92f)
)

private val borderPresets = listOf(
  AppearancePreset("Normal", 0.45f),
  AppearancePreset("Balanced", 0.70f),
  AppearancePreset("Extra", 0.95f)
)

private val cornerPresets = listOf(
  AppearancePreset("Normal", 18f),
  AppearancePreset("Balanced", 30f),
  AppearancePreset("Extra", 48f)
)

@Composable
fun AppearanceScreen(viewModel: LiquidChatViewModel, onBackClick: () -> Unit) {
  val appearance by viewModel.appearance.collectAsStateWithLifecycle()
  val defaultAccent = if (appearance.isDarkMode) "#78C7FF" else "#0068D9"
  val accents = listOf(defaultAccent, "#00A38D", "#007AFF", "#AF52DE", "#D98320")

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
      GlassHeader("Appearance", subtitle = "Make Liquid Chat feel like you", onBackClick = onBackClick)
      GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("LIVE PREVIEW", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Surface(
            shape = liquidRoundedShape(18f),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.align(Alignment.Start)
          ) {
            Text("A little more clarity.", Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onSurface)
          }
          Surface(
            shape = liquidRoundedShape(18f),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.End)
          ) {
            Text("A little more you.", Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onPrimary)
          }
        }
      }
      AppearanceToggleCard(
        title = "Dark appearance",
        checked = appearance.isDarkMode,
        onCheckedChange = { viewModel.updateAppearance(appearance.copy(isDarkMode = it)) }
      )

      AppearancePresetCard(
        title = "Backdrop blur",
        subtitle = "How strongly the background is diffused",
        current = appearance.blurAlpha,
        presets = blurPresets,
        onSelected = { viewModel.updateAppearance(appearance.copy(blurAlpha = it)) }
      )

      AppearancePresetCard(
        title = "Glass tint",
        subtitle = "How dense the translucent glass surface feels",
        current = appearance.glassIntensity,
        presets = tintPresets,
        onSelected = { viewModel.updateAppearance(appearance.copy(glassIntensity = it)) }
      )

      AppearancePresetCard(
        title = "Border highlight",
        subtitle = "Strength of the specular glass rim",
        current = appearance.borderStrength,
        presets = borderPresets,
        onSelected = { viewModel.updateAppearance(appearance.copy(borderStrength = it)) }
      )

      AppearancePresetCard(
        title = "Corner radius",
        subtitle = "Applies across shared glass cards, fields and buttons",
        current = appearance.cornerRadiusDp,
        presets = cornerPresets,
        onSelected = { viewModel.updateAppearance(appearance.copy(cornerRadiusDp = it)) }
      )

      AppearanceAccentCard(
        accents = accents,
        selected = if (appearance.accentColorHex.isBlank() ||
          appearance.accentColorHex.uppercase() in setOf("#00E39C", "#78C7FF", "#0068D9")) defaultAccent
          else appearance.accentColorHex,
        onSelect = { viewModel.updateAppearance(appearance.copy(accentColorHex = if (it == defaultAccent) "" else it)) }
      )

      AppearanceToggleCard(
        title = "Reduce motion",
        checked = appearance.isReducedMotion,
        onCheckedChange = { viewModel.updateAppearance(appearance.copy(isReducedMotion = it)) }
      )

      AppearanceToggleCard(
        title = "Reduce transparency",
        checked = appearance.isReducedTransparency,
        onCheckedChange = { viewModel.updateAppearance(appearance.copy(isReducedTransparency = it)) }
      )

      GlassButton(
        "Restore balanced glass",
        {
          viewModel.updateAppearance(
            AppearanceSettings(
              isDarkMode = appearance.isDarkMode,
              glassIntensity = 0.75f,
              blurAlpha = 0.35f,
              cornerRadiusDp = 30f,
              borderStrength = 0.70f,
              accentColorHex = "",
              isReducedMotion = appearance.isReducedMotion,
              isReducedTransparency = appearance.isReducedTransparency
            )
          )
        },
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
    shape = liquidRoundedShape(28f),
    backgroundColor = if (glass.isDark) {
      Color(0xFF0D1F27).copy(alpha = .40f)
    } else {
      Color.White.copy(alpha = .52f)
    },
    borderColor = if (glass.isDark) {
      Color.White.copy(alpha = .16f)
    } else {
      Color.White.copy(alpha = .72f)
    },
    elevation = 0.dp,
    enableBackdrop = false
  ) {
    Column(
      Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(11.dp),
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
private fun AppearancePresetCard(
  title: String,
  subtitle: String,
  current: Float,
  presets: List<AppearancePreset>,
  onSelected: (Float) -> Unit
) {
  val glass = LocalLiquidGlass.current
  val selected = presets.minByOrNull { abs(current - it.value) }?.label ?: "Balanced"

  AppearanceGlassCard {
    Text(
      title,
      style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
    )
    Text(
      subtitle,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      presets.forEach { preset ->
        val active = selected == preset.label
        Surface(
          onClick = { onSelected(preset.value) },
          modifier = Modifier.weight(1f).heightIn(min = 48.dp),
          shape = liquidRoundedShape(14f),
          color = if (active) {
            glass.accentColor.copy(alpha = if (glass.isDark) .25f else .18f)
          } else if (glass.isDark) {
            Color.White.copy(alpha = .06f)
          } else {
            Color.White.copy(alpha = .54f)
          },
          border = BorderStroke(
            1.dp,
            if (active) glass.accentColor.copy(alpha = .75f)
            else MaterialTheme.colorScheme.onSurface.copy(alpha = .10f)
          )
        ) {
          Text(
            preset.label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 11.dp),
            color = if (active) glass.accentColor else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.Medium),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
          )
        }
      }
    }
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
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      accents.forEach { hex ->
        val active = selected.equals(hex, ignoreCase = true)
        val label = when (hex) {
          "#78C7FF", "#0068D9" -> "Ocean"
          "#00A38D" -> "Teal"
          "#007AFF" -> "Blue"
          "#AF52DE" -> "Violet"
          else -> "Amber"
        }
        val color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Blue)
        Surface(
          onClick = { onSelect(hex) },
          shape = CircleShape,
          color = color,
          border = BorderStroke(
            if (selected.equals(hex, ignoreCase = true)) 3.dp else 1.dp,
            if (selected.equals(hex, ignoreCase = true)) MaterialTheme.colorScheme.onSurface
            else Color.White.copy(alpha = .35f)
          ),
          modifier = Modifier.size(48.dp).semantics {
            contentDescription = "$label accent"
            this.selected = active
          }
        ) {}
      }
    }
  }
}
