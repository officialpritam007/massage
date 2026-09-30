package com.example.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.AppearanceSettings
import com.example.ui.components.*
import com.example.ui.viewmodel.LiquidChatViewModel

@Composable
fun AppearanceScreen(viewModel: LiquidChatViewModel, onBackClick: () -> Unit) {
  val a by viewModel.appearance.collectAsState()
  val accents = listOf("#176BFF", "#00A7D8", "#7A63FF", "#14A46F")

  LiquidBackground(crystal = true) {
    Column(
      Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
      GlassHeader("Appearance", subtitle = "Liquid Glass", onBackClick = onBackClick)

      GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("Live glass preview", style = MaterialTheme.typography.titleLarge)
          GlassCard(
            Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(a.cornerRadiusDp.dp)
          ) {
            Column(Modifier.padding(18.dp)) {
              Text("Liquid Chat", style = MaterialTheme.typography.titleMedium)
              Text(
                "Readable content, layered depth and fluid material.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }

      GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Dark appearance", Modifier.weight(1f))
            Switch(a.isDarkMode, { viewModel.updateAppearance(a.copy(isDarkMode = it)) })
          }

          Text("Backdrop blur")
          Slider(a.blurAlpha, { viewModel.updateAppearance(a.copy(blurAlpha = it)) }, valueRange = .2f..1f)

          Text("Glass tint")
          Slider(a.glassIntensity, { viewModel.updateAppearance(a.copy(glassIntensity = it)) }, valueRange = .3f..1f)

          Text("Border highlight")
          Slider(a.borderStrength, { viewModel.updateAppearance(a.copy(borderStrength = it)) }, valueRange = .2f..1f)

          Text("Corner radius")
          Slider(a.cornerRadiusDp, { viewModel.updateAppearance(a.copy(cornerRadiusDp = it)) }, valueRange = 16f..36f)

          Text("Accent")
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            accents.forEach { hex ->
              val color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Blue)
              Surface(
                onClick = { viewModel.updateAppearance(a.copy(accentColorHex = hex)) },
                shape = androidx.compose.foundation.shape.CircleShape,
                color = color,
                border = if (a.accentColorHex == hex) androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
                modifier = Modifier.size(38.dp)
              ) {}
            }
          }

          Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Reduce motion", Modifier.weight(1f))
            Switch(a.isReducedMotion, { viewModel.updateAppearance(a.copy(isReducedMotion = it)) })
          }
        }
      }

      GlassButton(
        "Restore balanced glass",
        { viewModel.updateAppearance(AppearanceSettings()) },
        modifier = Modifier.fillMaxWidth()
      )
    }
  }
}
