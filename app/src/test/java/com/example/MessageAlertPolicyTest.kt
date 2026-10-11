package com.example

import android.app.Notification
import com.example.notifications.MessageAlertPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [25])
class MessageAlertPolicyTest {
  @Test fun separateChannelsPreserveUserVibrationPreference() {
    assertEquals("messages", MessageAlertPolicy.channelId(true))
    assertEquals("messages_no_vibration", MessageAlertPolicy.channelId(false))
  }

  @Test fun turningOffVibrationDoesNotMuteSoundOnOlderAndroid() {
    val withVibration = MessageAlertPolicy.legacyDefaults(true)
    val withoutVibration = MessageAlertPolicy.legacyDefaults(false)
    assertTrue(withVibration and Notification.DEFAULT_SOUND != 0)
    assertTrue(withVibration and Notification.DEFAULT_VIBRATE != 0)
    assertTrue(withoutVibration and Notification.DEFAULT_SOUND != 0)
    assertEquals(0, withoutVibration and Notification.DEFAULT_VIBRATE)
  }
}
