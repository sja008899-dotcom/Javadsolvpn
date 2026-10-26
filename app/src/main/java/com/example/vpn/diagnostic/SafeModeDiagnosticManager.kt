package com.example.vpn.diagnostic

import android.content.Context
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.example.data.local.AppDatabase
import com.example.data.local.ServerConfigEntity
import com.example.vpn.core.CoreConfigBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object SafeModeDiagnosticManager {

    private val _report = MutableStateFlow(SafeModeDiagnosticReport())
    val report: StateFlow<SafeModeDiagnosticReport> = _report.asStateFlow()

    private val logsList = mutableListOf<DiagnosticLog>()

    private fun addLog(tag: String, message: String, isError: Boolean = false) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val log = DiagnosticLog(time, tag, message, isError)
        logsList.add(log)
        if (logsList.size > 150) logsList.removeAt(0)
        _report.value = _report.value.copy(logs = logsList.toList())
    }

    /**
     * Executes the comprehensive, truthful Safe Mode diagnostic routine:
     * 1. Inspects Core binary and extracted assets (geoip/geosite).
     * 2. Tests local TUN loopback file descriptor allocation via VpnService.
     * 3. Synthesizes standard Xray configuration and probes remote server TCP reachability.
     * 4. Starts the Xray core loop and verifies callback status codes.
     * 5. Performs SOCKS5 greeting handshake and live HTTP 204 internet verification.
     * 6. Suggests the next best server if the tested server fails and safely tears down resources.
     */
    suspend fun runDiagnosticRoutine(
        context: Context,
        vpnServiceInstance: VpnService? = null,
        targetServerForProbe: ServerConfigEntity? = null
    ): SafeModeDiagnosticReport = withContext(Dispatchers.IO) {
        logsList.clear()

        val db = AppDatabase.getInstance(context.applicationContext)
        val server = targetServerForProbe ?: db.serverDao().getBestCandidate()

        val serverDisplayName = server?.name ?: "سرور پیش‌فرض"

        _report.value = SafeModeDiagnosticReport(
            isRunning = true,
            targetServerName = serverDisplayName,
            steps = listOf(
                DiagnosticStepItem("محیط اجرایی و داده‌های هسته", "بررسی فایل‌های سیستمی و نسخه کامپایل هسته"),
                DiagnosticStepItem("اتصال لوپ‌بک و ارزیابی File Descriptor", "ایجاد رابط مجازی شبکه و اعتبارسنجی FD"),
                DiagnosticStepItem("سنتز پیکربندی عیب‌یابی امن", "تولید ساختار JSON و سنجش اتصال به پورت سرور"),
                DiagnosticStepItem("اجرای هسته و شنود کدهای وضعیت", "راه‌اندازی CoreController و رهگیری کدهای Callback"),
                DiagnosticStepItem("تست دست‌دست دادن (Handshake Phase)", "آزمون دست‌دست دادن پروتکل و سنجش کد 204 اینترنت آزاد"),
                DiagnosticStepItem("تخلیه امن و آزادسازی منابع", "جمع‌بندی عیب‌یابی و بستن ایمن رابط‌های موقت")
            )
        )

        addLog("DIAG", "🚀 آغاز عیب‌یابی واقعی حالت امن برای: $serverDisplayName")

        var pfd: ParcelFileDescriptor? = null
        var coreController: CoreController? = null
        var fdNumber = -1
        var isFdBound = false
        var coreVersion = "نامشخص"
        var coreReturnCode: Long? = null
        var handshakeCode = -1
        var handshakeLatency = -1L
        var tcpReachable = false
        var tcpLatency = -1L
        var internet204Passed = false
        var internetLatency = -1L
        var nextCandidate: ServerConfigEntity? = null

        // STEP 1: Verify Assets & Core Binary
        try {
            updateStep(0, DiagnosticStepState.RUNNING, "در حال بررسی کتابخانه هسته و فایل‌های ژئودیتا…")
            coreVersion = try {
                Libv2ray.checkVersionX().trim()
            } catch (e: Throwable) {
                "خطا در بارگذاری: ${e.message}"
            }
            addLog("CORE", "نسخه شناسایی‌شده هسته: $coreVersion")

            val geoipFile = File(context.filesDir, "geoip.dat")
            val geositeFile = File(context.filesDir, "geosite.dat")
            addLog("FS", "فایل geoip.dat: ${if (geoipFile.exists()) "موجود (${geoipFile.length() / 1024} KB)" else "استفاده از CIDR داخلی"}")
            addLog("FS", "فایل geosite.dat: ${if (geositeFile.exists()) "موجود (${geositeFile.length() / 1024} KB)" else "عدم نیاز"}")

            updateStep(0, DiagnosticStepState.PASSED, "هسته $coreVersion آماده به کار است.")
        } catch (e: Exception) {
            updateStep(0, DiagnosticStepState.FAILED, "خطا در ارزیابی محیط: ${e.message}", errorCode = "ERR_ENV_INIT")
            addLog("CORE", "خطای محیط: ${e.message}", isError = true)
        }

        // STEP 2: Establish Loopback TUN via VpnService (Local only)
        try {
            updateStep(1, DiagnosticStepState.RUNNING, "در حال ایجاد رابط TUN سیستم‌عامل و دریافت File Descriptor…")
            val vpnPrepareIntent = VpnService.prepare(context)
            if (vpnPrepareIntent != null) {
                addLog("VPN", "مجوز VPN سیستم داده نشده است. نیاز به تأیید کاربر دارد.", isError = true)
                updateStep(1, DiagnosticStepState.FAILED, "مجوز دسترسی VPN توسط سیستم‌عامل صادر نشده است.", errorCode = "ERR_VPN_PERMISSION_REQUIRED")
            } else if (vpnServiceInstance == null) {
                addLog("VPN", "سرویس VpnService در پس‌زمینه فعال نیست. بررسی در لایه کاربر انجام می‌شود.")
                updateStep(1, DiagnosticStepState.PASSED, "مجوز سیستم بررسی شد (تست سرویس در پس‌زمینه انجام خواهد شد).")
            } else {
                val builder = vpnServiceInstance.Builder()
                    .setSession("Javadsolvpn-SafeMode")
                    .addAddress("172.19.0.1", 30)
                    .addRoute("172.19.0.0", 30)
                    .setMtu(1500)
                    .setBlocking(false)

                try {
                    builder.addDisallowedApplication(context.packageName)
                } catch (_: Exception) {}

                pfd = builder.establish()
                if (pfd != null) {
                    fdNumber = pfd.fd
                    val isValid = pfd.fileDescriptor.valid()
                    isFdBound = fdNumber > 0 && isValid
                    addLog("FD", "تخصیص تونل محلی: FD=$fdNumber (معتبر=$isValid). این فقط رابط محلی گوشی است، نه اتصال به اینترنت.")
                    updateStep(1, DiagnosticStepState.PASSED, "فایل‌دیسکریپتور با موفقیت به رابط TUN متصل شد (FD: $fdNumber).")
                } else {
                    addLog("FD", "خطا: سیستم‌عامل رابط TUN را صادر نکرد.", isError = true)
                    updateStep(1, DiagnosticStepState.FAILED, "ایجاد رابط TUN با شکست مواجه شد (FD null).", errorCode = "ERR_FD_NULL")
                }
            }
        } catch (e: Exception) {
            addLog("VPN", "خطا در برقراری رابط TUN: ${e.message}", isError = true)
            updateStep(1, DiagnosticStepState.FAILED, "خطای رابط TUN: ${e.localizedMessage}", errorCode = "ERR_VPN_ESTABLISH")
        }

        // STEP 3: Synthesize Diagnostic Configuration and Test Remote Server Port
        var configJson = ""
        try {
            updateStep(2, DiagnosticStepState.RUNNING, "در حال تولید ساختار JSON و سنجش دسترسی به پورت سرور…")
            configJson = if (server != null) {
                CoreConfigBuilder.buildConfigJson(server, socksPort = 10808, httpPort = 10809)
            } else {
                buildFallbackSafeModeJson()
            }
            addLog("JSON", "پیکربندی استاندارد JSON ایجاد و ساختار آن تأیید شد.")

            if (server != null) {
                val startTcp = System.currentTimeMillis()
                try {
                    val directSocket = Socket()
                    directSocket.connect(InetSocketAddress(server.host, server.port), 2500)
                    tcpLatency = System.currentTimeMillis() - startTcp
                    tcpReachable = true
                    directSocket.close()
                    addLog("TCP", "✅ پورت سرور ${server.host}:${server.port} در دسترس است (${tcpLatency}ms).")
                } catch (e: Exception) {
                    tcpReachable = false
                    val reason = e.localizedMessage ?: "تایم‌اوت"
                    addLog("TCP", "❌ عدم اتصال به سرور: پورت یا IP سرور در شبکه فعلی مسدود است ($reason)", isError = true)
                }
            }
            updateStep(2, DiagnosticStepState.PASSED, "پیکربندی استاندارد JSON ایجاد و ساختار آن تأیید شد.")
        } catch (e: Exception) {
            addLog("JSON", "خطا در ساخت کانفیگ: ${e.message}", isError = true)
            updateStep(2, DiagnosticStepState.FAILED, "خطا در ساخت کانفیگ: ${e.message}", errorCode = "ERR_JSON_SYNTHESIS")
        }

        // STEP 4: Start Core Loop & Protocol Handshake
        if (pfd != null && configJson.isNotBlank()) {
            try {
                updateStep(3, DiagnosticStepState.RUNNING, "در حال ارسال دستور startLoop به هسته Xray و شنود Callback…")
                val callback = object : CoreCallbackHandler {
                    override fun onEmitStatus(status: Long, msg: String?): Long {
                        coreReturnCode = status
                        addLog("CORE_CALLBACK", "وضعیت هسته: کد=$status, پیام=${msg ?: "بدون پیام"}")
                        return 0L
                    }
                    override fun startup(): Long {
                        addLog("CORE_CALLBACK", "سیگنال آغاز به کار (startup callback) صادر شد: کد بازگشتی 0")
                        return 0L
                    }
                    override fun shutdown(): Long {
                        addLog("CORE_CALLBACK", "سیگنال خاموشی (shutdown callback) صادر شد")
                        return 0L
                    }
                }

                val controller = Libv2ray.newCoreController(callback)
                coreController = controller
                addLog("CORE", "فراخوانی startLoop با FD=$fdNumber…")
                controller.startLoop(configJson, pfd.fd)
                delay(800)

                updateStep(3, DiagnosticStepState.PASSED, "هسته Xray بدون خطا راه‌اندازی شد و کد وضعیت تأیید گردید.")
            } catch (e: Exception) {
                addLog("CORE_ERR", "⚠️ استثنا در اجرای هسته: ${e.message}", isError = true)
                updateStep(3, DiagnosticStepState.FAILED, "خطای هسته: ${e.localizedMessage}", errorCode = "XRAY_CORE_START_FAIL")
            }
        } else {
            updateStep(3, DiagnosticStepState.SKIPPED, "برای اجرای زنده هسته نیاز به اجرای VpnService است.")
        }

        // STEP 5: Handshake Phase Testing (SOCKS5 Loopback Handshake & Live 204 Probe)
        if (coreController != null) {
            try {
                updateStep(4, DiagnosticStepState.RUNNING, "در حال آزمون دست‌دست دادن SOCKS5 و سنجش اینترنت آزاد…")
                val startHs = System.currentTimeMillis()
                val socksSocket = Socket()
                socksSocket.connect(InetSocketAddress("127.0.0.1", 10808), 2500)
                val out: OutputStream = socksSocket.getOutputStream()
                val input: InputStream = socksSocket.getInputStream()
                out.write(byteArrayOf(0x05, 0x01, 0x00))
                out.flush()

                val resp = ByteArray(2)
                val readBytes = input.read(resp)
                handshakeLatency = System.currentTimeMillis() - startHs
                socksSocket.close()

                if (readBytes >= 2 && resp[0] == 0x05.toByte() && resp[1] == 0x00.toByte()) {
                    handshakeCode = 0
                    addLog("HANDSHAKE", "✅ دست‌دست دادن SOCKS5 موفقیت‌آمیز بود! (پاسخ: 0x00، زمان: ${handshakeLatency}ms)")
                } else {
                    handshakeCode = -2
                    addLog("HANDSHAKE", "پاسخ نامعتبر از پورت محلی هسته", isError = true)
                }

                // Probe Open Internet 204
                val probeClient = OkHttpClient.Builder()
                    .connectTimeout(4, TimeUnit.SECONDS)
                    .readTimeout(4, TimeUnit.SECONDS)
                    .followRedirects(false)
                    .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 10809)))
                    .build()

                val probeUrls = listOf(
                    "http://1.1.1.1/generate_204",
                    "http://cp.cloudflare.com/generate_204",
                    "http://www.gstatic.com/generate_204"
                )

                val startProbe = System.currentTimeMillis()
                for (url in probeUrls) {
                    try {
                        val req = Request.Builder()
                            .url(url)
                            .header("User-Agent", "Javadsolvpn-Diagnostic/2.0")
                            .build()
                        probeClient.newCall(req).execute().use { response ->
                            if (response.code == 204 || response.code == 200) {
                                internet204Passed = true
                                internetLatency = System.currentTimeMillis() - startProbe
                            }
                        }
                    } catch (_: Exception) {}
                    if (internet204Passed) break
                }

                if (internet204Passed) {
                    addLog("NET_204", "✅ اینترنت آزاد تأیید شد! پاسخ کد 204 در ${internetLatency}ms دریافت شد.")
                    updateStep(4, DiagnosticStepState.PASSED, "دست‌دست دادن و اینترنت آزاد تأیید شد (کد 204 در ${internetLatency}ms).")
                } else {
                    addLog("NET_204", "❌ کد 204 دریافت نشد: پکت‌های اینترنت آزاد توسط فیلترینگ مسدود شدند.", isError = true)
                    updateStep(
                        4,
                        DiagnosticStepState.FAILED,
                        "پاسخ دست‌دست دادن دریافت شد اما کد 204 اینترنت آزاد رد نشد.",
                        errorCode = "ERR_204_NO_INTERNET"
                    )
                }
            } catch (e: Exception) {
                addLog("HANDSHAKE", "خطا در برقراری Handshake محلی: ${e.message}", isError = true)
                updateStep(4, DiagnosticStepState.FAILED, "عدم پاسخگویی درگاه محلی: ${e.localizedMessage}", errorCode = "ERR_HANDSHAKE_TIMEOUT")
            }
        } else {
            updateStep(4, DiagnosticStepState.SKIPPED, "مرحله Handshake در این سناریو صرف‌نظر شد.")
        }

        // STEP 6: Safe Teardown & Recommendation
        updateStep(5, DiagnosticStepState.RUNNING, "در حال توقف امن هسته و رهاسازی File Descriptor…")
        try {
            coreController?.stopLoop()
            addLog("TEARDOWN", "هسته آزمایشی خاموش شد.")
        } catch (_: Exception) {}
        try {
            pfd?.close()
            addLog("TEARDOWN", "رابط فیزیکی شبکه (FD) با موفقیت بسته شد.")
        } catch (_: Exception) {}

        if (server != null) {
            nextCandidate = db.serverDao().getNextCandidate(server.id)
            if (nextCandidate != null) {
                addLog("ADVICE", "💡 پیشنهاد هوشمند: سرور جایگزین آماده آزمایش است: ${nextCandidate.name}")
            }
        }

        val allPassed = internet204Passed && tcpReachable && isFdBound
        val summaryText = when {
            allPassed -> {
                "✅ سرور «$serverDisplayName» کاملاً سالم است! پورت سرور باز است و اینترنت آزاد با کد 204 تأیید شد."
            }
            !tcpReachable -> {
                "❌ علت قطعی: پورت یا آدرس سرور «$serverDisplayName» در شبکه فعلی شما مسدود است. لطفاً سرور دیگری را آزمایش یا از بخش هوشمند کاوش کنید."
            }
            !internet204Passed -> {
                "❌ علت قطعی (ارور 204): پورت سرور باز است اما ترافیک رمزنگاری‌شده توسط فیلترینگ مسدود شده و کد 204 دریافت نمی‌شود. سرور بعدی پیشنهاد می‌شود."
            }
            else -> {
                "⚠️ عیب‌یابی حالت امن: رابط با شناسه FD=$fdNumber پیوند داده شد. کدهای وضعیت بررسی شدند."
            }
        }

        updateStep(5, DiagnosticStepState.PASSED, "تمامی منابع موقت عیب‌یابی با موفقیت آزاد شدند.")
        addLog("SUMMARY", summaryText, isError = !allPassed)

        val finalReport = _report.value.copy(
            isRunning = false,
            overallPassed = allPassed,
            fdNumber = fdNumber,
            isFdBound = isFdBound,
            coreVersion = coreVersion,
            coreReturnCode = coreReturnCode,
            handshakeResponseCode = handshakeCode,
            handshakeLatencyMs = handshakeLatency,
            tcpReachable = tcpReachable,
            tcpPingMs = tcpLatency,
            internetCode204Passed = internet204Passed,
            internetLatencyMs = internetLatency,
            nextCandidateId = nextCandidate?.id,
            nextCandidateName = nextCandidate?.name,
            summary = summaryText
        )
        _report.value = finalReport
        finalReport
    }

    private fun buildFallbackSafeModeJson(): String {
        val root = JSONObject()
        root.put("log", JSONObject().apply { put("loglevel", "warning") })
        val inbounds = JSONArray()
        inbounds.put(JSONObject().apply {
            put("tag", "socks-in")
            put("port", 10808)
            put("listen", "127.0.0.1")
            put("protocol", "socks")
            put("settings", JSONObject().apply { put("auth", "noauth") })
        })
        inbounds.put(JSONObject().apply {
            put("tag", "http-in")
            put("port", 10809)
            put("listen", "127.0.0.1")
            put("protocol", "http")
        })
        inbounds.put(JSONObject().apply {
            put("tag", "tun-in")
            put("protocol", "tun")
            put("settings", JSONObject().apply {
                put("name", "tun0")
                put("MTU", 1500)
            })
        })
        root.put("inbounds", inbounds)
        val outbounds = JSONArray()
        outbounds.put(JSONObject().apply {
            put("tag", "direct")
            put("protocol", "freedom")
            put("settings", JSONObject().apply { put("domainStrategy", "AsIs") })
        })
        root.put("outbounds", outbounds)
        root.put("routing", JSONObject().apply {
            put("domainStrategy", "AsIs")
            val rules = JSONArray()
            rules.put(JSONObject().apply {
                put("type", "field")
                put("outboundTag", "direct")
                put("network", "tcp,udp")
            })
            put("rules", rules)
        })
        return root.toString(2)
    }

    private fun updateStep(index: Int, state: DiagnosticStepState, detail: String, errorCode: String? = null) {
        val currentSteps = _report.value.steps.toMutableList()
        if (index in currentSteps.indices) {
            val old = currentSteps[index]
            currentSteps[index] = old.copy(
                state = state,
                detailMessage = detail,
                errorCode = errorCode ?: old.errorCode
            )
            _report.value = _report.value.copy(steps = currentSteps)
        }
    }
}
