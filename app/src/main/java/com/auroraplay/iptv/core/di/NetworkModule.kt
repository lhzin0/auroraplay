package com.auroraplay.iptv.core.di

import android.content.Context
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
import java.io.IOException
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
        // Older builds shared a disk cache with authenticated Xtream requests.
        // Clear that obsolete, app-private cache; it may contain credential URLs.
        java.io.File(context.cacheDir, "http_cache").deleteRecursively()
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            // No HTTP logging or disk cache: Xtream URLs contain credentials.
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                val redirect = response.header("Location")?.let(request.url::resolve)
                if (request.url.isHttps && response.isRedirect && redirect?.isHttps == false) {
                    response.close()
                    throw IOException("O servidor tentou redirecionar uma conexão segura para HTTP.")
                }
                response
            }
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
    fun provideTmdbRetrofit(client: OkHttpClient, @ApplicationContext context: Context): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .client(client.newBuilder().cache(Cache(java.io.File(context.cacheDir, "metadata_http_cache"), 25L * 1024 * 1024)).build())
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
