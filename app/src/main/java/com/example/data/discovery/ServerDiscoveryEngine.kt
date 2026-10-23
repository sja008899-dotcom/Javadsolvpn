package com.example.data.discovery

import com.example.data.local.AppDatabase
import com.example.data.local.DiscoverySourceEntity
import com.example.data.local.ServerConfigEntity
import com.example.data.parser.ConfigParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class DiscoveryLogEntry(
    val timestamp: String,
    val level: String, // INFO, SUCCESS, WARN, ERROR
    val message: String
)

data class DiscoveryProgress(
    val isScanning: Boolean = false,
    val currentSource: String = "",
    val sourcesProcessed: Int = 0,
    val totalSources: Int = 0,
    val totalFoundRaw: Int = 0,
    val totalValidated: Int = 0,
    val totalDuplicatesRemoved: Int = 0,
    val recentLogs: List<DiscoveryLogEntry> = emptyList(),
    val latestDiscovered: ServerConfigEntity? = null
)

class ServerDiscoveryEngine(private val db: AppDatabase) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _progress = MutableStateFlow(DiscoveryProgress())
    val progress: StateFlow<DiscoveryProgress> = _progress.asStateFlow()

    private val logsList = mutableListOf<DiscoveryLogEntry>()

    private fun addLog(level: String, message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val entry = DiscoveryLogEntry(time, level, message)
        logsList.add(0, entry)
        if (logsList.size > 80) logsList.removeAt(logsList.lastIndex)
        _progress.value = _progress.value.copy(recentLogs = logsList.toList())
    }

    suspend fun runDiscovery(targetProtocol: String = "ALL"): Int = withContext(Dispatchers.IO) {
        if (_progress.value.isScanning) return@withContext 0

        logsList.clear()
        addLog("INFO", "آغاز کاوش خودکار سرورها (پروتکل هدف: $targetProtocol)…")

        val sources = db.sourceDao().getEnabledSources()
        _progress.value = DiscoveryProgress(
            isScanning = true,
            totalSources = sources.size,
            sourcesProcessed = 0,
            recentLogs = logsList.toList()
        )

        var totalInserted = 0
        var totalRaw = 0
        var duplicatesCount = 0
        val seenFingerprints = mutableSetOf<String>()

        // Preload existing fingerprints to avoid inserting duplicates
        val existingServers = db.serverDao().getBestCandidate() // trigger read
        
        var sourceIndex = 0
        for (source in sources) {
            sourceIndex++
            _progress.value = _progress.value.copy(
                currentSource = source.name,
                sourcesProcessed = sourceIndex
            )

            addLog("INFO", "در حال دریافت: ${source.name}…")
            try {
                val request = Request.Builder()
                    .url(source.url)
                    .header("User-Agent", "Javadsolvpn-DiscoveryEngine/2.0 (Android)")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        if (body.isNotBlank()) {
                            val parsedConfigs = ConfigParser.parsePayload(body, source.url)
                            totalRaw += parsedConfigs.size
                            addLog("INFO", "تعداد ${parsedConfigs.size} کانفیگ خام از ${source.name} استخراج شد.")

                            val filtered = parsedConfigs.filter { config ->
                                val matchProtocol = targetProtocol == "ALL" ||
                                    config.protocol.equals(targetProtocol, ignoreCase = true)
                                matchProtocol
                            }

                            val validUnique = mutableListOf<ServerConfigEntity>()
                            for (cfg in filtered) {
                                val fingerprint = "${cfg.protocol}:${cfg.host.lowercase().trim()}:${cfg.port}"
                                if (seenFingerprints.add(fingerprint)) {
                                    validUnique.add(cfg)
                                } else {
                                    duplicatesCount++
                                }
                            }

                            if (validUnique.isNotEmpty()) {
                                db.serverDao().insertAll(validUnique)
                                totalInserted += validUnique.size
                                val sample = validUnique.first()
                                addLog("SUCCESS", "✅ ${validUnique.size} نود معتبر ذخیره شد. آخرین: ${sample.name} (${sample.host})")
                                _progress.value = _progress.value.copy(latestDiscovered = sample)
                            }

                            db.sourceDao().updateFetchStatus(
                                source.id,
                                System.currentTimeMillis(),
                                validUnique.size,
                                "موفق (${validUnique.size} کانفیگ)"
                            )
                        } else {
                            addLog("WARN", "پاسخ خالی از منبع ${source.name}")
                        }
                    } else {
                        addLog("ERROR", "خطای دریافت از منبع ${source.name}: کد ${response.code}")
                    }
                }
            } catch (e: Exception) {
                addLog("ERROR", "عدم دسترسی به ${source.name}: ${e.message?.take(30)}")
            }

            _progress.value = _progress.value.copy(
                totalFoundRaw = totalRaw,
                totalValidated = totalInserted,
                totalDuplicatesRemoved = duplicatesCount
            )
            delay(300) // Brief yield for responsive UI
        }

        addLog("SUCCESS", "🏁 کاوش پایان یافت: $totalInserted نود جدید اضافه شد، $duplicatesCount تکراری حذف گردید.")
        _progress.value = _progress.value.copy(isScanning = false, currentSource = "پایان اسکن")
        totalInserted
    }
}
