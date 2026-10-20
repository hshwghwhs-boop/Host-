package com.example.telegram.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TelegramResponse<T>(
    val ok: Boolean,
    val result: T? = null,
    val description: String? = null,
    @Json(name = "error_code") val errorCode: Int? = null
)

@JsonClass(generateAdapter = true)
data class TelegramUser(
    val id: Long,
    @Json(name = "is_bot") val isBot: Boolean? = false,
    @Json(name = "first_name") val firstName: String? = null,
    @Json(name = "last_name") val lastName: String? = null,
    val username: String? = null
)

@JsonClass(generateAdapter = true)
data class TelegramChat(
    val id: Long,
    val type: String? = null,
    val title: String? = null,
    val username: String? = null,
    @Json(name = "first_name") val firstName: String? = null
)

@JsonClass(generateAdapter = true)
data class TelegramDocument(
    @Json(name = "file_id") val fileId: String,
    @Json(name = "file_unique_id") val fileUniqueId: String? = null,
    @Json(name = "file_name") val fileName: String? = null,
    @Json(name = "mime_type") val mimeType: String? = null,
    @Json(name = "file_size") val fileSize: Long? = null
)

@JsonClass(generateAdapter = true)
data class TelegramPhotoSize(
    @Json(name = "file_id") val fileId: String,
    @Json(name = "file_size") val fileSize: Long? = null,
    val width: Int? = null,
    val height: Int? = null
)

@JsonClass(generateAdapter = true)
data class TelegramMessage(
    @Json(name = "message_id") val messageId: Long,
    val from: TelegramUser? = null,
    val chat: TelegramChat,
    val date: Long = 0,
    val text: String? = null,
    val document: TelegramDocument? = null,
    val photo: List<TelegramPhotoSize>? = null,
    val caption: String? = null,
    @Json(name = "reply_to_message") val replyToMessage: TelegramMessage? = null
)

@JsonClass(generateAdapter = true)
data class TelegramCallbackQuery(
    val id: String,
    val from: TelegramUser,
    val message: TelegramMessage? = null,
    val data: String? = null
)

@JsonClass(generateAdapter = true)
data class TelegramUpdate(
    @Json(name = "update_id") val updateId: Long,
    val message: TelegramMessage? = null,
    @Json(name = "callback_query") val callbackQuery: TelegramCallbackQuery? = null
)

@JsonClass(generateAdapter = true)
data class TelegramFile(
    @Json(name = "file_id") val fileId: String,
    @Json(name = "file_unique_id") val fileUniqueId: String? = null,
    @Json(name = "file_size") val fileSize: Long? = null,
    @Json(name = "file_path") val filePath: String? = null
)

// Keyboards
@JsonClass(generateAdapter = true)
data class InlineKeyboardMarkup(
    @Json(name = "inline_keyboard") val inlineKeyboard: List<List<InlineKeyboardButton>>
)

@JsonClass(generateAdapter = true)
data class InlineKeyboardButton(
    val text: String,
    @Json(name = "callback_data") val callbackData: String? = null,
    val url: String? = null
)

@JsonClass(generateAdapter = true)
data class ReplyKeyboardMarkup(
    val keyboard: List<List<KeyboardButton>>,
    @Json(name = "resize_keyboard") val resizeKeyboard: Boolean = true,
    @Json(name = "one_time_keyboard") val oneTimeKeyboard: Boolean = false
)

@JsonClass(generateAdapter = true)
data class KeyboardButton(
    val text: String
)
