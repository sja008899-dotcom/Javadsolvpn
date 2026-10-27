package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.TrafficStats as AndroidTrafficStats
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.ConnectionHistoryEntity
import com.example.data.local.ServerConfigEntity
import com.example.vpn.core.CoreConfigBuilder
import com.example.vpn.core.DefaultVpnCoreEngine
import com.example.vpn.core.EngineStats
import com.example.vpn.core.TunnelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

class JavadVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var statsJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    private var coreController: CoreController? = null
    private var isCoreEnvInitialized = false

    private var connectTimeMillis: Long = 0L
    private var initialUidRxBytes: Long = 0L
    private var initialUidTxBytes: Long = 0L
    private var lastMeasuredRx: Long = 0L
    private var lastMeasuredTx: Long = 0L
    private var lastStatsSampleTime: Long = 0L

    private val triedServersInSession = mutableSetOf<Long>()
    // Invalidates asynchronous health checks when the user disconnects or starts a new session.
    @Volatile private var connectionGeneration: Long = 0L

    private val verificationClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    companion object {
        const val ACTION_CONNECT = "com.example.vpn.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.example.vpn.ACTION_DISCONNECT"
        const val ACTION_SAFE_MODE_DIAGNOSTIC = "com.example.vpn.ACTION_SAFE_MODE_DIAGNOSTIC"
        const val EXTRA_SERVER_ID = "extra_server_id"
        const val EXTRA_DNS = "extra_dns"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "javad_vpn_channel"

        private val _vpnStatus = MutableStateFlow(VpnStatus.IDLE)
        val vpnStatus: StateFlow<VpnStatus> = _vpnStatus.asStateFlow()

        private val _activeServer = MutableStateFlow<ServerConfigEntity?>(null)
        val activeServer: StateFlow<ServerConfigEntity?> = _activeServer.asStateFlow()

        private val _trafficStats = MutableStateFlow(TrafficStats())
        val trafficStats: StateFlow<TrafficStats> = _trafficStats.asStateFlow()

        private val _errorMessage = MutableStateFlow<String?>(null)
        val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

        fun startVpn(context: Context, serverId: Long, preferredDns: String = "1.1.1.1") {
            val intent = Intent(context, JavadVpnService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_SERVER_ID, serverId)
                putExtra(EXTRA_DNS, preferredDns)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopVpn(context: Context) {
            val intent = Intent(context, JavadVpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            context.startService(intent)
        }

        fun runSafeModeDiagnostic(context: Context, serverId: Long = -1L) {
            val intent = Intent(context, JavadVpnService::class.java).apply {
                action = ACTION_SAFE_MODE_DIAGNOSTIC
                putExtra(EXTRA_SERVER_ID, serverId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initCore()
    }

    private fun initCore() {
        if (!isCoreEnvInitialized) {
            try {
                extractCoreAssets()
                Libv2ray.initCoreEnv(filesDir.absolutePath, "")
                isCoreEnvInitialized = true
            } catch (_: Throwable) {}
        }
    }

    private fun extractCoreAssets() {
        val assetNames = listOf("geoip.dat", "geosite.dat", "geoip-only-cn-private.dat")
        for (name in assetNames) {
            val destFile = java.io.File(filesDir, name)
            if (!destFile.exists() || destFile.length() == 0L) {
                try {
                    assets.open(name).use { input ->
                        java.io.FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val serverId = intent.getLongExtra(EXTRA_SERVER_ID, -1L)
                val preferredDns = intent.getStringExtra(EXTRA_DNS) ?: "1.1.1.1"
                connectionGeneration += 1
                triedServersInSession.clear()
                initiateConnection(serverId, preferredDns, connectionGeneration)
            }
            ACTION_DISCONNECT -> {
                terminateVpn("قطع اتصال توسط کاربر")
            }
            ACTION_SAFE_MODE_DIAGNOSTIC -> {
                val serverId = intent.getLongExtra(EXTRA_SERVER_ID, -1L)
                startForeground(NOTIFICATION_ID, buildNotification("حالت عیب‌یابی امن", "در حال ارزیابی هسته Xray و File Descriptor…"))
                serviceScope.launch {
                    val db = AppDatabase.getInstance(applicationContext)
                    val server = if (serverId > 0) db.serverDao().getServerById(serverId) else db.serverDao().getBestCandidate()
                    com.example.vpn.diagnostic.SafeModeDiagnosticManager.runDiagnosticRoutine(
                        context = this@JavadVpnService,
                        vpnServiceInstance = this@JavadVpnService,
                        targetServerForProbe = server
                    )
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun initiateConnection(serverId: Long, dns: String, generation: Long = connectionGeneration) {
        _vpnStatus.value = VpnStatus.CONNECTING
        _errorMessage.value = null
        startForeground(NOTIFICATION_ID, buildNotification("در حال اتصال…", "آماده‌سازی هسته شبکه"))

        serviceScope.launch {
            if (generation != connectionGeneration) return@launch
            val db = AppDatabase.getInstance(applicationContext)
            var targetServer = if (serverId > 0) db.serverDao().getServerById(serverId) else null
            if (targetServer == null) {
                targetServer = db.serverDao().getBestCandidate()
            }

            if (generation != connectionGeneration) return@launch

            if (targetServer == null) {
                _vpnStatus.value = VpnStatus.ERROR
                _errorMessage.value = "هیچ کانفیگ سالمی برای اتصال یافت نشد."
                stopSelf()
                return@launch
            }

            triedServersInSession.add(targetServer.id)
            _activeServer.value = targetServer

            try {
                // 1. Pre-resolve server domain to IP to avoid DNS deadlock
                withContext(Dispatchers.IO) {
                    try {
                        InetAddress.getAllByName(targetServer.host)
                    } catch (_: Exception) {}
                }

                // 2. Prepare Xray Core JSON
                val configJson = CoreConfigBuilder.buildConfigJson(targetServer)

                if (generation != connectionGeneration) return@launch

                // 3. Prepare parameters for Android VpnService Builder
                val dnsList = when (dns) {
                    "8.8.8.8" -> listOf("8.8.8.8", "8.8.4.4")
                    "9.9.9.9" -> listOf("9.9.9.9", "149.112.112.112")
                    else -> listOf("1.1.1.1", "1.0.0.1")
                }

                val tunnelConfig = TunnelConfig(server = targetServer, dnsServers = dnsList)
                val paramsResult = DefaultVpnCoreEngine.instance.prepareSession(tunnelConfig)
                val params = paramsResult.getOrThrow()
                if (generation != connectionGeneration) return@launch

                val builder = Builder()
                    .setSession("Javadsolvpn - ${targetServer.name}")
                    .addAddress(params.virtualIp, params.routingPrefix)
                    .addRoute("0.0.0.0", 0)
                    .setMtu(params.mtu)
                    .setBlocking(false)

                // Add IPv6 route to prevent IPv6 leakage
                try {
                    builder.addRoute("::", 0)
                } catch (_: Exception) {}

                // Exempt this app's own process to prevent core outbound socket loops
                try {
                    builder.addDisallowedApplication(packageName)
                } catch (_: Exception) {}

                params.dnsServers.forEach { builder.addDnsServer(it) }

                // Close existing tunnel if any
                stopCore()
                try { vpnInterface?.close() } catch (_: Exception) {}

                val pfd = builder.establish()
                if (pfd == null) {
                    _vpnStatus.value = VpnStatus.ERROR
                    _errorMessage.value = "خطا در ایجاد رابط VPN سیستم (مجوز سیستم صادر نشد)"
                    stopSelf()
                    return@launch
                }
                vpnInterface = pfd

                // 4. Start Core Loop with the real TUN file descriptor
                val callback = object : CoreCallbackHandler {
                    override fun onEmitStatus(status: Long, msg: String?): Long = 0L
                    override fun startup(): Long = 0L
                    override fun shutdown(): Long = 0L
                }

                val controller = Libv2ray.newCoreController(callback)
                coreController = controller
                controller.startLoop(configJson, pfd.fd)

                // 4. Tunnel and Core are established successfully!
                connectTimeMillis = System.currentTimeMillis()
                val myUid = Process.myUid()
                initialUidRxBytes = AndroidTrafficStats.getUidRxBytes(myUid).coerceAtLeast(0L)
                initialUidTxBytes = AndroidTrafficStats.getUidTxBytes(myUid).coerceAtLeast(0L)
                lastMeasuredRx = 0L
                lastMeasuredTx = 0L
                lastStatsSampleTime = System.currentTimeMillis()

                DefaultVpnCoreEngine.instance.establishSession(params)
                _vpnStatus.value = VpnStatus.CONNECTED
                _errorMessage.value = null

                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(
                        "متصل به ${targetServer.name}",
                        "پروتکل: ${params.protocol} • تونل رمزگذاری‌شده فعال است"
                    )
                )

                startStatsTicker()

                // 5. Asynchronous background live health check (does not abruptly kill user's VPN if mobile latency is high)
                serviceScope.launch {
                    delay(1500)
                    if (generation != connectionGeneration) return@launch
                    val isAlive = verifyLiveInternet()
                    if (generation != connectionGeneration) return@launch
                    if (isAlive) {
                        db.serverDao().recordSuccess(targetServer.id)
                        startForeground(
                            NOTIFICATION_ID,
                            buildNotification(
                                "متصل به ${targetServer.name}",
                                "پروتکل: ${params.protocol} • اینترنت آزاد تأیید شد"
                            )
                        )
                    } else {
                        db.serverDao().recordFailure(targetServer.id)
                        // A started core is not proof of usable internet. Tear it down and
                        // try the next untested candidate, with a hard cap of 12 attempts.
                        stopCore()
                        try { vpnInterface?.close() } catch (_: Exception) {}
                        vpnInterface = null
                        DefaultVpnCoreEngine.instance.terminateSession("آزمون اینترنت ناموفق بود")
                        retryNextServer(db, dns, targetServer.id)
                    }
                }

            } catch (e: Exception) {
                if (generation != connectionGeneration) return@launch
                stopCore()
                try { vpnInterface?.close() } catch (_: Exception) {}
                vpnInterface = null
                _vpnStatus.value = VpnStatus.ERROR
                _errorMessage.value = "خطا در راه‌اندازی هسته: ${e.localizedMessage ?: "عدم ارتباط"}"
                db.serverDao().recordFailure(targetServer.id)
                retryNextServer(db, dns, targetServer.id)
            }
        }
    }

    private suspend fun retryNextServer(
        db: AppDatabase,
        dns: String,
        failedServerId: Long
    ) {
        // Hard session-wide cap: never attempt more than 12 distinct server IDs.
        if (triedServersInSession.size >= 12) {
            _vpnStatus.value = VpnStatus.ERROR
            _errorMessage.value = "حداکثر ۱۲ سرور آزمایش شد؛ اتصال واقعی برقرار نشد."
            _activeServer.value = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val next = db.serverDao().getAllServersOnce()
            .asSequence()
            .filter { it.id != failedServerId && it.id !in triedServersInSession }
            .firstOrNull()
        if (next != null) {
            triedServersInSession.add(next.id)
            _vpnStatus.value = VpnStatus.CONNECTING
            _errorMessage.value = "این سرور پاسخ نداد؛ در حال آزمایش سرور جایگزین (" + triedServersInSession.size + "/12)"
            _activeServer.value = null
            initiateConnection(next.id, dns, connectionGeneration)
        } else {
            _vpnStatus.value = VpnStatus.ERROR
            _errorMessage.value = "۱۲ تلاش یا همهٔ کانفیگ‌های موجود آزمایش شدند؛ اتصال واقعی برقرار نشد."
            _activeServer.value = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun verifyLiveInternet(): Boolean = withContext(Dispatchers.IO) {
        // 1. Direct core delay measurement if supported by CoreController
        try {
            val delay = coreController?.measureDelay("http://cp.cloudflare.com/generate_204") ?: -1L
            if (delay > 0) {
                return@withContext true
            }
        } catch (_: Exception) {}

        // 2. HTTP/HTTPS probe through proxy loopback
        val testUrls = listOf(
            "http://1.1.1.1/generate_204",
            "http://cp.cloudflare.com/generate_204",
            "http://www.gstatic.com/generate_204",
            "https://www.gstatic.com/generate_204",
            "https://connectivitycheck.gstatic.com/generate_204"
        )
        // Strictly route verification requests through the core's proxy inbounds
        // Proves that traffic is routed through the proxy outbound and not through unproxied direct internet
        val httpProxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 10809))
        val socksProxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 10808))
        val clients = listOf(
            verificationClient.newBuilder().proxy(httpProxy).build(),
            verificationClient.newBuilder().proxy(socksProxy).build()
        )

        for (attempt in 1..3) {
            for (client in clients) {
                for (url in testUrls) {
                    try {
                        val req = Request.Builder()
                            .url(url)
                            .header("User-Agent", "Mozilla/5.0 Javadsolvpn/2.0")
                            .build()
                        client.newCall(req).execute().use { resp ->
                            if (resp.code == 204 || resp.code == 200) {
                                return@withContext true
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
            if (attempt < 3) delay(600)
        }
        false
    }

    private fun startStatsTicker() {
        statsJob?.cancel()
        statsJob = serviceScope.launch {
            while (isActive && _vpnStatus.value == VpnStatus.CONNECTED) {
                delay(1000)
                val now = System.currentTimeMillis()
                val durationSec = (now - connectTimeMillis) / 1000

                var currentRxBytes = 0L
                var currentTxBytes = 0L

                // 1. Try reading genuine stats from CoreController
                val coreStatsJson = try {
                    coreController?.queryAllOutboundTrafficStats()
                } catch (_: Exception) { null }

                if (!coreStatsJson.isNullOrBlank()) {
                    try {
                        val json = JSONObject(coreStatsJson)
                        val proxyObj = json.optJSONObject("proxy")
                        if (proxyObj != null) {
                            currentTxBytes = proxyObj.optLong("link", 0L)
                            currentRxBytes = proxyObj.optLong("downlink", 0L)
                        }
                    } catch (_: Exception) {}
                }

                // 2. Fall back to genuine OS TrafficStats if core JSON is unpopulated
                if (currentRxBytes == 0L && currentTxBytes == 0L) {
                    val myUid = Process.myUid()
                    val totalRx = AndroidTrafficStats.getUidRxBytes(myUid)
                    val totalTx = AndroidTrafficStats.getUidTxBytes(myUid)
                    if (totalRx >= initialUidRxBytes) {
                        currentRxBytes = totalRx - initialUidRxBytes
                    }
                    if (totalTx >= initialUidTxBytes) {
                        currentTxBytes = totalTx - initialUidTxBytes
                    }
                }

                val timeDeltaSec = ((now - lastStatsSampleTime) / 1000.0).coerceAtLeast(0.5)
                val deltaRx = (currentRxBytes - lastMeasuredRx).coerceAtLeast(0L)
                val deltaTx = (currentTxBytes - lastMeasuredTx).coerceAtLeast(0L)

                lastMeasuredRx = currentRxBytes
                lastMeasuredTx = currentTxBytes
                lastStatsSampleTime = now

                val speedInKbps = ((deltaRx * 8.0) / 1024.0) / timeDeltaSec
                val speedOutKbps = ((deltaTx * 8.0) / 1024.0) / timeDeltaSec

                val stats = TrafficStats(
                    bytesIn = currentRxBytes,
                    bytesOut = currentTxBytes,
                    downloadSpeedKbps = speedInKbps,
                    uploadSpeedKbps = speedOutKbps,
                    durationSeconds = durationSec,
                    pingMs = _activeServer.value?.pingMs?.coerceAtLeast(1L) ?: 1L
                )
                _trafficStats.value = stats

                DefaultVpnCoreEngine.instance.updateStats(
                    EngineStats(
                        totalBytesIn = currentRxBytes,
                        totalBytesOut = currentTxBytes,
                        currentDownloadKbps = speedInKbps,
                        currentUploadKbps = speedOutKbps,
                        activeConnections = if (currentRxBytes > 0) 1 else 0
                    )
                )
            }
        }
    }

    private fun stopCore() {
        try {
            coreController?.stopLoop()
        } catch (_: Exception) {}
        coreController = null
    }

    private fun terminateVpn(reason: String) {
        // Invalidate any in-flight health check/failover before tearing down the tunnel.
        connectionGeneration += 1
        DefaultVpnCoreEngine.instance.terminateSession(reason)
        stopCore()

        val server = _activeServer.value
        val duration = if (connectTimeMillis > 0) (System.currentTimeMillis() - connectTimeMillis) / 1000 else 0L

        if (server != null && duration > 0) {
            val finalRx = lastMeasuredRx
            val finalTx = lastMeasuredTx
            serviceScope.launch {
                val db = AppDatabase.getInstance(applicationContext)
                db.historyDao().insertHistory(
                    ConnectionHistoryEntity(
                        serverName = server.name,
                        protocol = server.protocol,
                        connectedAt = connectTimeMillis,
                        durationSeconds = duration,
                        bytesDownloaded = finalRx,
                        bytesUploaded = finalTx,
                        disconnectReason = reason
                    )
                )
            }
        }

        statsJob?.cancel()
        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null

        _vpnStatus.value = VpnStatus.IDLE
        _activeServer.value = null
        _trafficStats.value = TrafficStats()

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        terminateVpn("مجوز دسترسی VPN از طرف سیستم لغو شد")
        super.onRevoke()
    }

    override fun onDestroy() {
        terminateVpn("سرویس پایان یافت")
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.vpn_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.vpn_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(this, JavadVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this,
            1,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.javadsolvpn_orbit)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.vpn_action_disconnect),
                disconnectPendingIntent
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
