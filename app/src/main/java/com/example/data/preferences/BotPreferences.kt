package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences

class BotPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("bot_host_preferences", Context.MODE_PRIVATE)

    var botToken: String
        get() = prefs.getString(KEY_BOT_TOKEN, DEFAULT_TOKEN) ?: DEFAULT_TOKEN
        set(value) = prefs.edit().putString(KEY_BOT_TOKEN, value.trim()).apply()

    var ownerId: Long
        get() = prefs.getLong(KEY_OWNER_ID, DEFAULT_OWNER_ID)
        set(value) = prefs.edit().putLong(KEY_OWNER_ID, value).apply()

    var adminId: Long
        get() = prefs.getLong(KEY_ADMIN_ID, DEFAULT_ADMIN_ID)
        set(value) = prefs.edit().putLong(KEY_ADMIN_ID, value).apply()

    var ownerUsername: String
        get() = prefs.getString(KEY_OWNER_USERNAME, DEFAULT_OWNER_USERNAME) ?: DEFAULT_OWNER_USERNAME
        set(value) = prefs.edit().putString(KEY_OWNER_USERNAME, value.trim()).apply()

    var updateChannel: String
        get() = prefs.getString(KEY_UPDATE_CHANNEL, DEFAULT_UPDATE_CHANNEL) ?: DEFAULT_UPDATE_CHANNEL
        set(value) = prefs.edit().putString(KEY_UPDATE_CHANNEL, value.trim()).apply()

    var adminApprovalRequired: Boolean
        get() = prefs.getBoolean(KEY_ADMIN_APPROVAL_REQUIRED, true)
        set(value) = prefs.edit().putBoolean(KEY_ADMIN_APPROVAL_REQUIRED, value).apply()

    var isBotLocked: Boolean
        get() = prefs.getBoolean(KEY_BOT_LOCKED, false)
        set(value) = prefs.edit().putBoolean(KEY_BOT_LOCKED, value).apply()

    var wakeLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_WAKE_LOCK_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_WAKE_LOCK_ENABLED, value).apply()

    var autoStartOnBoot: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START_BOOT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START_BOOT, value).apply()

    var httpPort: Int
        get() = prefs.getInt(KEY_HTTP_PORT, 8080)
        set(value) = prefs.edit().putInt(KEY_HTTP_PORT, value).apply()

    var isHttpKeepAliveEnabled: Boolean
        get() = prefs.getBoolean(KEY_HTTP_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_HTTP_ENABLED, value).apply()

    companion object {
        private const val KEY_BOT_TOKEN = "bot_token"
        private const val KEY_OWNER_ID = "owner_id"
        private const val KEY_ADMIN_ID = "admin_id"
        private const val KEY_OWNER_USERNAME = "owner_username"
        private const val KEY_UPDATE_CHANNEL = "update_channel"
        private const val KEY_ADMIN_APPROVAL_REQUIRED = "admin_approval_required"
        private const val KEY_BOT_LOCKED = "bot_locked"
        private const val KEY_WAKE_LOCK_ENABLED = "wake_lock_enabled"
        private const val KEY_AUTO_START_BOOT = "auto_start_boot"
        private const val KEY_HTTP_PORT = "http_port"
        private const val KEY_HTTP_ENABLED = "http_enabled"

        // Default credentials from user
        const val DEFAULT_TOKEN = "8833720419:AAGXdASdwBFpgbsr0LY1hDGW9kY_hOJ9ksk"
        const val DEFAULT_OWNER_ID = 8714511542L
        const val DEFAULT_ADMIN_ID = 8714511542L
        const val DEFAULT_OWNER_USERNAME = "@ll_DARK_GETO_ll"
        const val DEFAULT_UPDATE_CHANNEL = "@geto_bot_hub"
    }
}
