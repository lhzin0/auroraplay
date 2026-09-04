package com.auroraplay.iptv.core.di

import android.content.Context
import androidx.room.Room
import com.auroraplay.iptv.core.util.Constants
import com.auroraplay.iptv.data.database.AppDatabase
import com.auroraplay.iptv.data.database.AppDatabaseMigrations
import com.auroraplay.iptv.data.database.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, Constants.DATABASE_NAME)
            .addMigrations(*AppDatabaseMigrations.ALL)
            // No fallbackToDestructiveMigration: a missing or failed migration
            // must throw on open (loud, recoverable by reinstalling the current
            // app) rather than silently drop the user's profiles, favourites,
            // watch history and downloads index. Every DB version bump adds its
            // migration in AppDatabaseMigrations.
            .build()

    @Provides fun provideConnectionDao(db: AppDatabase): ConnectionDao = db.connectionDao()
    @Provides fun provideProfileDao(db: AppDatabase): ProfileDao = db.profileDao()
    @Provides fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideChannelDao(db: AppDatabase): ChannelDao = db.channelDao()
    @Provides fun provideMovieDao(db: AppDatabase): MovieDao = db.movieDao()
    @Provides fun provideSeriesDao(db: AppDatabase): SeriesDao = db.seriesDao()
    @Provides fun provideEpisodeDao(db: AppDatabase): EpisodeDao = db.episodeDao()
    @Provides fun provideFavoriteDao(db: AppDatabase): FavoriteDao = db.favoriteDao()
    @Provides fun provideWatchProgressDao(db: AppDatabase): WatchProgressDao = db.watchProgressDao()
}
