package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.model.MessageType
import com.example.ui.components.GlassDialog
import com.example.ui.components.NetworkStatusBanner
import com.example.ui.navigation.Screen
import com.example.ui.screens.*
import com.example.ui.theme.GlassStyle
import com.example.ui.theme.LiquidChatTheme
import com.example.ui.theme.LiquidGlassConfig
import com.example.ui.viewmodel.LiquidChatViewModel

@Composable
fun LiquidChatApp(
  notificationConversation: String? = null,
  onNotificationHandled: () -> Unit = {},
  chatViewModel: LiquidChatViewModel = viewModel()
) {
  val appearance by chatViewModel.appearance.collectAsState()
  var homeTab by rememberSaveable { mutableStateOf("All") }
  val lifecycleOwner = LocalLifecycleOwner.current

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START -> chatViewModel.setPresence(true)
        Lifecycle.Event.ON_STOP -> chatViewModel.setPresence(false)
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val glassConfig = LiquidGlassConfig(
    glassIntensity = appearance.glassIntensity,
    blurAlpha = appearance.blurAlpha,
    cornerRadiusDp = appearance.cornerRadiusDp,
    borderStrength = appearance.borderStrength,
    accentColor = try {
      Color(android.graphics.Color.parseColor(appearance.accentColorHex))
    } catch (_: Exception) {
      Color(0xFF176BFF)
    },
    glassStyle = if (appearance.glassStyle == "Clear") GlassStyle.Clear else GlassStyle.Regular,
    isReducedMotion = appearance.isReducedMotion,
    isDark = appearance.isDarkMode
  )

  LiquidChatTheme(
    darkTheme = appearance.isDarkMode,
    glassConfig = glassConfig
  ) {
    val navController = rememberNavController()
    val startDestination = if (chatViewModel.isUserLoggedIn()) Screen.Chats.route else Screen.Auth.route

    LaunchedEffect(notificationConversation) {
      if (!notificationConversation.isNullOrBlank() && chatViewModel.isUserLoggedIn()) {
        navController.navigate(Screen.Conversation.createRoute(notificationConversation))
        onNotificationHandled()
      }
    }

    val error by chatViewModel.error.collectAsState()
    error?.let { message ->
      GlassDialog("Liquid Chat", { chatViewModel.repository.clearError() }) {
        Text(message)
        if (chatViewModel.repository.hasUploadRetry()) {
          TextButton(onClick = {
            chatViewModel.repository.retryUpload()
            chatViewModel.repository.clearError()
          }) { Text("Retry upload") }
        }
      }
    }

    Box(Modifier.fillMaxSize()) {
      NavHost(
        navController = navController,
      startDestination = startDestination,
      enterTransition = {
        if (appearance.isReducedMotion) fadeIn(tween(0))
        else fadeIn(tween(180)) + slideInHorizontally(
          spring(dampingRatio = 0.8f, stiffness = 420f)
        ) { it / 5 }
      },
      exitTransition = { fadeOut(tween(if (appearance.isReducedMotion) 0 else 130)) },
      popEnterTransition = { fadeIn(tween(if (appearance.isReducedMotion) 0 else 180)) },
      popExitTransition = {
        if (appearance.isReducedMotion) fadeOut(tween(0))
        else fadeOut(tween(150)) + slideOutHorizontally(tween(220)) { it / 4 }
      }
    ) {
      composable(Screen.Auth.route) {
        AuthScreen(
          viewModel = chatViewModel,
          onAuthenticated = {
            navController.navigate(Screen.Chats.route) {
              popUpTo(Screen.Auth.route) { inclusive = true }
            }
          }
        )
      }

      composable(Screen.Chats.route) {
        ChatsHomeScreen(
          viewModel = chatViewModel,
          onNavigateToConversation = { navController.navigate(Screen.Conversation.createRoute(it)) },
          onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
          onNavigateToSearch = { navController.navigate(Screen.Search.route) },
          onNavigateToAppearance = { navController.navigate(Screen.Appearance.route) },
          onNavigateToProfile = { navController.navigate(Screen.ContactProfile.createRoute(it)) },
          initialTab = homeTab,
          onHomeTabSelected = { homeTab = it }
        )
      }

      composable(
        route = Screen.Conversation.route,
        arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
      ) { backStackEntry ->
        val convId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
        ConversationScreen(
          conversationId = convId,
          viewModel = chatViewModel,
          onBackClick = { navController.popBackStack() },
          onNavigateToProfile = { navController.navigate(Screen.ContactProfile.createRoute(it)) },
          onNavigateToCamera = { navController.navigate(Screen.Camera.createRoute(convId)) }
        )
      }

      composable(
        route = Screen.Camera.route,
        arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
      ) { backStackEntry ->
        val convId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
        CameraScreen(
          conversationId = convId,
          onPhotoCaptured = { uri, caption ->
            chatViewModel.uploadChatMedia(convId, uri, MessageType.IMAGE) { result ->
              result.onSuccess { url ->
                // An empty caption is correct here: the bubble shows the photo, never a generic label.
                chatViewModel.sendMessage(
                  convId,
                  caption,
                  MessageType.IMAGE,
                  url
                )
              }
            }
            navController.popBackStack()
          },
          onClose = { navController.popBackStack() }
        )
      }

      composable(Screen.Search.route) {
        SearchScreen(
          viewModel = chatViewModel,
          onNavigateToConversation = { navController.navigate(Screen.Conversation.createRoute(it)) },
          onNavigateToProfile = { navController.navigate(Screen.ContactProfile.createRoute(it)) },
          onBackClick = { navController.popBackStack() }
        )
      }

      composable(
        route = Screen.ContactProfile.route,
        arguments = listOf(navArgument("userId") { type = NavType.StringType })
      ) { backStackEntry ->
        val userId = backStackEntry.arguments?.getString("userId").orEmpty()
        ContactProfileScreen(
          userId = userId,
          viewModel = chatViewModel,
          onBackClick = { navController.popBackStack() },
          onNavigateToConversation = { navController.navigate(Screen.Conversation.createRoute(it)) }
        )
      }

      composable(Screen.Appearance.route) {
        AppearanceScreen(viewModel = chatViewModel, onBackClick = { navController.popBackStack() })
      }

      composable(Screen.Diagnostics.route) {
        DiagnosticsScreen(viewModel = chatViewModel, onBackClick = { navController.popBackStack() })
      }

      composable(Screen.Settings.route) {
        SettingsScreen(
          viewModel = chatViewModel,
          onBackClick = { navController.popBackStack() },
          onNavigateToAppearance = { navController.navigate(Screen.Appearance.route) },
          onNavigateToDiagnostics = { navController.navigate(Screen.Diagnostics.route) },
          onNavigateToHomeTab = { tab ->
            homeTab = tab
            navController.popBackStack(Screen.Chats.route, false)
          },
          onLogout = {
            navController.navigate(Screen.Auth.route) {
              popUpTo(0) { inclusive = true }
            }
          }
        )
      }
      }

      NetworkStatusBanner(
        Modifier
          .align(Alignment.TopCenter)
          .statusBarsPadding()
          .padding(top = 8.dp)
      )
    }
  }
}
