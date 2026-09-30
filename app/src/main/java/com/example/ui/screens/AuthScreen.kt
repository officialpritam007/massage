package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassTextField
import com.example.ui.components.LiquidBackground
import com.example.ui.components.lensHighlight
import com.example.ui.components.rememberLiquidHaptics
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.viewmodel.LiquidChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
  onAuthenticated: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: LiquidChatViewModel
) {
  var register by remember { mutableStateOf(false) }
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var name by remember { mutableStateOf("") }
  var username by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()
  val haptics = rememberLiquidHaptics()
  val config = LocalLiquidGlass.current

  // Staged hero entrance: the glass rises into place like a droplet settling on glass.
  var entered by remember { mutableStateOf(config.isReducedMotion) }
  LaunchedEffect(Unit) { delay(60); entered = true }
  val heroAlpha by animateFloatAsState(
    targetValue = if (entered) 1f else 0f,
    animationSpec = tween(if (config.isReducedMotion) 0 else 500),
    label = "auth_hero_alpha"
  )
  val heroProgress by animateFloatAsState(
    targetValue = if (entered) 1f else 0f,
    animationSpec = spring(dampingRatio = .78f, stiffness = 90f),
    label = "auth_hero_rise"
  )

  LiquidBackground(modifier) {
    Column(
      Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .imePadding()
        .verticalScroll(rememberScrollState())
        .padding(28.dp),
      verticalArrangement = Arrangement.Center
    ) {
      Column(
        Modifier
          .alpha(heroAlpha)
          .graphicsLayer {
            translationY = (1f - heroProgress) * 90f
            scaleX = 0.94f + 0.06f * heroProgress
            scaleY = 0.94f + 0.06f * heroProgress
          },
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Liquid droplet mark — a glass orb with a specular top edge.
        Box(
          Modifier
            .size(76.dp)
            .graphicsLayer { scaleX = heroProgress.coerceIn(0f, 1f); scaleY = heroProgress.coerceIn(0f, 1f) }
            .background(
              androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(config.accentColor.copy(alpha = .85f), config.accentColor.copy(alpha = .45f))
              ),
              CircleShape
            )
            .lensHighlight(dark = true, strength = 1.6f),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            Icons.AutoMirrored.Filled.Chat,
            contentDescription = null,
            tint = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.size(34.dp)
          )
        }
        Spacer(Modifier.height(18.dp))
        Text(
          "Liquid Chat",
          style = MaterialTheme.typography.headlineLarge,
          fontWeight = FontWeight.Bold
        )
        Text(
          "A little closer.",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Spacer(Modifier.height(36.dp))

      GlassCard {
        Column(
          Modifier.padding(22.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          Text(
            if (register) "Create account" else "Welcome back",
            style = MaterialTheme.typography.headlineSmall
          )
          if (register) {
            GlassTextField(name, { name = it }, placeholder = "Your name")
            GlassTextField(username, { username = it }, placeholder = "Username")
          }
          GlassTextField(email, { email = it }, placeholder = "Email")
          GlassTextField(
            password, { password = it },
            placeholder = "Password",
            visualTransformation = PasswordVisualTransformation()
          )
          error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
          GlassButton(
            if (register) "Create account" else "Sign in",
            onClick = {
              haptics.tap()
              busy = true; error = null
              scope.launch {
                val r = if (register) viewModel.registerWithEmail(email, password, name, username, "")
                else viewModel.signInWithEmail(email, password)
                busy = false
                r.fold({
                  haptics.confirm()
                  onAuthenticated()
                }, { error = it.message })
              }
            },
            isLoading = busy,
            modifier = Modifier.fillMaxWidth()
          )
          TextButton(onClick = { register = !register }, enabled = !busy) {
            Text(if (register) "Already have an account? Sign in" else "Create a new account")
          }
          if (!register) TextButton(
            onClick = { viewModel.repository.resetPassword(email) },
            enabled = email.isNotBlank()
          ) { Text("Forgot password?") }
        }
      }
    }
  }
}
