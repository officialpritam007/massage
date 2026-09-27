package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.model.MessageType
import com.example.ui.navigation.Screen
import com.example.ui.screens.ActiveCallScreen
import com.example.ui.screens.AppearanceScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BusinessCardScreen
import com.example.ui.screens.CallsScreen
import com.example.ui.screens.CameraScreen
import com.example.ui.screens.ChatsHomeScreen
import com.example.ui.screens.ContactProfileScreen
import com.example.ui.screens.ConversationScreen
import com.example.ui.screens.GroupChatScreen
import com.example.ui.screens.GroupsScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.UpdatesScreen
import com.example.ui.theme.LiquidChatTheme
import com.example.ui.theme.LiquidGlassConfig
import com.example.ui.viewmodel.LiquidChatViewModel

@Composable
fun LiquidChatApp(
  chatViewModel: LiquidChatViewModel = viewModel()
) {
  val appearance by chatViewModel.appearance.collectAsState()
  val activeCall by chatViewModel.activeCall.collectAsState()

  val glassConfig = LiquidGlassConfig(
    glassIntensity = appearance.glassIntensity,
    blurAlpha = appearance.blurAlpha,
    accentColor = try {
      Color(android.graphics.Color.parseColor(appearance.accentColorHex))
    } catch (e: Exception) {
      Color(0xFF00D2FF)
    },
    isReducedMotion = appearance.isReducedMotion
  )

  LiquidChatTheme(
    darkTheme = appearance.isDarkMode,
    glassConfig = glassConfig
  ) {
    val navController = rememberNavController()

    val startDestination = if (chatViewModel.isUserLoggedIn()) Screen.Chats.route else Screen.Auth.route
    NavHost(
      navController = navController,
      startDestination = startDestination
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
          onNavigateToGroups = {
            navController.navigate(Screen.Groups.route)
          },
          onNavigateToUpdates = {
            navController.navigate(Screen.Updates.route)
          },
          onNavigateToCalls = {
            navController.navigate(Screen.Calls.route)
          },
          onNavigateToCard = {
            navController.navigate(Screen.BusinessCard.route)
          },
          onNavigateToSearch = {
            navController.navigate(Screen.Search.route)
          },
          onNavigateToSettings = {
            navController.navigate(Screen.Settings.route)
          },
          onNavigateToAppearance = {
            navController.navigate(Screen.Appearance.route)
          },
          onNavigateToProfile = { userId ->
            navController.navigate(Screen.ContactProfile.createRoute(userId))
          }
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
          onNavigateToActiveCall = {
            navController.navigate(Screen.ActiveCall.route)
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
            chatViewModel.sendMessage(
              conversationId = convId,
              text = if (caption.isNotBlank()) caption else "Photo",
              type = MessageType.IMAGE,
              mediaUrl = uri.toString()
            )
            navController.popBackStack()
          },
          onClose = { navController.popBackStack() }
        )
      }

      composable(Screen.Groups.route) {
        GroupsScreen(
          viewModel = chatViewModel,
          onNavigateToGroupChat = { grpId ->
            navController.navigate(Screen.GroupChat.createRoute(grpId))
          },
          onNavigateToChats = {
            navController.navigate(Screen.Chats.route)
          },
          onNavigateToUpdates = {
            navController.navigate(Screen.Updates.route)
          },
          onNavigateToCalls = {
            navController.navigate(Screen.Calls.route)
          },
          onNavigateToCard = {
            navController.navigate(Screen.BusinessCard.route)
          },
          onNavigateToSearch = {
            navController.navigate(Screen.Search.route)
          }
        )
      }

      composable(
        route = Screen.GroupChat.route,
        arguments = listOf(navArgument("groupId") { type = NavType.StringType })
      ) { backStackEntry ->
        val grpId = backStackEntry.arguments?.getString("groupId") ?: "grp_design_lab"
        GroupChatScreen(
          groupId = grpId,
          viewModel = chatViewModel,
          onBackClick = { navController.popBackStack() }
        )
      }

      composable(Screen.Updates.route) {
        UpdatesScreen(
          viewModel = chatViewModel,
          onNavigateToChats = {
            navController.navigate(Screen.Chats.route)
          },
          onNavigateToGroups = {
            navController.navigate(Screen.Groups.route)
          },
          onNavigateToCalls = {
            navController.navigate(Screen.Calls.route)
          },
          onNavigateToCard = {
            navController.navigate(Screen.BusinessCard.route)
          }
        )
      }

      composable(Screen.Calls.route) {
        CallsScreen(
          viewModel = chatViewModel,
          onNavigateToActiveCall = {
            navController.navigate(Screen.ActiveCall.route)
          },
          onNavigateToChats = {
            navController.navigate(Screen.Chats.route)
          },
          onNavigateToGroups = {
            navController.navigate(Screen.Groups.route)
          },
          onNavigateToUpdates = {
            navController.navigate(Screen.Updates.route)
          },
          onNavigateToCard = {
            navController.navigate(Screen.BusinessCard.route)
          }
        )
      }

      composable(Screen.ActiveCall.route) {
        ActiveCallScreen(
          viewModel = chatViewModel,
          onCallEnded = { navController.popBackStack() }
        )
      }

      composable(Screen.Search.route) {
        SearchScreen(
          viewModel = chatViewModel,
          onNavigateToConversation = { convId ->
            navController.navigate(Screen.Conversation.createRoute(convId))
          },
          onNavigateToGroupChat = { grpId ->
            navController.navigate(Screen.GroupChat.createRoute(grpId))
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
          onNavigateToActiveCall = {
            navController.navigate(Screen.ActiveCall.route)
          }
        )
      }

      composable(Screen.BusinessCard.route) {
        BusinessCardScreen(
          viewModel = chatViewModel,
          onNavigateToChats = {
            navController.navigate(Screen.Chats.route)
          },
          onNavigateToGroups = {
            navController.navigate(Screen.Groups.route)
          },
          onNavigateToUpdates = {
            navController.navigate(Screen.Updates.route)
          },
          onNavigateToCalls = {
            navController.navigate(Screen.Calls.route)
          }
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
