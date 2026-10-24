package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.local.ServerConfigEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import libv2ray.Libv2ray

class JavadApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            Libv2ray.initCoreEnv(filesDir.absolutePath, "")
        } catch (_: Throwable) {}

        val db = AppDatabase.getInstance(this)

        CoroutineScope(Dispatchers.IO).launch {
            // Seed initial trusted servers if database is freshly created
            val initialServers = listOf(
                ServerConfigEntity(
                    name = "Javad 1 • اتصال آمریکا (VLESS Reality)",
                    protocol = "vless",
                    host = "us-east.javadsolvpn.net",
                    port = 443,
                    rawLink = "vless://9f4e45ab-234e-4f01-92b3-a1286c8f4955@us-east.javadsolvpn.net:443?type=tcp&security=reality&sni=speed.cloudflare.com&fp=chrome&pbk=1111111111111111111111111111111111111111111=&sid=12345678#Javad%201",
                    uuidOrPassword = "9f4e45ab-234e-4f01-92b3-a1286c8f4955",
                    countryCode = "US",
                    countryNameFa = "آمریکا",
                    countryNameEn = "United States",
                    securityType = "reality",
                    transportType = "tcp",
                    sni = "speed.cloudflare.com",
                    publicKey = "1111111111111111111111111111111111111111111=",
                    shortId = "12345678",
                    fingerprint = "chrome",
                    pingMs = 120L,
                    isFavorite = true,
                    isWorking = true
                ),
                ServerConfigEntity(
                    name = "Javad 2 • اتصال آلمان (Trojan TLS)",
                    protocol = "trojan",
                    host = "de-fra.javadsolvpn.net",
                    port = 443,
                    rawLink = "trojan://javad-secure-pass-2026@de-fra.javadsolvpn.net:443?security=tls&type=ws&path=%2Ftrojan-ws#Javad%202",
                    uuidOrPassword = "javad-secure-pass-2026",
                    countryCode = "DE",
                    countryNameFa = "آلمان",
                    countryNameEn = "Germany",
                    securityType = "tls",
                    transportType = "ws",
                    path = "/trojan-ws",
                    pingMs = 85L,
                    isFavorite = true,
                    isWorking = true
                ),
                ServerConfigEntity(
                    name = "Javad 3 • اتصال ژاپن (VLESS WS)",
                    protocol = "vless",
                    host = "jp-tokyo.javadsolvpn.net",
                    port = 443,
                    rawLink = "vless://b4a3915f-5ec1-4e8c-8f4b-325d7c99e1a8@jp-tokyo.javadsolvpn.net:443?type=ws&security=tls&path=%2Fvless-ws#Javad%203",
                    uuidOrPassword = "b4a3915f-5ec1-4e8c-8f4b-325d7c99e1a8",
                    countryCode = "JP",
                    countryNameFa = "ژاپن",
                    countryNameEn = "Japan",
                    securityType = "tls",
                    transportType = "ws",
                    path = "/vless-ws",
                    pingMs = 160L,
                    isFavorite = false,
                    isWorking = true
                ),
                ServerConfigEntity(
                    name = "Javad 4 • اتصال هلند (VMess WS)",
                    protocol = "vmess",
                    host = "nl-ams.javadsolvpn.net",
                    port = 443,
                    rawLink = "vmess://eyJhZGQiOiJubC1hbXMuamF2YWRzb2x2cG4ubmV0IiwicG9ydCI6NDQzLCJpZCI6ImFiNzU4MWE2LWI1NzctNGFiOC1hNzA4LTZmODUxZjE4MzNjNyIsImFpZCI6MCwic2N5IjoiYXV0byIsIm5ldCI6IndzIiwidGxzIjoidGxzIiwicHMiOiJKYXZhZCA0In0=",
                    uuidOrPassword = "ab7581a6-b577-4ab8-a708-6f851f1833c7",
                    countryCode = "NL",
                    countryNameFa = "هلند",
                    countryNameEn = "Netherlands",
                    securityType = "tls",
                    transportType = "ws",
                    pingMs = 95L,
                    isFavorite = false,
                    isWorking = true
                ),
                ServerConfigEntity(
                    name = "Javad 5 • اتصال فنلاند (Shadowsocks)",
                    protocol = "ss",
                    host = "fi-hel.javadsolvpn.net",
                    port = 8388,
                    rawLink = "ss://YWVzLTI1Ni1nY206amF2YWQtc2hhZG93LTIwMjY=@fi-hel.javadsolvpn.net:8388#Javad%205",
                    uuidOrPassword = "aes-256-gcm:javad-shadow-2026",
                    countryCode = "FI",
                    countryNameFa = "فنلاند",
                    countryNameEn = "Finland",
                    securityType = "cipher",
                    transportType = "tcp",
                    pingMs = 110L,
                    isFavorite = false,
                    isWorking = true
                )
            )
            db.serverDao().insertAll(initialServers)
        }
    }
}
