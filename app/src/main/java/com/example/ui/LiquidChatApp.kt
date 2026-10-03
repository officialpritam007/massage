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
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.model.MessageType
import com.example.ui.components.GlassDialog
import com.example.ui.components.GlassCard
import com.example.ui.components.NetworkStatusBanner
import com.example.ui.navigation.Screen
import com.example.ui.screens.*
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

  val referenceAccent = appearance.accentColorHex.isBlank()
  val referenceDark = appearance.isDarkMode
  val view = androidx.compose.ui.platform.LocalView.current
  SideEffect {
    val window = (view.context as? android.app.Activity)?.window
    if (window != null) androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
      isAppearanceLightStatusBars = !referenceDark
      isAppearanceLightNavigationBars = !referenceDark
    }
  }
  val glassConfig = LiquidGlassConfig(
    glassIntensity = appearance.glassIntensity,
    blurAlpha = appearance.blurAlpha,
    cornerRadiusDp = appearance.cornerRadiusDp,
    borderStrength = appearance.borderStrength,
    accentColor = if (referenceAccent) {
      Color(0xFF00E39C)
    } else try {
      Color(android.graphics.Color.parseColor(appearance.accentColorHex))
    } catch (_: Exception) {
      Color(0xFF00E39C)
    },
    isReducedMotion = appearance.isReducedMotion,
    isDark = referenceDark
  )

  LiquidChatTheme(
    darkTheme = referenceDark,
    glassConfig = glassConfig
  ) {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(currentEntry?.destination?.route) {
      val route = currentEntry?.destination?.route
      if (route != Screen.Conversation.route && route != Screen.ContactProfile.route) {
        chatViewModel.repository.stopOpenConversationObservers()
      }
    }
    fun navigateOnce(route: String) {
      navController.navigate(route) { launchSingleTop = true }
    }
    val startDestination = if (chatViewModel.isUserLoggedIn()) Screen.Chats.route else Screen.Auth.route
    val deletionPending by chatViewModel.deletionPending.collectAsState()
    val deletionComplete by chatViewModel.deletionComplete.collectAsState()
    LaunchedEffect(deletionPending, deletionComplete) {
      if (deletionComplete) navController.navigate(Screen.Auth.route) {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
      } else if (deletionPending) navigateOnce(Screen.Settings.route)
    }

    LaunchedEffect(notificationConversation) {
      if (!notificationConversation.isNullOrBlank() && chatViewModel.isUserLoggedIn() && !deletionPending) {
        navigateOnce(Screen.Conversation.createRoute(notificationConversation))
        onNotificationHandled()
      }
    }

    val error by chatViewModel.error.collectAsState()
    val syncWarning by chatViewModel.repository.syncWarning.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val blockingPrivacyError = error?.let { message ->
      message.startsWith("Secure logout", ignoreCase = true) ||
        message.startsWith("Secure reinstall", ignoreCase = true)
    } == true
    val optionalBackendQuotaError = error?.contains(
      "Resource limit for the current billing cycle",
      ignoreCase = true
    ) == true

    LaunchedEffect(error, optionalBackendQuotaError, blockingPrivacyError) {
      val message = error ?: return@LaunchedEffect
      if (optionalBackendQuotaError && !blockingPrivacyError) {
        Toast.makeText(
          context,
          "Media service is temporarily unavailable. Text chat remains active.",
          Toast.LENGTH_LONG
        ).show()
        chatViewModel.repository.clearError()
      }
    }

    error?.takeIf { !optionalBackendQuotaError || blockingPrivacyError }?.let { message ->
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
        // Glass screens are translucent. Crossfades exposed the previous
        // screen's text through the new page during every navigation.
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
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
          onNavigateToConversation = { navigateOnce(Screen.Conversation.createRoute(it)) },
          onNavigateToSettings = { navigateOnce(Screen.Settings.route) },
          onNavigateToContacts = { navigateOnce(Screen.Contacts.route) },
          onNavigateToSearch = { navigateOnce(Screen.Search.route) },
          onNavigateToAppearance = { navigateOnce(Screen.Appearance.route) },
          onNavigateToProfile = { navigateOnce(Screen.ContactProfile.createRoute(it)) },
          initialTab = homeTab,
          onHomeTabSelected = { homeTab = it }
        )
      }

      composable(Screen.Contacts.route) {
        ContactsScreen(
          viewModel = chatViewModel,
          onNavigateToConversation = { navigateOnce(Screen.Conversation.createRoute(it)) },
          onNavigateToChats = {
            navController.popBackStack(Screen.Chats.route, false)
          },
          onNavigateToSettings = {
            navController.navigate(Screen.Settings.route) { launchSingleTop = true }
          },
          onNavigateToSearch = { navigateOnce(Screen.Search.route) },
          onNavigateToProfile = { navigateOnce(Screen.ContactProfile.createRoute(it)) }
        )
      }

      composable(
        route = Screen.Conversation.route,
        arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
      ) { backStackEntry ->
        val convId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
        ConversationScreenV2(
          conversationId = convId,
          viewModel = chatViewModel,
          onBackClick = { navController.popBackStack() },
          onNavigateToProfile = { navigateOnce(Screen.ContactProfile.createRoute(it)) },
          onNavigateToCamera = { navigateOnce(Screen.Camera.createRoute(convId)) }
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
                chatViewModel.sendMessage(
                  convId,
                  caption.ifBlank { "Photo" },
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
          onNavigateToConversation = { navigateOnce(Screen.Conversation.createRoute(it)) },
          onNavigateToProfile = { navigateOnce(Screen.ContactProfile.createRoute(it)) },
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
          onNavigateToConversation = { navigateOnce(Screen.Conversation.createRoute(it)) }
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
          onNavigateToAppearance = { navigateOnce(Screen.Appearance.route) },
          onNavigateToDiagnostics = { navigateOnce(Screen.Diagnostics.route) },
          onNavigateToProfile = { navigateOnce(Screen.ContactProfile.createRoute(it)) },
          onNavigateToContacts = { navigateOnce(Screen.Contacts.route) },
          onNavigateToHomeTab = { tab ->
            homeTab = tab
            navController.popBackStack(Screen.Chats.route, false)
          },
          onLogout = {
            navController.navigate(Screen.Auth.route) {
              popUpTo(0) { inclusive = true }
              launchSingleTop = true
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

      AnimatedVisibility(
        visible = !syncWarning.isNullOrBlank(),
        modifier = Modifier
          .align(Alignment.TopCenter)
          .statusBarsPadding()
          .padding(top = 52.dp, start = 16.dp, end = 16.dp),
        enter = fadeIn(tween(if (appearance.isReducedMotion) 0 else 150)),
        exit = fadeOut(tween(if (appearance.isReducedMotion) 0 else 120))
      ) {
        GlassCard(
          shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
          backgroundColor = MaterialTheme.colorScheme.error.copy(alpha = .08f),
          elevation = 1.dp
        ) {
          Text(
            text = syncWarning ?: "",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}
