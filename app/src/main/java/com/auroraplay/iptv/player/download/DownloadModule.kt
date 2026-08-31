package com.auroraplay.iptv.player.download

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.scheduler.Requirements
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Singleton

/**
 * Provides the storage (Cache) and machinery (DownloadManager) behind
 * "Baixar" on movies/episodes. Uses its own dedicated cache directory and a
 * NoOpCacheEvictor — downloaded files are only ever removed when the user
 * explicitly deletes them (DownloadTracker.removeDownload), never by an
 * automatic size-based eviction policy that could silently delete a movie
 * someone is mid-way through offline.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {

    @Provides
    @Singleton
    fun provideDatabaseProvider(@ApplicationContext context: Context): StandaloneDatabaseProvider =
        StandaloneDatabaseProvider(context)

    @Provides
    @Singleton
    fun provideDownloadCache(
        @ApplicationContext context: Context,
        databaseProvider: StandaloneDatabaseProvider,
    ): Cache {
        val downloadDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "aurora_downloads")
        return SimpleCache(downloadDir, NoOpCacheEvictor(), databaseProvider)
    }

    @Provides
    @Singleton
    fun provideDownloadHttpDataSourceFactory(): DefaultHttpDataSource.Factory =
        DefaultHttpDataSource.Factory()
            .setUserAgent("AuroraPlay/1.0 (Android)")
            .setAllowCrossProtocolRedirects(true)
            // IPTV origins are often slow to answer and frequently redirect
            // http→https. Without explicit timeouts a stalled connect/read
            // sits on the default 8s and reads as a frozen picture; 20s gives
            // a genuinely slow server room to respond before ExoPlayer treats
            // it as an error and rebuffers.
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(20_000)

    /** Read/write factory used by the DownloadManager itself when executing downloads. */
    @Provides
    @Singleton
    @DownloadCacheReadWrite
    fun provideDownloadReadWriteCacheDataSourceFactory(
        cache: Cache,
        httpDataSourceFactory: DefaultHttpDataSource.Factory,
    ): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpDataSourceFactory)

    /** Read-only factory used by PlayerManager for everyday playback: it
     * serves already-downloaded content instantly from disk, but never
     * writes new data into the download cache while just streaming live TV
     * or an un-downloaded movie — that cache is reserved for explicit
     * "Baixar" requests only, so casual viewing can never grow it. */
    @Provides
    @Singleton
    @PlaybackCacheReadOnly
    fun providePlaybackCacheDataSourceFactory(
        cache: Cache,
        httpDataSourceFactory: DefaultHttpDataSource.Factory,
    ): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** DownloadManager builds its own internal downloader from this
     * DataSource.Factory — passing a DefaultDownloaderFactory directly here
     * is a type mismatch, since the constructor expects DataSource.Factory,
     * not Downloader.Factory. */
    @Provides
    @Singleton
    fun provideDownloadManager(
        @ApplicationContext context: Context,
        databaseProvider: StandaloneDatabaseProvider,
        cache: Cache,
        @DownloadCacheReadWrite cacheDataSourceFactory: CacheDataSource.Factory,
    ): DownloadManager =
        DownloadManager(context, databaseProvider, cache, cacheDataSourceFactory, Executors.newFixedThreadPool(3)).apply {
            maxParallelDownloads = 2
            requirements = Requirements(Requirements.NETWORK)
        }
}

@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadCacheReadWrite

@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlaybackCacheReadOnly
