package com.example.data.repository

import com.example.BuildConfig
import com.example.data.model.PrivacySettings
import com.google.firebase.database.*

/** Optional RTDB transport; Firestore presence remains usable until an instance is configured. */
class RealtimePresence(private val account: String, private val privacy: () -> PrivacySettings) {
  private val database = runCatching {
    if (BuildConfig.FIREBASE_DATABASE_URL.isBlank()) FirebaseDatabase.getInstance()
    else FirebaseDatabase.getInstance(BuildConfig.FIREBASE_DATABASE_URL)
  }.getOrNull()
  private val own = database?.getReference("presence")?.child(account)
  private val connection = database?.getReference(".info/connected")
  private val peers = mutableMapOf<String, Pair<DatabaseReference, ValueEventListener>>()
  private var foreground = false
  private var connected = false
  private var closed = false

  private fun payload(online: Boolean): Map<String, Any> {
    val settings = privacy()
    return mapOf("uid" to account,
      "isOnline" to (online && settings.onlineVisibility != "Nobody"),
      "onlineVisible" to (settings.onlineVisibility != "Nobody"),
      "lastSeenVisible" to (settings.lastSeenVisibility != "Nobody"),
      "heartbeatAt" to ServerValue.TIMESTAMP,
      "lastSeen" to if (settings.lastSeenVisibility != "Nobody") ServerValue.TIMESTAMP else 0L)
  }

  private val connectionListener = object : ValueEventListener {
    override fun onDataChange(snapshot: DataSnapshot) {
      connected = snapshot.getValue(Boolean::class.java) == true
      if (!connected || closed) return
      // Register the server-side disconnect before advertising online. Re-register on reconnect.
      own?.onDisconnect()?.setValue(payload(false))?.addOnSuccessListener {
        if (connected && !closed) own.setValue(payload(foreground))
      }
    }
    override fun onCancelled(error: DatabaseError) { connected = false }
  }

  init { connection?.addValueEventListener(connectionListener) }

  fun publish(online: Boolean) {
    foreground = online
    if (connected && !closed) {
      own?.onDisconnect()?.setValue(payload(false))?.addOnSuccessListener {
        if (connected && !closed) own.setValue(payload(foreground))
      }
    }
  }

  data class State(val online: Boolean, val heartbeat: Long, val lastSeen: Long,
    val onlineVisible: Boolean, val lastSeenVisible: Boolean)

  fun observe(peer: String, onState: (State) -> Unit) {
    val db = database ?: return
    if (peer.isBlank() || peers.containsKey(peer)) return
    val ref = db.getReference("presence").child(peer)
    val listener = object : ValueEventListener {
      override fun onDataChange(snapshot: DataSnapshot) {
        if (closed || !snapshot.exists()) return
        runCatching {
          onState(State(snapshot.child("isOnline").getValue(Boolean::class.java) == true,
            snapshot.child("heartbeatAt").getValue(Long::class.java) ?: 0L,
            snapshot.child("lastSeen").getValue(Long::class.java) ?: 0L,
            snapshot.child("onlineVisible").getValue(Boolean::class.java) != false,
            snapshot.child("lastSeenVisible").getValue(Boolean::class.java) != false))
        }
      }
      override fun onCancelled(error: DatabaseError) = Unit
    }
    peers[peer] = ref to listener
    ref.addValueEventListener(listener)
  }

  fun close() {
    if (connected) own?.setValue(payload(false))
    closed = true
    connection?.removeEventListener(connectionListener)
    peers.values.forEach { (ref, listener) -> ref.removeEventListener(listener) }
    peers.clear()
  }
}
