package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MotionPhotosOff
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.LiquidBackground
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.viewmodel.LiquidChatViewModel

@Composable
fun AppearanceScreen(
  viewModel: LiquidChatViewModel,
  onBackClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val appearance by viewModel.appearance.collectAsState()
  val glassConfig = LocalLiquidGlass.current


  LiquidBackground(modifier = modifier) {
    Scaffold(
      containerColor = Color.Transparent,
      topBar = {
        GlassCard(
          modifier = Modifier.fillMaxWidth().statusBarsPadding(),
          shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
          elevation = 8.dp
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(
              onClick = onBackClick,
              modifier = Modifier.testTag("appearance_back_button")
            ) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }

            Text(
              text = "Liquid Glass Appearance",
              style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 18.sp
              )
            )
          }
        }
      }
    ) { innerPadding ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // Live Glass Preview Card
        GlassCard(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(24.dp)
        ) {
          Column(modifier = Modifier.padding(20.dp)) {
            Text(
              text = "LIVE GLASS PREVIEW",
              style = MaterialTheme.typography.labelSmall.copy(
                color = CyanAccent,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
              )
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(44.dp)
                  .clip(CircleShape)
                  .background(CyanAccent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Default.WaterDrop, contentDescription = null, tint = CyanAccent)
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = "Liquid Glass Surface",
                  style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
                )
                Text(
                  text = "Refraction: ${(appearance.glassIntensity * 100).toInt()}% • Opacity: ${(appearance.blurAlpha * 100).toInt()}%",
                  style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
              }
            }
          }
        }

        // Theme Mode Toggle
        GlassCard(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(24.dp)
        ) {
          Column(modifier = Modifier.padding(18.dp)) {
            Text(
              text = "THEME MODE",
              style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              ThemeModeOption(
                title = "Dark Glass",
                icon = Icons.Default.DarkMode,
                isSelected = appearance.isDarkMode,
                onClick = { viewModel.updateAppearance(appearance.copy(isDarkMode = true)) },
                modifier = Modifier.weight(1f)
              )

              ThemeModeOption(
                title = "Light Frost",
                icon = Icons.Default.LightMode,
                isSelected = !appearance.isDarkMode,
                onClick = { viewModel.updateAppearance(appearance.copy(isDarkMode = false)) },
                modifier = Modifier.weight(1f)
              )
            }
          }
        }

        // Glass Intensity Slider
        GlassCard(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(24.dp)
        ) {
          Column(modifier = Modifier.padding(18.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Glass Refraction Intensity",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
              )
              Text(
                text = "${(appearance.glassIntensity * 100).toInt()}%",
                color = CyanAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
              )
            }
            Slider(
              value = appearance.glassIntensity,
              onValueChange = { viewModel.updateAppearance(appearance.copy(glassIntensity = it)) },
              valueRange = 0.2f..1f,
              colors = SliderDefaults.colors(
                thumbColor = CyanAccent,
                activeTrackColor = CyanAccent,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
              )
            )
          }
        }

        // Blur / Alpha Slider
        GlassCard(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(24.dp)
        ) {
          Column(modifier = Modifier.padding(18.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Backdrop Frosted Transparency",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
              )
              Text(
                text = "${(appearance.blurAlpha * 100).toInt()}%",
                color = CyanAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
              )
            }
            Slider(
              value = appearance.blurAlpha,
              onValueChange = { viewModel.updateAppearance(appearance.copy(blurAlpha = it)) },
              valueRange = 0.3f..0.95f,
              colors = SliderDefaults.colors(
                thumbColor = CyanAccent,
                activeTrackColor = CyanAccent,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
              )
            )
          }
        }

        // Boxy ↔ Rounded
        GlassCard(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(24.dp)
        ) {
          Column(modifier = Modifier.padding(18.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Boxy ↔ Extra Rounded", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
              Text("${appearance.cornerRadiusDp.toInt()} dp", color = CyanAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Slider(
              value = appearance.cornerRadiusDp,
              onValueChange = { viewModel.updateAppearance(appearance.copy(cornerRadiusDp = it)) },
              valueRange = 0f..64f,
              colors = SliderDefaults.colors(thumbColor = CyanAccent, activeTrackColor = CyanAccent)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
              listOf("Boxy" to 4f, "Balanced" to 20f, "Rounded" to 40f, "Extra Round" to 64f).forEach { (label, radius) ->
                Box(
                  modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (kotlin.math.abs(appearance.cornerRadiusDp - radius) < 7f) CyanAccent.copy(alpha = 0.24f) else Color.White.copy(alpha = if (glassConfig.isDark) 0.06f else 0.45f))
                    .border(1.dp, if (kotlin.math.abs(appearance.cornerRadiusDp - radius) < 7f) CyanAccent else Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                    .clickable { viewModel.updateAppearance(appearance.copy(cornerRadiusDp = radius)) }
                    .padding(vertical = 9.dp, horizontal = 2.dp),
                  contentAlignment = Alignment.Center
                ) { Text(label, color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
              }
            }
            Box(
              modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                .clip(RoundedCornerShape(appearance.cornerRadiusDp.coerceIn(0f, 64f).dp))
                .background(if (glassConfig.isDark) Color(0xFF176BFF).copy(alpha = 0.42f) else Color(0xFF176BFF).copy(alpha = 0.16f))
                .border(1.dp, CyanAccent.copy(alpha = 0.55f), RoundedCornerShape(appearance.cornerRadiusDp.coerceIn(0f, 64f).dp))
                .padding(horizontal = 16.dp, vertical = 15.dp),
              contentAlignment = Alignment.Center
            ) { Text("Extra Rounded Live Preview", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
          }
        }

        // Border Strength
        GlassCard(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(24.dp)
        ) {
          Column(modifier = Modifier.padding(18.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Glass Border Strength", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
              Text("${(appearance.borderStrength * 100).toInt()}%", color = CyanAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Slider(
              value = appearance.borderStrength,
              onValueChange = { viewModel.updateAppearance(appearance.copy(borderStrength = it)) },
              valueRange = 0.1f..1f,
              colors = SliderDefaults.colors(thumbColor = CyanAccent, activeTrackColor = CyanAccent)
            )
          }
        }

        // Reduced Motion Toggle
        GlassCard(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(24.dp)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.MotionPhotosOff, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text("Reduced Motion", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
              Text("Disables mesh orb floating loops", color = TextMuted, fontSize = 12.sp)
            }
            Switch(
              checked = appearance.isReducedMotion,
              onCheckedChange = { viewModel.updateAppearance(appearance.copy(isReducedMotion = it)) },
              colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = CyanAccent)
            )
          }
        }

        GlassButton(
          text = "Reset Glass Defaults",
          onClick = { viewModel.updateAppearance(appearance.copy(glassIntensity = 0.85f, blurAlpha = 0.70f, cornerRadiusDp = 32f, borderStrength = 0.70f, isReducedMotion = false)) },
          modifier = Modifier.fillMaxWidth(),
          isPrimary = false
        )

        Spacer(modifier = Modifier.height(20.dp))
      }
    }
  }
}

@Composable
fun ThemeModeOption(
  title: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val glassConfig = LocalLiquidGlass.current

  Box(
    modifier = modifier
      .clip(RoundedCornerShape(16.dp))
      .background(if (isSelected) CyanAccent else if (glassConfig.isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.72f))
      .clickable(onClick = onClick)
      .padding(vertical = 12.dp, horizontal = 8.dp),
    contentAlignment = Alignment.Center
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(icon, contentDescription = null, tint = if (isSelected) Color.Black else TextSecondary, modifier = Modifier.size(18.dp))
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = title,
        color = if (isSelected) Color.Black else TextPrimary,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp
      )
    }
  }
}
