package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hosted_files")
data class HostedFileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,
    val fileName: String,
    val fileType: String, // "py", "js", "zip"
    val filePath: String,
    val scriptContent: String = "",
    val status: String = "STOPPED", // "RUNNING", "STOPPED", "PENDING"
    val uploadTime: Long = System.currentTimeMillis(),
    val lastStarted: Long? = null,
    val executionLogs: String = "",
    val pid: Int = 0
)

@Entity(tableName = "active_users")
data class ActiveUserEntity(
    @PrimaryKey
    val userId: Long,
    val username: String? = null,
    val firstName: String? = null,
    val joinTime: Long = System.currentTimeMillis(),
    val lastSeenTime: Long = System.currentTimeMillis(),
    val messageCount: Int = 1
)

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey
    val userId: Long,
    val expiryTimestamp: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "admins")
data class AdminEntity(
    @PrimaryKey
    val userId: Long,
    val addedAt: Long = System.currentTimeMillis(),
    val isOwner: Boolean = false
)

@Entity(tableName = "system_logs")
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val level: String, // "INFO", "WARN", "ERROR", "TG", "SCRIPT", "HTTP"
    val tag: String,
    val message: String
)
