package com.example.ui.screens

import com.example.data.model.User

/**
 * Directory-backed identity resolution shared by the chat list and the conversation header.
 *
 * A generic "Contact" placeholder is only correct when the conversation snapshot *and* the
 * public directory both have nothing; whenever the directory knows the person we use that
 * name/photo instead so the UI never shows a fallback for known contacts.
 */
internal fun User.withDirectoryFallback(directory: List<User>): User {
  if (displayName.isNotBlank() && photoUrl.isNotBlank()) return this
  val match = directory.firstOrNull { it.uid == uid } ?: return this
  return copy(
    displayName = displayName.ifBlank { match.displayName },
    photoUrl = photoUrl.ifBlank { match.photoUrl },
    username = username.ifBlank { match.username }
  )
}
