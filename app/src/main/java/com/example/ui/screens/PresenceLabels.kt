package com.example.ui.screens

import com.example.data.model.User
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun presenceLabel(user: User): String {
    if (user.onlineVisible && user.isOnline) return "Online"
    if (!user.lastSeenVisible || user.lastSeen <= 0L) return ""

    val now = Calendar.getInstance()
    val seen = Calendar.getInstance().apply { timeInMillis = user.lastSeen }
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(user.lastSeen))

    val today = now.get(Calendar.YEAR) == seen.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == seen.get(Calendar.DAY_OF_YEAR)

    now.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = now.get(Calendar.YEAR) == seen.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == seen.get(Calendar.DAY_OF_YEAR)

    return "Last seen " + when {
        today -> "today at $time"
        yesterday -> "yesterday at $time"
        else -> SimpleDateFormat("d MMM yyyy 'at' h:mm a", Locale.getDefault())
            .format(Date(user.lastSeen))
    }
}
