package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ServerConfigEntity::class,
        DiscoverySourceEntity::class,
        ConnectionHistoryEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun sourceDao(): SourceDao
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Reset legacy unverified records to unverified state
                db.execSQL("UPDATE server_configs SET isWorking = 0, pingMs = -1, lastTestedTime = 0")
                // Enforce strictly 12 servers maximum in active repository, preserving favorites and manual imports
                db.execSQL("""
                    DELETE FROM server_configs
                    WHERE id NOT IN (
                        SELECT id FROM server_configs
                        ORDER BY isFavorite DESC, isManual DESC, createdAt DESC, id ASC
                        LIMIT 12
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE server_configs SET isWorking = 0, pingMs = -1, lastTestedTime = 0")
                db.execSQL("""
                    DELETE FROM server_configs
                    WHERE id NOT IN (
                        SELECT id FROM server_configs
                        ORDER BY isFavorite DESC, isManual DESC, createdAt DESC, id ASC
                        LIMIT 12
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "javadsolvpn.db"
                )
                .addMigrations(MIGRATION_2_3, MIGRATION_1_3)
                .fallbackToDestructiveMigration()
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                INSTANCE?.seedInitialDataIfNeeded()
                            } catch (_: Exception) {}
                        }
                    }
                }).build()
                INSTANCE = instance
                instance
            }
        }
    }

    suspend fun seedInitialDataIfNeeded() {
        val enabledSources = sourceDao().getEnabledSources()
        if (enabledSources.isEmpty()) {
            sourceDao().insertSources(PreloadedConfigs.DEFAULT_SOURCES)
        }

        val serverCount = serverDao().getServerCountNow()
        if (serverCount == 0) {
            serverDao().insertCapped(PreloadedConfigs.DEFAULT_SERVERS, ServerLimits.MAX_ACTIVE_CONFIGS)
        }
    }
}
