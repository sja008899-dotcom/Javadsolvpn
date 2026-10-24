package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.ConnectionHistoryEntity
import com.example.data.local.DiscoverySourceEntity
import com.example.data.local.ServerConfigEntity
import com.example.data.parser.ConfigParser
import com.example.data.prober.ServerProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class VpnRepository(private val db: AppDatabase) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val allServers: Flow<List<ServerConfigEntity>> = db.serverDao().getAllServers()
    val favoriteServers: Flow<List<ServerConfigEntity>> = db.serverDao().getFavoriteServers()
    val allSources: Flow<List<DiscoverySourceEntity>> = db.sourceDao().getAllSources()
    val connectionHistory: Flow<List<ConnectionHistoryEntity>> = db.historyDao().getAllHistory()

    suspend fun getBestCandidate(): ServerConfigEntity? = withContext(Dispatchers.IO) {
        db.serverDao().getBestCandidate()
    }

    suspend fun getNextCandidate(excludeId: Long): ServerConfigEntity? = withContext(Dispatchers.IO) {
        db.serverDao().getNextCandidate(excludeId)
    }

    suspend fun toggleFavorite(id: Long, currentVal: Boolean) = withContext(Dispatchers.IO) {
        db.serverDao().setFavorite(id, !currentVal)
    }

    suspend fun deleteServer(id: Long) = withContext(Dispatchers.IO) {
        db.serverDao().deleteServer(id)
    }

    suspend fun clearAllServers() = withContext(Dispatchers.IO) {
        db.serverDao().deleteAll()
    }

    suspend fun importRawConfig(raw: String): Int = withContext(Dispatchers.IO) {
        val parsed = ConfigParser.parsePayload(raw, "Manual Import")
        if (parsed.isNotEmpty()) {
            val markedManual = parsed.map { it.copy(isManual = true) }
            db.serverDao().insertAll(markedManual)
        }
        parsed.size
    }

    suspend fun addSource(name: String, url: String) = withContext(Dispatchers.IO) {
        db.sourceDao().insertSource(
            DiscoverySourceEntity(name = name, url = url, isEnabled = true)
        )
    }

    suspend fun toggleSource(id: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        db.sourceDao().setEnabled(id, enabled)
    }

    suspend fun deleteSource(id: Long) = withContext(Dispatchers.IO) {
        db.sourceDao().deleteSource(id)
    }

    suspend fun refreshAllSources(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val sources = db.sourceDao().getEnabledSources()
        var totalAdded = 0
        var successfulSources = 0

        for (source in sources) {
            try {
                val request = Request.Builder()
                    .url(source.url)
                    .header("User-Agent", "Javadsolvpn/2.0 (Android)")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        if (body.isNotBlank()) {
                            val configs = ConfigParser.parsePayload(body, source.url)
                            if (configs.isNotEmpty()) {
                                db.serverDao().insertAll(configs)
                                totalAdded += configs.size
                                successfulSources++
                                db.sourceDao().updateFetchStatus(
                                    source.id,
                                    System.currentTimeMillis(),
                                    configs.size,
                                    "موفق (${configs.size} کانفیگ)"
                                )
                            } else {
                                db.sourceDao().updateFetchStatus(
                                    source.id,
                                    System.currentTimeMillis(),
                                    0,
                                    "کانفیگ سازگاری یافت نشد"
                                )
                            }
                        }
                    } else {
                        db.sourceDao().updateFetchStatus(
                            source.id,
                            System.currentTimeMillis(),
                            0,
                            "خطای سرور: ${response.code}"
                        )
                    }
                }
            } catch (e: Exception) {
                db.sourceDao().updateFetchStatus(
                    source.id,
                    System.currentTimeMillis(),
                    0,
                    "عدم پاسخ: ${e.message?.take(20) ?: "خطای شبکه"}"
                )
            }
        }
        Pair(totalAdded, successfulSources)
    }

    suspend fun testLatency(server: ServerConfigEntity): Long = withContext(Dispatchers.IO) {
        val ping = ServerProber.probe(server)
        val isWorking = ping > 0
        db.serverDao().updatePingAndStatus(
            server.id,
            ping,
            isWorking,
            System.currentTimeMillis()
        )
        ping
    }

    suspend fun testAllLatencies(servers: List<ServerConfigEntity>) = withContext(Dispatchers.IO) {
        coroutineScope {
            val jobs = servers.map { server ->
                async {
                    testLatency(server)
                }
            }
            jobs.awaitAll()
        }
        // Retain top 100 fastest healthy servers, preserving all favorites and manual imports
        db.serverDao().trimToTopHealthy(100)
    }
}
