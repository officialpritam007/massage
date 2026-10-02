package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.R
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassTextField
import com.example.ui.components.LiquidBackground
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.viewmodel.LiquidChatViewModel
import kotlinx.coroutines.launch

private val AuthEmailRegex =
  Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", RegexOption.IGNORE_CASE)

private fun validateEmail(value: String): String? = when {
  value.isBlank() -> "Email is required"
  !AuthEmailRegex.matches(value.trim()) -> "Enter a valid email address"
  else -> null
}

private fun validatePassword(value: String): String? = when {
  value.isBlank() -> "Password is required"
  value.length < 6 -> "Password must be at least 6 characters"
  else -> null
}

private fun validateName(value: String): String? = when {
  value.isBlank() -> "Name is required"
  value.trim().length < 2 -> "Name must be at least 2 characters"
  else -> null
}

private fun validateUsername(value: String): String? = when {
  value.isBlank() -> "Username is required"
  !Regex("^[a-z0-9_.]{3,32}$").matches(value.trim().lowercase()) ->
    "Use 3–32 letters, numbers, dots or underscores"
  else -> null
}

@Composable
fun AuthScreen(
  onAuthenticated: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: LiquidChatViewModel,
  onGoogleSignIn: (() -> Unit)? = null
) {
  var register by remember { mutableStateOf(false) }
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var name by remember { mutableStateOf("") }
  var username by remember { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  var backendError by remember { mutableStateOf<String?>(null) }

  var emailTouched by remember { mutableStateOf(false) }
  var passwordTouched by remember { mutableStateOf(false) }
  var nameTouched by remember { mutableStateOf(false) }
  var usernameTouched by remember { mutableStateOf(false) }
  var submitAttempted by remember { mutableStateOf(false) }

  val scope = rememberCoroutineScope()
  val focusManager = LocalFocusManager.current
  val glass = LocalLiquidGlass.current

  val currentEmailError = validateEmail(email)
  val currentPasswordError = validatePassword(password)
  val currentNameError = if (register) validateName(name) else null
  val currentUsernameError = if (register) validateUsername(username) else null

  val emailError = currentEmailError.takeIf { emailTouched || submitAttempted }
  val passwordError = currentPasswordError.takeIf { passwordTouched || submitAttempted }
  val nameError = currentNameError.takeIf { register && (nameTouched || submitAttempted) }
  val usernameError = currentUsernameError.takeIf { register && (usernameTouched || submitAttempted) }

  val formValid =
    currentEmailError == null &&
      currentPasswordError == null &&
      (!register || (currentNameError == null && currentUsernameError == null))

  fun submit() {
    submitAttempted = true
    backendError = null
    if (!formValid || busy) return

    focusManager.clearFocus()
    busy = true
    scope.launch {
      val result = if (register) {
        viewModel.registerWithEmail(
          email.trim(),
          password,
          name.trim(),
          username.trim().lowercase(),
          ""
        )
      } else {
        viewModel.signInWithEmail(email.trim(), password)
      }

      busy = false
      result.fold(
        onSuccess = { onAuthenticated() },
        onFailure = { backendError = it.message ?: "Unable to continue. Please try again." }
      )
    }
  }

  LiquidBackground(modifier = modifier) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.linearGradient(
            colors = listOf(
              Color(0x1A4F8CFF),
              Color(0x1239D8C8),
              Color(0x18A66BFF),
              Color.Transparent
            )
          )
        )
    ) {
      Box(
        modifier = Modifier
          .size(260.dp)
          .align(Alignment.TopEnd)
          .background(
            Brush.radialGradient(
              listOf(
                glass.accentColor.copy(alpha = 0.18f),
                Color.Transparent
              )
            ),
            CircleShape
          )
      )

      Column(
        modifier = Modifier
          .fillMaxSize()
          .statusBarsPadding()
          .imePadding()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 22.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Box(
          modifier = Modifier
            .size(72.dp)
            .background(glass.accentColor.copy(alpha = 0.13f), CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Chat,
            contentDescription = null,
            modifier = Modifier.size(34.dp),
            tint = glass.accentColor
          )
        }

        Spacer(Modifier.height(14.dp))

        Text(
          text = "Liquid Chat",
          style = MaterialTheme.typography.headlineLarge,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = if (register) "Create your account and start chatting." else "Welcome back. Sign in to continue.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(26.dp))

        GlassCard(
          modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
          shape = RoundedCornerShape(30.dp),
          elevation = 7.dp
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Text(
              text = if (register) "Create account" else "Sign in",
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.SemiBold
            )
            Text(
              text = if (register) {
                "Use a valid email and choose a unique username."
              } else {
                "Use the same Firebase account you already use in Liquid Chat."
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AnimatedVisibility(visible = register) {
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GlassTextField(
                  value = name,
                  onValueChange = {
                    name = it.take(60)
                    nameTouched = true
                    backendError = null
                  },
                  placeholder = "Your name",
                  leadingIcon = {
                    Icon(
                      Icons.Default.Person,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  },
                  keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                  ),
                  testTag = "auth_name"
                )
                ValidationMessage(nameError)
              }
            }

            AnimatedVisibility(visible = register) {
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GlassTextField(
                  value = username,
                  onValueChange = {
                    username = it
                      .lowercase()
                      .filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '.' }
                      .take(32)
                    usernameTouched = true
                    backendError = null
                  },
                  placeholder = "Username",
                  leadingIcon = {
                    Icon(
                      Icons.Default.AlternateEmail,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  },
                  keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                  ),
                  testTag = "auth_username"
                )
                ValidationMessage(usernameError)
              }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              GlassTextField(
                value = email,
                onValueChange = {
                  email = it.trim().take(160)
                  emailTouched = true
                  backendError = null
                },
                placeholder = "Email address",
                leadingIcon = {
                  Icon(
                    Icons.Default.Email,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                },
                keyboardOptions = KeyboardOptions(
                  keyboardType = KeyboardType.Email,
                  imeAction = ImeAction.Next
                ),
                testTag = "auth_email"
              )
              ValidationMessage(emailError)
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              GlassTextField(
                value = password,
                onValueChange = {
                  password = it.take(128)
                  passwordTouched = true
                  backendError = null
                },
                placeholder = "Password",
                leadingIcon = {
                  Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                },
                trailingIcon = {
                  IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                      imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                      contentDescription = if (passwordVisible) "Hide password" else "Show password",
                      tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                },
                visualTransformation =
                  if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                  keyboardType = KeyboardType.Password,
                  imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                testTag = "auth_password"
              )
              ValidationMessage(passwordError)
            }

            AnimatedVisibility(visible = backendError != null) {
              Text(
                text = backendError.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
              )
            }

            if (!register) {
              Box(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                  onClick = { viewModel.repository.resetPassword(email.trim()) },
                  enabled = currentEmailError == null && !busy,
                  modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                  Text("Forgot password?")
                }
              }
            }

            AuthPrimaryButton(
              text = if (register) "Create account" else "Sign in",
              loadingText = if (register) "Creating account…" else "Signing in…",
              loading = busy,
              enabled = formValid && !busy,
              onClick = { submit() }
            )

            if (!register) {
              OrDivider()

              OutlinedButton(
                onClick = {
                  backendError = null
                  if (onGoogleSignIn != null) {
                    onGoogleSignIn()
                  } else {
                    backendError = "Google sign-in is not configured in this build yet."
                  }
                },
                enabled = !busy,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(54.dp),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(
                  1.dp,
                  MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                )
              ) {
                Box(
                  modifier = Modifier
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = "G",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                  )
                }
                Spacer(Modifier.size(10.dp))
                Text(
                  text = "Continue with Google",
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.SemiBold
                )
              }
            }

            TextButton(
              onClick = {
                if (!busy) {
                  register = !register
                  submitAttempted = false
                  backendError = null
                  nameTouched = false
                  usernameTouched = false
                  emailTouched = false
                  passwordTouched = false
                }
              },
              enabled = !busy,
              modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
              Text(
                if (register) {
                  "Already have an account? Sign in"
                } else {
                  "New to Liquid Chat? Create account"
                }
              )
            }
          }
        }

        Spacer(Modifier.height(16.dp))

        Text(
          text = "Secure sign-in powered by your existing Firebase Authentication setup.",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center
        )
      }
    }
  }
}

