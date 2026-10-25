package com.example.data.discovery

import com.example.data.local.AppDatabase
import com.example.data.local.ConfigFingerprint
import com.example.data.local.DiscoverySourceEntity
import com.example.data.local.ServerConfigEntity
import com.example.data.local.ServerLimits
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
        addLog("INFO", "آغاز کاوش هوشمند (ظرفیت مخزن: حداکثر ${ServerLimits.MAX_ACTIVE_CONFIGS} سرور)…")

        // Enforce 12-server cap initially on existing records
        db.serverDao().trimToLimit(ServerLimits.MAX_ACTIVE_CONFIGS)

        db.seedInitialDataIfNeeded()
        var sources = db.sourceDao().getEnabledSources()
        if (sources.isEmpty()) {
            db.sourceDao().insertSources(com.example.data.local.PreloadedConfigs.DEFAULT_SOURCES)
            sources = db.sourceDao().getEnabledSources()
        }

        _progress.value = DiscoveryProgress(
            isScanning = true,
            totalSources = sources.size,
            sourcesProcessed = 0,
            recentLogs = logsList.toList()
        )

        var totalInserted = 0
        var totalRaw = 0
        var duplicatesCount = 0

        // Preload complete fingerprints across all active records
        val seenFingerprints = db.serverDao().getAllServersOnce()
            .mapTo(mutableSetOf()) { ConfigFingerprint.of(it) }

        var sourceIndex = 0
        for (source in sources) {
            val currentCount = db.serverDao().getServerCountNow()
            val replaceableBroken = db.serverDao().getReplaceableBrokenId()
            if (currentCount >= ServerLimits.MAX_ACTIVE_CONFIGS && replaceableBroken == null) {
                addLog("SUCCESS", "ظرفیت مخزن فعال تکمیل و تمام ${ServerLimits.MAX_ACTIVE_CONFIGS} سرور در وضعیت سالم هستند.")
                break
            }

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
                            addLog("INFO", "تعداد ${parsedConfigs.size} لینک از ${source.name} استخراج شد.")

                            val filtered = parsedConfigs.filter { config ->
                                targetProtocol == "ALL" || config.protocol.equals(targetProtocol, ignoreCase = true)
                            }

                            val validUnique = mutableListOf<ServerConfigEntity>()
                            for (cfg in filtered) {
                                val fingerprint = ConfigFingerprint.of(cfg)
                                if (seenFingerprints.add(fingerprint)) {
                                    validUnique.add(
                                        cfg.copy(
                                            isWorking = false,
                                            pingMs = -1L,
                                            lastTestedTime = 0L
                                        )
                                    )
                                } else {
                                    duplicatesCount++
                                }
                            }

                            if (validUnique.isNotEmpty()) {
                                val activeCount = db.serverDao().getServerCountNow()
                                if (activeCount < ServerLimits.MAX_ACTIVE_CONFIGS) {
                                    val inserted = db.serverDao().insertCapped(validUnique, ServerLimits.MAX_ACTIVE_CONFIGS)
                                    totalInserted += inserted
                                    if (inserted > 0) {
                                        val sample = validUnique.first()
                                        addLog("SUCCESS", "✅ $inserted نود جدید در مخزن فعال (سقف ${ServerLimits.MAX_ACTIVE_CONFIGS}) ذخیره شد.")
                                        _progress.value = _progress.value.copy(latestDiscovered = sample)
                                    }
                                } else {
                                    // Pool is at 12; probe candidates to replace broken inactive nodes
                                    for (cand in validUnique) {
                                        if (db.serverDao().getReplaceableBrokenId() == null) break
                                        val ping = com.example.data.prober.ServerProber.probe(cand, 2000)
                                        if (ping > 0L) {
                                            val replaced = db.serverDao().insertVerifiedReplacingBroken(
                                                cand.copy(pingMs = ping, isWorking = true, lastTestedTime = System.currentTimeMillis()),
                                                ServerLimits.MAX_ACTIVE_CONFIGS
                                            )
                                            if (replaced) {
                                                totalInserted++
                                                addLog("SUCCESS", "⚡ سرور سالم با پینگ ${ping}ms جایگزین سرور معیوب قبلی شد.")
                                                _progress.value = _progress.value.copy(latestDiscovered = cand)
                                            }
                                        }
                                    }
                                }
                            }

                            db.sourceDao().updateFetchStatus(
                                source.id,
                                System.currentTimeMillis(),
                                validUnique.size,
                                "موفق ($totalInserted نود افزوده شد)"
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
            delay(200)
        }

        val finalCount = db.serverDao().getServerCountNow()
        addLog("SUCCESS", "🏁 کاوش پایان یافت: $totalInserted نود جدید اضافه شد، $duplicatesCount تکراری رد شد. مجموع مخزن فعال: $finalCount از ${ServerLimits.MAX_ACTIVE_CONFIGS}")
        _progress.value = _progress.value.copy(isScanning = false, currentSource = "پایان اسکن")
        totalInserted
    }
}
