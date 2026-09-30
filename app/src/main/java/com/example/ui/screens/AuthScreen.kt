package com.example.ui.screens
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.components.*
import com.example.ui.viewmodel.LiquidChatViewModel
import kotlinx.coroutines.launch
@Composable
fun AuthScreen(onAuthenticated:()->Unit,modifier:Modifier=Modifier,viewModel:LiquidChatViewModel){
 var register by remember{mutableStateOf(false)};var email by remember{mutableStateOf("")};var password by remember{mutableStateOf("")};var name by remember{mutableStateOf("")};var username by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)};val scope=rememberCoroutineScope()
 LiquidBackground(modifier){Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.Center){
  Text("Liquid Chat",style=MaterialTheme.typography.headlineLarge);Text("A little closer.",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(36.dp))
  GlassCard{Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
   Text(if(register)"Create account"else "Welcome back",style=MaterialTheme.typography.headlineSmall)
   if(register){GlassTextField(name,{name=it},placeholder="Your name");GlassTextField(username,{username=it},placeholder="Username")}
   GlassTextField(email,{email=it},placeholder="Email");GlassTextField(password,{password=it},placeholder="Password",visualTransformation=PasswordVisualTransformation())
   error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
   GlassButton(if(register)"Create account"else "Sign in",onClick={busy=true;error=null;scope.launch{val r=if(register)viewModel.registerWithEmail(email,password,name,username,"")else viewModel.signInWithEmail(email,password);busy=false;r.fold({onAuthenticated()},{error=it.message})}},isLoading=busy,modifier=Modifier.fillMaxWidth())
   TextButton(onClick={register=!register},enabled=!busy){Text(if(register)"Already have an account? Sign in" else "Create a new account")}
   if(!register)TextButton(onClick={viewModel.repository.resetPassword(email)},enabled=email.isNotBlank()){Text("Forgot password?")}
  }}
 }}
}
