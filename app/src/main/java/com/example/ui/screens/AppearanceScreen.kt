package com.example.ui.screens
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import com.example.data.model.AppearanceSettings
import com.example.ui.components.*
import com.example.ui.viewmodel.LiquidChatViewModel
@Composable fun AppearanceScreen(viewModel:LiquidChatViewModel,onBackClick:()->Unit){
 val a by viewModel.appearance.collectAsState()
 LiquidBackground(crystal=true){Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
  GlassHeader("Appearance",onBackClick=onBackClick)
  GlassCard(Modifier.fillMaxWidth()){Column(Modifier.padding(24.dp)){Text("Liquid Glass",style=MaterialTheme.typography.headlineSmall);Text("Balanced clarity. Expressive motion.",color=MaterialTheme.colorScheme.onSurfaceVariant)}}
  GlassCard(Modifier.fillMaxWidth()){Column(Modifier.padding(20.dp)){
   Row(verticalAlignment=Alignment.CenterVertically){Text("Dark appearance",Modifier.weight(1f));Switch(a.isDarkMode,{viewModel.updateAppearance(a.copy(isDarkMode=it))})}
   Text("Backdrop blur");Slider(a.blurAlpha,{viewModel.updateAppearance(a.copy(blurAlpha=it))},valueRange=.2f..1f)
   Text("Glass tint");Slider(a.glassIntensity,{viewModel.updateAppearance(a.copy(glassIntensity=it))},valueRange=.3f..1f)
   Text("Corner radius");Slider(a.cornerRadiusDp,{viewModel.updateAppearance(a.copy(cornerRadiusDp=it))},valueRange=16f..32f)
   Row(verticalAlignment=Alignment.CenterVertically){Text("Reduce motion",Modifier.weight(1f));Switch(a.isReducedMotion,{viewModel.updateAppearance(a.copy(isReducedMotion=it))})}
  }}
  GlassButton("Restore balanced glass",{viewModel.updateAppearance(AppearanceSettings())},modifier=Modifier.fillMaxWidth())
 }}
}
