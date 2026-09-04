package com.auroraplay.iptv.data.database.dao

import androidx.room.*
import com.auroraplay.iptv.data.database.entity.WatchProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchProgressDao {
    @Query("""SELECT * FROM watch_progress WHERE profileId = :profileId
        AND hiddenFromContinue = 0
        AND (positionMillis * 1.0 / MAX(durationMillis, 1)) BETWEEN 0.02 AND 0.95
        ORDER BY lastWatchedMillis DESC""")
    fun observeContinueWatching(profileId: String): Flow<List<WatchProgressEntity>>

    /** Full watch history for the profile — every film/episode ever played,
     * finished or not, hidden from the rail or not. Newest first. Backs the
     * "Histórico" card; kept until the user clears it. LIVE channel rows are
     * excluded (they live in "Canais recentes"). */
    @Query("""SELECT * FROM watch_progress WHERE profileId = :profileId
        AND type <> 'LIVE'
        ORDER BY lastWatchedMillis DESC""")
    fun observeWatchHistory(profileId: String): Flow<List<WatchProgressEntity>>

    /** Manual "Limpar histórico" — never called automatically. */
    @Query("DELETE FROM watch_progress WHERE profileId = :profileId AND type <> 'LIVE'")
    suspend fun clearWatchHistory(profileId: String)

    /** Remove one Histórico row (a movie, or a single episode "<seriesId>:<epId>"). */
    @Query("DELETE FROM watch_progress WHERE profileId = :profileId AND contentId = :contentId AND type = :type")
    suspend fun deleteByKey(profileId: String, contentId: String, type: String)

    /** Remove a whole series from the Histórico: the "<seriesId>" row plus
     * every "<seriesId>:<episodeId>" episode row. */
    @Query("""DELETE FROM watch_progress
        WHERE profileId = :profileId AND type = 'SERIES'
        AND (contentId = :seriesId OR contentId LIKE :seriesId || ':%')""")
    suspend fun deleteSeriesHistory(profileId: String, seriesId: String)

    /** "Remover de Continuar assistindo" for a single movie — keeps the row,
     * the progress and the history; only drops it from the rail. */
    @Query("UPDATE watch_progress SET hiddenFromContinue = 1 WHERE profileId = :profileId AND contentId = :contentId AND type = :type")
    suspend fun hideFromContinue(profileId: String, contentId: String, type: String)

    /** Same, for a whole series: episode rows are stored as "<seriesId>:<episodeId>",
     * so match the series id itself and every "<seriesId>:*" episode. */
    @Query("""UPDATE watch_progress SET hiddenFromContinue = 1
        WHERE profileId = :profileId AND type = 'SERIES'
        AND (contentId = :seriesId OR contentId LIKE :seriesId || ':%')""")
    suspend fun hideSeriesFromContinue(profileId: String, seriesId: String)

    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId AND contentId = :contentId AND type = :type LIMIT 1")
    suspend fun get(profileId: String, contentId: String, type: String): WatchProgressEntity?

    /** Every row — for the local Auto-Backup snapshot. */
    @Query("SELECT * FROM watch_progress")
    suspend fun getAll(): List<WatchProgressEntity>

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
