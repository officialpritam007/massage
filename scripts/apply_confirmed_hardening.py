from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"{label}: expected source block not found")
    return text.replace(old, new, 1)


# -----------------------------------------------------------------------------
# Authenticated backend: permanent deletion must remain authoritative.
# -----------------------------------------------------------------------------
p = Path("appwrite-functions/liquid-api/src/main.js")
s = p.read_text()

# Merely opening a contact must not resurrect a deleted chat. A later send action
# already removes deletedFor for both participants and therefore starts a fresh chat.
s = replace_once(
    s,
    """        } else {
          t.update(ref, {deletedFor: FieldValue.arrayRemove(uid)});
        }
""",
    """        }
""",
    "conversation must not resurrect deleted chat",
)

# Media access must be derived from a currently visible message reference, not just
# conversation membership. This covers Delete for me, Delete for everyone and a
# deleted-chat cutoff without exposing old media after sync/reinstall/another device.
s = replace_once(
    s,
    """        const revoked = new Set(meta.revokedFrom || []);
        for (const cid of [meta.conversationId, ...(meta.forwardedTo || [])]) {
          if (revoked.has(cid)) continue;
          try {
            await allowed(db, cid, uid);
            ok = true;
            break;
          } catch {}
        }
""",
    """        const revoked = new Set(meta.revokedFrom || []);
        const mediaRef = 'appwrite:' + p.fileId;
        for (const cid of [meta.conversationId, ...(meta.forwardedTo || [])]) {
          if (!cid || revoked.has(cid)) continue;
          try {
            const access = await allowed(db, cid, uid);
            const cutoff = Number(access.c.deletedBefore?.[uid] || 0);
            const candidates = await access.ref.collection('messages')
              .where('mediaUrl', '==', mediaRef)
              .limit(20)
              .get();
            const visible = candidates.docs.some(doc => {
              const message = doc.data();
              const createdAt = Number(message.createdAt || 0);
              return !message.deletedForEveryone
                && !(message.hiddenFor || []).includes(uid)
                && !(message.expiresAt && Number(message.expiresAt) <= now)
                && (!cutoff || createdAt > cutoff);
            });
            if (!visible) continue;
            ok = true;
            break;
          } catch {}
        }
""",
    "media access visibility enforcement",
)

# A forwarded message is a fresh message and should never inherit an undefined
# hiddenFor variable from the source path.
s = replace_once(
    s,
    """          waveform: sanitizeWaveform(source.waveform),
          createdAt: now,
          status: 'SENT',
          isDeleted: false,
          deletedForEveryone: false,
          hiddenFor,
          isEdited: false,
""",
    """          waveform: sanitizeWaveform(source.waveform),
          createdAt: now,
          status: 'SENT',
          isDeleted: false,
          deletedForEveryone: false,
          hiddenFor: [],
          isEdited: false,
""",
    "forward hiddenFor initialization",
)

# The send path intentionally looks up persistent per-user message tombstones.
# Use the result rather than throwing it away, so a retried UUID can never resurrect.
s = replace_once(
    s,
    """          waveform: p.type === 'VOICE' ? sanitizeWaveform(p.waveform) : [],
          createdAt: now,
          status: 'SENT',
          isDeleted: false,
          deletedForEveryone: false,
          hiddenFor: [],
          isEdited: false,
""",
    """          waveform: p.type === 'VOICE' ? sanitizeWaveform(p.waveform) : [],
          createdAt: now,
          status: 'SENT',
          isDeleted: false,
          deletedForEveryone: false,
          hiddenFor,
          isEdited: false,
""",
    "send tombstone visibility wiring",
)

# Keep secure URLs deliberately short lived. Android also clears its local resolved
# URL and Coil caches immediately after a delete operation / deletion FCM.
s = s.replace(
    "expire: new Date(now + 5 * 60000).toISOString()",
    "expire: new Date(now + 60 * 1000).toISOString()",
)

p.write_text(s)


# -----------------------------------------------------------------------------
# Android: deletion invalidates token/image caches immediately on this device.
# -----------------------------------------------------------------------------
p = Path("app/src/main/java/com/example/data/network/LiquidApi.kt")
s = p.read_text()
s = replace_once(
    s,
    """  fun invalidateMedia(url: String) {
    if (url.startsWith(\"appwrite:\")) resolvedMedia.remove(url.removePrefix(\"appwrite:\"))
  }
""",
    """  fun invalidateMedia(url: String, purgeCaches: Boolean = true) {
    if (url.startsWith(\"appwrite:\")) resolvedMedia.remove(url.removePrefix(\"appwrite:\"))
    if (purgeCaches && ::context.isInitialized) {
      // A deleted message must never be resurrected from an already-resolved URL.
      coil.Coil.imageLoader(context).memoryCache?.clear()
      coil.Coil.imageLoader(context).diskCache?.clear()
    }
  }
""",
    "Android media cache invalidation",
)
s = s.replace(
    "resolvedMedia[fileId] = ResolvedMedia(resolved, now + 4 * 60_000)",
    "resolvedMedia[fileId] = ResolvedMedia(resolved, now + 45_000)",
)
p.write_text(s)


