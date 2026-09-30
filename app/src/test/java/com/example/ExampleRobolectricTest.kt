package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.MessageDeliveryStatus
import com.example.data.model.MessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read app name string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Liquid Chat", appName)
  }

  @Test
  fun `one to one message model supports current delivery lifecycle`() {
    val states = MessageDeliveryStatus.entries
    assertTrue(MessageDeliveryStatus.SENDING in states)
    assertTrue(MessageDeliveryStatus.SENT in states)
    assertTrue(MessageDeliveryStatus.DELIVERED in states)
    assertTrue(MessageDeliveryStatus.READ in states)
    assertTrue(MessageDeliveryStatus.FAILED in states)
  }

  @Test
  fun `supported message types exclude group and call state`() {
    val types = MessageType.entries.map { it.name }.toSet()
    assertTrue("TEXT" in types)
    assertTrue("IMAGE" in types)
    assertTrue("VIDEO" in types)
    assertTrue("VOICE" in types)
    assertTrue("FILE" in types)
    assertTrue("GROUP" !in types)
    assertTrue("CALL" !in types)
  }
}
