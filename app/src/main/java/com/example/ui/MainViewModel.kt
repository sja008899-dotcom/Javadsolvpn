package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.ConnectionHistoryEntity
import com.example.data.local.DiscoverySourceEntity
import com.example.data.local.ServerConfigEntity
import com.example.data.repository.VpnRepository
import com.example.vpn.JavadVpnService
import com.example.vpn.TrafficStats
import com.example.vpn.VpnStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = VpnRepository(db)
    val discoveryEngine = com.example.data.discovery.ServerDiscoveryEngine(db)
    val coreEngine = com.example.vpn.core.DefaultVpnCoreEngine.instance

    // VPN Service and Core Status
    val vpnStatus: StateFlow<VpnStatus> = JavadVpnService.vpnStatus
    val coreState: StateFlow<com.example.vpn.core.VpnCoreState> = coreEngine.coreState
    val engineStats: StateFlow<com.example.vpn.core.EngineStats> = coreEngine.engineStats
    val activeServer: StateFlow<ServerConfigEntity?> = JavadVpnService.activeServer
    val trafficStats: StateFlow<TrafficStats> = JavadVpnService.trafficStats
    val errorMessage: StateFlow<String?> = JavadVpnService.errorMessage
    val discoveryProgress = discoveryEngine.progress

    // Navigation and Filtering
    val currentTab = MutableStateFlow(0) // 0: Home, 1: Servers, 2: Smart Hub, 3: Activity, 4: Settings
    val searchQuery = MutableStateFlow("")
    val selectedProtocol = MutableStateFlow("ALL") // ALL, VLESS, VMESS, TROJAN, SS
    val selectedCountry = MutableStateFlow("ALL")
    val onlyFavorites = MutableStateFlow(false)

    // Current Selected Server for manual or smart connection
    val selectedServer = MutableStateFlow<ServerConfigEntity?>(null)

    // User Preferences
    val preferredDns = MutableStateFlow("1.1.1.1")
    val autoConnectOnLaunch = MutableStateFlow(false)
    val autoFailover = MutableStateFlow(true)
    val appLanguage = MutableStateFlow("FA") // FA, EN

    // Operation progress
    val isDiscovering = MutableStateFlow(false)
    val isTestingPing = MutableStateFlow(false)
    val isFetchingRepository = MutableStateFlow(false)
    val bannerMessage = MutableStateFlow<String?>(null)

    val allServers: StateFlow<List<ServerConfigEntity>> = repository.allServers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSources: StateFlow<List<DiscoverySourceEntity>> = repository.allSources
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val connectionHistory: StateFlow<List<ConnectionHistoryEntity>> = repository.connectionHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered Servers
    val filteredServers: StateFlow<List<ServerConfigEntity>> = combine(
        allServers,
        searchQuery,
        selectedProtocol,
        selectedCountry,
        onlyFavorites
    ) { list, query, protocol, country, favOnly ->
        list.filter { server ->
            val matchQuery = query.isBlank() ||
                server.name.contains(query, ignoreCase = true) ||
                server.countryNameFa.contains(query, ignoreCase = true) ||
                server.countryNameEn.contains(query, ignoreCase = true) ||
                server.host.contains(query, ignoreCase = true)

            val matchProtocol = protocol == "ALL" || server.protocol.equals(protocol, ignoreCase = true)
            val matchCountry = country == "ALL" || server.countryCode.equals(country, ignoreCase = true)
            val matchFav = !favOnly || server.isFavorite

            matchQuery && matchProtocol && matchCountry && matchFav
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            allServers.collect { list ->
                if (selectedServer.value == null && list.isNotEmpty()) {
                    selectedServer.value = list.firstOrNull { it.isFavorite } ?: list.first()
                }
            }
        }
    }

    fun selectServer(server: ServerConfigEntity) {
        selectedServer.value = server
    }

    fun connect(context: Context, serverToConnect: ServerConfigEntity? = null) {
        val target = serverToConnect ?: selectedServer.value
        if (target != null) {
            selectedServer.value = target
            JavadVpnService.startVpn(context, target.id, preferredDns.value)
        } else {
            smartConnect(context)
        }
    }

    fun smartConnect(context: Context) {
        viewModelScope.launch {
            val best = repository.getBestCandidate()
            if (best != null) {
                selectedServer.value = best
                JavadVpnService.startVpn(context, best.id, preferredDns.value)
            } else {
                bannerMessage.value = "هیچ سروری برای اتصال یافت نشد. لطفاً از بخش هوشمند هاب را بروزرسانی کنید."
            }
        }
    }

    fun disconnect(context: Context) {
        JavadVpnService.stopVpn(context)
    }

    fun toggleFavorite(server: ServerConfigEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(server.id, server.isFavorite)
        }
    }

    fun deleteServer(server: ServerConfigEntity) {
        viewModelScope.launch {
            repository.deleteServer(server.id)
            if (selectedServer.value?.id == server.id) {
                selectedServer.value = null
            }
        }
    }

    fun runDiscovery(targetProtocol: String = "ALL") {
        viewModelScope.launch {
            isDiscovering.value = true
            try {
                val inserted = discoveryEngine.runDiscovery(targetProtocol)
                bannerMessage.value = if (inserted > 0) {
                    "$inserted کانفیگ جدید با موفقیت کشف و به انبار اضافه شد."
                } else {
                    "کاوش تکمیل شد. تمام کانفیگ‌های فعال هم‌اکنون در انبار موجودند."
                }
            } catch (e: Exception) {
                bannerMessage.value = "خطا در کاوش: ${e.localizedMessage}"
            } finally {
                isDiscovering.value = false
            }
        }
    }

    fun refreshDiscovery() {
        viewModelScope.launch {
            isDiscovering.value = true
            bannerMessage.value = "در حال بروزرسانی هاب و دریافت جدیدترین کانفیگ‌ها…"
            try {
                val (added, sources) = repository.refreshAllSources()
                bannerMessage.value = if (added > 0) {
                    "$added کانفیگ جدید از $sources منبع با موفقیت اضافه شد."
                } else {
                    "کانفیگ‌ها بروز هستند. همه منابع بررسی شدند."
                }
            } catch (e: Exception) {
                bannerMessage.value = "خطا در دریافت: ${e.localizedMessage}"
            } finally {
                isDiscovering.value = false
            }
        }
    }

    fun pingAll() {
        viewModelScope.launch {
            isTestingPing.value = true
            bannerMessage.value = "در حال اندازه‌گیری پینگ واقعی سرورها…"
            try {
                repository.testAllLatencies(allServers.value)
                bannerMessage.value = "تست پینگ سرورها با موفقیت تکمیل شد."
            } catch (e: Exception) {
                bannerMessage.value = "خطا در تست پینگ: ${e.localizedMessage}"
            } finally {
                isTestingPing.value = false
            }
        }
    }

    /**
     * Fetches up to 12 top-ranked public configs per tap, with a hard 110-link daily download quota.
     * The local active pool remains capped at 12 by design.
     */
    fun fetchLatestRepositoryConfigs() {
        if (isFetchingRepository.value) return
        viewModelScope.launch {
            isFetchingRepository.value = true
            try {
                val prefs = getApplication<Application>().getSharedPreferences("config_repository_quota", Context.MODE_PRIVATE)
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                val savedDay = prefs.getString("day", "")
                val used = if (savedDay == today) prefs.getInt("used", 0) else 0
                val remaining = (110 - used).coerceAtLeast(0)
                if (remaining == 0) {
                    bannerMessage.value = "سهمیهٔ دریافت روزانهٔ ۱۱۰ کانفیگ تمام شده است؛ فردا دوباره امتحان کنید."
                    return@launch
                }

                bannerMessage.value = "در حال دریافت تازه‌ترین کانفیگ‌های رتبه‌بندی‌شده…"
                val connection = (URL("https://raw.githubusercontent.com/sja008899-dotcom/Javadsolvpn/main/config-repository/top20.txt").openConnection() as HttpURLConnection)
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 15000
                connection.setRequestProperty("User-Agent", "Javadsolvpn-Android")
                val body = try {
                    if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP ${connection.responseCode}")
                    connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } finally {
                    connection.disconnect()
                }
                val supported = body.lineSequence().map { it.trim() }
                    .filter { line -> line.startsWith("vless://") || line.startsWith("vmess://") || line.startsWith("trojan://") || line.startsWith("ss://") }
                    .distinct().toList()
                val batch = supported.take(minOf(12, remaining))
                if (batch.isEmpty()) {
                    bannerMessage.value = "مخزن هنوز کانفیگ تأییدشده‌ای منتشر نکرده است؛ بعداً دوباره تلاش کنید."
                    return@launch
                }

                val inserted = repository.importRawConfig(batch.joinToString("\n"))
                prefs.edit().putString("day", today).putInt("used", used + batch.size).apply()
                if (inserted > 0) {
                    selectedServer.value = db.serverDao().getAllServersOnce()
                        .filter { it.isManual }
                        .maxByOrNull { it.id }
                    currentTab.value = 1
                    bannerMessage.value = "${batch.size} کانفیگ از مخزن دریافت شد؛ ${inserted} مورد جدید به فهرست آزمایش اضافه شد. سهمیهٔ امروز: ${used + batch.size}/110."
                } else {
                    bannerMessage.value = "کانفیگ‌ها دریافت شدند اما مورد جدیدی اضافه نشد؛ احتمالاً تکراری‌اند یا جایگاه‌های دستی/علاقه‌مندی پر است. سهمیه: ${used + batch.size}/110."
                }
            } catch (e: Exception) {
                bannerMessage.value = "دریافت از مخزن ناموفق بود: ${e.localizedMessage ?: "خطای شبکه"}"
            } finally {
                isFetchingRepository.value = false
            }
        }
    }

    fun importConfig(raw: String) {
        viewModelScope.launch {
            val count = repository.importRawConfig(raw)
            if (count > 0) {
                // Select the most recently inserted manual config so it is ready to test.
                selectedServer.value = db.serverDao().getAllServersOnce()
                    .filter { it.isManual }
                    .maxByOrNull { it.id }
                currentTab.value = 1
                bannerMessage.value = "$count کانفیگ وارد شد و برای آزمایش انتخاب شد؛ اکنون می‌توانید اتصال را شروع کنید."
            } else {
                bannerMessage.value = "کانفیگ جدیدی وارد نشد (ممکن است تکراری/نامعتبر باشد یا همهٔ ۱۲ جایگاه به موارد دستی/علاقه‌مندی اختصاص یافته باشند)."
            }
        }
    }

    fun addSource(name: String, url: String) {
        viewModelScope.launch {
            if (url.isNotBlank()) {
                repository.addSource(name.ifBlank { "منبع سفارشی" }, url)
                bannerMessage.value = "منبع جدید اضافه شد."
            }
        }
    }

    fun toggleSource(source: DiscoverySourceEntity) {
        viewModelScope.launch {
            repository.toggleSource(source.id, !source.isEnabled)
        }
    }

    fun deleteSource(source: DiscoverySourceEntity) {
        viewModelScope.launch {
            repository.deleteSource(source.id)
        }
    }

    fun clearBanner() {
        bannerMessage.value = null
    }

    // Safe Mode Diagnostics
    val diagnosticReport = com.example.vpn.diagnostic.SafeModeDiagnosticManager.report
    val showDiagnosticDialog = MutableStateFlow(false)

    fun startSafeModeDiagnostic(context: Context, targetServer: ServerConfigEntity? = null) {
        showDiagnosticDialog.value = true
        val vpnPrepareIntent = android.net.VpnService.prepare(context)
        if (vpnPrepareIntent == null) {
            // Permission already granted, launch via service to establish loopback TUN and test FD binding
            JavadVpnService.runSafeModeDiagnostic(context, targetServer?.id ?: -1L)
        } else {
            // Permission needed for TUN binding; run baseline environment diagnostic
            viewModelScope.launch {
                com.example.vpn.diagnostic.SafeModeDiagnosticManager.runDiagnosticRoutine(
                    context = context,
                    vpnServiceInstance = null,
                    targetServerForProbe = targetServer
                )
            }
        }
    }

    fun testNextServerInDiagnostic(context: Context) {
        val nextId = diagnosticReport.value.nextCandidateId ?: return
        viewModelScope.launch {
            val nextServer = db.serverDao().getServerById(nextId)
            if (nextServer != null) {
                selectedServer.value = nextServer
                startSafeModeDiagnostic(context, nextServer)
            }
        }
    }

    fun dismissDiagnosticDialog() {
        showDiagnosticDialog.value = false
    }
}
