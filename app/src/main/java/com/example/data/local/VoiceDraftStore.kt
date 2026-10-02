package com.example.data.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Private, account-scoped draft metadata; AAC data stays recoverable across process death. */
class VoiceDraftStore(context: Context, account: String, conversationId: String) {
    data class Draft(val file: File, val seconds: Int, val waveform: List<Float>)
    private val directory = File(context.filesDir, "voice-drafts").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("voice-drafts", Context.MODE_PRIVATE)
    private val key = "$account:$conversationId"
    fun newFile(): File = File.createTempFile("voice-", ".aac", directory)
    fun save(file: File, seconds: Int, waveform: List<Float>) {
        prefs.edit().putString(key, JSONObject().put("file", file.name).put("seconds", seconds)
            .put("waveform", JSONArray(waveform)).toString()).apply()
    }
    fun load(): Draft? = runCatching {
        val json = JSONObject(prefs.getString(key, null) ?: return null)
        val file = File(directory, json.getString("file"))
        if (file.canonicalFile.parentFile != directory.canonicalFile || !file.exists() || file.length() == 0L) return null
        val bars = json.optJSONArray("waveform") ?: JSONArray()
        Draft(file, json.optInt("seconds").coerceAtLeast(1), (0 until bars.length()).map { bars.optDouble(it, .1).toFloat() })
    }.getOrNull()
    fun forget(file: File) {
        val stored = runCatching { JSONObject(prefs.getString(key, "{}")!!).optString("file") }.getOrDefault("")
        if (stored == file.name) prefs.edit().remove(key).apply()
    }
}
