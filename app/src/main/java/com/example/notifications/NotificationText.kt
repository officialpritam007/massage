package com.example.notifications

object NotificationText {
  fun body(type: String, text: String?, showPreview: Boolean): String {
    if (!showPreview) return "New message"
    if (!text.isNullOrBlank()) return text.take(240)
    return when (type) {
      "IMAGE" -> "Photo"
      "VIDEO" -> "Video"
      "VOICE", "AUDIO" -> "Voice message"
      "FILE" -> "Document"
      "LOCATION" -> "Location"
      else -> "New message"
    }
  }
}
