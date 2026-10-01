package com.example.data.local

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class VoiceDraft(val file: File, val seconds: Int, val waveform: List<Float>)

object VoiceDraftStore {
  private fun key(cid: String) = "voice-draft:${FirebaseAuth.getInstance().currentUser?.uid}:$cid"
  private fun prefs(context: Context) = context.getSharedPreferences("liquid-private", 0)
  fun read(context: Context, cid: String): VoiceDraft? = runCatching {
    val data = JSONObject(prefs(context).getString(key(cid), null) ?: return null)
    val file = File(data.getString("path"))
    if (!file.isFile || file.length() == 0L) return null
    val values = data.optJSONArray("waveform") ?: JSONArray()
    VoiceDraft(file, data.optInt("seconds", 1), List(values.length()) { values.optDouble(it, .1).toFloat() })
  }.getOrNull()
  fun save(context: Context, cid: String, draft: VoiceDraft) {
    prefs(context).edit().putString(key(cid), JSONObject().put("path", draft.file.absolutePath)
      .put("seconds", draft.seconds).put("waveform", JSONArray(draft.waveform)).toString()).apply()
  }
  fun clear(context: Context, cid: String, deleteFile: Boolean = true) {
    if (deleteFile) read(context, cid)?.file?.let { check(!it.exists() || it.delete()) { "Recording could not be deleted" } }
    prefs(context).edit().remove(key(cid)).apply()
  }
}
