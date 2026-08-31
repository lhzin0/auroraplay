package com.auroraplay.iptv.data.database.dao

import androidx.room.*
import com.auroraplay.iptv.data.database.entity.ConnectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionDao {
    @Query("SELECT * FROM connections ORDER BY isDefault DESC, name ASC")
    fun observeAll(): Flow<List<ConnectionEntity>>

    @Query("SELECT * FROM connections WHERE id = :id")
    suspend fun getById(id: String): ConnectionEntity?

    @Query("SELECT * FROM connections WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): ConnectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(connection: ConnectionEntity)

    @Query("DELETE FROM connections WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE connections SET isDefault = 0")
    suspend fun clearDefaults()

    @Query("UPDATE connections SET isDefault = 1 WHERE id = :id")
    suspend fun markDefault(id: String)

    @Query("UPDATE connections SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE connections SET lastSyncMillis = :millis WHERE id = :id")
    suspend fun updateLastSync(id: String, millis: Long)
}
