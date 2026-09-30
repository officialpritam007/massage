from pathlib import Path

p = Path('app/src/main/java/com/example/data/repository/ChatRepository.kt')
s = p.read_text()

old = '''        val cid = j.getString("conversationId")
        if (_conversations.value.none { it.id == cid }) return@runCatching
        val message = Message(
'''
new = '''        val cid = j.getString("conversationId")
        val otherUid = j.optString("otherUid").takeIf { it.isNotBlank() }
        if (_conversations.value.none { it.id == cid }) {
          if (otherUid == null) return@runCatching
          pendingPeers[cid] = _users.value.find { it.uid == otherUid }
            ?: User(uid = otherUid, displayName = "Contact")
        }
        val message = Message(
'''
if old not in s:
    raise SystemExit('restoreOutbox block not found')
s = s.replace(old, new, 1)

old = '''        val cid = j.getString("conversationId")
        if (_conversations.value.none { it.id == cid }) {
          prefs.edit().remove(key).apply()
          continue
        }
        val data = j.keys().asSequence().associateWith { j.opt(it).takeUnless { value -> value == JSONObject.NULL } }
'''
new = '''        val cid = j.getString("conversationId")
        val knownConversation = _conversations.value.any { it.id == cid }
        val otherUid = j.optString("otherUid").takeIf { it.isNotBlank() }
          ?: pendingPeers[cid]?.uid?.takeIf { it.isNotBlank() }
        if (!knownConversation && otherUid == null) {
          // Corrupt/legacy outbox entry: there is no safe recipient to send to.
          prefs.edit().remove(key).apply()
          continue
        }
        if (!knownConversation && otherUid != null) {
          pendingPeers[cid] = _users.value.find { it.uid == otherUid }
            ?: User(uid = otherUid, displayName = "Contact")
        }
        val data = j.keys().asSequence().associateWith { j.opt(it).takeUnless { value -> value == JSONObject.NULL } }.toMutableMap()
        if (data["otherUid"] == null && otherUid != null) data["otherUid"] = otherUid
'''
if old not in s:
    raise SystemExit('flushOutbox gate block not found')
s = s.replace(old, new, 1)

old = '''      val result = runCatching { LiquidApi.upload(uri, cid) { progress -> scope.launch { _upload.value = progress } } }
      _upload.value = null
      if (result.isFailure && result.exceptionOrNull() !is CancellationException) _error.value = result.exceptionOrNull()?.message
'''
new = '''      val result = runCatching {
        if (_conversations.value.none { it.id == cid }) {
          val peer = pendingPeers[cid]?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Contact is not ready yet")
          // Ensure the server-side conversation document exists before uploadBegin.
          // This does not resurrect deleted history; only a subsequent new send unhides the fresh chat.
          LiquidApi.call("conversation", mapOf("otherUid" to peer))
        }
        LiquidApi.upload(uri, cid) { progress -> scope.launch { _upload.value = progress } }
      }
      _upload.value = null
      if (result.isFailure && result.exceptionOrNull() !is CancellationException) {
        _error.value = result.exceptionOrNull()?.let { friendlyError(it) }
      }
'''
if old not in s:
    raise SystemExit('uploadChatMedia block not found')
s = s.replace(old, new, 1)

s = s.replace('_error.value = e.message\n', '_error.value = friendlyError(e)\n')

p.write_text(s)
print('Fresh/deleted chat first-message outbox reliability patch applied')
