package com.example.ui.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeFormat {
  private val messageClock = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

  fun messageTime(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    return runCatching {
      messageClock.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
    }.getOrDefault("")
  }
}
