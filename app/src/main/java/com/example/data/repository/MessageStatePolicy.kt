package com.example.data.repository

import com.example.data.model.Message

/**
 * Prevents transient encryption/bootstrap states from replacing stable UI content.
 *
 * A peer directory snapshot can arrive after the message snapshot. Until the peer key is
 * available, an encrypted row is not a proven permanent failure and should stay hidden (or keep
 * the previously decrypted value). A later directory update forces a targeted message re-decode.
 */
internal fun stabilizeDecodedMessage(
  candidate: Message,
  previous: Message?,
  accountId: String,
  peerKeyReady: Boolean
): Message? = when {
  candidate.encryptionUnavailable && previous != null && !previous.encryptionUnavailable -> previous
  candidate.encryptionUnavailable && candidate.senderId != accountId && !peerKeyReady -> previous
  else -> candidate
}
