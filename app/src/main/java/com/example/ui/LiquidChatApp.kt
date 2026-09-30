package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import com.example.ui.components.GlassDialog
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.DisposableEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.model.MessageType
import com.example.ui.navigation.Screen
import com.example.ui.screens.AppearanceScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CameraScreen
import com.example.ui.screens.ChatsHomeScreen
import com.example.ui.screens.ContactProfileScreen
import com.example.ui.screens.ConversationScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
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
    } catch (e: Exception) {
      Color(0xFF00D2FF)
    },
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
      if (!notificationConversation.isNullOrBlank() && chatViewModel.isUserLoggedIn()) { navController.navigate(Screen.Conversation.createRoute(notificationConversation)); onNotificationHandled() }
    }
    val error by chatViewModel.error.collectAsState()
    error?.let { message -> GlassDialog("Liquid Chat", { chatViewModel.repository.clearError() }) { Text(message); if(chatViewModel.repository.hasUploadRetry()) TextButton(onClick = { chatViewModel.repository.retryUpload(); chatViewModel.repository.clearError() }) { Text("Retry upload") } } }
    NavHost(
      navController = navController,
      startDestination = startDestination,
      enterTransition = { if (appearance.isReducedMotion) fadeIn(tween(0)) else fadeIn(tween(180)) + slideInHorizontally(spring(dampingRatio = 0.8f, stiffness = 420f)) { it / 5 } },
      exitTransition = { fadeOut(tween(if (appearance.isReducedMotion) 0 else 130)) },
      popEnterTransition = { fadeIn(tween(if (appearance.isReducedMotion) 0 else 180)) },
      popExitTransition = { if (appearance.isReducedMotion) fadeOut(tween(0)) else fadeOut(tween(150)) + slideOutHorizontally(tween(220)) { it / 4 } }
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
          onNavigateToConversation = { convId ->
            navController.navigate(Screen.Conversation.createRoute(convId))
          },
          onNavigateToSettings = {
            navController.navigate(Screen.Settings.route)
          },
          onNavigateToSearch = {
            navController.navigate(Screen.Search.route)
          },
          onNavigateToAppearance = {
            navController.navigate(Screen.Appearance.route)
          },
          onNavigateToProfile = { userId ->
            navController.navigate(Screen.ContactProfile.createRoute(userId))
          },
          initialTab = homeTab,
          onHomeTabSelected = { homeTab = it }
        )
      }

      composable(
        route = Screen.Conversation.route,
        arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
      ) { backStackEntry ->
        val convId = backStackEntry.arguments?.getString("conversationId") ?: "conv_elena"
        ConversationScreen(
          conversationId = convId,
          viewModel = chatViewModel,
          onBackClick = { navController.popBackStack() },
          onNavigateToProfile = { userId ->
            navController.navigate(Screen.ContactProfile.createRoute(userId))
          },
          onNavigateToCamera = {
            navController.navigate(Screen.Camera.createRoute(convId))
          }
        )
      }

      composable(
        route = Screen.Camera.route,
        arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
      ) { backStackEntry ->
        val convId = backStackEntry.arguments?.getString("conversationId") ?: "conv_elena"
        CameraScreen(
          conversationId = convId,
          onPhotoCaptured = { uri, caption ->
            chatViewModel.uploadChatMedia(convId, uri, MessageType.IMAGE) { result ->
              result.onSuccess { url -> chatViewModel.sendMessage(convId, caption.ifBlank { "Photo" }, MessageType.IMAGE, url) }
            }
            navController.popBackStack()
          },
          onClose = { navController.popBackStack() }
        )
      }

      composable(Screen.Search.route) {
        SearchScreen(
          viewModel = chatViewModel,
          onNavigateToConversation = { convId ->
            navController.navigate(Screen.Conversation.createRoute(convId))
          },
          onNavigateToProfile = { userId ->
            navController.navigate(Screen.ContactProfile.createRoute(userId))
          },
          onBackClick = { navController.popBackStack() }
        )
      }

      composable(
        route = Screen.ContactProfile.route,
        arguments = listOf(navArgument("userId") { type = NavType.StringType })
      ) { backStackEntry ->
        val uid = backStackEntry.arguments?.getString("userId") ?: ""
        ContactProfileScreen(
          userId = uid,
          viewModel = chatViewModel,
          onBackClick = { navController.popBackStack() },
          onNavigateToConversation = { convId ->
            navController.navigate(Screen.Conversation.createRoute(convId))
          },
        )
      }

      composable(Screen.Appearance.route) {
        AppearanceScreen(
          viewModel = chatViewModel,
          onBackClick = { navController.popBackStack() }
        )
      }

      composable(Screen.Settings.route) {
        SettingsScreen(
          viewModel = chatViewModel,
          onBackClick = { navController.popBackStack() },
          onNavigateToAppearance = {
            navController.navigate(Screen.Appearance.route)
          },
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
  }
}
