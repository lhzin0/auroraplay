package com.auroraplay.iptv.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.auroraplay.iptv.data.database.dao.CategoryDao
import com.auroraplay.iptv.data.database.dao.ChannelDao
import com.auroraplay.iptv.data.database.dao.ConnectionDao
import com.auroraplay.iptv.data.database.dao.EpisodeDao
import com.auroraplay.iptv.data.database.dao.FavoriteDao
import com.auroraplay.iptv.data.database.dao.MovieDao
import com.auroraplay.iptv.data.database.dao.ProfileDao
import com.auroraplay.iptv.data.database.dao.SeriesDao
import com.auroraplay.iptv.data.database.dao.WatchProgressDao
import com.auroraplay.iptv.data.database.entity.CategoryEntity
import com.auroraplay.iptv.data.database.entity.ChannelEntity
import com.auroraplay.iptv.data.database.entity.ConnectionEntity
import com.auroraplay.iptv.data.database.entity.EpisodeEntity
import com.auroraplay.iptv.data.database.entity.FavoriteEntity
import com.auroraplay.iptv.data.database.entity.MovieEntity
import com.auroraplay.iptv.data.database.entity.ProfileEntity
import com.auroraplay.iptv.data.database.entity.SeriesEntity
import com.auroraplay.iptv.data.database.entity.WatchProgressEntity

@Database(
    entities = [
        ConnectionEntity::class,
        ProfileEntity::class,
        CategoryEntity::class,
        ChannelEntity::class,
        MovieEntity::class,
        SeriesEntity::class,
        EpisodeEntity::class,
        FavoriteEntity::class,
        WatchProgressEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun connectionDao(): ConnectionDao
    abstract fun profileDao(): ProfileDao
    abstract fun categoryDao(): CategoryDao
    abstract fun channelDao(): ChannelDao
    abstract fun movieDao(): MovieDao
    abstract fun seriesDao(): SeriesDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun watchProgressDao(): WatchProgressDao
}
