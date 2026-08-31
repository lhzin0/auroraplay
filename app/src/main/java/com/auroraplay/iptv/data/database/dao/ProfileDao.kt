package com.auroraplay.iptv.data.database.dao

import androidx.room.*
import com.auroraplay.iptv.data.database.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY createdAtMillis ASC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: String): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: String)
}
