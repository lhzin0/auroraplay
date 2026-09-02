package com.auroraplay.iptv.data.database.dao

import androidx.room.*
import com.auroraplay.iptv.data.database.entity.CategoryEntity
import com.auroraplay.iptv.data.database.entity.ChannelEntity
import com.auroraplay.iptv.data.database.entity.EpisodeEntity
import com.auroraplay.iptv.data.database.entity.MovieEntity
import com.auroraplay.iptv.data.database.entity.SeriesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE connectionId = :connectionId AND type = :type ORDER BY name ASC")
    fun observe(connectionId: String, type: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE connectionId = :connectionId AND type = :type")
    suspend fun getAll(connectionId: String, type: String): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE connectionId = :connectionId AND type = :type")
    suspend fun clear(connectionId: String, type: String)
}

@Dao
interface ChannelDao {
    @Query("""SELECT * FROM channels WHERE connectionId = :connectionId
        AND (:categoryId IS NULL OR categoryId = :categoryId) ORDER BY name ASC""")
    fun observe(connectionId: String, categoryId: String?): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE connectionId = :connectionId AND name LIKE '%' || :query || '%' LIMIT 50")
    suspend fun search(connectionId: String, query: String): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE id = :id AND connectionId = :connectionId LIMIT 1")
    suspend fun getById(connectionId: String, id: String): ChannelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE connectionId = :connectionId")
    suspend fun clear(connectionId: String)
}

@Dao
interface MovieDao {
    @Query("""SELECT * FROM movies WHERE connectionId = :connectionId
        AND (:categoryId IS NULL OR categoryId = :categoryId) ORDER BY addedAtMillis DESC""")
    fun observe(connectionId: String, categoryId: String?): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE connectionId = :connectionId AND name LIKE '%' || :query || '%' LIMIT 50")
    suspend fun search(connectionId: String, query: String): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE id = :id AND connectionId = :connectionId LIMIT 1")
    suspend fun getById(connectionId: String, id: String): MovieEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(movies: List<MovieEntity>)

    @Update
    suspend fun update(movie: MovieEntity)

    @Query("DELETE FROM movies WHERE connectionId = :connectionId")
    suspend fun clear(connectionId: String)
}

@Dao
interface SeriesDao {
    @Query("""SELECT * FROM series WHERE connectionId = :connectionId
        AND (:categoryId IS NULL OR categoryId = :categoryId) ORDER BY addedAtMillis DESC""")
    fun observe(connectionId: String, categoryId: String?): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE connectionId = :connectionId AND name LIKE '%' || :query || '%' LIMIT 50")
    suspend fun search(connectionId: String, query: String): List<SeriesEntity>

    @Query("SELECT * FROM series WHERE id = :id AND connectionId = :connectionId LIMIT 1")
    suspend fun getById(connectionId: String, id: String): SeriesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(series: List<SeriesEntity>)

    @Query("DELETE FROM series WHERE connectionId = :connectionId")
    suspend fun clear(connectionId: String)
}

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId AND connectionId = :connectionId ORDER BY seasonNumber ASC, episodeNumber ASC")
    suspend fun getForSeries(connectionId: String, seriesId: String): List<EpisodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(episodes: List<EpisodeEntity>)

    @Query("DELETE FROM episodes WHERE seriesId = :seriesId AND connectionId = :connectionId")
    suspend fun clearForSeries(connectionId: String, seriesId: String)
}
