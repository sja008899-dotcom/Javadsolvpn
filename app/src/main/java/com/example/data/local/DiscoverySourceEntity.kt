package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "discovery_sources")
data class DiscoverySourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val isEnabled: Boolean = true,
    val lastFetched: Long = 0L,
    val configsCount: Int = 0,
    val lastStatus: String = "آماده"
)
