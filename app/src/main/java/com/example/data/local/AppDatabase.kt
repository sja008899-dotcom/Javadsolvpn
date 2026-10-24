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
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            val preloadedSources = listOf(
                                DiscoverySourceEntity(
                                    name = "هاب اختصاصی جواد (Javad Hub)",
                                    url = "https://raw.githubusercontent.com/yebekhe/TVC/main/subscriptions/xray/normal/mix",
                                    isEnabled = true
                                ),
                                DiscoverySourceEntity(
                                    name = "منبع عمومی کانفیگ‌های پرسرعت VLESS",
                                    url = "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Sub1.txt",
                                    isEnabled = true
                                ),
                                DiscoverySourceEntity(
                                    name = "مجموعه Trojan و Shadowsocks آزاد",
                                    url = "https://raw.githubusercontent.com/mahdibland/V2RayAggregator/master/sub/sub_merge.txt",
                                    isEnabled = true
                                ),
                                DiscoverySourceEntity(
                                    name = "منبع نودهای فعال V2Ray و SS",
                                    url = "https://raw.githubusercontent.com/freefq/free/master/v2",
                                    isEnabled = true
                                )
                            )
                            getInstance(context).sourceDao().insertSources(preloadedSources)
                        }
                    }
                }).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
