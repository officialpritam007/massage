package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.google.firebase.auth.FirebaseAuth
import io.appwrite.Client
import io.appwrite.ID
import io.appwrite.Permission
import io.appwrite.Role
import io.appwrite.models.InputFile
import io.appwrite.services.Storage
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Firebase Auth -> Appwrite Storage bridge.
 *
 * Firebase remains the source of identity. An Appwrite Function validates the
 * Firebase ID token, mirrors the Firebase UID as an Appwrite user, and returns
 * a short-lived Appwrite JWT. Files are uploaded with the client SDK and a
 * server-created resource token is returned as the shareable media URL.
 *
 * No Appwrite API key is stored in the Android APK.
 */
object AppwriteStorageService {
    private const val ENDPOINT = "https://sgp.cloud.appwrite.io/v1"
    private const val PROJECT_ID = "6abae44b0030a4b3c0b4"
    private const val BUCKET_ID = "6abae4e300352b37c209"

    // Appwrite Console -> Functions -> firebase-appwrite-bridge -> Domains
    // Replace this single value after the function is deployed.
    private const val AUTH_FUNCTION_URL = "REPLACE_WITH_APPWRITE_FUNCTION_URL"

    @Volatile
    private var jwt: String = ""

    @Volatile
    private var jwtExpiresAtMs: Long = 0L

    private var appContext: Context? = null
    private var client: Client? = null
    private var storage: Storage? = null

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (client != null) return
        client = Client(context.applicationContext)
            .setEndpoint(ENDPOINT)
            .setProject(PROJECT_ID)
        storage = Storage(client!!)
    }

    fun isConfigured(): Boolean = !AUTH_FUNCTION_URL.startsWith("REPLACE_")

    fun clearSession() {
        jwt = ""
        jwtExpiresAtMs = 0L
        client = null
        storage = null
        appContext?.let { initialize(it) }
    }

    suspend fun ensureSession(forceRefresh: Boolean = false): String {
        val context = appContext
            ?: throw IllegalStateException("AppwriteStorageService is not initialized")
        initialize(context)

        if (!isConfigured()) {
            throw IllegalStateException("Appwrite auth function URL is not configured")
        }

        val now = System.currentTimeMillis()
        if (!forceRefresh && jwt.isNotBlank() && now < jwtExpiresAtMs) {
            return jwt
        }

        val response = callBridge(action = "auth", forceFreshFirebaseToken = forceRefresh)
        val token = response.optString("jwt")
        if (token.isBlank()) {
            throw IllegalStateException("Appwrite auth bridge returned no JWT")
        }

        jwt = token
        // Function currently requests a one-hour JWT; refresh a few minutes early.
        val expiresInSeconds = response.optLong("expiresIn", 3600L).coerceAtLeast(60L)
        jwtExpiresAtMs = now + (expiresInSeconds - 180L).coerceAtLeast(60L) * 1000L
        client?.setJWT(token)
        return token
    }

    /** Uploads any selected Android Uri and returns a tokenized Appwrite view URL. */
    suspend fun upload(
        uri: Uri,
        category: String,
        currentUid: String
    ): String {
        val context = appContext
            ?: throw IllegalStateException("AppwriteStorageService is not initialized")
        initialize(context)
        val localFile = copyUriToCache(context, uri, category)

        return try {
            ensureSession()
            try {
                createFileAndToken(localFile, currentUid)
            } catch (first: Exception) {
                // Retry once when Appwrite rejected an expired/stale JWT.
                if (first.message?.contains("401") == true) {
                    ensureSession(forceRefresh = true)
                    createFileAndToken(localFile, currentUid)
                } else {
                    throw first
                }
            }
        } finally {
            localFile.delete()
        }
    }

    private suspend fun createFileAndToken(localFile: File, currentUid: String): String {
        val result = storage!!.createFile(
            bucketId = BUCKET_ID,
            fileId = ID.unique(),
            file = InputFile.fromPath(localFile.absolutePath),
            permissions = listOf(
                Permission.read(Role.user(currentUid)),
                Permission.write(Role.user(currentUid))
            )
        )

        return try {
            val tokenResponse = callBridge(
                action = "fileToken",
                extraBody = JSONObject().put("fileId", result.id),
                forceFreshFirebaseToken = false
            )
            tokenResponse.optString("fileUrl").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Appwrite bridge returned no file URL")
        } catch (tokenError: Exception) {
            // If the token step fails, delete the just-created file to avoid an orphan.
            runCatching { storage?.deleteFile(bucketId = BUCKET_ID, fileId = result.id) }
            throw tokenError
        }
    }

    suspend fun deleteByUrl(url: String) {
        if (!isAppwriteFileUrl(url)) return
        val fileId = extractFileId(url) ?: return
        ensureSession()
        storage?.deleteFile(bucketId = BUCKET_ID, fileId = fileId)
    }

    fun isAppwriteFileUrl(url: String): Boolean {
        return url.startsWith(ENDPOINT) && url.contains("/storage/buckets/$BUCKET_ID/files/")
    }

    private suspend fun callBridge(
        action: String,
        extraBody: JSONObject = JSONObject(),
        forceFreshFirebaseToken: Boolean = true
    ): JSONObject {
        if (!isConfigured()) {
            throw IllegalStateException("Appwrite auth function URL is not configured")
        }

        val firebaseUser = FirebaseAuth.getInstance().currentUser
            ?: throw IllegalStateException("Firebase user is not logged in")
        val idToken = firebaseUser.getIdToken(forceFreshFirebaseToken).await()?.token
            ?: throw IllegalStateException("Unable to obtain Firebase ID token")

        val body = JSONObject(extraBody.toString()).put("action", action)
        val request = Request.Builder()
            .url(AUTH_FUNCTION_URL)
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Authorization", "Bearer $idToken")
            .header("Content-Type", "application/json")
            .build()

        http.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val serverMessage = runCatching {
                    JSONObject(responseBody).optString("error")
                }.getOrNull().orEmpty()
                throw IllegalStateException(
                    "Appwrite bridge failed (${response.code}): " +
                        (serverMessage.ifBlank { responseBody.ifBlank { "Unknown error" } })
                )
            }
            return if (responseBody.isBlank()) JSONObject() else JSONObject(responseBody)
        }
    }

    private fun copyUriToCache(context: Context, uri: Uri, category: String): File {
        val mime = context.contentResolver.getType(uri).orEmpty()
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            ?.takeIf { it.isNotBlank() }
            ?: "bin"
        val safeCategory = category.replace(Regex("[^A-Za-z0-9._-]"), "_").take(32)
        val fileName = "${safeCategory}_${System.currentTimeMillis()}_${UUID.randomUUID()}.$extension"
        val localFile = File(context.cacheDir, fileName)

        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open selected media" }
            localFile.outputStream().use { output -> input.copyTo(output) }
        }
        return localFile
    }

    private fun extractFileId(url: String): String? {
        val segments = runCatching { Uri.parse(url).pathSegments }.getOrNull() ?: return null
        val filesIndex = segments.indexOf("files")
        return if (filesIndex >= 0 && filesIndex + 1 < segments.size) {
            segments[filesIndex + 1]
        } else {
            null
        }
    }
}
