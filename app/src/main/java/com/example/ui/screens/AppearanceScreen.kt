package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppearanceSettings
import com.example.ui.components.*
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.viewmodel.LiquidChatViewModel

@Composable
fun AppearanceScreen(viewModel: LiquidChatViewModel, onBackClick: () -> Unit) {
  val a by viewModel.appearance.collectAsState()
  val glass = LocalLiquidGlass.current
  val accents = listOf("#176BFF", "#00A7D8", "#7A63FF", "#14A46F", "#FF5D73")

  fun applyPreset(name: String) {
    val next = when (name) {
      "Soft" -> a.copy(glassIntensity = .58f, blurAlpha = .46f, borderStrength = .48f, cornerRadiusDp = 28f)
      "Bold" -> a.copy(glassIntensity = .92f, blurAlpha = .82f, borderStrength = .82f, cornerRadiusDp = 34f)
      else -> AppearanceSettings(
        isDarkMode = a.isDarkMode,
        accentColorHex = a.accentColorHex,
        isReducedMotion = a.isReducedMotion
      )
    }
    viewModel.updateAppearance(next)
  }

  LiquidBackground(crystal = true) {
    Column(
      Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
        .verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      GlassHeader("Appearance", subtitle = "Modern Liquid Glass", onBackClick = onBackClick)

      GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp), elevation = 3.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("Live preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
          Text(
            "Balanced depth, readable surfaces and lightweight motion.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Column(
            Modifier.fillMaxWidth().background(
              glass.accentColor.copy(alpha = if (glass.isDark) .07f else .055f),
              RoundedCornerShape(24.dp)
            ).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Box(
              Modifier.fillMaxWidth(.72f).background(
                if (glass.isDark) Color.White.copy(alpha = .09f) else Color.White.copy(alpha = .72f),
                RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)
              ).padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
              Text("Everything feels lighter.", style = MaterialTheme.typography.bodyMedium)
            }
            Box(
              Modifier.fillMaxWidth(.68f).align(Alignment.End).background(
                glass.accentColor.copy(alpha = if (glass.isDark) .54f else .78f),
                RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp)
              ).padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
              Text("And stays smooth.", style = MaterialTheme.typography.bodyMedium, color = Color.White)
            }
          }
        }
      }

      GlassCard(Modifier.fillMaxWidth(), elevation = 1.dp, enableBlur = false) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("Quick style", style = MaterialTheme.typography.titleMedium)
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Soft", "Balanced", "Bold").forEach { preset ->
              GlassButton(
                text = preset,
                onClick = { applyPreset(preset) },
                modifier = Modifier.weight(1f),
                isPrimary = preset == "Balanced",
                shape = RoundedCornerShape(18.dp)
              )
            }
          }
        }
      }

      GlassCard(Modifier.fillMaxWidth(), elevation = 1.dp, enableBlur = false) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text("Dark appearance", style = MaterialTheme.typography.bodyLarge)
              Text(
                "Deeper contrast with the same glass hierarchy",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Switch(a.isDarkMode, { viewModel.updateAppearance(a.copy(isDarkMode = it)) })
          }

          AppearanceSlider("Backdrop blur", a.blurAlpha, .2f..1f) {
            viewModel.updateAppearance(a.copy(blurAlpha = it))
          }
          AppearanceSlider("Glass tint", a.glassIntensity, .3f..1f) {
            viewModel.updateAppearance(a.copy(glassIntensity = it))
          }
          AppearanceSlider("Border highlight", a.borderStrength, .2f..1f) {
            viewModel.updateAppearance(a.copy(borderStrength = it))
          }

          Text("Corner radius  ${a.cornerRadiusDp.toInt()} dp", style = MaterialTheme.typography.labelLarge)
          Slider(
            value = a.cornerRadiusDp,
            onValueChange = { viewModel.updateAppearance(a.copy(cornerRadiusDp = it)) },
            valueRange = 18f..36f
          )

          Text("Accent", style = MaterialTheme.typography.labelLarge)
          Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            accents.forEach { hex ->
              val color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Blue)
              Surface(
                onClick = { viewModel.updateAppearance(a.copy(accentColorHex = hex)) },
                shape = CircleShape,
                color = color,
                border = if (a.accentColorHex == hex) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface)
                  else BorderStroke(1.dp, Color.White.copy(alpha = .45f)),
                modifier = Modifier.size(40.dp)
              ) {}
            }
          }

          Spacer(Modifier.size(2.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text("Reduce motion", style = MaterialTheme.typography.bodyLarge)
              Text(
                "Disables spring-heavy transitions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Switch(a.isReducedMotion, { viewModel.updateAppearance(a.copy(isReducedMotion = it)) })
          }
        }
      }

      GlassButton(
        "Restore balanced glass",
        { viewModel.updateAppearance(AppearanceSettings()) },
        modifier = Modifier.fillMaxWidth(),
        isPrimary = false,
        shape = RoundedCornerShape(22.dp)
      )
    }
  }
}

@Composable
private fun AppearanceSlider(
  title: String,
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  onValueChange: (Float) -> Unit
) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Row(Modifier.fillMaxWidth()) {
      Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
      Text(
        "${(value * 100).toInt()}%",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    Slider(value = value, onValueChange = onValueChange, valueRange = range)
  }
}
