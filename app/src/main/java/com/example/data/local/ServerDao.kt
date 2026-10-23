package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {
    @Query("SELECT * FROM server_configs ORDER BY isFavorite DESC, pingMs ASC, id ASC")
    fun getAllServers(): Flow<List<ServerConfigEntity>>

    @Query("SELECT * FROM server_configs WHERE isFavorite = 1 ORDER BY pingMs ASC")
    fun getFavoriteServers(): Flow<List<ServerConfigEntity>>

    @Query("SELECT * FROM server_configs WHERE protocol = :protocol ORDER BY pingMs ASC")
    fun getServersByProtocol(protocol: String): Flow<List<ServerConfigEntity>>

    @Query("SELECT * FROM server_configs WHERE countryCode = :countryCode ORDER BY pingMs ASC")
    fun getServersByCountry(countryCode: String): Flow<List<ServerConfigEntity>>

    @Query("SELECT * FROM server_configs WHERE id = :id LIMIT 1")
    suspend fun getServerById(id: Long): ServerConfigEntity?

    @Query("SELECT * FROM server_configs WHERE isWorking = 1 ORDER BY isFavorite DESC, (CASE WHEN pingMs > 0 THEN pingMs ELSE 9999 END) ASC LIMIT 1")
    suspend fun getBestCandidate(): ServerConfigEntity?

    @Query("SELECT * FROM server_configs WHERE isWorking = 1 AND id != :excludeId ORDER BY isFavorite DESC, (CASE WHEN pingMs > 0 THEN pingMs ELSE 9999 END) ASC LIMIT 1")
    suspend fun getNextCandidate(excludeId: Long): ServerConfigEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertServer(server: ServerConfigEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(servers: List<ServerConfigEntity>): List<Long>

    @Update
    suspend fun updateServer(server: ServerConfigEntity)

    @Query("UPDATE server_configs SET pingMs = :ping, lastTestedTime = :timestamp WHERE id = :id")
    suspend fun updatePing(id: Long, ping: Long, timestamp: Long)

    @Query("UPDATE server_configs SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE server_configs SET successCount = successCount + 1, isWorking = 1 WHERE id = :id")
    suspend fun recordSuccess(id: Long)

    @Query("UPDATE server_configs SET failureCount = failureCount + 1 WHERE id = :id")
    suspend fun recordFailure(id: Long)

    @Query("DELETE FROM server_configs WHERE id = :id")
    suspend fun deleteServer(id: Long)

    @Query("DELETE FROM server_configs")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM server_configs")
    fun getServerCount(): Flow<Int>
}
