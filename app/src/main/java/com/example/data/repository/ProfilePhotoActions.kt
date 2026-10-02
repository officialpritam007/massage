package com.example.data.repository

import com.example.data.network.LiquidApi

/**
 * Removes the current profile photo through the authenticated backend.
 * The server owns identity fields and Cloudinary media deletion, so Android never bypasses
 * Firestore security rules or receives storage admin credentials.
 */
suspend fun ChatRepository.removeProfilePhoto(): Result<Unit> = runCatching {
    val previous = currentUser.value.photoUrl
    LiquidApi.call("profilePhotoDelete")
    if (previous.isNotBlank()) LiquidApi.invalidateMedia(previous)
}
