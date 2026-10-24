package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "server_configs",
    indices = [
        Index(value = ["rawLink"], unique = true),
        Index(value = ["isFavorite"]),
        Index(value = ["protocol"]),
        Index(value = ["isWorking"]),
        Index(value = ["countryCode"])
    ]
)
data class ServerConfigEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val protocol: String, // vless, vmess, trojan, ss
    val host: String,
    val port: Int,
    val rawLink: String,
    val uuidOrPassword: String = "",
    val flow: String = "",
    val securityType: String = "tls", // none, tls, reality
    val transportType: String = "tcp", // tcp, ws, grpc, httpupgrade, xhttp
    val sni: String = "",
    val publicKey: String = "", // for reality (pbk)
    val shortId: String = "", // for reality (sid)
    val fingerprint: String = "", // fp (chrome, firefox, etc.)
    val path: String = "", // ws or http path
    val serviceName: String = "", // grpc serviceName
    val alpn: String = "",
    val headerType: String = "none",
    val alterId: Int = 0,
    val countryCode: String = "AUTO",
    val countryNameFa: String = "اتصال خودکار",
    val countryNameEn: String = "Auto Location",
    val pingMs: Long = -1L,
    val isFavorite: Boolean = false,
    val isWorking: Boolean = true,
    val isManual: Boolean = false,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val sourceUrl: String = "",
    val lastTestedTime: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
