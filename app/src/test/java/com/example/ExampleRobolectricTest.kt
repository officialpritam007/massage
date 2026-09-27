package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.CallType
import com.example.data.model.MessageType
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
  fun `test chat repository initial data and sending message`() = runTest {
    val repository = ChatRepository()
    val convs = repository.conversations.value
    assertTrue("Initial conversations should not be empty", convs.isNotEmpty())

    val firstConv = convs.first()
    repository.sendMessage(
      conversationId = firstConv.id,
      text = "Testing real-time Liquid Glass messaging"
    )

    val updatedMsgs = repository.messages.value[firstConv.id]
    assertNotNull(updatedMsgs)
    assertTrue(updatedMsgs!!.any { it.text == "Testing real-time Liquid Glass messaging" })
  }

  @Test
  fun `test call start and end state`() = runTest {
    val repository = ChatRepository()
    val user = repository.users.value.first()

    repository.startCall(user, CallType.AUDIO)
    val call = repository.activeCall.value
    assertNotNull("Active call should be set", call)
    assertEquals(user.uid, call!!.user.uid)

    repository.endCall()
    assertEquals(null, repository.activeCall.value)
    assertTrue("Call records should include the ended call", repository.callRecords.value.isNotEmpty())
  }
}
