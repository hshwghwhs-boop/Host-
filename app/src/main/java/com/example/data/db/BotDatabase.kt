package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.BotDao
import com.example.data.model.ActiveUserEntity
import com.example.data.model.AdminEntity
import com.example.data.model.HostedFileEntity
import com.example.data.model.LogEntryEntity
import com.example.data.model.SubscriptionEntity

@Database(
    entities = [
        HostedFileEntity::class,
        ActiveUserEntity::class,
        SubscriptionEntity::class,
        AdminEntity::class,
        LogEntryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class BotDatabase : RoomDatabase() {
    abstract fun botDao(): BotDao

    companion object {
        @Volatile
        private var INSTANCE: BotDatabase? = null

        fun getInstance(context: Context): BotDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BotDatabase::class.java,
                    "bot_host_database.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
