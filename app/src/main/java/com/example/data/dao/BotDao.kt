package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ActiveUserEntity
import com.example.data.model.AdminEntity
import com.example.data.model.HostedFileEntity
import com.example.data.model.LogEntryEntity
import com.example.data.model.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BotDao {

    // --- Hosted Files ---
    @Query("SELECT * FROM hosted_files ORDER BY uploadTime DESC")
    fun getAllFilesFlow(): Flow<List<HostedFileEntity>>

    @Query("SELECT * FROM hosted_files ORDER BY uploadTime DESC")
    suspend fun getAllFiles(): List<HostedFileEntity>

    @Query("SELECT * FROM hosted_files WHERE userId = :userId ORDER BY uploadTime DESC")
    suspend fun getFilesByUserId(userId: Long): List<HostedFileEntity>

    @Query("SELECT * FROM hosted_files WHERE userId = :userId AND fileName = :fileName LIMIT 1")
    suspend fun getFile(userId: Long, fileName: String): HostedFileEntity?

    @Query("SELECT * FROM hosted_files WHERE status = 'PENDING' ORDER BY uploadTime DESC")
    fun getPendingFilesFlow(): Flow<List<HostedFileEntity>>

    @Query("SELECT * FROM hosted_files WHERE status = 'PENDING' ORDER BY uploadTime DESC")
    suspend fun getPendingFiles(): List<HostedFileEntity>

    @Query("SELECT * FROM hosted_files WHERE status = 'RUNNING'")
    suspend fun getRunningFiles(): List<HostedFileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateFile(file: HostedFileEntity): Long

    @Update
    suspend fun updateFile(file: HostedFileEntity)

    @Query("DELETE FROM hosted_files WHERE userId = :userId AND fileName = :fileName")
    suspend fun deleteFile(userId: Long, fileName: String)

    @Query("DELETE FROM hosted_files WHERE id = :id")
    suspend fun deleteFileById(id: Long)

    @Query("UPDATE hosted_files SET status = :status, pid = :pid, lastStarted = :lastStarted WHERE userId = :userId AND fileName = :fileName")
    suspend fun updateFileStatus(userId: Long, fileName: String, status: String, pid: Int, lastStarted: Long?)

    @Query("UPDATE hosted_files SET executionLogs = :logs WHERE userId = :userId AND fileName = :fileName")
    suspend fun updateFileLogs(userId: Long, fileName: String, logs: String)

    // --- Active Users ---
    @Query("SELECT * FROM active_users ORDER BY lastSeenTime DESC")
    fun getAllUsersFlow(): Flow<List<ActiveUserEntity>>

    @Query("SELECT * FROM active_users ORDER BY lastSeenTime DESC")
    suspend fun getAllUsers(): List<ActiveUserEntity>

    @Query("SELECT * FROM active_users WHERE userId = :userId LIMIT 1")
    suspend fun getUser(userId: Long): ActiveUserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateUser(user: ActiveUserEntity)

    // --- Subscriptions ---
    @Query("SELECT * FROM subscriptions")
    fun getAllSubscriptionsFlow(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE userId = :userId LIMIT 1")
    suspend fun getSubscription(userId: Long): SubscriptionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSubscription(sub: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE userId = :userId")
    suspend fun deleteSubscription(userId: Long)

    // --- Admins ---
    @Query("SELECT * FROM admins ORDER BY addedAt ASC")
    fun getAllAdminsFlow(): Flow<List<AdminEntity>>

    @Query("SELECT * FROM admins ORDER BY addedAt ASC")
    suspend fun getAllAdmins(): List<AdminEntity>

    @Query("SELECT * FROM admins WHERE userId = :userId LIMIT 1")
    suspend fun getAdmin(userId: Long): AdminEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdmin(admin: AdminEntity)

    @Query("DELETE FROM admins WHERE userId = :userId AND isOwner = 0")
    suspend fun deleteAdmin(userId: Long): Int

    // --- Logs ---
    @Query("SELECT * FROM system_logs ORDER BY id DESC LIMIT 500")
    fun getRecentLogsFlow(): Flow<List<LogEntryEntity>>

    @Insert
    suspend fun insertLog(log: LogEntryEntity)

    @Query("DELETE FROM system_logs")
    suspend fun clearLogs()
}
