package com.example.telegram.api

import com.example.telegram.model.InlineKeyboardMarkup
import com.example.telegram.model.ReplyKeyboardMarkup
import com.example.telegram.model.TelegramFile
import com.example.telegram.model.TelegramMessage
import com.example.telegram.model.TelegramResponse
import com.example.telegram.model.TelegramUpdate
import com.example.telegram.model.TelegramUser
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class TelegramApiClient(private val tokenProvider: () -> String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun baseUrl(): String = "https://api.telegram.org/bot${tokenProvider()}"
    private fun fileBaseUrl(): String = "https://api.telegram.org/file/bot${tokenProvider()}"

    suspend fun getMe(): Result<TelegramUser> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${baseUrl()}/getMe")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext Result.failure(IOException("Empty body"))
                val type = Types.newParameterizedType(TelegramResponse::class.java, TelegramUser::class.java)
                val adapter = moshi.adapter<TelegramResponse<TelegramUser>>(type)
                val parsed = adapter.fromJson(body)
                if (parsed?.ok == true && parsed.result != null) {
                    Result.success(parsed.result)
                } else {
                    Result.failure(IOException(parsed?.description ?: "Failed getMe: HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUpdates(offset: Long, timeoutSeconds: Int = 20): Result<List<TelegramUpdate>> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl()}/getUpdates?offset=$offset&timeout=$timeoutSeconds"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext Result.failure(IOException("Empty body"))
                val listType = Types.newParameterizedType(List::class.java, TelegramUpdate::class.java)
                val responseType = Types.newParameterizedType(TelegramResponse::class.java, listType)
                val adapter = moshi.adapter<TelegramResponse<List<TelegramUpdate>>>(responseType)
                val parsed = adapter.fromJson(body)
                if (parsed?.ok == true && parsed.result != null) {
                    Result.success(parsed.result)
                } else {
                    Result.failure(IOException(parsed?.description ?: "getUpdates error: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        chatId: Long,
        text: String,
        replyMarkup: Any? = null,
        replyToMessageId: Long? = null,
        parseMode: String? = "Markdown"
    ): Result<TelegramMessage> = withContext(Dispatchers.IO) {
        try {
            val map = mutableMapOf<String, Any?>(
                "chat_id" to chatId,
                "text" to text
            )
            if (parseMode != null) map["parse_mode"] = parseMode
            if (replyToMessageId != null) map["reply_to_message_id"] = replyToMessageId
            if (replyMarkup != null) {
                map["reply_markup"] = when (replyMarkup) {
                    is InlineKeyboardMarkup -> {
                        moshi.adapter(InlineKeyboardMarkup::class.java).toJsonValue(replyMarkup)
                    }
                    is ReplyKeyboardMarkup -> {
                        moshi.adapter(ReplyKeyboardMarkup::class.java).toJsonValue(replyMarkup)
                    }
                    else -> replyMarkup
                }
            }

            val jsonBody = moshi.adapter(Map::class.java).toJson(map)
            val request = Request.Builder()
                .url("${baseUrl()}/sendMessage")
                .post(jsonBody.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext Result.failure(IOException("Empty body"))
                val type = Types.newParameterizedType(TelegramResponse::class.java, TelegramMessage::class.java)
                val adapter = moshi.adapter<TelegramResponse<TelegramMessage>>(type)
                val parsed = adapter.fromJson(body)
                if (parsed?.ok == true && parsed.result != null) {
                    Result.success(parsed.result)
                } else {
                    Result.failure(IOException(parsed?.description ?: "sendMessage error: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun editMessageText(
        chatId: Long,
        messageId: Long,
        text: String,
        replyMarkup: InlineKeyboardMarkup? = null,
        parseMode: String? = "Markdown"
    ): Result<TelegramMessage> = withContext(Dispatchers.IO) {
        try {
            val map = mutableMapOf<String, Any?>(
                "chat_id" to chatId,
                "message_id" to messageId,
                "text" to text
            )
            if (parseMode != null) map["parse_mode"] = parseMode
            if (replyMarkup != null) {
                map["reply_markup"] = moshi.adapter(InlineKeyboardMarkup::class.java).toJsonValue(replyMarkup)
            }

            val jsonBody = moshi.adapter(Map::class.java).toJson(map)
            val request = Request.Builder()
                .url("${baseUrl()}/editMessageText")
                .post(jsonBody.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext Result.failure(IOException("Empty body"))
                val type = Types.newParameterizedType(TelegramResponse::class.java, TelegramMessage::class.java)
                val adapter = moshi.adapter<TelegramResponse<TelegramMessage>>(type)
                val parsed = adapter.fromJson(body)
                if (parsed?.ok == true && parsed.result != null) {
                    Result.success(parsed.result)
                } else {
                    Result.failure(IOException(parsed?.description ?: "editMessageText error: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun editMessageReplyMarkup(
        chatId: Long,
        messageId: Long,
        replyMarkup: InlineKeyboardMarkup?
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val map = mutableMapOf<String, Any?>(
                "chat_id" to chatId,
                "message_id" to messageId
            )
            if (replyMarkup != null) {
                map["reply_markup"] = moshi.adapter(InlineKeyboardMarkup::class.java).toJsonValue(replyMarkup)
            }

            val jsonBody = moshi.adapter(Map::class.java).toJson(map)
            val request = Request.Builder()
                .url("${baseUrl()}/editMessageReplyMarkup")
                .post(jsonBody.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext Result.failure(IOException("Empty body"))
                Result.success(response.isSuccessful)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun answerCallbackQuery(
        callbackQueryId: String,
        text: String? = null,
        showAlert: Boolean = false
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val map = mutableMapOf<String, Any?>(
                "callback_query_id" to callbackQueryId,
                "show_alert" to showAlert
            )
            if (text != null) map["text"] = text

            val jsonBody = moshi.adapter(Map::class.java).toJson(map)
            val request = Request.Builder()
                .url("${baseUrl()}/answerCallbackQuery")
                .post(jsonBody.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                Result.success(response.isSuccessful)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFile(fileId: String): Result<TelegramFile> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl()}/getFile?file_id=$fileId"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext Result.failure(IOException("Empty body"))
                val type = Types.newParameterizedType(TelegramResponse::class.java, TelegramFile::class.java)
                val adapter = moshi.adapter<TelegramResponse<TelegramFile>>(type)
                val parsed = adapter.fromJson(body)
                if (parsed?.ok == true && parsed.result != null) {
                    Result.success(parsed.result)
                } else {
                    Result.failure(IOException(parsed?.description ?: "getFile error"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadFileBytes(filePath: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val url = "${fileBaseUrl()}/$filePath"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null) Result.success(bytes)
                    else Result.failure(IOException("Empty downloaded content"))
                } else {
                    Result.failure(IOException("HTTP ${response.code} downloading file"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendChatAction(chatId: Long, action: String = "typing"): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val map = mapOf("chat_id" to chatId, "action" to action)
            val jsonBody = moshi.adapter(Map::class.java).toJson(map)
            val request = Request.Builder()
                .url("${baseUrl()}/sendChatAction")
                .post(jsonBody.toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                Result.success(response.isSuccessful)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMessage(chatId: Long, messageId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val map = mapOf("chat_id" to chatId, "message_id" to messageId)
            val jsonBody = moshi.adapter(Map::class.java).toJson(map)
            val request = Request.Builder()
                .url("${baseUrl()}/deleteMessage")
                .post(jsonBody.toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                Result.success(response.isSuccessful)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
