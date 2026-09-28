package com.example.data.calls

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Firestore signaling transport for WebRTC offer/answer/ICE metadata.
 * Media itself must be carried by a WebRTC engine; never put audio/video bytes in Firestore.
 */
class FirestoreCallSignaling(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val calls get() = db.collection("calls")

    suspend fun createCall(callerId: String, receiverId: String, type: String): String {
        val id = UUID.randomUUID().toString()
        calls.document(id).set(
            mapOf(
                "callerId" to callerId,
                "receiverId" to receiverId,
                "type" to type,
                "state" to "ringing",
                "createdAt" to System.currentTimeMillis()
            )
        ).await()
        return id
    }

    suspend fun writeOffer(callId: String, offer: Map<String, Any?>) {
        calls.document(callId).set(mapOf("offer" to offer), SetOptions.merge()).await()
    }

    suspend fun writeAnswer(callId: String, answer: Map<String, Any?>) {
        calls.document(callId).set(mapOf("answer" to answer), SetOptions.merge()).await()
    }

    suspend fun addIceCandidate(callId: String, side: String, candidate: Map<String, Any?>) {
        calls.document(callId).collection("iceCandidates").document().set(
            mapOf("side" to side, "candidate" to candidate, "createdAt" to System.currentTimeMillis())
        ).await()
    }

    fun observe(callId: String, onChanged: (Map<String, Any?>) -> Unit): ListenerRegistration =
        calls.document(callId).addSnapshotListener { snapshot, error ->
            if (error == null && snapshot != null) onChanged(snapshot.data ?: emptyMap())
        }
    suspend fun setState(callId: String, state: String, endedBy: String? = null) {
        val values = mutableMapOf<String, Any>("state" to state)
        if (!endedBy.isNullOrBlank()) values["endedBy"] = endedBy
        calls.document(callId).set(values, SetOptions.merge()).await()
    }

    suspend fun accept(callId: String) = setState(callId, "accepted")
    suspend fun reject(callId: String, userId: String) = setState(callId, "rejected", userId)
    suspend fun markConnected(callId: String) = setState(callId, "connected")

}
