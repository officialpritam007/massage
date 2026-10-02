package com.example.ui.navigation

sealed class Screen(val route: String) {
  object Auth : Screen("auth")
  object Chats : Screen("chats")
  object Contacts : Screen("contacts")
  object Conversation : Screen("conversation/{conversationId}") {
    fun createRoute(conversationId: String) = "conversation/$conversationId"
  }
  object Search : Screen("search")
  object ContactProfile : Screen("contact_profile/{userId}") {
    fun createRoute(userId: String) = "contact_profile/$userId"
  }
  object Camera : Screen("camera/{conversationId}") {
    fun createRoute(conversationId: String) = "camera/$conversationId"
  }
  object Appearance : Screen("appearance")
  object Diagnostics : Screen("diagnostics")
  object Settings : Screen("settings")
}
