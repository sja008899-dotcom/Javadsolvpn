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
        Index(value = ["countryCode"])
    ]
)
data class ServerConfigEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val protocol: String, // vless, vmess, trojan, ss, openvpn
    val host: String,
    val port: Int,
    val rawLink: String,
    val countryCode: String = "AUTO",
    val countryNameFa: String = "اتصال خودکار",
    val countryNameEn: String = "Auto Location",
    val securityType: String = "tls", // none, tls, reality
    val transportType: String = "tcp", // tcp, ws, grpc, xhttp
    val pingMs: Long = -1L,
    val isFavorite: Boolean = false,
    val isWorking: Boolean = true,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val sourceUrl: String = "",
    val lastTestedTime: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
