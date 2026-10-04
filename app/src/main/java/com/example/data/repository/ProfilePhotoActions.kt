package com.example.data.repository

import com.example.data.network.LiquidApi
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Removes the profile-photo reference from Firestore.
 *
 * Free direct-upload mode never ships the Cloudinary API secret in Android, so the
 * previously uploaded Cloudinary object may remain orphaned after its Firestore reference is removed.
 */
suspend fun ChatRepository.removeProfilePhoto(): Result<Unit> = runCatching {
    val account = FirebaseAuth.getInstance().currentUser?.uid ?: error("Please sign in again")
    val previous = currentUser.value.photoUrl
    val db = FirebaseFirestore.getInstance()

    val batch = db.batch()
    batch.update(db.document("users/$account"), "photoUrl", FieldValue.delete())
    batch.update(db.document("directory/$account"), "photoUrl", FieldValue.delete())
    batch.commit().await()

    if (previous.isNotBlank()) LiquidApi.invalidateMedia(previous)
}
