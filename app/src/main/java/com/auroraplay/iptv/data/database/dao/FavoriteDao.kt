package com.auroraplay.iptv.data.database.dao

import androidx.room.*
import com.auroraplay.iptv.data.database.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites WHERE profileId = :profileId AND (:type IS NULL OR type = :type) ORDER BY addedAtMillis DESC")
    fun observe(profileId: String, type: String?): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE profileId = :profileId AND contentId = :contentId AND type = :type)")
    fun observeIsFavorite(profileId: String, contentId: String, type: String): Flow<Boolean>

    @Query("SELECT * FROM favorites WHERE profileId = :profileId AND contentId = :contentId AND type = :type LIMIT 1")
    suspend fun get(profileId: String, contentId: String, type: String): FavoriteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE profileId = :profileId AND contentId = :contentId AND type = :type")
    suspend fun delete(profileId: String, contentId: String, type: String)
}
