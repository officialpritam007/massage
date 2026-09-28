package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Secure Appwrite bridge for Liquid Chat.
 * Firebase remains the source of identity; an Appwrite Function exchanges the
 * Firebase ID token for an Appwrite JWT. The JWT is then used for file-level
 * permissions. No Appwrite API key is shipped in the APK.
 */
object AppwriteStorageService {
    private const val ENDPOINT = "https://sgp.cloud.appwrite.io/v1"
    private const val PROJECT_ID = "6abae44b0030a4b3c0b4"
    private const val BUCKET_ID = "6abae4e300352b37c209"

    // Replace this after the Appwrite Function is deployed.
    private const val AUTH_FUNCTION_URL = "REPLACE_WITH_APPWRITE_FUNCTION_URL"

    @Volatile
    private var jwt: String = ""

    private var appContext: Context? = null
    private var client: Client? = null
    private var storage: Storage? = null
    private val http = OkHttpClient()

    fun initialize(context: Context) {
        if (client != null) return
        appContext = context.applicationContext
        client = Client(context.applicationContext)
            .setEndpoint(ENDPOINT)
            .setProject(PROJECT_ID)
        storage = Storage(client!!)
    }

    val currentJwt: String
        get() = jwt

    suspend fun ensureSession(forceRefresh: Boolean = false): String {
        val context = appContext ?: throw IllegalStateException("AppwriteStorageService is not initialized")
        if (AUTH_FUNCTION_URL.startsWith("REPLACE_")) {
            throw IllegalStateException("Appwrite auth function URL is not configured")
        }

        val firebaseUser = FirebaseAuth.getInstance().currentUser
            ?: throw IllegalStateException("Firebase user is not logged in")

        if (jwt.isNotBlank() && !forceRefresh) {
            return jwt
        }

        val idToken = firebaseUser.getIdToken(true).await()?.token
            ?: throw IllegalStateException("Unable to obtain Firebase ID token")

        val request = Request.Builder()
            .url(AUTH_FUNCTION_URL)
            .post(okhttp3.RequestBody.create("application/json".toMediaType(), "{}"))
            .header("Authorization", "Bearer $idToken")
            .header("Content-Type", "application/json")
            .build()

        val response = http.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw IllegalStateException("Appwrite auth bridge failed (${response.code}): $body")
        }

        val token = JSONObject(body).optString("jwt")
        if (token.isBlank()) {
            throw IllegalStateException("Appwrite auth bridge returned no JWT")
        }

        jwt = token
        client?.setJWT(token)
        return token
    }

    suspend fun uploadImage(
        uri: Uri,
        conversationId: String,
        currentUid: String
    ): String {
        val context = appContext ?: throw IllegalStateException("AppwriteStorageService is not initialized")
        initialize(context)
        ensureSession()

        val db = FirebaseFirestore.getInstance()
        val conversation = db.collection("conversations")
            .document(conversationId)
            .get()
            .await()

        val participants = (conversation.get("participantIds") as? List<*>)
            ?.filterIsInstance<String>()
            ?.distinct()
            ?: listOf(currentUid)

        if (!participants.contains(currentUid)) {
            throw SecurityException("Current user is not a conversation participant")
        }

        val fileName = "${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg"
        val localFile = File(context.cacheDir, fileName)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open selected image" }
            localFile.outputStream().use { output -> input.copyTo(output) }
        }

        return try {
            val permissions = buildList {
                participants.forEach { uid ->
                    add(Permission.read(Role.user(uid)))
                }
                add(Permission.write(Role.user(currentUid)))
            }

            val result = storage!!.createFile(
                bucketId = BUCKET_ID,
                fileId = ID.unique(),
                file = InputFile.fromPath(localFile.absolutePath),
                permissions = permissions
            )

            "$ENDPOINT/storage/buckets/$BUCKET_ID/files/${result.id}/view?project=$PROJECT_ID"
        } finally {
            localFile.delete()
        }
    }
}
