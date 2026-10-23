package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {
    @Query("SELECT * FROM discovery_sources ORDER BY id ASC")
    fun getAllSources(): Flow<List<DiscoverySourceEntity>>

    @Query("SELECT * FROM discovery_sources WHERE isEnabled = 1")
    suspend fun getEnabledSources(): List<DiscoverySourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(source: DiscoverySourceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSources(sources: List<DiscoverySourceEntity>)

    @Update
    suspend fun updateSource(source: DiscoverySourceEntity)

    @Query("UPDATE discovery_sources SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE discovery_sources SET lastFetched = :timestamp, configsCount = :count, lastStatus = :status WHERE id = :id")
    suspend fun updateFetchStatus(id: Long, timestamp: Long, count: Int, status: String)

    @Query("DELETE FROM discovery_sources WHERE id = :id")
    suspend fun deleteSource(id: Long)
}
