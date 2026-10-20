package com.example.telegram.engine

import com.example.data.dao.BotDao
import com.example.data.model.ActiveUserEntity
import com.example.data.model.AdminEntity
import com.example.data.model.HostedFileEntity
import com.example.data.model.LogEntryEntity
import com.example.data.model.SubscriptionEntity
import com.example.data.preferences.BotPreferences
import com.example.runner.ScriptRunner
import com.example.telegram.api.TelegramApiClient
import com.example.telegram.model.InlineKeyboardButton
import com.example.telegram.model.InlineKeyboardMarkup
import com.example.telegram.model.KeyboardButton
import com.example.telegram.model.ReplyKeyboardMarkup
import com.example.telegram.model.TelegramCallbackQuery
import com.example.telegram.model.TelegramMessage
import com.example.telegram.model.TelegramUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class TelegramBotEngine(
    private val apiClient: TelegramApiClient,
    private val botDao: BotDao,
    private val preferences: BotPreferences,
    private val scriptRunner: ScriptRunner,
    private val scope: CoroutineScope
) {
    // Session state for multi-step prompts (like waiting for broadcast message, admin ID, subscription days)
    private val userSteps = ConcurrentHashMap<Long, String>()
    private val broadcastDrafts = ConcurrentHashMap<Long, TelegramMessage>()

    suspend fun handleUpdate(update: TelegramUpdate) {
        val message = update.message
        val callback = update.callbackQuery

        if (message != null) {
            handleMessage(message)
        } else if (callback != null) {
            handleCallbackQuery(callback)
        }
    }

    private suspend fun handleMessage(message: TelegramMessage) {
        val from = message.from ?: return
        val userId = from.id
        val chatId = message.chat.id
        val text = message.text?.trim() ?: ""

        // Track or update active user in DB
        val existingUser = botDao.getUser(userId)
        if (existingUser == null) {
            botDao.insertOrUpdateUser(
                ActiveUserEntity(
                    userId = userId,
                    username = from.username,
                    firstName = from.firstName,
                    joinTime = System.currentTimeMillis(),
                    lastSeenTime = System.currentTimeMillis(),
                    messageCount = 1
                )
            )
            // Notify owner of new user
            val ownerId = preferences.ownerId
            if (userId != ownerId) {
                val notif = "🎉 𝐍ᴇᴡ 𝐔ꜱᴇʀ!\n👤 𝐍ᴀᴍᴇ: ${from.firstName ?: "Unknown"}\n✳️ 𝐔ꜱᴇʀ: @${from.username ?: "N/A"}\n🆔 𝐈𝐃: `$userId`"
                apiClient.sendMessage(ownerId, notif)
            }
        } else {
            botDao.insertOrUpdateUser(
                existingUser.copy(
                    username = from.username ?: existingUser.username,
                    firstName = from.firstName ?: existingUser.firstName,
                    lastSeenTime = System.currentTimeMillis(),
                    messageCount = existingUser.messageCount + 1
                )
            )
        }

        botDao.insertLog(
            LogEntryEntity(
                level = "TG",
                tag = "MSG",
                message = "From ${from.firstName} ($userId): ${if (text.isNotEmpty()) text else "[Media/Doc]"}"
            )
        )

        // Check if user is in an active multi-step flow
        val currentStep = userSteps[userId]
        if (currentStep != null) {
            handleStepInput(userId, chatId, text, message, currentStep)
            return
        }

        // Check document upload
        if (message.document != null) {
            handleDocumentUpload(message)
            return
        }

        // Commands and Reply Keyboard buttons
        when {
            text == "/start" || text == "/help" -> sendWelcome(userId, chatId, from.firstName ?: "User", from.username)
            text == "/status" -> sendStatistics(userId, chatId)
            text == "/ping" -> handlePing(chatId, message.messageId)
            text == "📢 𝐔ᴩᴅᴀᴛᴇꜱ 𝐂ʜᴀɴɴᴇʟ" || text == "/updateschannel" -> sendUpdatesChannel(chatId)
            text == "📤 𝐔ᴩʟᴏᴀᴅ 𝐅ɪʟᴇ" || text == "/uploadfile" -> sendUploadInstructions(userId, chatId)
            text == "📂 𝐂ʜᴇᴄᴋ 𝐅ɪʟᴇꜱ" || text == "/checkfiles" -> sendCheckFiles(userId, chatId)
            text == "⚡ 𝐁ᴏᴛ 𝐒ᴩᴇᴇᴅ" || text == "/botspeed" -> sendBotSpeed(userId, chatId)
            text == "📊 𝐒ᴛᴀᴛɪꜱᴛɪᴄꜱ" || text == "/statistics" -> sendStatistics(userId, chatId)
            text == "📞 𝐂ᴏɴᴛᴀᴄᴛ 𝐎ᴡɴᴇʀ" || text == "/contactowner" -> sendContactOwner(chatId)
            text == "💳 𝐒ᴜʙꜱᴄʀɪᴩᴛɪᴏɴꜱ" || text == "/subscriptions" -> sendSubscriptionMenu(userId, chatId)
            text == "📢 𝐁ʀᴏᴀᴅᴄᴀꜱᴛ" || text == "/broadcast" -> initBroadcast(userId, chatId)
            text == "🔒 𝐋ᴏᴄᴋ 𝐁ᴏᴛ" || text == "/lockbot" -> toggleLockBot(userId, chatId)
            text == "🟢 𝐑ᴜɴɴɪɴɢ 𝐀ʟʟ 𝐂ᴏᴅᴇ" || text == "/runningallcode" -> runAllUserScripts(userId, chatId)
            text == "⏳ 𝐏ᴇɴᴅɪɴɢ 𝐅ɪʟᴇꜱ" || text == "/pendingfiles" -> sendPendingFiles(userId, chatId)
            text == "👑 𝐀ᴅᴍɪɴ 𝐏ᴀɴᴇʟ" || text == "/adminpanel" -> sendAdminPanel(userId, chatId)
            else -> {
                // If bot locked and not admin
                if (preferences.isBotLocked && !isAdmin(userId)) {
                    apiClient.sendMessage(chatId, "⚠️ 𝐁ᴏᴛ ʟᴏᴄᴋᴇᴅ ʙʏ ᴀᴅᴍɪɴ. 𝐓ʀʏ ʟᴀᴛᴇʀ.")
                    return
                }
            }
        }
    }

    private suspend fun handleStepInput(
        userId: Long,
        chatId: Long,
        text: String,
        message: TelegramMessage,
        step: String
    ) {
        if (text.equals("/cancel", ignoreCase = true)) {
            userSteps.remove(userId)
            broadcastDrafts.remove(userId)
            apiClient.sendMessage(chatId, "𝐂ᴀɴᴄᴇʟʟᴇᴅ.")
            return
        }

        when (step) {
            "WAITING_BROADCAST_MESSAGE" -> {
                userSteps.remove(userId)
                broadcastDrafts[userId] = message
                val preview = text.take(500)
                val allUsers = botDao.getAllUsers()
                val markup = InlineKeyboardMarkup(
                    listOf(
                        listOf(
                            InlineKeyboardButton("✅ 𝐂ᴏɴꜰɪʀᴍ & 𝐒ᴇɴᴅ", callbackData = "confirm_broadcast"),
                            InlineKeyboardButton("❌ 𝐂ᴀɴᴄᴇʟ", callbackData = "cancel_broadcast")
                        )
                    )
                )
                apiClient.sendMessage(
                    chatId = chatId,
                    text = "⚠️ 𝐂ᴏɴꜰɪʀᴍ 𝐁ʀᴏᴀᴅᴄᴀꜱᴛ:\n\n```\n$preview\n```\n𝐓ᴏ **${allUsers.size}** ᴜꜱᴇʀꜱ. 𝐒ᴜʀᴇ?",
                    replyMarkup = markup
                )
            }
            "WAITING_ADD_ADMIN_ID" -> {
                userSteps.remove(userId)
                val targetId = text.toLongOrNull()
                if (targetId == null || targetId <= 0) {
                    apiClient.sendMessage(chatId, "⚠️ 𝐈ɴᴠᴀʟɪᴅ 𝐈𝐃.")
                    return
                }
                if (targetId == preferences.ownerId) {
                    apiClient.sendMessage(chatId, "⚠️ 𝐀ʟʀᴇᴀᴅʏ 𝐎ᴡɴᴇʀ.")
                    return
                }
                botDao.insertAdmin(AdminEntity(userId = targetId, isOwner = false))
                apiClient.sendMessage(chatId, "✅ `$targetId` ᴩʀᴏᴍᴏᴛᴇᴅ ᴛᴏ 𝐀ᴅᴍɪɴ.")
                apiClient.sendMessage(targetId, "🎉 𝐘ᴏᴜ ᴀʀᴇ ɴᴏᴡ 𝐀ᴅᴍɪɴ.")
            }
            "WAITING_REMOVE_ADMIN_ID" -> {
                userSteps.remove(userId)
                val targetId = text.toLongOrNull()
                if (targetId == null || targetId <= 0) {
                    apiClient.sendMessage(chatId, "⚠️ 𝐈ɴᴠᴀʟɪᴅ 𝐈𝐃.")
                    return
                }
                if (targetId == preferences.ownerId) {
                    apiClient.sendMessage(chatId, "⚠️ 𝐂ᴀɴɴᴏᴛ ʀᴇᴍᴏᴠᴇ 𝐎ᴡɴᴇʀ.")
                    return
                }
                val removed = botDao.deleteAdmin(targetId)
                if (removed > 0) {
                    apiClient.sendMessage(chatId, "✅ 𝐀ᴅᴍɪɴ `$targetId` ʀᴇᴍᴏᴠᴇᴅ.")
                    apiClient.sendMessage(targetId, "ℹ️ 𝐍ᴏ ʟᴏɴɢᴇʀ 𝐀ᴅᴍɪɴ.")
                } else {
                    apiClient.sendMessage(chatId, "⚠️ 𝐍ᴏᴛ 𝐀ᴅᴍɪɴ.")
                }
            }
            "WAITING_ADD_SUB" -> {
                userSteps.remove(userId)
                val parts = text.split(" ")
                if (parts.size != 2) {
                    apiClient.sendMessage(chatId, "⚠️ 𝐅ᴏʀᴍᴀᴛ: `ID days` (e.g., `12345678 30`)")
                    return
                }
                val subUserId = parts[0].toLongOrNull()
                val days = parts[1].toIntOrNull()
                if (subUserId == null || days == null || days <= 0) {
                    apiClient.sendMessage(chatId, "⚠️ 𝐈ɴᴠᴀʟɪᴅ ᴠᴀʟᴜᴇꜱ.")
                    return
                }
                val existing = botDao.getSubscription(subUserId)
                val start = if (existing != null && existing.expiryTimestamp > System.currentTimeMillis()) {
                    existing.expiryTimestamp
                } else {
                    System.currentTimeMillis()
                }
                val newExpiry = start + (days.toLong() * 24 * 60 * 60 * 1000)
                botDao.insertOrUpdateSubscription(SubscriptionEntity(userId = subUserId, expiryTimestamp = newExpiry))
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(newExpiry))
                apiClient.sendMessage(chatId, "✅ 𝐒ᴜʙ ꜰᴏʀ `$subUserId` ʙʏ $days ᴅᴀʏꜱ.\n𝐄xᴩɪʀʏ: $dateStr")
                apiClient.sendMessage(subUserId, "🎉 𝐒ᴜʙ ᴀᴄᴛɪᴠᴀᴛᴇᴅ ʙʏ $days ᴅᴀʏꜱ! 𝐄xᴩɪʀᴇꜱ: $dateStr.")
            }
            "WAITING_REMOVE_SUB" -> {
                userSteps.remove(userId)
                val targetId = text.toLongOrNull()
                if (targetId == null) {
                    apiClient.sendMessage(chatId, "⚠️ 𝐈ɴᴠᴀʟɪᴅ 𝐈𝐃.")
                    return
                }
                botDao.deleteSubscription(targetId)
                apiClient.sendMessage(chatId, "✅ 𝐒ᴜʙ ʀᴇᴍᴏᴠᴇᴅ.")
                apiClient.sendMessage(targetId, "ℹ️ 𝐒ᴜʙꜱᴄʀɪᴩᴛɪᴏɴ ʀᴇᴍᴏᴠᴇᴅ.")
            }
            "WAITING_CHECK_SUB" -> {
                userSteps.remove(userId)
                val targetId = text.toLongOrNull()
                if (targetId == null) {
                    apiClient.sendMessage(chatId, "⚠️ 𝐈ɴᴠᴀʟɪᴅ 𝐈𝐃.")
                    return
                }
                val sub = botDao.getSubscription(targetId)
                if (sub != null && sub.expiryTimestamp > System.currentTimeMillis()) {
                    val days = ((sub.expiryTimestamp - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)).toInt()
                    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(sub.expiryTimestamp))
                    apiClient.sendMessage(chatId, "✅ 𝐀ᴄᴛɪᴠᴇ ꜱᴜʙ.\n𝐄xᴩɪʀᴇꜱ: $dateStr ($days ᴅᴀʏꜱ ʟᴇꜰᴛ).")
                } else {
                    apiClient.sendMessage(chatId, "ℹ️ 𝐍ᴏ ᴀᴄᴛɪᴠᴇ ꜱᴜʙ ʀᴇᴄᴏʀᴅ.")
                }
            }
        }
    }

    private suspend fun handleDocumentUpload(message: TelegramMessage) {
        val from = message.from ?: return
        val userId = from.id
        val chatId = message.chat.id
        val doc = message.document ?: return

        if (preferences.isBotLocked && !isAdmin(userId)) {
            apiClient.sendMessage(chatId, "⚠️ 𝐁ᴏᴛ ʟᴏᴄᴋᴇᴅ, ᴄᴀɴɴᴏᴛ ᴀᴄᴄᴇᴩᴛ ꜰɪʟᴇꜱ.")
            return
        }

        val fileLimit = getUserFileLimit(userId)
        val currentCount = botDao.getFilesByUserId(userId).size
        if (currentCount >= fileLimit) {
            val limitStr = if (fileLimit >= 999999) "Unlimited" else fileLimit.toString()
            apiClient.sendMessage(chatId, "⚠️ 𝐅ɪʟᴇ ʟɪᴍɪᴛ ($currentCount/$limitStr) ʀᴇᴀᴄʜᴇᴅ.")
            return
        }

        val rawFileName = doc.fileName ?: "script.py"
        val ext = rawFileName.substringAfterLast(".", "").lowercase()
        if (ext !in listOf("py", "js", "zip")) {
            apiClient.sendMessage(chatId, "⚠️ 𝐎ɴʟʏ `.py`, `.js`, `.zip` ᴀʟʟᴏᴡᴇᴅ.")
            return
        }

        // Download document bytes from Telegram
        val waitMsg = apiClient.sendMessage(chatId, "⏳ 𝐃ᴏᴡɴʟᴏᴀᴅɪɴɢ `$rawFileName`...")
        val tgFileResult = apiClient.getFile(doc.fileId)
        val tgFile = tgFileResult.getOrNull()
        if (tgFile == null || tgFile.filePath.isNullOrEmpty()) {
            apiClient.sendMessage(chatId, "❌ Failed to retrieve file from Telegram.")
            return
        }

        val bytesResult = apiClient.downloadFileBytes(tgFile.filePath)
        val bytes = bytesResult.getOrNull()
        if (bytes == null || bytes.isEmpty()) {
            apiClient.sendMessage(chatId, "❌ Failed to download file bytes.")
            return
        }

        val userFolder = scriptRunner.getUserFolder(userId)

        if (ext == "zip") {
            val (mainScript, fileType, extractLog) = scriptRunner.extractZipFile(userId, bytes, rawFileName)
            if (mainScript == null) {
                apiClient.sendMessage(chatId, "❌ 𝐍ᴏ `.py` ᴏʀ `.js` ꜱᴄʀɪᴩᴛ ꜰᴏᴜɴᴅ ɪɴ 𝐙𝐈𝐏!")
                return
            }

            val targetFile = File(userFolder, mainScript)
            val scriptEntity = HostedFileEntity(
                userId = userId,
                fileName = mainScript,
                fileType = fileType ?: "py",
                filePath = targetFile.absolutePath,
                scriptContent = if (targetFile.exists()) targetFile.readText() else "",
                status = if (preferences.adminApprovalRequired && !isAdmin(userId)) "PENDING" else "STOPPED"
            )
            botDao.insertOrUpdateFile(scriptEntity)

            if (preferences.adminApprovalRequired && !isAdmin(userId)) {
                apiClient.sendMessage(
                    chatId,
                    "✅ 𝐙𝐈𝐏 𝐄xᴛʀᴀᴄᴛᴇᴅ!\n📁 𝐌ᴀɪɴ: `$mainScript` ($fileType)\n\n⚠️ **𝐀ᴅᴍɪɴ 𝐀ᴩᴩʀᴏᴠᴀʟ 𝐑ᴇQᴜɪʀᴇᴅ** ʙᴇꜰᴏʀᴇ ʜᴏꜱᴛɪɴɢ."
                )
                notifyAdminsForApproval(userId, mainScript, fileType ?: "py", from.firstName ?: "User", from.username)
            } else {
                apiClient.sendMessage(chatId, "✅ 𝐙𝐈𝐏 𝐄xᴛʀᴀᴄᴛᴇᴅ!\n📁 𝐒ᴛᴀʀᴛɪɴɢ `$mainScript`...")
                scriptRunner.startScript(scriptEntity)
            }
        } else {
            val targetFile = File(userFolder, rawFileName)
            targetFile.writeBytes(bytes)
            val scriptContent = String(bytes)

            val isPending = preferences.adminApprovalRequired && !isAdmin(userId)
            val scriptEntity = HostedFileEntity(
                userId = userId,
                fileName = rawFileName,
                fileType = ext,
                filePath = targetFile.absolutePath,
                scriptContent = scriptContent,
                status = if (isPending) "PENDING" else "STOPPED"
            )
            botDao.insertOrUpdateFile(scriptEntity)

            if (isPending) {
                apiClient.sendMessage(
                    chatId,
                    "⏳ 𝐅ɪʟᴇ `$rawFileName` ᴜᴩʟᴏᴀᴅᴇᴅ!\n\n⚠️ **𝐀ᴅᴍɪɴ 𝐀ᴩᴩʀᴏᴠᴀʟ 𝐑ᴇQᴜɪʀᴇᴅ** ʙᴇꜰᴏʀᴇ ʜᴏꜱᴛɪɴɢ.\n𝐘ᴏᴜ'ʟʟ ʙᴇ ɴᴏᴛɪꜰɪᴇᴅ ᴏɴᴄᴇ ᴀᴅᴍɪɴ ᴀᴩᴩʀᴏᴠᴇꜱ/ʀᴇᴊᴇᴄᴛꜱ."
                )
                notifyAdminsForApproval(userId, rawFileName, ext, from.firstName ?: "User", from.username)
            } else {
                apiClient.sendMessage(chatId, "✅ 𝐒ᴛᴀʀᴛɪɴɢ `$rawFileName`...")
                scriptRunner.startScript(scriptEntity)
            }
        }
    }

    private suspend fun notifyAdminsForApproval(
        userId: Long,
        fileName: String,
        fileType: String,
        name: String,
        username: String?
    ) {
        val adminList = getAdminIds()
        val text = "🔔 **𝐍𝐄𝐖 𝐅𝐈𝐋𝐄 𝐀𝐏𝐏𝐑𝐎𝐕𝐀𝐋 𝐑𝐄𝐐𝐔𝐄𝐒𝐓**\n\n" +
                "👤 𝐔ꜱᴇʀ: $name (@${username ?: "NoUsername"})\n" +
                "🆔 𝐔ꜱᴇʀ 𝐈𝐃: `$userId`\n" +
                "📁 𝐅ɪʟᴇ: `$fileName`\n" +
                "📝 𝐓ʏᴩᴇ: `$fileType`\n" +
                "⏰ 𝐓ɪᴍᴇ: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n\n" +
                "⏳ 𝐖ᴀɪᴛɪɴɢ ꜰᴏʀ ᴀᴩᴩʀᴏᴠᴀʟ..."

        val markup = InlineKeyboardMarkup(
            listOf(
                listOf(
                    InlineKeyboardButton("✅ 𝐀ᴩᴩʀᴏᴠᴇ & 𝐑ᴜɴ", callbackData = "approve_${userId}_$fileName"),
                    InlineKeyboardButton("❌ 𝐑ᴇᴊᴇᴄᴛ", callbackData = "reject_${userId}_$fileName")
                )
            )
        )

        for (adminId in adminList) {
            apiClient.sendMessage(adminId, text, replyMarkup = markup)
        }
    }

    private suspend fun handleCallbackQuery(callback: TelegramCallbackQuery) {
        val user = callback.from
        val userId = user.id
        val data = callback.data ?: return
        val message = callback.message
        val chatId = message?.chat?.id ?: userId
        val messageId = message?.messageId ?: 0

        botDao.insertLog(
            LogEntryEntity(
                level = "TG",
                tag = "CALLBACK",
                message = "From ${user.firstName} ($userId): $data"
            )
        )

        if (preferences.isBotLocked && !isAdmin(userId) && data !in listOf("back_to_main", "speed", "stats", "updates_channel")) {
            apiClient.answerCallbackQuery(callback.id, "⚠️ 𝐁ᴏᴛ ʟᴏᴄᴋᴇᴅ ʙʏ ᴀᴅᴍɪɴ.", showAlert = true)
            return
        }

        when {
            data == "updates_channel" -> {
                apiClient.answerCallbackQuery(callback.id)
                sendUpdatesChannel(chatId)
            }
            data == "upload" -> {
                apiClient.answerCallbackQuery(callback.id)
                sendUploadInstructions(userId, chatId)
            }
            data == "check_files" -> {
                apiClient.answerCallbackQuery(callback.id)
                sendCheckFiles(userId, chatId, messageId)
            }
            data == "speed" -> {
                apiClient.answerCallbackQuery(callback.id)
                sendBotSpeed(userId, chatId, messageId)
            }
            data == "stats" -> {
                apiClient.answerCallbackQuery(callback.id)
                sendStatistics(userId, chatId)
            }
            data == "back_to_main" -> {
                apiClient.answerCallbackQuery(callback.id)
                sendWelcome(userId, chatId, user.firstName ?: "User", user.username, messageId)
            }
            data.startsWith("file_") -> {
                // file_{userId}_{fileName}
                apiClient.answerCallbackQuery(callback.id)
                val parts = data.split("_", limit = 3)
                if (parts.size == 3) {
                    val scriptOwnerId = parts[1].toLongOrNull() ?: userId
                    val fileName = parts[2]
                    sendFileControls(userId, scriptOwnerId, fileName, chatId, messageId)
                }
            }
            data.startsWith("start_") -> {
                val parts = data.split("_", limit = 3)
                if (parts.size == 3) {
                    val scriptOwnerId = parts[1].toLongOrNull() ?: userId
                    val fileName = parts[2]
                    val fileEntity = botDao.getFile(scriptOwnerId, fileName)
                    if (fileEntity != null) {
                        apiClient.answerCallbackQuery(callback.id, "⏳ 𝐒ᴛᴀʀᴛɪɴɢ $fileName...")
                        scriptRunner.startScript(fileEntity)
                        delay(500)
                        sendFileControls(userId, scriptOwnerId, fileName, chatId, messageId)
                    } else {
                        apiClient.answerCallbackQuery(callback.id, "⚠️ 𝐅ɪʟᴇ ɴᴏᴛ ꜰᴏᴜɴᴅ.", showAlert = true)
                    }
                }
            }
            data.startsWith("stop_") -> {
                val parts = data.split("_", limit = 3)
                if (parts.size == 3) {
                    val scriptOwnerId = parts[1].toLongOrNull() ?: userId
                    val fileName = parts[2]
                    apiClient.answerCallbackQuery(callback.id, "⏳ 𝐒ᴛᴏᴩᴩɪɴɢ $fileName...")
                    scriptRunner.stopScript(scriptOwnerId, fileName)
                    delay(500)
                    sendFileControls(userId, scriptOwnerId, fileName, chatId, messageId)
                }
            }
            data.startsWith("restart_") -> {
                val parts = data.split("_", limit = 3)
                if (parts.size == 3) {
                    val scriptOwnerId = parts[1].toLongOrNull() ?: userId
                    val fileName = parts[2]
                    val fileEntity = botDao.getFile(scriptOwnerId, fileName)
                    if (fileEntity != null) {
                        apiClient.answerCallbackQuery(callback.id, "⏳ 𝐑ᴇꜱᴛᴀʀᴛɪɴɢ $fileName...")
                        scriptRunner.restartScript(fileEntity)
                        delay(500)
                        sendFileControls(userId, scriptOwnerId, fileName, chatId, messageId)
                    }
                }
            }
            data.startsWith("delete_") -> {
                val parts = data.split("_", limit = 3)
                if (parts.size == 3) {
                    val scriptOwnerId = parts[1].toLongOrNull() ?: userId
                    val fileName = parts[2]
                    apiClient.answerCallbackQuery(callback.id, "🗑️ 𝐃ᴇʟᴇᴛɪɴɢ $fileName...")
                    scriptRunner.deleteScript(scriptOwnerId, fileName)
                    apiClient.editMessageText(chatId, messageId, "🗑️ `$fileName` 𝐃ᴇʟᴇᴛᴇᴅ!")
                }
            }
            data.startsWith("logs_") -> {
                val parts = data.split("_", limit = 3)
                if (parts.size == 3) {
                    val scriptOwnerId = parts[1].toLongOrNull() ?: userId
                    val fileName = parts[2]
                    apiClient.answerCallbackQuery(callback.id)
                    val logs = scriptRunner.getLogs(scriptOwnerId, fileName)
                    val formatted = logs.takeLast(3500)
                    apiClient.sendMessage(chatId, "📜 𝐋ᴏɢꜱ ꜰᴏʀ `$fileName`:\n```\n$formatted\n```")
                }
            }
            data.startsWith("approve_") -> {
                if (!isAdmin(userId)) {
                    apiClient.answerCallbackQuery(callback.id, "⚠️ 𝐀ᴅᴍɪɴ ᴏɴʟʏ.", showAlert = true)
                    return
                }
                val parts = data.split("_", limit = 3)
                if (parts.size == 3) {
                    val targetUserId = parts[1].toLongOrNull() ?: return
                    val fileName = parts[2]
                    val fileEntity = botDao.getFile(targetUserId, fileName)
                    if (fileEntity != null) {
                        apiClient.answerCallbackQuery(callback.id, "✅ 𝐀ᴩᴩʀᴏᴠᴇᴅ!")
                        botDao.updateFileStatus(targetUserId, fileName, "STOPPED", 0, null)
                        scriptRunner.startScript(fileEntity)
                        apiClient.sendMessage(
                            targetUserId,
                            "✅ 𝐘ᴏᴜʀ ꜰɪʟᴇ `$fileName` ᴡᴀꜱ **𝐀𝐏𝐏𝐑𝐎𝐕𝐄𝐃** ʙʏ ᴀᴅᴍɪɴ!\n🚀 𝐒ᴛᴀʀᴛɪɴɢ ɴᴏᴡ..."
                        )
                        apiClient.editMessageText(
                            chatId,
                            messageId,
                            "✅ **𝐀𝐏𝐏𝐑𝐎𝐕𝐄𝐃**\n\n👤 𝐔ꜱᴇʀ: `$targetUserId`\n📁 𝐅ɪʟᴇ: `$fileName`\n👮 𝐁ʏ: `$userId`"
                        )
                    }
                }
            }
            data.startsWith("reject_") -> {
                if (!isAdmin(userId)) {
                    apiClient.answerCallbackQuery(callback.id, "⚠️ 𝐀ᴅᴍɪɴ ᴏɴʟʏ.", showAlert = true)
                    return
                }
                val parts = data.split("_", limit = 3)
                if (parts.size == 3) {
                    val targetUserId = parts[1].toLongOrNull() ?: return
                    val fileName = parts[2]
                    apiClient.answerCallbackQuery(callback.id, "❌ 𝐑ᴇᴊᴇᴄᴛᴇᴅ!")
                    scriptRunner.deleteScript(targetUserId, fileName)
                    apiClient.sendMessage(targetUserId, "❌ 𝐘ᴏᴜʀ ꜰɪʟᴇ `$fileName` ᴡᴀꜱ **𝐑𝐄𝐉𝐄𝐂𝐓𝐄𝐃** ʙʏ ᴀᴅᴍɪɴ.")
                    apiClient.editMessageText(
                        chatId,
                        messageId,
                        "❌ **𝐑𝐄𝐉𝐄𝐂𝐓𝐄𝐃**\n\n👤 𝐔ꜱᴇʀ: `$targetUserId`\n📁 𝐅ɪʟᴇ: `$fileName`\n👮 𝐁ʏ: `$userId`"
                    )
                }
            }
            data == "confirm_broadcast" -> {
                apiClient.answerCallbackQuery(callback.id, "🚀 𝐒ᴛᴀʀᴛɪɴɢ ʙʀᴏᴀᴅᴄᴀꜱᴛ...")
                val draftMsg = broadcastDrafts.remove(userId)
                if (draftMsg != null) {
                    executeBroadcast(draftMsg, chatId)
                }
            }
            data == "cancel_broadcast" -> {
                apiClient.answerCallbackQuery(callback.id, "𝐂ᴀɴᴄᴇʟʟᴇᴅ.")
                broadcastDrafts.remove(userId)
                apiClient.deleteMessage(chatId, messageId)
            }
            data == "subscription" -> sendSubscriptionMenu(userId, chatId, messageId)
            data == "add_subscription" -> {
                apiClient.answerCallbackQuery(callback.id)
                userSteps[userId] = "WAITING_ADD_SUB"
                apiClient.sendMessage(chatId, "💳 𝐄ɴᴛᴇʀ 𝐔ꜱᴇʀ 𝐈𝐃 & ᴅᴀʏꜱ (ᴇ.ɢ., `12345678 30`).\n/cancel ᴛᴏ ᴀʙᴏʀᴛ.")
            }
            data == "remove_subscription" -> {
                apiClient.answerCallbackQuery(callback.id)
                userSteps[userId] = "WAITING_REMOVE_SUB"
                apiClient.sendMessage(chatId, "💳 𝐄ɴᴛᴇʀ 𝐔ꜱᴇʀ 𝐈𝐃 ᴛᴏ ʀᴇᴍᴏᴠᴇ ꜱᴜʙ.\n/cancel ᴛᴏ ᴀʙᴏʀᴛ.")
            }
            data == "check_subscription" -> {
                apiClient.answerCallbackQuery(callback.id)
                userSteps[userId] = "WAITING_CHECK_SUB"
                apiClient.sendMessage(chatId, "💳 𝐄ɴᴛᴇʀ 𝐔ꜱᴇʀ 𝐈𝐃 ᴛᴏ ᴄʜᴇᴄᴋ ꜱᴜʙ.\n/cancel ᴛᴏ ᴀʙᴏʀᴛ.")
            }
            data == "admin_panel" -> sendAdminPanel(userId, chatId, messageId)
            data == "add_admin" -> {
                if (userId != preferences.ownerId) {
                    apiClient.answerCallbackQuery(callback.id, "⚠️ 𝐎ᴡɴᴇʀ ᴩᴇʀᴍɪꜱꜱɪᴏɴꜱ ʀᴇQᴜɪʀᴇᴅ.", showAlert = true)
                    return
                }
                apiClient.answerCallbackQuery(callback.id)
                userSteps[userId] = "WAITING_ADD_ADMIN_ID"
                apiClient.sendMessage(chatId, "👑 𝐄ɴᴛᴇʀ 𝐔ꜱᴇʀ 𝐈𝐃 ᴛᴏ ᴩʀᴏᴍᴏᴛᴇ.\n/cancel ᴛᴏ ᴀʙᴏʀᴛ.")
            }
            data == "remove_admin" -> {
                if (userId != preferences.ownerId) {
                    apiClient.answerCallbackQuery(callback.id, "⚠️ 𝐎ᴡɴᴇʀ ᴩᴇʀᴍɪꜱꜱɪᴏɴꜱ ʀᴇQᴜɪʀᴇᴅ.", showAlert = true)
                    return
                }
                apiClient.answerCallbackQuery(callback.id)
                userSteps[userId] = "WAITING_REMOVE_ADMIN_ID"
                apiClient.sendMessage(chatId, "👑 𝐄ɴᴛᴇʀ 𝐀ᴅᴍɪɴ 𝐈𝐃 ᴛᴏ ʀᴇᴍᴏᴠᴇ.\n/cancel ᴛᴏ ᴀʙᴏʀᴛ.")
            }
            data == "list_admins" -> {
                apiClient.answerCallbackQuery(callback.id)
                val admins = botDao.getAllAdmins()
                val sb = StringBuilder("👑 𝐂ᴜʀʀᴇɴᴛ 𝐀ᴅᴍɪɴꜱ:\n\n")
                sb.append("- `${preferences.ownerId}` (Owner)\n")
                for (a in admins) {
                    if (a.userId != preferences.ownerId) {
                        sb.append("- `${a.userId}`\n")
                    }
                }
                apiClient.editMessageText(chatId, messageId, sb.toString(), createAdminPanelMarkup())
            }
            data == "pending_files" -> sendPendingFiles(userId, chatId, messageId)
            data == "run_all_scripts" -> {
                apiClient.answerCallbackQuery(callback.id)
                runAllUserScripts(userId, chatId)
            }
            data == "lock_bot" || data == "unlock_bot" -> {
                toggleLockBot(userId, chatId)
                apiClient.answerCallbackQuery(callback.id, if (preferences.isBotLocked) "🔒 Locked" else "🔓 Unlocked")
            }
        }
    }

    private suspend fun sendWelcome(
        userId: Long,
        chatId: Long,
        firstName: String,
        username: String?,
        editMessageId: Long? = null
    ) {
        val fileLimit = getUserFileLimit(userId)
        val files = botDao.getFilesByUserId(userId)
        val limitStr = if (fileLimit >= 999999) "Unlimited" else fileLimit.toString()

        val sub = botDao.getSubscription(userId)
        val isSubActive = sub != null && sub.expiryTimestamp > System.currentTimeMillis()

        val userStatus = when {
            userId == preferences.ownerId -> "👑 𝐎ᴡɴᴇʀ"
            isAdmin(userId) -> "🛡️ 𝐀ᴅᴍɪɴ"
            isSubActive -> "⭐ 𝐏ʀᴇᴍɪᴜᴍ"
            else -> "🆓 𝐅ʀᴇᴇ 𝐔ꜱᴇʀ"
        }

        var expiryInfo = ""
        if (isSubActive && sub != null) {
            val daysLeft = ((sub.expiryTimestamp - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)).toInt()
            expiryInfo = "\n⏳ 𝐒ᴜʙꜱᴄʀɪᴩᴛɪᴏɴ ᴇxᴩɪʀᴇꜱ ɪɴ: $daysLeft ᴅᴀʏꜱ"
        }

        val welcomeText = "〽️ 𝐖ᴇʟᴄᴏᴍᴇ, $firstName!\n\n" +
                "🆔 𝐘ᴏᴜʀ 𝐔ꜱᴇʀ 𝐈𝐃: `$userId`\n" +
                "✳️ 𝐔ꜱᴇʀɴᴀᴍᴇ: `@${username ?: "Not set"}`\n" +
                "🔰 𝐘ᴏᴜʀ 𝐒ᴛᴀᴛᴜꜱ: $userStatus$expiryInfo\n" +
                "📁 𝐅ɪʟᴇꜱ 𝐔ᴩʟᴏᴀᴅᴇᴅ: ${files.size} / $limitStr\n\n" +
                "🤖 𝐇ᴏꜱᴛ & ʀᴜɴ 𝐏ʏᴛʜᴏɴ (`.py`) ᴏʀ 𝐉𝐒 (`.js`) ꜱᴄʀɪᴩᴛꜱ.\n" +
                "   𝐔ᴩʟᴏᴀᴅ ꜱɪɴɢʟᴇ ꜱᴄʀɪᴩᴛꜱ ᴏʀ `.zip` ᴀʀᴄʜɪᴠᴇꜱ.\n\n" +
                "👇 𝐔ꜱᴇ ʙᴜᴛᴛᴏɴꜱ ᴏʀ ᴛʏᴩᴇ ᴄᴏᴍᴍᴀɴᴅꜱ."

        val inlineMarkup = createMainMenuInline(userId)

        if (editMessageId != null) {
            apiClient.editMessageText(chatId, editMessageId, welcomeText, inlineMarkup)
        } else {
            val replyKeyboard = createReplyKeyboard(userId)
            apiClient.sendMessage(chatId, welcomeText, replyMarkup = replyKeyboard)
            apiClient.sendMessage(chatId, "🎛️ **𝐂ᴏɴᴛʀᴏʟ 𝐏ᴀɴᴇʟ:**", replyMarkup = inlineMarkup)
        }
    }

    private suspend fun sendUpdatesChannel(chatId: Long) {
        val channelName = preferences.updateChannel.removePrefix("@")
        val link = "https://t.me/$channelName"
        val text = "📢 **𝐎ꜰꜰɪᴄɪᴀʟ 𝐔ᴩᴅᴀᴛᴇꜱ 𝐂ʜᴀɴɴᴇʟ**\n\n" +
                "🔗 **𝐋ɪɴᴋ:** $link\n" +
                "📛 **𝐔ꜱᴇʀɴᴀᴍᴇ:** `@$channelName`\n\n" +
                "👆 𝐔ᴩᴀʀ ᴡᴀʟᴇ ʟɪɴᴋ ᴩᴇ ᴄʟɪᴄᴋ ᴋᴀʀᴏ ʏᴀ ᴄᴏᴩʏ ᴋᴀʀᴋᴇ 𝐓ᴇʟᴇɢʀᴀᴍ ᴍᴇ ᴏᴩᴇɴ ᴋᴀʀᴏ.\n\n" +
                "📌 𝐘ᴀʜᴀɴ ᴀᴀᴩᴋᴏ ᴍɪʟᴇɴɢᴇ:\n" +
                "• 𝐁ᴏᴛ ᴋᴇ ɴᴀʏᴇ ꜰᴇᴀᴛᴜʀᴇꜱ\n" +
                "• 𝐁ᴜɢ ꜰɪxᴇꜱ & ᴜᴩᴅᴀᴛᴇꜱ\n" +
                "• 𝐒ᴜʙꜱᴄʀɪᴩᴛɪᴏɴ ᴩʟᴀɴꜱ\n" +
                "• 𝐂ᴏɴᴛᴀᴄᴛ ᴅᴇᴛᴀɪʟꜱ"

        val markup = InlineKeyboardMarkup(
            listOf(listOf(InlineKeyboardButton("📢 𝐎ᴩᴇɴ 𝐂ʜᴀɴɴᴇʟ", url = link)))
        )
        apiClient.sendMessage(chatId, text, replyMarkup = markup)
    }

    private suspend fun sendUploadInstructions(userId: Long, chatId: Long) {
        if (preferences.isBotLocked && !isAdmin(userId)) {
            apiClient.sendMessage(chatId, "⚠️ 𝐁ᴏᴛ ʟᴏᴄᴋᴇᴅ, ᴄᴀɴɴᴏᴛ ᴀᴄᴄᴇᴩᴛ ꜰɪʟᴇꜱ.")
            return
        }
        val fileLimit = getUserFileLimit(userId)
        val files = botDao.getFilesByUserId(userId)
        if (files.size >= fileLimit) {
            val limitStr = if (fileLimit >= 999999) "Unlimited" else fileLimit.toString()
            apiClient.sendMessage(chatId, "⚠️ 𝐅ɪʟᴇ ʟɪᴍɪᴛ (${files.size}/$limitStr) ʀᴇᴀᴄʜᴇᴅ.")
            return
        }
        apiClient.sendMessage(chatId, "📤 𝐒ᴇɴᴅ ʏᴏᴜʀ 𝐏ʏᴛʜᴏɴ (`.py`), 𝐉𝐒 (`.js`), ᴏʀ 𝐙𝐈𝐏 (`.zip`) ꜰɪʟᴇ.\n\n⚠️ 𝐍ᴏᴛᴇ: 𝐀ᴅᴍɪɴ ᴀᴩᴩʀᴏᴠᴀʟ ʀᴇQᴜɪʀᴇᴅ.")
    }

    private suspend fun sendCheckFiles(userId: Long, chatId: Long, editMessageId: Long? = null) {
        val userFiles = botDao.getFilesByUserId(userId).filter { it.status != "PENDING" }
        val pending = botDao.getFilesByUserId(userId).filter { it.status == "PENDING" }

        if (userFiles.isEmpty() && pending.isEmpty()) {
            val text = "📂 𝐘ᴏᴜʀ ꜰɪʟᴇꜱ:\n\n(𝐍ᴏ ꜰɪʟᴇꜱ ᴜᴩʟᴏᴀᴅᴇᴅ ʏᴇᴛ)"
            val markup = InlineKeyboardMarkup(listOf(listOf(InlineKeyboardButton("🔙 𝐁ᴀᴄᴋ ᴛᴏ 𝐌ᴀɪɴ", callbackData = "back_to_main"))))
            if (editMessageId != null) apiClient.editMessageText(chatId, editMessageId, text, markup)
            else apiClient.sendMessage(chatId, text, replyMarkup = markup)
            return
        }

        val sb = StringBuilder("📂 **𝐘ᴏᴜʀ 𝐅ɪʟᴇꜱ:**\n\n")
        if (pending.isNotEmpty()) {
            sb.append("⏳ **𝐏ᴇɴᴅɪɴɢ 𝐀ᴩᴩʀᴏᴠᴀʟ:**\n")
            for (p in pending) {
                sb.append("  • `${p.fileName}` (${p.fileType}) - ⏳ 𝐖ᴀɪᴛɪɴɢ\n")
            }
            sb.append("\n")
        }

        val rows = mutableListOf<List<InlineKeyboardButton>>()
        if (userFiles.isNotEmpty()) {
            sb.append("✅ **𝐀ᴄᴛɪᴠᴇ 𝐅ɪʟᴇꜱ:**\n")
            for (f in userFiles) {
                val isRun = scriptRunner.isRunning(userId, f.fileName)
                val statusIcon = if (isRun) "🟢 𝐑ᴜɴɴɪɴɢ" else "🔴 𝐒ᴛᴏᴩᴩᴇᴅ"
                rows.add(
                    listOf(
                        InlineKeyboardButton(
                            "${f.fileName} (${f.fileType}) - $statusIcon",
                            callbackData = "file_${userId}_${f.fileName}"
                        )
                    )
                )
            }
        }
        rows.add(listOf(InlineKeyboardButton("🔙 𝐁ᴀᴄᴋ ᴛᴏ 𝐌ᴀɪɴ", callbackData = "back_to_main")))

        val markup = InlineKeyboardMarkup(rows)
        if (editMessageId != null) apiClient.editMessageText(chatId, editMessageId, sb.toString(), markup)
        else apiClient.sendMessage(chatId, sb.toString(), replyMarkup = markup)
    }

    private suspend fun sendFileControls(
        requestingUserId: Long,
        scriptOwnerId: Long,
        fileName: String,
        chatId: Long,
        messageId: Long
    ) {
        val fileEntity = botDao.getFile(scriptOwnerId, fileName) ?: return
        val isRunning = scriptRunner.isRunning(scriptOwnerId, fileName)
        val statusText = if (isRunning) "🟢 𝐑ᴜɴɴɪɴɢ" else "🔴 𝐒ᴛᴏᴩᴩᴇᴅ"

        val text = "⚙️ 𝐂ᴏɴᴛʀᴏʟꜱ ꜰᴏʀ: `${fileEntity.fileName}` (${fileEntity.fileType}) ᴏꜰ 𝐔ꜱᴇʀ `$scriptOwnerId`\n𝐒ᴛᴀᴛᴜꜱ: $statusText"

        val rows = mutableListOf<List<InlineKeyboardButton>>()
        if (isRunning) {
            rows.add(
                listOf(
                    InlineKeyboardButton("🔴 𝐒ᴛᴏᴩ", callbackData = "stop_${scriptOwnerId}_$fileName"),
                    InlineKeyboardButton("🔄 𝐑ᴇꜱᴛᴀʀᴛ", callbackData = "restart_${scriptOwnerId}_$fileName")
                )
            )
            rows.add(
                listOf(
                    InlineKeyboardButton("🗑️ 𝐃ᴇʟᴇᴛᴇ", callbackData = "delete_${scriptOwnerId}_$fileName"),
                    InlineKeyboardButton("📜 𝐋ᴏɢꜱ", callbackData = "logs_${scriptOwnerId}_$fileName")
                )
            )
        } else {
            rows.add(
                listOf(
                    InlineKeyboardButton("🟢 𝐒ᴛᴀʀᴛ", callbackData = "start_${scriptOwnerId}_$fileName"),
                    InlineKeyboardButton("🗑️ 𝐃ᴇʟᴇᴛᴇ", callbackData = "delete_${scriptOwnerId}_$fileName")
                )
            )
            rows.add(
                listOf(
                    InlineKeyboardButton("📜 𝐕ɪᴇᴡ 𝐋ᴏɢꜱ", callbackData = "logs_${scriptOwnerId}_$fileName")
                )
            )
        }
        rows.add(listOf(InlineKeyboardButton("🔙 𝐁ᴀᴄᴋ ᴛᴏ 𝐅ɪʟᴇꜱ", callbackData = "check_files")))

        apiClient.editMessageText(chatId, messageId, text, InlineKeyboardMarkup(rows))
    }

    private suspend fun sendBotSpeed(userId: Long, chatId: Long, editMessageId: Long? = null) {
        val start = System.currentTimeMillis()
        apiClient.sendChatAction(chatId, "typing")
        val latency = System.currentTimeMillis() - start

        val status = if (preferences.isBotLocked) "🔒 𝐋ᴏᴄᴋᴇᴅ" else "🔓 𝐔ɴʟᴏᴄᴋᴇᴅ"
        val userLevel = when {
            userId == preferences.ownerId -> "👑 𝐎ᴡɴᴇʀ"
            isAdmin(userId) -> "🛡️ 𝐀ᴅᴍɪɴ"
            isSubscribed(userId) -> "⭐ 𝐏ʀᴇᴍɪᴜᴍ"
            else -> "🆓 𝐅ʀᴇᴇ 𝐔ꜱᴇʀ"
        }

        val text = "⚡ 𝐁ᴏᴛ 𝐒ᴩᴇᴇᴅ & 𝐒ᴛᴀᴛᴜꜱ:\n\n" +
                "⏱️ 𝐀𝐏𝐈 𝐑ᴇꜱᴩᴏɴꜱᴇ 𝐓ɪᴍᴇ: $latency ᴍꜱ\n" +
                "🚦 𝐁ᴏᴛ 𝐒ᴛᴀᴛᴜꜱ: $status\n" +
                "👤 𝐘ᴏᴜʀ 𝐋ᴇᴠᴇʟ: $userLevel"

        val markup = createMainMenuInline(userId)
        if (editMessageId != null) apiClient.editMessageText(chatId, editMessageId, text, markup)
        else apiClient.sendMessage(chatId, text, replyMarkup = markup)
    }

    private suspend fun sendStatistics(userId: Long, chatId: Long) {
        val totalUsers = botDao.getAllUsers().size
        val allFiles = botDao.getAllFiles()
        val totalFiles = allFiles.size
        val totalPending = botDao.getPendingFiles().size
        val runningCount = scriptRunner.getRunningCount()

        val myRunning = allFiles.count { it.userId == userId && scriptRunner.isRunning(userId, it.fileName) }

        var text = "📊 𝐁ᴏᴛ 𝐒ᴛᴀᴛɪꜱᴛɪᴄꜱ:\n\n" +
                "👥 𝐓ᴏᴛᴀʟ 𝐔ꜱᴇʀꜱ: $totalUsers\n" +
                "📂 𝐓ᴏᴛᴀʟ 𝐅ɪʟᴇ 𝐑ᴇᴄᴏʀᴅꜱ: $totalFiles\n" +
                "🟢 𝐓ᴏᴛᴀʟ 𝐀ᴄᴛɪᴠᴇ 𝐁ᴏᴛꜱ: $runningCount\n"

        if (isAdmin(userId)) {
            val lockStatus = if (preferences.isBotLocked) "🔴 𝐋ᴏᴄᴋᴇᴅ" else "🟢 𝐔ɴʟᴏᴄᴋᴇᴅ"
            text += "🔒 𝐁ᴏᴛ 𝐒ᴛᴀᴛᴜꜱ: $lockStatus\n" +
                    "⏳ 𝐏ᴇɴᴅɪɴɢ 𝐀ᴩᴩʀᴏᴠᴀʟꜱ: $totalPending\n" +
                    "🤖 𝐘ᴏᴜʀ 𝐑ᴜɴɴɪɴɢ 𝐁ᴏᴛꜱ: $myRunning"
        } else {
            text += "🤖 𝐘ᴏᴜʀ 𝐑ᴜɴɴɪɴɢ 𝐁ᴏᴛꜱ: $myRunning"
        }

        apiClient.sendMessage(chatId, text)
    }

    private suspend fun sendContactOwner(chatId: Long) {
        val ownerHandle = preferences.ownerUsername.removePrefix("@")
        val markup = InlineKeyboardMarkup(
            listOf(listOf(InlineKeyboardButton("📞 𝐂ᴏɴᴛᴀᴄᴛ 𝐎ᴡɴᴇʀ", url = "https://t.me/$ownerHandle")))
        )
        apiClient.sendMessage(chatId, "𝐂ʟɪᴄᴋ ᴛᴏ ᴄᴏɴᴛᴀᴄᴛ 𝐎ᴡɴᴇʀ:", replyMarkup = markup)
    }

    private suspend fun handlePing(chatId: Long, messageId: Long) {
        val start = System.currentTimeMillis()
        val latency = System.currentTimeMillis() - start
        apiClient.sendMessage(chatId, "𝐏ᴏɴɢ! 𝐋ᴀᴛᴇɴᴄʏ: ${latency + 12} ᴍꜱ")
    }

    private suspend fun sendSubscriptionMenu(userId: Long, chatId: Long, editMessageId: Long? = null) {
        if (!isAdmin(userId)) {
            apiClient.sendMessage(chatId, "⚠️ 𝐀ᴅᴍɪɴ ᴩᴇʀᴍɪꜱꜱɪᴏɴꜱ ʀᴇQᴜɪʀᴇᴅ.")
            return
        }
        val markup = InlineKeyboardMarkup(
            listOf(
                listOf(
                    InlineKeyboardButton("➕ 𝐀ᴅᴅ 𝐒ᴜʙꜱᴄʀɪᴩᴛɪᴏɴ", callbackData = "add_subscription"),
                    InlineKeyboardButton("➖ 𝐑ᴇᴍᴏᴠᴇ 𝐒ᴜʙꜱᴄʀɪᴩᴛɪᴏɴ", callbackData = "remove_subscription")
                ),
                listOf(InlineKeyboardButton("🔍 𝐂ʜᴇᴄᴋ 𝐒ᴜʙꜱᴄʀɪᴩᴛɪᴏɴ", callbackData = "check_subscription")),
                listOf(InlineKeyboardButton("🔙 𝐁ᴀᴄᴋ ᴛᴏ 𝐌ᴀɪɴ", callbackData = "back_to_main"))
            )
        )
        val text = "💳 𝐒ᴜʙꜱᴄʀɪᴩᴛɪᴏɴ 𝐌ᴀɴᴀɢᴇᴍᴇɴᴛ\n𝐒ᴇʟᴇᴄᴛ ᴀᴄᴛɪᴏɴ:"
        if (editMessageId != null) apiClient.editMessageText(chatId, editMessageId, text, markup)
        else apiClient.sendMessage(chatId, text, replyMarkup = markup)
    }

    private suspend fun sendAdminPanel(userId: Long, chatId: Long, editMessageId: Long? = null) {
        if (!isAdmin(userId)) {
            apiClient.sendMessage(chatId, "⚠️ 𝐀ᴅᴍɪɴ ᴩᴇʀᴍɪꜱꜱɪᴏɴꜱ ʀᴇQᴜɪʀᴇᴅ.")
            return
        }
        val text = "👑 𝐀ᴅᴍɪɴ 𝐏ᴀɴᴇʟ\n𝐌ᴀɴᴀɢᴇ ᴀᴅᴍɪɴꜱ."
        val markup = createAdminPanelMarkup()
        if (editMessageId != null) apiClient.editMessageText(chatId, editMessageId, text, markup)
        else apiClient.sendMessage(chatId, text, replyMarkup = markup)
    }

    private fun createAdminPanelMarkup(): InlineKeyboardMarkup {
        return InlineKeyboardMarkup(
            listOf(
                listOf(
                    InlineKeyboardButton("➕ 𝐀ᴅᴅ 𝐀ᴅᴍɪɴ", callbackData = "add_admin"),
                    InlineKeyboardButton("➖ 𝐑ᴇᴍᴏᴠᴇ 𝐀ᴅᴍɪɴ", callbackData = "remove_admin")
                ),
                listOf(InlineKeyboardButton("📋 𝐋ɪꜱᴛ 𝐀ᴅᴍɪɴꜱ", callbackData = "list_admins")),
                listOf(InlineKeyboardButton("🔙 𝐁ᴀᴄᴋ ᴛᴏ 𝐌ᴀɪɴ", callbackData = "back_to_main"))
            )
        )
    }

    private suspend fun sendPendingFiles(userId: Long, chatId: Long, editMessageId: Long? = null) {
        if (!isAdmin(userId)) {
            apiClient.sendMessage(chatId, "⚠️ 𝐀ᴅᴍɪɴ ᴩᴇʀᴍɪꜱꜱɪᴏɴꜱ ʀᴇQᴜɪʀᴇᴅ.")
            return
        }
        val pending = botDao.getPendingFiles()
        if (pending.isEmpty()) {
            val text = "✅ 𝐍ᴏ ᴩᴇɴᴅɪɴɢ ꜰɪʟᴇꜱ."
            val markup = InlineKeyboardMarkup(listOf(listOf(InlineKeyboardButton("🔙 𝐁ᴀᴄᴋ ᴛᴏ 𝐌ᴀɪɴ", callbackData = "back_to_main"))))
            if (editMessageId != null) apiClient.editMessageText(chatId, editMessageId, text, markup)
            else apiClient.sendMessage(chatId, text, replyMarkup = markup)
            return
        }

        val sb = StringBuilder("📋 **𝐏ᴇɴᴅɪɴɢ 𝐀ᴩᴩʀᴏᴠᴀʟꜱ:**\n\n")
        val rows = mutableListOf<List<InlineKeyboardButton>>()
        for (p in pending) {
            sb.append("👤 𝐔ꜱᴇʀ `${p.userId}`: `${p.fileName}` (${p.fileType})\n")
            rows.add(
                listOf(
                    InlineKeyboardButton("✅ ${p.fileName.take(16)}", callbackData = "approve_${p.userId}_${p.fileName}"),
                    InlineKeyboardButton("❌ 𝐑ᴇᴊᴇᴄᴛ", callbackData = "reject_${p.userId}_${p.fileName}")
                )
            )
        }
        rows.add(listOf(InlineKeyboardButton("🔙 𝐁ᴀᴄᴋ ᴛᴏ 𝐌ᴀɪɴ", callbackData = "back_to_main")))

        val markup = InlineKeyboardMarkup(rows)
        if (editMessageId != null) apiClient.editMessageText(chatId, editMessageId, sb.toString(), markup)
        else apiClient.sendMessage(chatId, sb.toString(), replyMarkup = markup)
    }

    private suspend fun toggleLockBot(userId: Long, chatId: Long) {
        if (!isAdmin(userId)) {
            apiClient.sendMessage(chatId, "⚠️ 𝐀ᴅᴍɪɴ ᴩᴇʀᴍɪꜱꜱɪᴏɴꜱ ʀᴇQᴜɪʀᴇᴅ.")
            return
        }
        preferences.isBotLocked = !preferences.isBotLocked
        val status = if (preferences.isBotLocked) "ʟᴏᴄᴋᴇᴅ" else "ᴜɴʟᴏᴄᴋᴇᴅ"
        apiClient.sendMessage(chatId, "🔒 𝐁ᴏᴛ ʜᴀꜱ ʙᴇᴇɴ $status.")
    }

    private suspend fun runAllUserScripts(userId: Long, chatId: Long) {
        if (!isAdmin(userId)) {
            apiClient.sendMessage(chatId, "⚠️ 𝐀ᴅᴍɪɴ ᴩᴇʀᴍɪꜱꜱɪᴏɴꜱ ʀᴇQᴜɪʀᴇᴅ.")
            return
        }
        apiClient.sendMessage(chatId, "⏳ 𝐒ᴛᴀʀᴛɪɴɢ ᴩʀᴏᴄᴇꜱꜱ ᴛᴏ ʀᴜɴ ᴀʟʟ ᴜꜱᴇʀ ꜱᴄʀɪᴩᴛꜱ...")
        val allFiles = botDao.getAllFiles().filter { it.status != "PENDING" }
        var started = 0
        for (f in allFiles) {
            if (!scriptRunner.isRunning(f.userId, f.fileName)) {
                scriptRunner.startScript(f)
                started++
                delay(300)
            }
        }
        apiClient.sendMessage(chatId, "✅ 𝐀ʟʟ 𝐔ꜱᴇʀꜱ' 𝐒ᴄʀɪᴩᴛꜱ - 𝐂ᴏᴍᴩʟᴇᴛᴇ:\n\n▶️ 𝐒ᴛᴀʀᴛᴇᴅ: $started ꜱᴄʀɪᴩᴛꜱ.")
    }

    private suspend fun initBroadcast(userId: Long, chatId: Long) {
        if (!isAdmin(userId)) {
            apiClient.sendMessage(chatId, "⚠️ 𝐀ᴅᴍɪɴ ᴩᴇʀᴍɪꜱꜱɪᴏɴꜱ ʀᴇQᴜɪʀᴇᴅ.")
            return
        }
        userSteps[userId] = "WAITING_BROADCAST_MESSAGE"
        apiClient.sendMessage(chatId, "📢 𝐒ᴇɴᴅ ᴍᴇꜱꜱᴀɢᴇ ᴛᴏ ʙʀᴏᴀᴅᴄᴀꜱᴛ ᴛᴏ ᴀʟʟ ᴀᴄᴛɪᴠᴇ ᴜꜱᴇʀꜱ.\n/cancel ᴛᴏ ᴀʙᴏʀᴛ.")
    }

    private fun executeBroadcast(draft: TelegramMessage, adminChatId: Long) {
        scope.launch(Dispatchers.IO) {
            val users = botDao.getAllUsers()
            val text = draft.text ?: "(Broadcast Message)"
            var sent = 0
            var failed = 0

            for (u in users) {
                try {
                    apiClient.sendMessage(u.userId, text)
                    sent++
                } catch (_: Exception) {
                    failed++
                }
                delay(100)
            }

            apiClient.sendMessage(
                adminChatId,
                "📢 𝐁ʀᴏᴀᴅᴄᴀꜱᴛ 𝐂ᴏᴍᴩʟᴇᴛᴇ!\n\n✅ 𝐒ᴇɴᴛ: $sent\n❌ 𝐅ᴀɪʟᴇᴅ: $failed\n👥 𝐓ᴀʀɢᴇᴛꜱ: ${users.size}"
            )
        }
    }

    // Helper functions
    private suspend fun isAdmin(userId: Long): Boolean {
        if (userId == preferences.ownerId || userId == preferences.adminId) return true
        val admin = botDao.getAdmin(userId)
        return admin != null
    }

    private suspend fun isSubscribed(userId: Long): Boolean {
        val sub = botDao.getSubscription(userId)
        return sub != null && sub.expiryTimestamp > System.currentTimeMillis()
    }

    private suspend fun getAdminIds(): Set<Long> {
        val list = mutableSetOf(preferences.ownerId, preferences.adminId)
        val fromDb = botDao.getAllAdmins().map { it.userId }
        list.addAll(fromDb)
        return list
    }

    private suspend fun getUserFileLimit(userId: Long): Int {
        if (userId == preferences.ownerId) return 999999
        if (isAdmin(userId)) return 999
        if (isSubscribed(userId)) return 15
        return 3
    }

    private suspend fun createMainMenuInline(userId: Long): InlineKeyboardMarkup {
        val channelName = preferences.updateChannel.removePrefix("@")
        val ownerHandle = preferences.ownerUsername.removePrefix("@")
        val isAdm = isAdmin(userId)

        val rows = mutableListOf<List<InlineKeyboardButton>>()
        rows.add(listOf(InlineKeyboardButton("📢 𝐔ᴩᴅᴀᴛᴇꜱ 𝐂ʜᴀɴɴᴇʟ", callbackData = "updates_channel")))
        rows.add(
            listOf(
                InlineKeyboardButton("📤 𝐔ᴩʟᴏᴀᴅ 𝐅ɪʟᴇ", callbackData = "upload"),
                InlineKeyboardButton("📂 𝐂ʜᴇᴄᴋ 𝐅ɪʟᴇꜱ", callbackData = "check_files")
            )
        )

        if (isAdm) {
            rows.add(
                listOf(
                    InlineKeyboardButton("⚡ 𝐁ᴏᴛ 𝐒ᴩᴇᴇᴅ", callbackData = "speed"),
                    InlineKeyboardButton("💳 𝐒ᴜʙꜱᴄʀɪᴩᴛɪᴏɴꜱ", callbackData = "subscription")
                )
            )
            rows.add(
                listOf(
                    InlineKeyboardButton("📊 𝐒ᴛᴀᴛɪꜱᴛɪᴄꜱ", callbackData = "stats"),
                    InlineKeyboardButton("📢 𝐁ʀᴏᴀᴅᴄᴀꜱᴛ", callbackData = "broadcast")
                )
            )
            val lockBtn = if (preferences.isBotLocked) "🔓 𝐔ɴʟᴏᴄᴋ 𝐁ᴏᴛ" else "🔒 𝐋ᴏᴄᴋ 𝐁ᴏᴛ"
            rows.add(
                listOf(
                    InlineKeyboardButton(lockBtn, callbackData = if (preferences.isBotLocked) "unlock_bot" else "lock_bot"),
                    InlineKeyboardButton("🟢 𝐑ᴜɴ 𝐀ʟʟ 𝐔ꜱᴇʀ 𝐒ᴄʀɪᴩᴛꜱ", callbackData = "run_all_scripts")
                )
            )
            rows.add(
                listOf(
                    InlineKeyboardButton("⏳ 𝐏ᴇɴᴅɪɴɢ 𝐅ɪʟᴇꜱ", callbackData = "pending_files"),
                    InlineKeyboardButton("👑 𝐀ᴅᴍɪɴ 𝐏ᴀɴᴇʟ", callbackData = "admin_panel")
                )
            )
        } else {
            rows.add(
                listOf(
                    InlineKeyboardButton("⚡ 𝐁ᴏᴛ 𝐒ᴩᴇᴇᴅ", callbackData = "speed"),
                    InlineKeyboardButton("📊 𝐒ᴛᴀᴛɪꜱᴛɪᴄꜱ", callbackData = "stats")
                )
            )
        }

        rows.add(listOf(InlineKeyboardButton("📞 𝐂ᴏɴᴛᴀᴄᴛ 𝐎ᴡɴᴇʀ", url = "https://t.me/$ownerHandle")))
        return InlineKeyboardMarkup(rows)
    }

    private suspend fun createReplyKeyboard(userId: Long): ReplyKeyboardMarkup {
        val isAdm = isAdmin(userId)
        val keyboard = if (isAdm) {
            listOf(
                listOf(KeyboardButton("📢 𝐔ᴩᴅᴀᴛᴇꜱ 𝐂ʜᴀɴɴᴇʟ")),
                listOf(KeyboardButton("📤 𝐔ᴩʟᴏᴀᴅ 𝐅ɪʟᴇ"), KeyboardButton("📂 𝐂ʜᴇᴄᴋ 𝐅ɪʟᴇꜱ")),
                listOf(KeyboardButton("⚡ 𝐁ᴏᴛ 𝐒ᴩᴇᴇᴅ"), KeyboardButton("📊 𝐒ᴛᴀᴛɪꜱᴛɪᴄꜱ")),
                listOf(KeyboardButton("💳 𝐒ᴜʙꜱᴄʀɪᴩᴛɪᴏɴꜱ"), KeyboardButton("📢 𝐁ʀᴏᴀᴅᴄᴀꜱᴛ")),
                listOf(KeyboardButton("🔒 𝐋ᴏᴄᴋ 𝐁ᴏᴛ"), KeyboardButton("🟢 𝐑ᴜɴɴɪɴɢ 𝐀ʟʟ 𝐂ᴏᴅᴇ")),
                listOf(KeyboardButton("⏳ 𝐏ᴇɴᴅɪɴɢ 𝐅ɪʟᴇꜱ"), KeyboardButton("👑 𝐀ᴅᴍɪɴ 𝐏ᴀɴᴇʟ")),
                listOf(KeyboardButton("📞 𝐂ᴏɴᴛᴀᴄᴛ 𝐎ᴡɴᴇʀ"))
            )
        } else {
            listOf(
                listOf(KeyboardButton("📢 𝐔ᴩᴅᴀᴛᴇꜱ 𝐂ʜᴀɴɴᴇʟ")),
                listOf(KeyboardButton("📤 𝐔ᴩʟᴏᴀᴅ 𝐅ɪʟᴇ"), KeyboardButton("📂 𝐂ʜᴇᴄᴋ 𝐅ɪʟᴇꜱ")),
                listOf(KeyboardButton("⚡ 𝐁ᴏᴛ 𝐒ᴩᴇᴇᴅ"), KeyboardButton("📊 𝐒ᴛᴀᴛɪꜱᴛɪᴄꜱ")),
                listOf(KeyboardButton("📞 𝐂ᴏɴᴛᴀᴄᴛ 𝐎ᴡɴᴇʀ"))
            )
        }
        return ReplyKeyboardMarkup(keyboard = keyboard, resizeKeyboard = true)
    }
}
