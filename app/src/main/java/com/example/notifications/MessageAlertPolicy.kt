package com.example.notifications

import android.app.Notification

/** Vibration is an independent preference: switching it off must never mute the alert sound. */
internal object MessageAlertPolicy {
  // Keep the original channel ID for users who already customized message alerts.
  const val VIBRATING_CHANNEL = "messages"
  const val NON_VIBRATING_CHANNEL = "messages_no_vibration"

  fun channelId(vibrationEnabled: Boolean): String =
    if (vibrationEnabled) VIBRATING_CHANNEL else NON_VIBRATING_CHANNEL

  /** Notification channels control alerts on Android O+; older devices use notification defaults. */
  fun legacyDefaults(vibrationEnabled: Boolean): Int =
    Notification.DEFAULT_SOUND or (if (vibrationEnabled) Notification.DEFAULT_VIBRATE else 0)
}
