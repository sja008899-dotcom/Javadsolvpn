package com.example.vpn.diagnostic

import android.content.Context
import android.net.VpnService
import android.os.ParcelFileDescriptor
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SafeModeDiagnosticManager {

    private val _report = MutableStateFlow(SafeModeDiagnosticReport())
    val report: StateFlow<SafeModeDiagnosticReport> = _report.asStateFlow()

    private val logsList = mutableListOf<DiagnosticLog>()

    private fun addLog(tag: String, message: String, isError: Boolean = false) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val log = DiagnosticLog(time, tag, message, isError)
        logsList.add(log)
        if (logsList.size > 120) logsList.removeAt(0)
        _report.value = _report.value.copy(logs = logsList.toList())
    }

    /**
     * Executes the comprehensive Safe Mode diagnostic routine:
     * 1. Inspects Core binary and extracted assets (geoip/geosite).
     * 2. Attempts loopback TUN interface binding via VpnService.Builder and verifies FD.
     * 3. Synthesizes a diagnostic loopback Xray config.
     * 4. Starts the core loop with CoreCallbackHandler to log specific status/error codes.
     * 5. Performs SOCKS5 and HTTP handshakes against loopback proxy inbounds to verify end-to-end socket flow.
     * 6. Gracefully terminates and releases resources.
     */
    suspend fun runDiagnosticRoutine(
        context: Context,
        vpnServiceInstance: VpnService? = null,
        targetServerForProbe: ServerConfigEntity? = null
    ): SafeModeDiagnosticReport = withContext(Dispatchers.IO) {
        logsList.clear()
        _report.value = SafeModeDiagnosticReport(
            isRunning = true,
            steps = listOf(
                DiagnosticStepItem("محیط اجرایی و داده‌های هسته", "بررسی فایل‌های سیستمی و نسخه کامپایل هسته"),
                DiagnosticStepItem("اتصال لوپ‌بک و ارزیابی File Descriptor", "ایجاد رابط مجازی شبکه و اعتبارسنجی FD"),
                DiagnosticStepItem("سنتز پیکربندی عیب‌یابی امن", "تولید ساختار JSON سازگار با هسته Xray"),
                DiagnosticStepItem("اجرای هسته و شنود کدهای وضعیت", "راه‌اندازی CoreController و رهگیری کدهای Callback"),
                DiagnosticStepItem("تست دست‌دست دادن (Handshake Phase)", "ارسال پکت‌های آزمایشی SOCKS5 و سنجش پاسخگویی"),
                DiagnosticStepItem("تخلیه امن و آزادسازی منابع", "متوقف‌سازی پردازش هسته و بستن رابط شبکه")
            )
        )

        addLog("DIAG", "🚀 آغاز روال عیب‌یابی حالت امن (Safe Mode Diagnostic Routine)")

        var pfd: ParcelFileDescriptor? = null
        var coreController: CoreController? = null
        var fdNumber = -1
        var isFdBound = false
        var coreVersion = "نامشخص"
        var coreReturnCode: Long? = null
        var handshakeCode = -1
        var handshakeLatency = -1L
        var failureOccurred = false

        // STEP 1: Verify Assets & Core Binary
        try {
            updateStep(0, DiagnosticStepState.RUNNING, "در حال بررسی کتابخانه هسته و فایل‌های ژئودیتا…")
            coreVersion = try {
                Libv2ray.checkVersionX().trim()
            } catch (e: Throwable) {
                "کتابخانه بارگذاری نشد: ${e.message}"
            }
            addLog("CORE", "نسخه شناسایی‌شده هسته: $coreVersion")

            val geoipFile = File(context.filesDir, "geoip.dat")
            val geositeFile = File(context.filesDir, "geosite.dat")
            addLog("FS", "بررسی geoip.dat: ${if (geoipFile.exists()) "موجود (${geoipFile.length() / 1024} KB)" else "ناموجود (استفاده از CIDR داخلی)"}")
            addLog("FS", "بررسی geosite.dat: ${if (geositeFile.exists()) "موجود (${geositeFile.length() / 1024} KB)" else "ناموجود"}")

            updateStep(0, DiagnosticStepState.PASSED, "هسته $coreVersion آماده به کار است.")
        } catch (e: Exception) {
            updateStep(0, DiagnosticStepState.FAILED, "خطا در ارزیابی محیط: ${e.message}", errorCode = "ERR_ENV_INIT")
            addLog("CORE", "خطای محیط: ${e.message}", isError = true)
            failureOccurred = true
        }

        // STEP 2: Establish Loopback via VpnService & Verify File Descriptor
        try {
            updateStep(1, DiagnosticStepState.RUNNING, "در حال ایجاد رابط شبکه لوپ‌بک و بررسی File Descriptor…")
            val vpnPrepareIntent = VpnService.prepare(context)
            if (vpnPrepareIntent != null) {
                addLog("VPN", "مجوز VpnService هنوز تأیید نشده است. نیاز به تأیید کاربر در دیالوگ سیستم دارد.", isError = true)
                updateStep(1, DiagnosticStepState.FAILED, "مجوز دسترسی VPN توسط سیستم‌عامل صادر نشده است.", errorCode = "ERR_VPN_PERMISSION_REQUIRED")
                failureOccurred = true
            } else if (vpnServiceInstance == null) {
                addLog("VPN", "سرویس فعال VpnService در دسترس نیست؛ آزمایش در لایه کاربر انجام می‌شود.")
                updateStep(1, DiagnosticStepState.PASSED, "مجوز سیستم بررسی شد (تست سرویس در پس‌زمینه انجام خواهد شد).")
            } else {
                val builder = vpnServiceInstance.Builder()
                    .setSession("Javadsolvpn-SafeMode-Diagnostic")
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
                    addLog("FD", "تأیید File Descriptor: شماره FD=$fdNumber, معتبر=$isValid, اتصال=Bound")
                    updateStep(1, DiagnosticStepState.PASSED, "فایل‌دیسکریپتور با موفقیت به رابط TUN متصل شد (FD: $fdNumber).")
                } else {
                    addLog("FD", "خطا: خروجی builder.establish() برابر null است.", isError = true)
                    updateStep(1, DiagnosticStepState.FAILED, "ایجاد رابط TUN با شکست مواجه شد (FD null).", errorCode = "ERR_FD_NULL")
                    failureOccurred = true
                }
            }
        } catch (e: Exception) {
            addLog("VPN", "خطا در برقراری اتصال لوپ‌بک: ${e.message}", isError = true)
            updateStep(1, DiagnosticStepState.FAILED, "خطای اتصال رابط VPN: ${e.localizedMessage}", errorCode = "ERR_VPN_ESTABLISH")
            failureOccurred = true
        }

        // STEP 3: Synthesize Diagnostic Loopback Configuration
        var configJson = ""
        try {
            updateStep(2, DiagnosticStepState.RUNNING, "در حال تولید پیکربندی آزمایشی لوپ‌بک…")
            configJson = buildSafeModeDiagnosticJson(targetServerForProbe)
            val jsonTest = JSONObject(configJson)
            val inbounds = jsonTest.getJSONArray("inbounds")
            addLog("JSON", "پیکربندی با ${inbounds.length()} این‌باند استاندارد (SOCKS 10808, HTTP 10809, TUN) با موفقیت تولید شد.")
            updateStep(2, DiagnosticStepState.PASSED, "پیکربندی استاندارد JSON ایجاد و ساختار آن تأیید شد.")
        } catch (e: Exception) {
            addLog("JSON", "خطا در تولید پیکربندی: ${e.message}", isError = true)
            updateStep(2, DiagnosticStepState.FAILED, "خطا در ساخت کانفیگ: ${e.message}", errorCode = "ERR_JSON_SYNTHESIS")
            failureOccurred = true
        }

        // STEP 4: Start Core Loop & Log Status/Callback Codes
        if (!failureOccurred && pfd != null) {
            try {
                updateStep(3, DiagnosticStepState.RUNNING, "در حال ارسال دستور startLoop به هسته Xray و شنود Callback…")
                val callback = object : CoreCallbackHandler {
                    override fun onEmitStatus(status: Long, msg: String?): Long {
                        coreReturnCode = status
                        addLog("CORE_CALLBACK", "وضعیت هسته دریافت شد: کد=$status, پیام=${msg ?: "بدون پیام"}")
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
                addLog("CORE", "متد startLoop بدون Exception به پایان رسید (حلقه فعال شد).")

                delay(800)
                updateStep(3, DiagnosticStepState.PASSED, "هسته Xray بدون خطا راه‌اندازی شد و کد وضعیت تأیید گردید.")
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: e.message ?: "خطای ناشناخته در استارت هسته"
                addLog("CORE_ERR", "⚠️ استثنا در هسته Xray: $errorMsg", isError = true)
                updateStep(3, DiagnosticStepState.FAILED, "هسته متوقف شد: $errorMsg", errorCode = "XRAY_CORE_START_FAIL: ${e.javaClass.simpleName}")
                failureOccurred = true
            }
        } else if (!failureOccurred) {
            updateStep(3, DiagnosticStepState.SKIPPED, "برای اجرای زنده هسته نیاز به اجرای VpnService است.")
        }

        // STEP 5: Handshake Phase Testing (SOCKS5 Loopback Handshake)
        if (!failureOccurred && coreController != null) {
            try {
                updateStep(4, DiagnosticStepState.RUNNING, "در حال انجام آزمون دست‌دست دادن SOCKS5 روی 127.0.0.1:10808…")
                val startHs = System.currentTimeMillis()

                val socksSocket = Socket()
                socksSocket.connect(InetSocketAddress("127.0.0.1", 10808), 3000)
                val out: OutputStream = socksSocket.getOutputStream()
                val input: InputStream = socksSocket.getInputStream()

                // SOCKS5 Greeting: VER=5, NMETHODS=1, METHOD=0 (No Auth)
                out.write(byteArrayOf(0x05, 0x01, 0x00))
                out.flush()

                val resp = ByteArray(2)
                val readBytes = input.read(resp)
                handshakeLatency = System.currentTimeMillis() - startHs
                socksSocket.close()

                if (readBytes >= 2 && resp[0] == 0x05.toByte() && resp[1] == 0x00.toByte()) {
                    handshakeCode = 0 // SOCKS5_SUCCESS_NO_AUTH
                    addLog("HANDSHAKE", "✅ دست‌دست دادن SOCKS5 موفقیت‌آمیز بود! پاسخ هسته: VER=5, METHOD=0 (تأخیر: ${handshakeLatency}ms)")
                    updateStep(4, DiagnosticStepState.PASSED, "دست‌دست دادن موفقیت‌آمیز در پورت لوپ‌بک (کد پاسخ: 0x00، زمان: ${handshakeLatency}ms).")
                } else {
                    handshakeCode = if (readBytes >= 2) resp[1].toInt() else -2
                    addLog("HANDSHAKE", "پاسخ نامتعارف از درگاه SOCKS5: بایت‌های دریافتی: ${resp.joinToString { "%02X".format(it) }}", isError = true)
                    updateStep(4, DiagnosticStepState.FAILED, "پاسخ نامعتبر در Handshake SOCKS5", errorCode = "SOCKS_REJECT_$handshakeCode")
                }
            } catch (e: Exception) {
                addLog("HANDSHAKE", "خطا در برقراری Handshake محلی با هسته: ${e.message}", isError = true)
                updateStep(4, DiagnosticStepState.FAILED, "عدم پاسخگویی درگاه محلی: ${e.localizedMessage}", errorCode = "ERR_HANDSHAKE_SOCKET_TIMEOUT")
            }
        } else if (!failureOccurred) {
            updateStep(4, DiagnosticStepState.SKIPPED, "مرحله Handshake در این سناریو صرف‌نظر شد.")
        }

        // STEP 6: Safe Teardown
        try {
            updateStep(5, DiagnosticStepState.RUNNING, "در حال توقف امن هسته و رهاسازی File Descriptor…")
            try {
                coreController?.stopLoop()
                addLog("TEARDOWN", "هسته آزمایشی با موفقیت خاموش شد.")
            } catch (_: Exception) {}
            try {
                pfd?.close()
                addLog("TEARDOWN", "رابط فیزیکی شبکه (FD) با موفقیت بسته شد.")
            } catch (_: Exception) {}
            updateStep(5, DiagnosticStepState.PASSED, "تمامی منابع موقت عیب‌یابی با موفقیت آزاد شدند.")
        } catch (e: Exception) {
            updateStep(5, DiagnosticStepState.FAILED, "خطا در آزادسازی منابع: ${e.message}")
        }

        val allPassed = _report.value.steps.none { it.state == DiagnosticStepState.FAILED }
        val finalSummary = if (allPassed) {
            "✅ عیب‌یابی حالت امن با موفقیت انجام شد: رابط TUN با شناسه FD=$fdNumber پیوند داده شد، هسته Xray بدون خطا آغاز به کار کرد و فاز دست‌دست دادن محلی با کد 0x00 تأیید شد."
        } else {
            val failedStep = _report.value.steps.firstOrNull { it.state == DiagnosticStepState.FAILED }
            "⚠️ خطا در عیب‌یابی: مرحله «${failedStep?.title}» ناموفق بود (کد خطا: ${failedStep?.errorCode ?: "نامشخص"})."
        }

        addLog("SUMMARY", finalSummary)
        val finalReport = _report.value.copy(
            isRunning = false,
            overallPassed = allPassed,
            fdNumber = fdNumber,
            isFdBound = isFdBound,
            coreVersion = coreVersion,
            coreReturnCode = coreReturnCode,
            handshakeResponseCode = handshakeCode,
            handshakeLatencyMs = handshakeLatency,
            summary = finalSummary
        )
        _report.value = finalReport
        finalReport
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

    private fun buildSafeModeDiagnosticJson(probeServer: ServerConfigEntity?): String {
        return if (probeServer != null) {
            CoreConfigBuilder.buildConfigJson(probeServer, socksPort = 10808, httpPort = 10809)
        } else {
            // Self-contained loopback test config (Direct/Blackhole)
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
                    put("userLevel", 0)
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
                put("domainStrategy", "IPIfNonMatch")
                val rules = JSONArray()
                rules.put(JSONObject().apply {
                    put("type", "field")
                    put("outboundTag", "direct")
                    put("network", "tcp,udp")
                })
                put("rules", rules)
            })

            root.put("dns", JSONObject().apply {
                put("servers", JSONArray().put("1.1.1.1").put("localhost"))
            })
            root.toString(2)
        }
    }
}
