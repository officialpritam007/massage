package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassIconButton
import com.example.ui.components.GlassTextField
import com.example.ui.components.LiquidBackground
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.viewmodel.LiquidChatViewModel
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
    var passwordVisible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val glass = LocalLiquidGlass.current

    val emailReady = email.trim().contains("@") && email.trim().contains(".")
    val passwordReady = password.length >= 6
    val profileReady = !register || (name.trim().length >= 2 && username.trim().length >= 3)
    val canSubmit = emailReady && passwordReady && profileReady && !busy

    fun submit() {
        if (!canSubmit) return
        busy = true
        error = null
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
                onFailure = { error = it.message ?: "Unable to continue" }
            )
        }
    }

    LiquidBackground(modifier = modifier, crystal = true) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .background(glass.accentColor.copy(alpha = if (glass.isDark) .18f else .12f), CircleShape)
                    .border(1.dp, glass.accentColor.copy(alpha = .32f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "L",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = glass.accentColor
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Liquid Chat",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "Private conversations, beautifully simple.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(26.dp))

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(30.dp),
                elevation = 5.dp
            ) {
                Column(
                    Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GlassButton(
                            text = "Sign in",
                            onClick = {
                                if (!busy) {
                                    register = false
                                    error = null
                                }
                            },
                            modifier = Modifier.weight(1f),
                            isPrimary = !register,
                            enabled = !busy,
                            shape = RoundedCornerShape(18.dp)
                        )
                        GlassButton(
                            text = "Create",
                            onClick = {
                                if (!busy) {
                                    register = true
                                    error = null
                                }
                            },
                            modifier = Modifier.weight(1f),
                            isPrimary = register,
                            enabled = !busy,
                            shape = RoundedCornerShape(18.dp)
                        )
                    }

                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (register) "Create your account" else "Welcome back",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (register) {
                            "Choose a name and username. Your private chats stay encrypted on-device."
                        } else {
                            "Sign in to continue your conversations."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (register) {
                        GlassTextField(
                            value = name,
                            onValueChange = { name = it.take(60) },
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
                            )
                        )
                        GlassTextField(
                            value = username,
                            onValueChange = {
                                username = it
                                    .lowercase()
                                    .filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '.' }
                                    .take(32)
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
                            )
                        )
                    }

                    GlassTextField(
                        value = email,
                        onValueChange = { email = it.trim().take(160) },
                        placeholder = "Email",
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
                        )
                    )

                    GlassTextField(
                        value = password,
                        onValueChange = { password = it.take(128) },
                        placeholder = "Password",
                        leadingIcon = {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            GlassIconButton(
                                icon = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                onClick = { passwordVisible = !passwordVisible },
                                size = 32.dp,
                                backgroundColor = Color.Transparent
                            )
                        },
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { submit() })
                    )

                    error?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    GlassButton(
                        text = if (register) "Create account" else "Sign in",
                        onClick = ::submit,
                        modifier = Modifier.fillMaxWidth(),
                        isLoading = busy,
                        enabled = canSubmit,
                        shape = RoundedCornerShape(20.dp)
                    )

                    if (!register) {
                        TextButton(
                            onClick = {
                                if (emailReady && !busy) {
                                    viewModel.repository.resetPassword(email.trim())
                                }
                            },
                            enabled = emailReady && !busy,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Forgot password?")
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "Firebase identity • Encrypted chat media • Cloudinary delivery",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
