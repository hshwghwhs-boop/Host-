package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.BotDatabase
import com.example.data.model.ActiveUserEntity
import com.example.data.model.AdminEntity
import com.example.data.model.HostedFileEntity
import com.example.data.model.LogEntryEntity
import com.example.data.model.SubscriptionEntity
import com.example.data.preferences.BotPreferences
import com.example.runner.ScriptRunner
import com.example.service.TelegramHostingService
import com.example.telegram.api.TelegramApiClient
import com.example.telegram.model.TelegramUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BotHostViewModel(application: Application) : AndroidViewModel(application) {

    private val database = BotDatabase.getInstance(application)
    val preferences = BotPreferences(application)
    private val scriptRunner = ScriptRunner(application, database.botDao(), viewModelScope)
    private val apiClient = TelegramApiClient { preferences.botToken }

    // Service state
    val isServiceRunning: StateFlow<Boolean> = TelegramHostingService.isRunningState
    val botUsername: StateFlow<String> = TelegramHostingService.botUsername
    val botFirstName: StateFlow<String> = TelegramHostingService.botFirstName
    val processedUpdates: StateFlow<Int> = TelegramHostingService.processedUpdates

    private val _uptimeString = MutableStateFlow("00:00:00")
    val uptimeString: StateFlow<String> = _uptimeString.asStateFlow()

    private val _pingLatencyMs = MutableStateFlow<Long?>(null)
    val pingLatencyMs: StateFlow<Long?> = _pingLatencyMs.asStateFlow()

    private val _connectionTestResult = MutableStateFlow<String?>(null)
    val connectionTestResult: StateFlow<String?> = _connectionTestResult.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    // DB flows
    val allFiles: StateFlow<List<HostedFileEntity>> = database.botDao().getAllFilesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingFiles: StateFlow<List<HostedFileEntity>> = database.botDao().getPendingFilesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allUsers: StateFlow<List<ActiveUserEntity>> = database.botDao().getAllUsersFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSubscriptions: StateFlow<List<SubscriptionEntity>> = database.botDao().getAllSubscriptionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAdmins: StateFlow<List<AdminEntity>> = database.botDao().getAllAdminsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentLogs: StateFlow<List<LogEntryEntity>> = database.botDao().getRecentLogsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Track uptime timer
        viewModelScope.launch {
            while (isActive) {
                if (isServiceRunning.value) {
                    val start = TelegramHostingService.serviceStartTime.value
                    if (start > 0) {
                        val diff = (System.currentTimeMillis() - start) / 1000
                        val hours = diff / 3600
                        val minutes = (diff % 3600) / 60
                        val seconds = diff % 60
                        _uptimeString.value = String.format("%02dh %02dm %02ds", hours, minutes, seconds)
                    }
                } else {
                    _uptimeString.value = "Offline"
                }
                delay(1000)
            }
        }

        // Prepopulate default admin if empty
        viewModelScope.launch(Dispatchers.IO) {
            val admins = database.botDao().getAllAdmins()
            if (admins.isEmpty()) {
                database.botDao().insertAdmin(AdminEntity(userId = preferences.ownerId, isOwner = true))
                if (preferences.adminId != preferences.ownerId) {
                    database.botDao().insertAdmin(AdminEntity(userId = preferences.adminId, isOwner = false))
                }
            }
        }
    }

    fun startService() {
        TelegramHostingService.start(getApplication())
    }

    fun stopService() {
        TelegramHostingService.stop(getApplication())
    }

    fun toggleService() {
        if (isServiceRunning.value) {
            stopService()
        } else {
            startService()
        }
    }

    fun updateToken(newToken: String) {
        preferences.botToken = newToken.trim()
        testConnection()
    }

    fun testConnection() {
        viewModelScope.launch {
            _isTestingConnection.value = true
            _connectionTestResult.value = null
            val start = System.currentTimeMillis()
            val result = apiClient.getMe()
            val latency = System.currentTimeMillis() - start
            _pingLatencyMs.value = latency

            if (result.isSuccess) {
                val user = result.getOrNull()
                _connectionTestResult.value = "✅ Connected to @${user?.username} (${user?.firstName}) | ${latency}ms"
                database.botDao().insertLog(
                    LogEntryEntity(
                        level = "INFO",
                        tag = "TEST",
                        message = "Telegram API verified: @${user?.username} (${user?.firstName})"
                    )
                )
            } else {
                val err = result.exceptionOrNull()?.message ?: "Unknown error"
                _connectionTestResult.value = "❌ Connection Failed: $err"
                database.botDao().insertLog(
                    LogEntryEntity(
                        level = "ERROR",
                        tag = "TEST",
                        message = "Connection test failed: $err"
                    )
                )
            }
            _isTestingConnection.value = false
        }
    }

    fun saveConfig(
        token: String,
        ownerId: Long,
        adminId: Long,
        ownerUsername: String,
        updateChannel: String,
        approvalRequired: Boolean,
        autoStartOnBoot: Boolean,
        wakeLockEnabled: Boolean,
        httpPort: Int,
        isHttpEnabled: Boolean
    ) {
        preferences.botToken = token.trim()
        preferences.ownerId = ownerId
        preferences.adminId = adminId
        preferences.ownerUsername = ownerUsername.trim()
        preferences.updateChannel = updateChannel.trim()
        preferences.adminApprovalRequired = approvalRequired
        preferences.autoStartOnBoot = autoStartOnBoot
        preferences.wakeLockEnabled = wakeLockEnabled
        preferences.httpPort = httpPort
        preferences.isHttpKeepAliveEnabled = isHttpEnabled

        viewModelScope.launch {
            database.botDao().insertLog(
                LogEntryEntity(
                    level = "INFO",
                    tag = "CONFIG",
                    message = "BotHost configuration updated."
                )
            )
        }
    }

    fun toggleBotLock() {
        preferences.isBotLocked = !preferences.isBotLocked
        val status = if (preferences.isBotLocked) "LOCKED" else "UNLOCKED"
        viewModelScope.launch {
            database.botDao().insertLog(
                LogEntryEntity(
                    level = "WARN",
                    tag = "LOCK",
                    message = "Bot status changed to: $status"
                )
            )
        }
    }

    fun startScript(file: HostedFileEntity) {
        viewModelScope.launch {
            scriptRunner.startScript(file)
        }
    }

    fun stopScript(userId: Long, fileName: String) {
        viewModelScope.launch {
            scriptRunner.stopScript(userId, fileName)
        }
    }

    fun restartScript(file: HostedFileEntity) {
        viewModelScope.launch {
            scriptRunner.restartScript(file)
        }
    }

    fun deleteScript(userId: Long, fileName: String) {
        viewModelScope.launch {
            scriptRunner.deleteScript(userId, fileName)
        }
    }

    suspend fun getScriptLogs(userId: Long, fileName: String): String {
        return withContext(Dispatchers.IO) {
            scriptRunner.getLogs(userId, fileName)
        }
    }

    fun approveFile(file: HostedFileEntity) {
        viewModelScope.launch {
            database.botDao().updateFileStatus(file.userId, file.fileName, "STOPPED", 0, null)
            scriptRunner.startScript(file)
            apiClient.sendMessage(
                file.userId,
                "✅ 𝐘ᴏᴜʀ ꜰɪʟᴇ `${file.fileName}` ᴡᴀꜱ **𝐀𝐏𝐏𝐑𝐎𝐕𝐄𝐃** ʙʏ ᴀᴅᴍɪɴ!\n🚀 𝐒ᴛᴀʀᴛɪɴɢ ɴᴏᴡ..."
            )
        }
    }

    fun rejectFile(file: HostedFileEntity) {
        viewModelScope.launch {
            scriptRunner.deleteScript(file.userId, file.fileName)
            apiClient.sendMessage(
                file.userId,
                "❌ 𝐘ᴏᴜʀ ꜰɪʟᴇ `${file.fileName}` ᴡᴀꜱ **𝐑𝐄𝐉𝐄𝐂𝐓𝐄𝐃** ʙʏ ᴀᴅᴍɪɴ."
            )
        }
    }

    fun createScriptLocally(userId: Long, fileName: String, fileType: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val userFolder = scriptRunner.getUserFolder(userId)
            val file = java.io.File(userFolder, fileName)
            file.writeText(content)

            val entity = HostedFileEntity(
                userId = userId,
                fileName = fileName,
                fileType = fileType,
                filePath = file.absolutePath,
                scriptContent = content,
                status = "STOPPED"
            )
            database.botDao().insertOrUpdateFile(entity)
            database.botDao().insertLog(
                LogEntryEntity(
                    level = "INFO",
                    tag = "FILE",
                    message = "Created new script '$fileName' ($fileType) for User $userId"
                )
            )
        }
    }

    fun addSubscription(userId: Long, days: Int) {
        viewModelScope.launch {
            val existing = database.botDao().getSubscription(userId)
            val start = if (existing != null && existing.expiryTimestamp > System.currentTimeMillis()) {
                existing.expiryTimestamp
            } else {
                System.currentTimeMillis()
            }
            val newExpiry = start + (days.toLong() * 24 * 60 * 60 * 1000)
            database.botDao().insertOrUpdateSubscription(
                SubscriptionEntity(userId = userId, expiryTimestamp = newExpiry)
            )
            val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(newExpiry))
            apiClient.sendMessage(userId, "🎉 𝐒ᴜʙ ᴀᴄᴛɪᴠᴀᴛᴇᴅ ʙʏ $days ᴅᴀʏꜱ! 𝐄xᴩɪʀᴇꜱ: $dateStr.")
        }
    }

    fun removeSubscription(userId: Long) {
        viewModelScope.launch {
            database.botDao().deleteSubscription(userId)
            apiClient.sendMessage(userId, "ℹ️ 𝐒ᴜʙꜱᴄʀɪᴩᴛɪᴏɴ ʀᴇᴍᴏᴠᴇᴅ.")
        }
    }

    fun addAdmin(adminId: Long) {
        viewModelScope.launch {
            database.botDao().insertAdmin(AdminEntity(userId = adminId, isOwner = false))
            apiClient.sendMessage(adminId, "🎉 𝐘ᴏᴜ ᴀʀᴇ ɴᴏᴡ 𝐀ᴅᴍɪɴ.")
        }
    }

    fun removeAdmin(adminId: Long) {
        viewModelScope.launch {
            database.botDao().deleteAdmin(adminId)
            apiClient.sendMessage(adminId, "ℹ️ 𝐍ᴏ ʟᴏɴɢᴇʀ 𝐀ᴅᴍɪɴ.")
        }
    }

    fun sendBroadcast(text: String, onComplete: (Int, Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val users = database.botDao().getAllUsers()
            var sent = 0
            var failed = 0
            for (u in users) {
                val res = apiClient.sendMessage(u.userId, text)
                if (res.isSuccess) sent++ else failed++
                delay(80)
            }
            withContext(Dispatchers.Main) {
                onComplete(sent, failed)
            }
        }
    }

    fun runAllScripts() {
        viewModelScope.launch {
            val files = database.botDao().getAllFiles().filter { it.status != "PENDING" }
            for (f in files) {
                if (!scriptRunner.isRunning(f.userId, f.fileName)) {
                    scriptRunner.startScript(f)
                    delay(300)
                }
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            database.botDao().clearLogs()
        }
    }
}
