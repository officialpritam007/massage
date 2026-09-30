from pathlib import Path


def once(s, old, new, label):
    if old not in s:
        raise SystemExit(f'{label}: source block not found')
    return s.replace(old, new, 1)

p = Path('app/src/main/java/com/example/data/network/LiquidApi.kt')
s = p.read_text()

s = once(s,
'''  fun invalidateMedia(url: String, purgeCaches: Boolean = true) {
    if (url.startsWith("appwrite:")) resolvedMedia.remove(url.removePrefix("appwrite:"))
    if (purgeCaches && ::context.isInitialized) {
      // A deleted message must never be resurrected from an already-resolved URL.
      coil.Coil.imageLoader(context).memoryCache?.clear()
      coil.Coil.imageLoader(context).diskCache?.clear()
    }
  }
''',
'''  fun invalidateMedia(url: String, purgeCaches: Boolean = true) {
    if (url.startsWith("appwrite:")) {
      val fileId = url.removePrefix("appwrite:")
      resolvedMedia.remove(fileId)
      if (::context.isInitialized) File(File(context.cacheDir, "private-media"), "$fileId.bin").delete()
    }
    if (purgeCaches && ::context.isInitialized) {
      // A deleted message must never be resurrected from an already-resolved URL.
      coil.Coil.imageLoader(context).memoryCache?.clear()
      coil.Coil.imageLoader(context).diskCache?.clear()
    }
  }

  suspend fun cachedPrivateMedia(url: String, forceRefresh: Boolean = false): File = withContext(Dispatchers.IO) {
    require(::context.isInitialized) { "App context is unavailable" }
    if (!url.startsWith("appwrite:")) {
      val id = Integer.toHexString(url.hashCode())
      val dir = File(context.cacheDir, "private-media").apply { mkdirs() }
      val cached = File(dir, "$id.bin")
      if (cached.exists() && cached.length() > 0 && !forceRefresh) return@withContext cached
      val response = http.newCall(Request.Builder().url(url).get().build()).execute()
      response.use { r ->
        check(r.isSuccessful) { "Media download failed (${r.code})" }
        val temp = File(dir, "$id.tmp")
        temp.outputStream().use { output -> r.body?.byteStream()?.use { input -> input.copyTo(output) } }
        check(temp.length() > 0) { "Downloaded media is empty" }
        if (cached.exists()) cached.delete()
        check(temp.renameTo(cached)) { "Unable to cache media" }
      }
      return@withContext cached
    }

    val fileId = url.removePrefix("appwrite:")
    val dir = File(context.cacheDir, "private-media").apply { mkdirs() }
    val cached = File(dir, "$fileId.bin")
    if (cached.exists() && cached.length() > 0 && !forceRefresh) return@withContext cached
    if (forceRefresh) cached.delete()

    val resolved = resolve(url, forceRefresh = forceRefresh)
    val response = http.newCall(Request.Builder().url(resolved).get().build()).execute()
    response.use { r ->
      check(r.isSuccessful) { "Media download failed (${r.code})" }
      val temp = File(dir, "$fileId.tmp")
      temp.outputStream().use { output ->
        r.body?.byteStream()?.use { input -> input.copyTo(output) }
      }
      check(temp.length() > 0) { "Downloaded media is empty" }
      if (cached.exists()) cached.delete()
      check(temp.renameTo(cached)) { "Unable to cache media" }
    }
    cached
  }
''', 'private media cache')

s = s.replace(
'''    // Server tokens live for five minutes; refresh before the actual expiry boundary.
''',
'''    // Server media tokens are intentionally short-lived; refresh before expiry.
''', 1)
p.write_text(s)

p = Path('app/src/main/java/com/example/ui/components/VoiceWaveform.kt')
s = p.read_text()
s = once(s,
'''      val url = LiquidApi.resolve(mediaUrl, forceRefresh = retry > 0)
      player.setDataSource(url)
''',
'''      val file = LiquidApi.cachedPrivateMedia(mediaUrl, forceRefresh = retry > 0)
      player.setDataSource(file.absolutePath)
''', 'voice cached source')
p.write_text(s)

print('Private voice media cache patch applied')