@Composable
private fun ValidationMessage(message: String?) {
  AnimatedVisibility(visible = message != null) {
    Text(
      text = message.orEmpty(),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.error,
      modifier = Modifier.padding(start = 6.dp)
    )
  }
}

@Composable
private fun AuthPrimaryButton(
  text: String,
  loadingText: String,
  loading: Boolean,
  enabled: Boolean,
  onClick: () -> Unit
) {
  val composition by rememberLottieComposition(
    LottieCompositionSpec.RawRes(R.raw.login_loading)
  )
  val progress by animateLottieCompositionAsState(
    composition = composition,
    isPlaying = loading,
    iterations = LottieConstants.IterateForever
  )

  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier
      .fillMaxWidth()
      .height(54.dp),
    shape = RoundedCornerShape(18.dp),
    colors = ButtonDefaults.buttonColors(
      containerColor = LocalLiquidGlass.current.accentColor,
      contentColor = Color.White,
      disabledContainerColor = LocalLiquidGlass.current.accentColor.copy(alpha = 0.35f),
      disabledContentColor = Color.White.copy(alpha = 0.72f)
    )
  ) {
    if (loading) {
      LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = Modifier.size(26.dp)
      )
      Spacer(Modifier.size(10.dp))
      Text(loadingText, fontWeight = FontWeight.SemiBold)
    } else {
      Text(text, fontWeight = FontWeight.Bold)
    }
  }
}

@Composable
private fun OrDivider() {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    HorizontalDivider(
      modifier = Modifier.weight(1f),
      color = MaterialTheme.colorScheme.outlineVariant
    )
    Text(
      text = "OR",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    HorizontalDivider(
      modifier = Modifier.weight(1f),
      color = MaterialTheme.colorScheme.outlineVariant
    )
  }
}
