package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM connection_history ORDER BY connectedAt DESC LIMIT 100")
    fun getAllHistory(): Flow<List<ConnectionHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: ConnectionHistoryEntity): Long

    @Query("DELETE FROM connection_history")
    suspend fun clearHistory()
}
