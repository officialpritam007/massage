package com.example.data.repository

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * The repository's server actions intentionally run on its own supervised scope. These helpers
 * turn the resulting observable state change back into a suspendable Result for UI animations.
 * That avoids guessing that a delete failed merely because a fixed 1-2 second timer expired.
 */
suspend fun ChatRepository.deleteMessageForMeAwait(
    conversationId: String,
    messageId: String
): Result<Unit> = awaitMessageRemoval(conversationId, messageId) {
    deleteMessageForMe(conversationId, messageId)
}

suspend fun ChatRepository.deleteMessageForEveryoneAwait(
    conversationId: String,
    messageId: String
): Result<Unit> = awaitMessageRemoval(conversationId, messageId) {
    deleteMessageForEveryone(conversationId, messageId)
}

suspend fun ChatRepository.deleteChatForMeAwait(conversationId: String): Result<Unit> {
    clearError()
    deleteChatForMe(conversationId)
    return combine(conversations, error) { current, currentError ->
        when {
            current.none { it.id == conversationId } -> Result.success(Unit)
            !currentError.isNullOrBlank() -> Result.failure(IllegalStateException(currentError))
            else -> null
        }
    }.filterNotNull().first()
}

private suspend fun ChatRepository.awaitMessageRemoval(
    conversationId: String,
    messageId: String,
    start: ChatRepository.() -> Unit
): Result<Unit> {
    clearError()
    start()
    return combine(messages, error) { current, currentError ->
        when {
            current[conversationId].orEmpty().none { it.id == messageId } -> Result.success(Unit)
            !currentError.isNullOrBlank() -> Result.failure(IllegalStateException(currentError))
            else -> null
        }
    }.filterNotNull().first()
}
