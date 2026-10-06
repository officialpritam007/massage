package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassTextField
import com.example.ui.components.LiquidBackground
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.viewmodel.LiquidChatViewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

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
  viewModel: LiquidChatViewModel
) {
  var register by rememberSaveable { mutableStateOf(false) }
  var email by rememberSaveable { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var name by rememberSaveable { mutableStateOf("") }
  var username by rememberSaveable { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  var backendError by remember { mutableStateOf<String?>(null) }
  var resetPending by remember { mutableStateOf(false) }
  var resetMessage by remember { mutableStateOf<String?>(null) }
  var resetFailed by remember { mutableStateOf(false) }

  var emailTouched by remember { mutableStateOf(false) }
  var passwordTouched by remember { mutableStateOf(false) }
  var nameTouched by remember { mutableStateOf(false) }
  var usernameTouched by remember { mutableStateOf(false) }
  var submitAttempted by remember { mutableStateOf(false) }

  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val credentialManager = remember(context) { CredentialManager.create(context) }
  val focusManager = LocalFocusManager.current
  val glass = LocalLiquidGlass.current
  val screenActive = remember { AtomicBoolean(true) }
  DisposableEffect(Unit) {
    screenActive.set(true)
    onDispose { screenActive.set(false) }
  }

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
    if (!formValid || busy || resetPending) return

    focusManager.clearFocus()
    busy = true
    scope.launch {
      try {
        val result = if (register) {
          viewModel.registerWithEmail(email.trim(), password, name.trim(), username.trim().lowercase(), "")
        } else {
          viewModel.signInWithEmail(email.trim(), password)
        }
        result.fold(
          onSuccess = { onAuthenticated() },
          onFailure = { backendError = it.message ?: "Unable to continue. Please try again." }
        )
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (failure: Exception) {
        backendError = failure.message ?: "Unable to continue. Please try again."
      } finally {
        busy = false
      }
    }
  }

  LiquidBackground(modifier = modifier) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.linearGradient(
            colors = listOf(
              Color(0x144F8CFF),
              Color(0x0D39D8C8),
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
          .navigationBarsPadding()
          .imePadding()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Row(Modifier.widthIn(max = 480.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
          GlassCard(Modifier.size(60.dp), shape = RoundedCornerShape(22.dp), elevation = 3.dp) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Icon(Icons.AutoMirrored.Filled.Chat, null, modifier = Modifier.size(30.dp), tint = glass.accentColor)
            }
          }
          Column {
            Text("Liquid Chat", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("A little closer.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }

        Spacer(Modifier.height(30.dp))

        Text(
          text = if (register) "Your people.\nYour space." else "Good conversations\nstart here.",
          style = MaterialTheme.typography.headlineLarge,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
        )
        Text(
          text = if (register) "Create an account and make yourself at home." else "Welcome back. Let's pick up where you left off.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(top = 10.dp)
        )

        Spacer(Modifier.height(26.dp))

        ScreenContentSurface(
          modifier = Modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .animateContentSize(animationSpec = androidx.compose.animation.core.tween(if (glass.isReducedMotion) 0 else 220))
        ) {
          Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                "Enter your email and password to continue."
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AuthVisibility(visible = register) {
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AuthFieldLabel("Name")
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
                  keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                  minHeight = 56.dp,
                  testTag = "auth_name"
                )
                ValidationMessage(nameError)
              }
            }

            AuthVisibility(visible = register) {
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AuthFieldLabel("Username")
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
                  keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                  minHeight = 56.dp,
                  testTag = "auth_username"
                )
                ValidationMessage(usernameError)
              }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              AuthFieldLabel("Email")
              GlassTextField(
                value = email,
                onValueChange = {
                  email = it.trim().take(160)
                  emailTouched = true
                  backendError = null
                  resetMessage = null
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
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                minHeight = 56.dp,
                testTag = "auth_email"
              )
              ValidationMessage(emailError)
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              AuthFieldLabel("Password")
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
                  IconButton(onClick = { passwordVisible = !passwordVisible }, modifier = Modifier.size(48.dp)) {
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
                minHeight = 56.dp,
                verticalPadding = 4.dp,
                testTag = "auth_password"
              )
              ValidationMessage(passwordError)
            }

            AuthVisibility(visible = backendError != null) {
              Text(
                text = backendError.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
              )
            }
            AuthVisibility(visible = resetMessage != null) {
              Text(
                resetMessage.orEmpty(), style = MaterialTheme.typography.bodySmall,
                color = if (resetFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
              )
            }

            if (!register) {
              Box(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                  onClick = {
                    backendError = null
                    resetMessage = null
                    resetPending = true
                    viewModel.resetPassword(email.trim()) { result ->
                      if (screenActive.get()) {
                        resetPending = false
                        resetFailed = result.isFailure
                        resetMessage = if (result.isSuccess) {
                          "If an account uses this email, reset instructions will arrive."
                        } else {
                          "Could not request reset instructions. Please try again."
                        }
                      }
                    }
                  },
                  enabled = currentEmailError == null && !busy && !resetPending,
                  modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                  Text(if (resetPending) "Sending reset email…" else "Forgot password?")
                }
              }
            }

            AuthPrimaryButton(
              text = if (register) "Create account" else "Sign in",
              loadingText = if (register) "Creating account…" else "Signing in…",
              loading = busy,
              enabled = formValid && !busy && !resetPending,
              onClick = { submit() }
            )

            if (!register) {
              OrDivider()

              OutlinedButton(
                onClick = {
                  backendError = null
                  focusManager.clearFocus()
                  busy = true
                  scope.launch {
                    try {
                      val googleIdOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(context.getString(R.string.default_web_client_id))
                        .setAutoSelectEnabled(false)
                        .build()
                      val request = GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build()
                      val credential = credentialManager
                        .getCredential(context = context, request = request)
                        .credential

                      if (
                        credential !is CustomCredential ||
                        credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                      ) {
                        error("Google did not return a supported credential")
                      }

                      val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                      val result = viewModel.signInWithGoogleIdToken(googleCredential.idToken)
                      result.fold(
                        onSuccess = { onAuthenticated() },
                        onFailure = {
                          backendError = it.message ?: "Google sign-in failed. Please try again."
                        }
                      )
                    } catch (_: GetCredentialCancellationException) {
                      // Account picker was dismissed intentionally.
                    } catch (cancelled: CancellationException) {
                      throw cancelled
                    } catch (t: Exception) {
                      backendError = t.message ?: "Google sign-in failed. Please try again."
                    } finally {
                      busy = false
                    }
                  }
                },
                enabled = !busy && !resetPending,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(56.dp),
                shape = RoundedCornerShape(24.dp),
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
                if (!busy && !resetPending) {
                  register = !register
                  submitAttempted = false
                  backendError = null
                  resetMessage = null
                  nameTouched = false
                  usernameTouched = false
                  emailTouched = false
                  passwordTouched = false
                }
              },
              enabled = !busy && !resetPending,
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
          text = "Your next conversation is just a hello away.",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center
        )
      }
    }
  }
}

@Composable
private fun AuthVisibility(visible: Boolean, content: @Composable () -> Unit) {
  val reduced = LocalLiquidGlass.current.isReducedMotion
  AnimatedVisibility(
    visible = visible,
    enter = if (reduced) EnterTransition.None else fadeIn(),
    exit = if (reduced) ExitTransition.None else fadeOut()
  ) { content() }
}

@Composable
private fun AuthFieldLabel(label: String) {
  Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
}

@Composable
private fun ValidationMessage(message: String?) {
  AuthVisibility(visible = message != null) {
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
  val config = LocalLiquidGlass.current

  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier
      .fillMaxWidth()
      .height(56.dp),
    shape = RoundedCornerShape(24.dp),
    colors = ButtonDefaults.buttonColors(
      containerColor = config.accentColor,
      contentColor = MaterialTheme.colorScheme.onPrimary,
      disabledContainerColor = if (loading) config.accentColor else config.accentColor.copy(alpha = .16f),
      disabledContentColor = if (loading) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    )
  ) {
    if (loading) {
      if (config.isReducedMotion) {
        Icon(Icons.Default.HourglassEmpty, null, modifier = Modifier.size(22.dp))
      } else {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
      }
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
