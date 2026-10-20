package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.db.BotDatabase
import com.example.data.model.LogEntryEntity
import com.example.data.preferences.BotPreferences
import com.example.runner.ScriptRunner
import com.example.telegram.api.TelegramApiClient
import com.example.telegram.engine.TelegramBotEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

class TelegramHostingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var wakeLock: PowerManager.WakeLock? = null
    private var pollingJob: Job? = null
    private var httpServer: HttpKeepAliveServer? = null

    private lateinit var database: BotDatabase
    private lateinit var preferences: BotPreferences
    private lateinit var scriptRunner: ScriptRunner
    private lateinit var apiClient: TelegramApiClient
    private lateinit var botEngine: TelegramBotEngine

    override fun onCreate() {
        super.onCreate()
        database = BotDatabase.getInstance(applicationContext)
        preferences = BotPreferences(applicationContext)
        scriptRunner = ScriptRunner(applicationContext, database.botDao(), serviceScope)
        apiClient = TelegramApiClient { preferences.botToken }
        botEngine = TelegramBotEngine(apiClient, database.botDao(), preferences, scriptRunner, serviceScope)

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundServiceNotification()
        acquireWakeLock()
        startHttpServer()
        startTelegramPolling()

        _isRunningState.value = true
        _serviceStartTime.value = System.currentTimeMillis()

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BotHost 24/7 Hosting Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Telegram bots and keep-alive web server active 24/7 in the background"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundServiceNotification() {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, TelegramHostingService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BotHost 24/7 - Host Active 🟢")
            .setContentText("Telegram bot polling & HTTP keep-alive server running")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Host", stopPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun acquireWakeLock() {
        if (preferences.wakeLockEnabled && wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BotHost::24x7ServiceWakeLock")?.apply {
                setReferenceCounted(false)
                acquire(24 * 60 * 60 * 1000L) // 24 hours renewal
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun startHttpServer() {
        if (preferences.isHttpKeepAliveEnabled && httpServer == null) {
            httpServer = HttpKeepAliveServer(
                port = preferences.httpPort,
                botDao = database.botDao(),
                scope = serviceScope,
                statusProvider = {
                    val uptimeSec = (System.currentTimeMillis() - _serviceStartTime.value) / 1000
                    JSONObject().apply {
                        put("status", "ONLINE")
                        put("service", "Marco File Host")
                        put("uptime_seconds", uptimeSec)
                        put("active_scripts", scriptRunner.getRunningCount())
                        put("processed_updates", _processedUpdates.value)
                    }.toString()
                }
            )
            httpServer?.start()
        }
    }

    private fun startTelegramPolling() {
        if (pollingJob != null && pollingJob?.isActive == true) return

        pollingJob = serviceScope.launch {
            var offset = 0L
            var consecutiveErrors = 0

            database.botDao().insertLog(
                LogEntryEntity(
                    level = "INFO",
                    tag = "SERVICE",
                    message = "🚀 BotHost 24/7 Service started. Initializing Telegram Long Polling..."
                )
            )

            // Test connection with getMe
            val meResult = apiClient.getMe()
            if (meResult.isSuccess) {
                val me = meResult.getOrNull()
                _botUsername.value = me?.username ?: ""
                _botFirstName.value = me?.firstName ?: ""
                database.botDao().insertLog(
                    LogEntryEntity(
                        level = "INFO",
                        tag = "BOT",
                        message = "Connected to @${me?.username} (${me?.firstName}) [ID: ${me?.id}]"
                    )
                )
            } else {
                database.botDao().insertLog(
                    LogEntryEntity(
                        level = "WARN",
                        tag = "BOT",
                        message = "Could not reach Telegram API: ${meResult.exceptionOrNull()?.message}"
                    )
                )
            }

            // Long-polling loop
            while (isActive) {
                val token = preferences.botToken.trim()
                if (token.isEmpty()) {
                    delay(5000)
                    continue
                }

                try {
                    val updatesResult = apiClient.getUpdates(offset = offset, timeoutSeconds = 25)
                    if (updatesResult.isSuccess) {
                        consecutiveErrors = 0
                        val updates = updatesResult.getOrNull() ?: emptyList()
                        for (u in updates) {
                            if (u.updateId >= offset) {
                                offset = u.updateId + 1
                            }
                            _processedUpdates.value += 1
                            botEngine.handleUpdate(u)
                        }
                    } else {
                        consecutiveErrors++
                        val err = updatesResult.exceptionOrNull()?.message ?: "Unknown error"
                        database.botDao().insertLog(
                            LogEntryEntity(
                                level = "WARN",
                                tag = "POLL",
                                message = "Poll attempt error: $err"
                            )
                        )
                        val backoff = when {
                            consecutiveErrors > 10 -> 30000L
                            consecutiveErrors > 5 -> 10000L
                            else -> 3000L
                        }
                        delay(backoff)
                    }
                } catch (e: Exception) {
                    consecutiveErrors++
                    delay(5000L)
                }
            }
        }
    }

    override fun onDestroy() {
        _isRunningState.value = false
        pollingJob?.cancel()
        httpServer?.stop()
        releaseWakeLock()
        serviceScope.cancel()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "bothost_247_service_channel"
        const val NOTIFICATION_ID = 2407
        const val ACTION_START_SERVICE = "com.example.service.START_247"
        const val ACTION_STOP_SERVICE = "com.example.service.STOP_247"

        private val _isRunningState = MutableStateFlow(false)
        val isRunningState: StateFlow<Boolean> = _isRunningState.asStateFlow()

        private val _serviceStartTime = MutableStateFlow(0L)
        val serviceStartTime: StateFlow<Long> = _serviceStartTime.asStateFlow()

        private val _processedUpdates = MutableStateFlow(0)
        val processedUpdates: StateFlow<Int> = _processedUpdates.asStateFlow()

        private val _botUsername = MutableStateFlow("")
        val botUsername: StateFlow<String> = _botUsername.asStateFlow()

        private val _botFirstName = MutableStateFlow("")
        val botFirstName: StateFlow<String> = _botFirstName.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, TelegramHostingService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, TelegramHostingService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}