p = Path("app/src/main/java/com/example/data/repository/ChatRepository.kt")
s = p.read_text()
s = replace_once(
    s,
    """  fun deleteMessageForMe(cid: String, id: String) = runAction {
    prefs.edit().remove(\"outbox:$uid:$id\").apply()
    LiquidApi.call(\"deleteForMe\", mapOf(\"conversationId\" to cid, \"messageId\" to id))
    removeLocalMessage(cid, id)
  }
""",
    """  fun deleteMessageForMe(cid: String, id: String) = runAction {
    val mediaUrl = _messages.value[cid].orEmpty().firstOrNull { it.id == id }?.mediaUrl.orEmpty()
    prefs.edit().remove(\"outbox:$uid:$id\").apply()
    LiquidApi.call(\"deleteForMe\", mapOf(\"conversationId\" to cid, \"messageId\" to id))
    if (mediaUrl.isNotBlank()) LiquidApi.invalidateMedia(mediaUrl)
    removeLocalMessage(cid, id)
  }
""",
    "delete-for-me cache purge",
)
s = replace_once(
    s,
    """  fun deleteMessageForEveryone(cid: String, id: String) = runAction {
    prefs.edit().remove(\"outbox:$uid:$id\").apply()
    LiquidApi.call(\"deleteForEveryone\", mapOf(\"conversationId\" to cid, \"messageId\" to id))
    removeLocalMessage(cid, id)
  }
""",
    """  fun deleteMessageForEveryone(cid: String, id: String) = runAction {
    val mediaUrl = _messages.value[cid].orEmpty().firstOrNull { it.id == id }?.mediaUrl.orEmpty()
    prefs.edit().remove(\"outbox:$uid:$id\").apply()
    LiquidApi.call(\"deleteForEveryone\", mapOf(\"conversationId\" to cid, \"messageId\" to id))
    if (mediaUrl.isNotBlank()) LiquidApi.invalidateMedia(mediaUrl)
    removeLocalMessage(cid, id)
  }
""",
    "delete-for-everyone cache purge",
)
s = replace_once(
    s,
    """  fun deleteChatForMe(cid: String) = runAction {
    val messageIds = _messages.value[cid].orEmpty().map { it.id }.toSet()
    LiquidApi.call(\"deleteChat\", mapOf(\"conversationId\" to cid))
""",
    """  fun deleteChatForMe(cid: String) = runAction {
    val currentMessages = _messages.value[cid].orEmpty()
    val messageIds = currentMessages.map { it.id }.toSet()
    val mediaUrls = currentMessages.map { it.mediaUrl }.filter { it.isNotBlank() }.distinct()
    LiquidApi.call(\"deleteChat\", mapOf(\"conversationId\" to cid))
    mediaUrls.forEach { LiquidApi.invalidateMedia(it) }
""",
    "deleted-chat cache purge",
)
p.write_text(s)


# Remote Delete for everyone: cancel notification AND evict private media caches.
p = Path("app/src/main/java/com/example/notifications/LiquidFirebaseMessagingService.kt")
s = p.read_text()
s = replace_once(
    s,
    """    if (message.data[\"type\"] == \"message_deleted\") {
      NotificationManagerCompat.from(this).cancel(id.hashCode())
      prefs.edit().remove(\"notified:$id\").apply()
      return
    }
""",
    """    if (message.data[\"type\"] == \"message_deleted\") {
      NotificationManagerCompat.from(this).cancel(id.hashCode())
      prefs.edit().remove(\"notified:$id\").apply()
      // The exact media ref is intentionally not sent in FCM. Clear private media
      // caches so a remote Delete for everyone cannot leave stale photo/video UI.
      if (com.example.data.network.LiquidApi::context.isInitialized) {
        coil.Coil.imageLoader(this).memoryCache?.clear()
        coil.Coil.imageLoader(this).diskCache?.clear()
      }
      return
    }
""",
    "remote deletion cache purge",
)
p.write_text(s)


# -----------------------------------------------------------------------------
# User-facing copy consistency.
# -----------------------------------------------------------------------------
p = Path("app/src/main/java/com/example/ui/screens/ChatsHomeScreen.kt")
s = p.read_text()
s = replace_once(
    s,
    """                Text(\"This hides the conversation from your inbox. A new message can bring it back.\")
""",
    """                Text(\"This permanently removes the current history from your account. It will not return after restart, sign-in, reinstall or sync. A later new message may start a fresh chat without restoring deleted history.\")
""",
    "delete chat permanent copy",
)
p.write_text(s)

for filename in ["README.md", "app/src/main/java/com/example/ui/screens/SettingsScreen.kt"]:
    p = Path(filename)
    s = p.read_text().replace("officialpritam@gmail.com", "officialpritam07@gmail.com")
    p.write_text(s)

print("Confirmed hardening patch applied")
