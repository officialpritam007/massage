package com.example.data.repository

/** An operation-scoped result, independent of unrelated snapshots and global error banners. */
suspend fun ChatRepository.deleteMessageForMeAwait(conversationId: String, messageId: String): Result<Unit> =
    deleteMessageForMeResult(conversationId, messageId)

suspend fun ChatRepository.deleteMessageForEveryoneAwait(conversationId: String, messageId: String): Result<Unit> =
    deleteMessageForEveryoneResult(conversationId, messageId)

suspend fun ChatRepository.deleteChatForMeAwait(conversationId: String): Result<Unit> =
    deleteChatForMeResult(conversationId)
