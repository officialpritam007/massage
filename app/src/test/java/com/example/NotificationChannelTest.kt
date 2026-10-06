package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import com.example.notifications.ensureMessageNotificationChannel
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class NotificationChannelTest {
  private fun manager() = ApplicationProvider.getApplicationContext<Context>()
    .getSystemService(NotificationManager::class.java)

  @Test fun disablingVibrationKeepsTheDefaultNotificationSound() {
    val manager = manager()
    val id = ensureMessageNotificationChannel(manager, vibrate = false)
    val channel = manager.getNotificationChannel(id)
    assertFalse(channel.shouldVibrate())
    assertNotNull(channel.sound)
    assertTrue(manager.getNotificationChannel("messages").shouldVibrate())
  }

  @Test fun noVibrationVariantPreservesExistingMutedAndBlockedChannelDefaults() {
    val manager = manager()
    manager.createNotificationChannel(NotificationChannel("messages", "Messages", NotificationManager.IMPORTANCE_NONE).apply {
      setSound(null, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
    })
    val id = ensureMessageNotificationChannel(manager, vibrate = false)
    assertNull(manager.getNotificationChannel(id).sound)
    assertEquals(NotificationManager.IMPORTANCE_NONE, manager.getNotificationChannel(id).importance)
  }

  @Test fun repeatedSelectionDoesNotRewriteUserChannelSettings() {
    val manager = manager()
    manager.createNotificationChannel(NotificationChannel("messages_no_vibration", "Personal settings", NotificationManager.IMPORTANCE_LOW))
    val id = ensureMessageNotificationChannel(manager, vibrate = false)
    assertEquals(NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel(id).importance)
  }
}
