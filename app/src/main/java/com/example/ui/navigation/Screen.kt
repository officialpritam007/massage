package com.example.ui.navigation

sealed class Screen(val route: String) {
  object Auth : Screen("auth")
  object Chats : Screen("chats")
  object Conversation : Screen("conversation/{conversationId}") {
    fun createRoute(conversationId: String) = "conversation/$conversationId"
  }
  object Groups : Screen("groups")
  object GroupChat : Screen("group_chat/{groupId}") {
    fun createRoute(groupId: String) = "group_chat/$groupId"
  }
  object Updates : Screen("updates")
  object Calls : Screen("calls")
  object ActiveCall : Screen("active_call")
  object Search : Screen("search")
  object ContactProfile : Screen("contact_profile/{userId}") {
    fun createRoute(userId: String) = "contact_profile/$userId"
  }
  object Camera : Screen("camera/{conversationId}") {
    fun createRoute(conversationId: String) = "camera/$conversationId"
  }
  object Appearance : Screen("appearance")
  object Settings : Screen("settings")
}
