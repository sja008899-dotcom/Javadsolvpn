package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.local.ServerConfigEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class JavadApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getInstance(this)

        CoroutineScope(Dispatchers.IO).launch {
            // Seed initial trusted servers if database is freshly created
            val initialServers = listOf(
                ServerConfigEntity(
                    name = "Javad 1 • اتصال آمریکا",
                    protocol = "vless",
                    host = "us-east.javadsolvpn.net",
                    port = 443,
                    rawLink = "vless://9f4e45ab-234e-4f01-92b3-a1286c8f4955@us-east.javadsolvpn.net:443?type=tcp&security=reality&sni=speed.cloudflare.com&fp=chrome#Javad%201",
                    countryCode = "US",
                    countryNameFa = "آمریکا",
                    countryNameEn = "United States",
                    securityType = "reality",
                    transportType = "tcp",
                    pingMs = 120L,
                    isFavorite = true,
                    isWorking = true
                ),
                ServerConfigEntity(
                    name = "Javad 2 • اتصال آلمان",
                    protocol = "trojan",
                    host = "de-fra.javadsolvpn.net",
                    port = 443,
                    rawLink = "trojan://javad-secure-pass-2026@de-fra.javadsolvpn.net:443?security=tls&type=ws&path=%2Ftrojan-ws#Javad%202",
                    countryCode = "DE",
                    countryNameFa = "آلمان",
                    countryNameEn = "Germany",
                    securityType = "tls",
                    transportType = "ws",
                    pingMs = 85L,
                    isFavorite = true,
                    isWorking = true
                ),
                ServerConfigEntity(
                    name = "Javad 3 • اتصال ژاپن",
                    protocol = "vless",
                    host = "jp-tokyo.javadsolvpn.net",
                    port = 443,
                    rawLink = "vless://b4a3915f-5ec1-4e8c-8f4b-325d7c99e1a8@jp-tokyo.javadsolvpn.net:443?type=ws&security=tls&path=%2Fvless-grpc#Javad%203",
                    countryCode = "JP",
                    countryNameFa = "ژاپن",
                    countryNameEn = "Japan",
                    securityType = "tls",
                    transportType = "ws",
                    pingMs = 160L,
                    isFavorite = false,
                    isWorking = true
                ),
                ServerConfigEntity(
                    name = "Javad 4 • اتصال هلند",
                    protocol = "vmess",
                    host = "nl-ams.javadsolvpn.net",
                    port = 443,
                    rawLink = "vmess://eyJhZGQiOiJubC1hbXMzLmphdmFkc29sdnBuLm5ldCIsInBvcnQiOjQ0MywiaWQiOiJhYjc1ODFhNi1iNTc3LTRhYjgtYTcwOC02Zjg1MWYxODMzYzciLCJhaWQiOjAsInNjeSI6ImF1dG8iLCJuZXQiOiJ3cyIsInRscyI6InRscyIsInBzIjoiSmF2YWQgNCJ9",
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
                    name = "Javad 5 • اتصال فنلاند",
                    protocol = "ss",
                    host = "fi-hel.javadsolvpn.net",
                    port = 8388,
                    rawLink = "ss://YWVzLTI1Ni1nY206amF2YWQtc2hhZG93LTIwMjY=@fi-hel.javadsolvpn.net:8388#Javad%205",
                    countryCode = "FI",
                    countryNameFa = "فنلاند",
                    countryNameEn = "Finland",
                    securityType = "cipher",
                    transportType = "tcp",
                    pingMs = 110L,
                    isFavorite = false,
                    isWorking = true
                ),
                ServerConfigEntity(
                    name = "Javad VPN 1 • اتصال ژاپن",
                    protocol = "openvpn",
                    host = "public-vpn-90.opengw.net",
                    port = 1195,
                    rawLink = "openvpn://public-vpn-90.opengw.net:1195#Javad%20VPN%201",
                    countryCode = "JP",
                    countryNameFa = "ژاپن",
                    countryNameEn = "Japan",
                    securityType = "tls",
                    transportType = "udp",
                    pingMs = 185L,
                    isFavorite = false,
                    isWorking = true
                )
            )
            db.serverDao().insertAll(initialServers)
        }
    }
}
