package com.example
import com.example.data.model.User
import com.example.ui.screens.presenceLabel
import org.junit.Assert.*
import org.junit.Test
class PresenceLabelTest {
 @Test fun `online is shown only when visible`() {
  assertEquals("Online",presenceLabel(User(isOnline=true,onlineVisible=true)))
  assertEquals("",presenceLabel(User(isOnline=true,onlineVisible=false,lastSeenVisible=false)))
 }
 @Test fun `hidden or unknown last seen is never invented`() {
  assertEquals("",presenceLabel(User(lastSeen=0)))
  assertEquals("",presenceLabel(User(lastSeen=1000,lastSeenVisible=false)))
 }
}
