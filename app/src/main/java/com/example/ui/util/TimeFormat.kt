package com.example.ui.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeFormat {
  // Per-call formatter: SimpleDateFormat is mutable and shared instances race on IO workers.
  // Also works on API 24/25 without java.time desugaring and follows locale/time-zone changes.
  fun messageTime(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    return runCatching {
      SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMillis))
    }.getOrDefault("")
  }
}
