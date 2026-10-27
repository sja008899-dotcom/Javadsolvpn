package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {

    @Query("SELECT * FROM server_configs ORDER BY isFavorite DESC, (CASE WHEN isWorking = 1 AND pingMs > 0 THEN pingMs ELSE 99999 END) ASC, id ASC")
    fun getAllServers(): Flow<List<ServerConfigEntity>>

    @Query("SELECT * FROM server_configs")
    suspend fun getAllServersOnce(): List<ServerConfigEntity>

    @Query("SELECT COUNT(*) FROM server_configs")
    suspend fun getServerCountNow(): Int

    @Query("SELECT * FROM server_configs WHERE isFavorite = 1 ORDER BY (CASE WHEN isWorking = 1 AND pingMs > 0 THEN pingMs ELSE 99999 END) ASC")
    fun getFavoriteServers(): Flow<List<ServerConfigEntity>>

    @Query("SELECT * FROM server_configs WHERE protocol = :protocol ORDER BY (CASE WHEN isWorking = 1 AND pingMs > 0 THEN pingMs ELSE 99999 END) ASC")
    fun getServersByProtocol(protocol: String): Flow<List<ServerConfigEntity>>

    @Query("SELECT * FROM server_configs WHERE countryCode = :countryCode ORDER BY (CASE WHEN isWorking = 1 AND pingMs > 0 THEN pingMs ELSE 99999 END) ASC")
    fun getServersByCountry(countryCode: String): Flow<List<ServerConfigEntity>>

    @Query("SELECT * FROM server_configs WHERE id = :id LIMIT 1")
    suspend fun getServerById(id: Long): ServerConfigEntity?

    @Query("""
        SELECT * FROM server_configs
        ORDER BY
            isFavorite DESC,
            CASE WHEN isWorking = 1 AND pingMs > 0 THEN 0
                 WHEN failureCount == 0 THEN 1
                 ELSE 2 END ASC,
            CASE WHEN pingMs > 0 THEN pingMs ELSE 999999 END ASC,
            failureCount ASC,
            successCount DESC,
            id ASC
        LIMIT 1
    """)
    suspend fun getBestCandidate(): ServerConfigEntity?

    @Query("""
        SELECT * FROM server_configs
        WHERE id != :excludeId
        ORDER BY
            isFavorite DESC,
            CASE WHEN isWorking = 1 AND pingMs > 0 THEN 0
                 WHEN failureCount == 0 THEN 1
                 ELSE 2 END ASC,
            CASE WHEN pingMs > 0 THEN pingMs ELSE 999999 END ASC,
            failureCount ASC,
            successCount DESC,
            id ASC
        LIMIT 1
    """)
    suspend fun getNextCandidate(excludeId: Long): ServerConfigEntity?

    @Query("""
        SELECT id FROM server_configs
        WHERE isWorking = 0 AND isFavorite = 0 AND isManual = 0
        ORDER BY failureCount DESC, lastTestedTime ASC, id ASC
        LIMIT 1
    """)
    suspend fun getReplaceableBrokenId(): Long?

    @Query("""
        SELECT id FROM server_configs
        WHERE isFavorite = 0 AND isManual = 0
        ORDER BY
            CASE WHEN isWorking = 0 THEN 0
                 WHEN pingMs <= 0 THEN 1
                 ELSE 2 END ASC,
            failureCount DESC,
            CASE WHEN pingMs > 0 THEN pingMs ELSE 999999 END DESC,
            lastTestedTime ASC,
            id ASC
        LIMIT 1
    """)
    suspend fun getReplaceableCandidateId(): Long?

    @Query("""
        DELETE FROM server_configs
        WHERE id NOT IN (
            SELECT id FROM server_configs
            ORDER BY
                CASE WHEN isWorking = 1 AND pingMs > 0 THEN 0 ELSE 1 END ASC,
                isFavorite DESC,
                CASE WHEN pingMs > 0 THEN pingMs ELSE 999999 END ASC,
                successCount DESC,
                failureCount ASC,
                lastTestedTime DESC,
                id ASC
            LIMIT :limit
        )
    """)
    suspend fun trimToLimit(limit: Int = ServerLimits.MAX_ACTIVE_CONFIGS)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertServer(server: ServerConfigEntity): Long

    @Transaction
    suspend fun insertCapped(
        servers: List<ServerConfigEntity>,
        limit: Int = ServerLimits.MAX_ACTIVE_CONFIGS
    ): Int {
        require(limit in 1..ServerLimits.MAX_ACTIVE_CONFIGS) {
            "Active server limit cannot exceed ${ServerLimits.MAX_ACTIVE_CONFIGS}"
        }

        trimToLimit(limit)

        val seen = getAllServersOnce()
            .mapTo(mutableSetOf()) { ConfigFingerprint.of(it) }

        var count = getServerCountNow()
        var insertedCount = 0

        for (candidate in servers) {
            if (count >= limit) break
            val fingerprint = ConfigFingerprint.of(candidate)
            if (!seen.add(fingerprint)) continue

            val unverified = candidate.copy(
                isWorking = false,
                pingMs = -1L,
                lastTestedTime = 0L
            )
            val id = insertServer(unverified)
            if (id != -1L) {
                count++
                insertedCount++
            }
        }
        return insertedCount
    }

    /**
     * Explicit user import may replace a non-favorite, auto-discovered entry when the
     * 12-slot pool is full. Imported links stay unverified until a real connection test.
     */
    @Transaction
    suspend fun insertManualReplacingAuto(
        candidate: ServerConfigEntity,
        limit: Int = ServerLimits.MAX_ACTIVE_CONFIGS
    ): Boolean {
        require(limit in 1..ServerLimits.MAX_ACTIVE_CONFIGS) {
            "Active server limit cannot exceed ${ServerLimits.MAX_ACTIVE_CONFIGS}"
        }
        trimToLimit(limit)

        val existing = getAllServersOnce()
        val fingerprint = ConfigFingerprint.of(candidate)
        if (existing.any {
                it.rawLink == candidate.rawLink || ConfigFingerprint.of(it) == fingerprint
            }) return false

        if (getServerCountNow() >= limit) {
            // Never evict a favorite or another manually imported config.
            val victimId = getReplaceableCandidateId() ?: return false
            deleteServer(victimId)
        }

        val id = insertServer(
            candidate.copy(
                isManual = true,
                isWorking = false,
                pingMs = -1L,
                lastTestedTime = 0L
            )
        )
        return id != -1L
    }

    @Transaction
    suspend fun insertVerifiedReplacingBroken(
        candidate: ServerConfigEntity,
        limit: Int = ServerLimits.MAX_ACTIVE_CONFIGS
    ): Boolean {
        require(limit in 1..ServerLimits.MAX_ACTIVE_CONFIGS)
        trimToLimit(limit)

        val existing = getAllServersOnce()
        val candidateFingerprint = ConfigFingerprint.of(candidate)
        if (existing.any {
                it.rawLink == candidate.rawLink || ConfigFingerprint.of(it) == candidateFingerprint
            }) return false

        if (getServerCountNow() >= limit) {
            val victimId = getReplaceableCandidateId() ?: return false
            deleteServer(victimId)
        }

        val insertedId = insertServer(
            candidate.copy(
                isWorking = true,
                pingMs = if (candidate.pingMs > 0) candidate.pingMs else 50L,
                lastTestedTime = System.currentTimeMillis()
            )
        )
        if (insertedId == -1L) {
            return false
        }
        return true
    }

    @Update
    suspend fun updateServer(server: ServerConfigEntity)

    @Query("UPDATE server_configs SET pingMs = :ping, isWorking = :isWorking, lastTestedTime = :timestamp WHERE id = :id")
    suspend fun updatePingAndStatus(id: Long, ping: Long, isWorking: Boolean, timestamp: Long)

    @Query("UPDATE server_configs SET pingMs = :ping, lastTestedTime = :timestamp WHERE id = :id")
    suspend fun updatePing(id: Long, ping: Long, timestamp: Long)

    @Query("UPDATE server_configs SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE server_configs SET successCount = successCount + 1, isWorking = 1 WHERE id = :id")
    suspend fun recordSuccess(id: Long)

    @Query("""
        UPDATE server_configs
        SET failureCount = failureCount + 1,
            isWorking = 0,
            pingMs = -1
        WHERE id = :id
    """)
    suspend fun recordFailure(id: Long)

    @Query("DELETE FROM server_configs WHERE id = :id")
    suspend fun deleteServer(id: Long)

    @Query("DELETE FROM server_configs")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM server_configs")
    fun getServerCount(): Flow<Int>
}
