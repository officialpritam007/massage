package com.example.ui.navigation

sealed class Screen(val route: String) {
  object Auth : Screen("auth")
  object Chats : Screen("chats")
  object Conversation : Screen("conversation/{conversationId}?messageId={messageId}") {
    /** [messageId] jumps straight to a search hit; plain chat opens omit it. */
    fun createRoute(conversationId: String, messageId: String? = null) =
      if (messageId.isNullOrBlank()) "conversation/$conversationId"
      else "conversation/$conversationId?messageId=$messageId"
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
