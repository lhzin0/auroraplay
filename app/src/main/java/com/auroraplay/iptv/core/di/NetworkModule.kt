package com.auroraplay.iptv.core.di

import android.content.Context
import com.auroraplay.iptv.BuildConfig
import com.auroraplay.iptv.data.api.XtreamApiService
import com.auroraplay.iptv.data.api.tmdb.TmdbApiService
import com.auroraplay.iptv.data.api.wikipedia.WikipediaApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            // BASIC logging was unconditional, including in release builds —
            // every request URL for an Xtream connection carries the
            // person's username and password in plain query params, so this
            // was writing credentials to logcat on every sync, plus paying
            // the interceptor's overhead on every call for no benefit once
            // the app is actually installed on someone's phone.
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(logging)
            // TMDB/Wikipedia both send standard cache-control headers, so a
            // disk cache turns a repeat lookup (re-opening a title, retrying
            // after a cold start) into a local read instead of a fresh
            // request. Xtream's own endpoints don't send cache headers, so
            // this is a no-op for them either way — safe to share the client.
            .cache(Cache(java.io.File(context.cacheDir, "http_cache"), 25L * 1024 * 1024))
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            // Base URL is a placeholder: every @Url call supplies the user's own
            // server address, since each Xtream connection has a different host.
            .baseUrl("https://placeholder.invalid/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideXtreamApiService(retrofit: Retrofit): XtreamApiService =
        retrofit.create(XtreamApiService::class.java)

    /** Separate Retrofit instance: TMDB has a real fixed base URL, unlike the
     * per-connection Xtream servers which supply a full @Url per call. */
    @Provides
    @Singleton
    @Named("tmdb")
    fun provideTmdbRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideTmdbApiService(@Named("tmdb") retrofit: Retrofit): TmdbApiService =
        retrofit.create(TmdbApiService::class.java)

    /** Wikipedia calls pass absolute @Url values, so the base URL is a placeholder. */
    @Provides
    @Singleton
    fun provideWikipediaApiService(retrofit: Retrofit): WikipediaApiService =
        retrofit.create(WikipediaApiService::class.java)
}
