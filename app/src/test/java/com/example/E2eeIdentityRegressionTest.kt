package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.crypto.E2eeCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class E2eeIdentityRegressionTest {
  @Test
  fun `incomplete saved identities are preserved instead of silently replaced`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = context.getSharedPreferences("liquid-e2ee", 0)
    listOf("private", "privateIv", "public").forEach { missing ->
      val uid = "incomplete-$missing"
      val stored = listOf("private", "privateIv", "public")
        .filterNot { it == missing }.associate { "$it:$uid" to "saved-$it" }
      val edit = prefs.edit()
      stored.forEach { (key, value) -> edit.putString(key, value) }
      edit.commit()
      val error = assertThrows(IllegalArgumentException::class.java) {
        E2eeCrypto.ensureIdentity(context, uid)
      }
      assertTrue(error.message.orEmpty().contains("preserved"))
      stored.forEach { (key, value) -> assertEquals(value, prefs.getString(key, null)) }
      assertEquals(null, prefs.getString("$missing:$uid", null))
    }
  }
}
