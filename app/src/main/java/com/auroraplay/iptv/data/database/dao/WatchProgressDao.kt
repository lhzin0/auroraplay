package com.auroraplay.iptv.data.database.dao

import androidx.room.*
import com.auroraplay.iptv.data.database.entity.WatchProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchProgressDao {
    @Query("""SELECT * FROM watch_progress WHERE profileId = :profileId
        AND (positionMillis * 1.0 / MAX(durationMillis, 1)) BETWEEN 0.02 AND 0.95
        ORDER BY lastWatchedMillis DESC""")
    fun observeContinueWatching(profileId: String): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId AND contentId = :contentId LIMIT 1")
    suspend fun get(profileId: String, contentId: String): WatchProgressEntity?

    /** Recently-opened live channels, newest first — the "Canais recentes"
     * Home rail. Stored in the same table with type = 'LIVE' and a zero
     * position so it never leaks into "Continuar assistindo". */
    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId AND type = 'LIVE' ORDER BY lastWatchedMillis DESC LIMIT 10")
    fun observeChannelHistory(profileId: String): Flow<List<WatchProgressEntity>>

    /** Keeps the live-channel history at 10 rows per profile. */
    @Query(
        """DELETE FROM watch_progress WHERE profileId = :profileId AND type = 'LIVE'
        AND contentId NOT IN (
            SELECT contentId FROM watch_progress WHERE profileId = :profileId AND type = 'LIVE'
            ORDER BY lastWatchedMillis DESC LIMIT 10
        )"""
    )
    suspend fun trimChannelHistory(profileId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: WatchProgressEntity)

    @Query("DELETE FROM watch_progress WHERE profileId = :profileId AND contentId = :contentId AND type = :type")
    suspend fun delete(profileId: String, contentId: String, type: String)
}
