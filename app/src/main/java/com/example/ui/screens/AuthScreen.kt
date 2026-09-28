package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassTextField
import com.example.ui.components.LiquidBackground
import com.example.ui.theme.CoralEndCall
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.GlassBorderStroke
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.viewmodel.LiquidChatViewModel
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
  onAuthenticated: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: LiquidChatViewModel = viewModel()
) {
  val coroutineScope = rememberCoroutineScope()

  val glassConfig = LocalLiquidGlass.current
  var isRegisterMode by remember { mutableStateOf(false) }
  var isPhoneOtpMode by remember { mutableStateOf(false) }

  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var fullName by remember { mutableStateOf("") }
  var username by remember { mutableStateOf("") }
  var phoneNumber by remember { mutableStateOf("+1 555 382 9471") }
  var otpCode by remember { mutableStateOf("") }
  var isOtpSent by remember { mutableStateOf(false) }

  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  fun handleSignIn() {
    val cleanEmail = email.trim()
    val cleanPass = password.trim()
    if (cleanEmail.isBlank() || cleanPass.isBlank()) {
      errorMessage = "Please enter both email and password."
      return
    }
    isLoading = true
    errorMessage = null
    coroutineScope.launch {
      val res = viewModel.signInWithEmail(cleanEmail, cleanPass)
      isLoading = false
      res.fold(
        onSuccess = { onAuthenticated() },
        onFailure = { err ->
          errorMessage = err.localizedMessage ?: "Failed to sign in. Please verify your credentials."
        }
      )
    }
  }

  fun handleRegister() {
    val cleanEmail = email.trim()
    val cleanPass = password.trim()
    if (cleanEmail.isBlank() || cleanPass.isBlank()) {
      errorMessage = "Please enter an email and password."
      return
    }
    if (cleanPass.length < 6) {
      errorMessage = "Password must be at least 6 characters long."
      return
    }
    isLoading = true
    errorMessage = null
    coroutineScope.launch {
      val res = viewModel.registerWithEmail(
        email = cleanEmail,
        pass = cleanPass,
        fullName = fullName.trim(),
        username = username.trim(),
        phoneNumber = phoneNumber.trim()
      )
      isLoading = false
      res.fold(
        onSuccess = { onAuthenticated() },
        onFailure = { err ->
          errorMessage = err.localizedMessage ?: "Registration failed. Please try again."
        }
      )
    }
  }

  fun handleDemoSignIn() {
    isLoading = true
    errorMessage = null
    coroutineScope.launch {
      val demoEmail = "demo@liquidchat.io"
      val demoPass = "LiquidPass123!"
      val signInRes = viewModel.signInWithEmail(demoEmail, demoPass)
      if (signInRes.isSuccess) {
        isLoading = false
        onAuthenticated()
      } else {
        // Try registering the demo account
        val regRes = viewModel.registerWithEmail(
          email = demoEmail,
          pass = demoPass,
          fullName = "Liquid Explorer",
          username = "liquid.explorer",
          phoneNumber = "+1 (555) 019-2831"
        )
        isLoading = false
        regRes.fold(
          onSuccess = { onAuthenticated() },
          onFailure = { err ->
            errorMessage = err.localizedMessage ?: "Demo access failed."
          }
        )
      }
    }
  }

  LiquidBackground(modifier = modifier) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 40.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      // Liquid Logo Icon
      Box(
        modifier = Modifier
          .size(80.dp)
          .clip(CircleShape)
          .border(
            width = 2.dp,
            brush = Brush.linearGradient(listOf(CyanNeon, ElectricBlue)),
            shape = CircleShape
          )
          .background(
            Brush.radialGradient(
              listOf(Color(0xFF00D2FF).copy(alpha = 0.22f), if (glassConfig.isDark) Color.Black else Color.White.copy(alpha = 0.82f))
            )
          ),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.WaterDrop,
          contentDescription = "Liquid Chat Logo",
          tint = CyanAccent,
          modifier = Modifier.size(42.dp)
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = "Liquid Chat",
        style = MaterialTheme.typography.headlineLarge.copy(
          fontWeight = FontWeight.ExtraBold,
          color = TextPrimary,
          fontSize = 32.sp
        )
      )

      Text(
        text = "Pure fluid communication, encased in glass",
        style = MaterialTheme.typography.bodyMedium.copy(
          color = TextSecondary,
          fontSize = 14.sp
        ),
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
      )

      // Main Glass Auth Card
      GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = 12.dp
      ) {
        Column(
          modifier = Modifier.padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          // Tab Switcher
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .background(if (glassConfig.isDark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.035f))
              .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
          ) {
            Box(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(if (!isRegisterMode && !isPhoneOtpMode) CyanAccent else Color.Transparent)
                .clickable {
                  isRegisterMode = false
                  isPhoneOtpMode = false
                  errorMessage = null
                }
                .padding(vertical = 10.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "Sign In",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (!isRegisterMode && !isPhoneOtpMode) Color.Black else TextSecondary
              )
            }

            Box(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isRegisterMode) CyanAccent else Color.Transparent)
                .clickable {
                  isRegisterMode = true
                  isPhoneOtpMode = false
                  errorMessage = null
                }
                .padding(vertical = 10.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "Register",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (isRegisterMode) Color.Black else TextSecondary
              )
            }

            Box(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isPhoneOtpMode) CyanAccent else Color.Transparent)
                .clickable {
                  isPhoneOtpMode = true
                  isRegisterMode = false
                  errorMessage = null
                }
                .padding(vertical = 10.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "Phone OTP",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (isPhoneOtpMode) Color.Black else TextSecondary
              )
            }
          }

          Spacer(modifier = Modifier.height(20.dp))

          // Error Message Display
          AnimatedVisibility(visible = errorMessage != null) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CoralEndCall.copy(alpha = 0.15f))
                .border(1.dp, CoralEndCall.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(12.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = errorMessage ?: "",
                color = CoralEndCall,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
              )
            }
          }

          if (isPhoneOtpMode) {
            // Phone OTP Flow
            GlassTextField(
              value = phoneNumber,
              onValueChange = { phoneNumber = it },
              placeholder = "Enter mobile number",
              leadingIcon = {
                Icon(Icons.Default.Phone, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
              },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
              testTag = "phone_input"
            )

            Spacer(modifier = Modifier.height(14.dp))

            AnimatedVisibility(visible = isOtpSent) {
              Column {
                GlassTextField(
                  value = otpCode,
                  onValueChange = { if (it.length <= 6) otpCode = it },
                  placeholder = "Enter 6-digit OTP (e.g. 749201)",
                  leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
                  },
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  testTag = "otp_input"
                )
                Spacer(modifier = Modifier.height(14.dp))
              }
            }

            GlassButton(
              text = if (!isOtpSent) "Send One-Time Passcode" else "Verify & Enter Liquid Chat",
              onClick = {
                if (!isOtpSent) {
                  isOtpSent = true
                } else {
                  handleDemoSignIn()
                }
              },
              modifier = Modifier.fillMaxWidth(),
              testTag = "otp_submit_button"
            )
          } else {
            // Email / Password Flow (Real Firebase Authentication)
            if (isRegisterMode) {
              GlassTextField(
                value = fullName,
                onValueChange = { fullName = it },
                placeholder = "Full Name (e.g. Elena Rostova)",
                leadingIcon = {
                  Icon(Icons.Default.Person, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
                },
                testTag = "register_name_input"
              )
              Spacer(modifier = Modifier.height(14.dp))

              GlassTextField(
                value = username,
                onValueChange = { username = it },
                placeholder = "Username (e.g. elena.design)",
                leadingIcon = {
                  Icon(Icons.Default.AlternateEmail, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
                },
                testTag = "register_username_input"
              )
              Spacer(modifier = Modifier.height(14.dp))
            }

            GlassTextField(
              value = email,
              onValueChange = { email = it },
              placeholder = "Email (e.g. user@example.com)",
              leadingIcon = {
                Icon(Icons.Default.Email, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
              },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
              testTag = "login_email_input"
            )

            Spacer(modifier = Modifier.height(14.dp))

            GlassTextField(
              value = password,
              onValueChange = { password = it },
              placeholder = "Password (min 6 characters)",
              visualTransformation = PasswordVisualTransformation(),
              leadingIcon = {
                Icon(Icons.Default.Lock, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
              },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
              testTag = "login_password_input"
            )

            if (isRegisterMode) {
              Spacer(modifier = Modifier.height(14.dp))
              GlassTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                placeholder = "Mobile Number (optional)",
                leadingIcon = {
                  Icon(Icons.Default.Phone, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                testTag = "register_phone_input"
              )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (isLoading) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(48.dp),
                contentAlignment = Alignment.Center
              ) {
                CircularProgressIndicator(
                  color = CyanAccent,
                  modifier = Modifier.size(28.dp),
                  strokeWidth = 3.dp
                )
              }
            } else {
              GlassButton(
                text = if (isRegisterMode) "Create Liquid Account" else "Sign In to Liquid Chat",
                onClick = {
                  if (isRegisterMode) handleRegister() else handleSignIn()
                },
                modifier = Modifier.fillMaxWidth(),
                testTag = "auth_submit_button"
              )
            }
          }

          Spacer(modifier = Modifier.height(18.dp))

          // Divider
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(GlassBorderStroke)
            )
            Text(
              text = "  OR  ",
              style = TextStyle(color = TextMuted, fontSize = 12.sp)
            )
            Box(
              modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(GlassBorderStroke)
            )
          }

          Spacer(modifier = Modifier.height(18.dp))

          // Quick Demo / Instant Access with Real Firebase
          GlassButton(
            text = "Instant Demo Account",
            onClick = { handleDemoSignIn() },
            isPrimary = false,
            modifier = Modifier.fillMaxWidth(),
            testTag = "demo_sso_button"
          )
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      Text(
        text = "Real-time Cloud Firestore person-to-person messaging encased in Liquid Glass.",
        style = TextStyle(
          color = TextMuted,
          fontSize = 12.sp,
          textAlign = TextAlign.Center
        )
      )
    }
  }
}
