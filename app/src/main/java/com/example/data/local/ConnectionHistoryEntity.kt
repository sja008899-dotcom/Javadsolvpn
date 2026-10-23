package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "connection_history")
data class ConnectionHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverName: String,
    val protocol: String,
    val connectedAt: Long = System.currentTimeMillis(),
    val durationSeconds: Long = 0L,
    val bytesDownloaded: Long = 0L,
    val bytesUploaded: Long = 0L,
    val disconnectReason: String = "قطع توسط کاربر"
)
