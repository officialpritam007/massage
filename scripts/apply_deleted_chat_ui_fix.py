from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"{label}: expected source block not found")
    return text.replace(old, new, 1)


p = Path("app/src/main/java/com/example/data/repository/ChatRepository.kt")
s = p.read_text()

s = replace_once(
    s,
    """  private val legacyDeleteMigrations = mutableSetOf<String>()
  private val sending = kotlinx.coroutines.sync.Mutex()
""",
    """  private val legacyDeleteMigrations = mutableSetOf<String>()
  // Keeps enough peer identity to compose a fresh message without putting a
  // server-deleted conversation back into the chats list before a new send.
  private val pendingPeers = mutableMapOf<String, User>()
  private val sending = kotlinx.coroutines.sync.Mutex()
""",
    "pending peer state",
)

s = replace_once(
    s,
    """  private fun reportSnapshotFailure(area: String, t: Throwable) {
    _error.value = \"$area could not be loaded. ${t.message ?: \"Invalid cached data\"}\"
  }
""",
    """  private fun friendlyError(t: Throwable, fallback: String = \"Operation failed\"): String {
    val message = t.message.orEmpty()
    return when {
      message.contains(\"PERMISSION_DENIED\", true) || message.contains(\"insufficient permissions\", true) ->
        \"Sync permission denied. Publish the latest Firestore rules, then reopen Liquid Chat.\"
      message.contains(\"UNAVAILABLE\", true) || message.contains(\"network\", true) || t is java.io.IOException ->
        \"Connection unavailable. Your pending messages will retry when the network returns.\"
      message.contains(\"token\", true) && message.contains(\"expired\", true) ->
        \"Your session needs refreshing. Please sign in again.\"
      message.contains(\"File not available\", true) || message.contains(\"Media access denied\", true) ->
        \"This media is no longer available.\"
      message.isNotBlank() -> message
      else -> fallback
    }
  }

  private fun reportSnapshotFailure(area: String, t: Throwable) {
    _error.value = \"$area: ${friendlyError(t, \"could not be loaded\")}\"
  }
""",
    "friendly error mapping",
)

s = replace_once(
    s,
    """      runCatching { block() }.onFailure {
        if (it !is CancellationException) _error.value = it.message ?: \"Operation failed\"
      }
""",
    """      runCatching { block() }.onFailure {
        if (it !is CancellationException) _error.value = friendlyError(it)
      }
""",
    "runAction friendly error",
)

# Listener errors should use the same user-facing mapper.
s = s.replace("_error.value = error.message", "_error.value = friendlyError(error)")

s = replace_once(
    s,
    """    deletedBefore.clear()
    legacyDeleteMigrations.clear()
""",
    """    deletedBefore.clear()
    legacyDeleteMigrations.clear()
    pendingPeers.clear()
""",
    "logout pending peer cleanup",
)

s = replace_once(
    s,
    """    val user = _users.value.find { it.uid == other } ?: User(uid = other, displayName = \"Contact\")
    fun flag(name: String) = (snapshot.get(name) as? List<*>)?.contains(uid) == true
""",
    """    val user = _users.value.find { it.uid == other } ?: User(uid = other, displayName = \"Contact\")
    pendingPeers[snapshot.id] = user
    fun flag(name: String) = (snapshot.get(name) as? List<*>)?.contains(uid) == true
""",
    "sync pending peer identity",
)

old_get = """  fun getOrCreateConversationId(otherUid: String): String {
    val cid = listOf(uid, otherUid).sorted().joinToString(\"_\")
    if (_conversations.value.none { it.id == cid }) {
      _conversations.update {
        it + Conversation(
          id = cid,
          participantIds = listOf(uid, otherUid),
          otherUser = _users.value.find { user -> user.uid == otherUid } ?: User(uid = otherUid, displayName = \"Contact\")
        )
      }
    }
    runAction {
      LiquidApi.call(\"conversation\", mapOf(\"otherUid\" to otherUid))
      observeConversation(cid)
      observePresence(cid)
    }
    return cid
  }
"""
new_get = """  fun getOrCreateConversationId(otherUid: String): String {
    val cid = listOf(uid, otherUid).sorted().joinToString(\"_\")
    pendingPeers[cid] = _users.value.find { user -> user.uid == otherUid }
      ?: User(uid = otherUid, displayName = \"Contact\")
    // Do not optimistically insert a conversation into _conversations. If this chat
    // was deleted for the current account, opening the contact must not resurrect
    // the row/history. The authenticated send action will start a fresh chat.
    runAction {
      LiquidApi.call(\"conversation\", mapOf(\"otherUid\" to otherUid))
      observeConversation(cid)
      observePresence(cid)
    }
    return cid
  }

  fun peerForConversation(cid: String): User? = pendingPeers[cid]
"""
s = replace_once(s, old_get, new_get, "deleted chat optimistic resurrection")

s = replace_once(
    s,
    """    \"otherUid\" to _conversations.value.find { it.id == message.conversationId }?.otherUser?.uid,
""",
    """    \"otherUid\" to (
      _conversations.value.find { it.id == message.conversationId }?.otherUser?.uid
        ?: pendingPeers[message.conversationId]?.uid
    ),
""",
    "outbox peer fallback",
)

# Preserve the peer mapping after local chat deletion so selecting the same contact
# can compose a new message without exposing old history.
p.write_text(s)


p = Path("app/src/main/java/com/example/ui/screens/ConversationScreen.kt")
s = p.read_text()
s = replace_once(
    s,
    """    val repo = viewModel.repository
    val conversation = conversations.find { it.id == conversationId }
    val other = conversation?.otherUser ?: User(displayName = \"Contact\")
""",
    """    val repo = viewModel.repository
    val conversation = conversations.find { it.id == conversationId }
    val other = conversation?.otherUser
        ?: repo.peerForConversation(conversationId)
        ?: User(displayName = \"Contact\")
""",
    "conversation pending peer fallback",
)
p.write_text(s)

print("Deleted-chat UI and error UX patch applied")
