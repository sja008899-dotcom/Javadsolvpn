package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun sourceDao(): SourceDao
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "javadsolvpn.db"
                ).addCallback(object : Callback() {
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
                                    name = "سرورهای عمومی و آزاد VPN Gate (OpenVPN)",
                                    url = "https://www.vpngate.net/api/iphone/",
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
