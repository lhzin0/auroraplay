package com.auroraplay.iptv.core.di

import com.auroraplay.iptv.data.repository.ConnectionRepositoryImpl
import com.auroraplay.iptv.data.repository.ContentRepositoryImpl
import com.auroraplay.iptv.data.repository.FavoriteRepositoryImpl
import com.auroraplay.iptv.data.repository.ProfileRepositoryImpl
import com.auroraplay.iptv.data.repository.SettingsRepositoryImpl
import com.auroraplay.iptv.data.repository.WatchProgressRepositoryImpl
import com.auroraplay.iptv.domain.repository.ConnectionRepository
import com.auroraplay.iptv.domain.repository.ContentRepository
import com.auroraplay.iptv.domain.repository.FavoriteRepository
import com.auroraplay.iptv.domain.repository.ProfileRepository
import com.auroraplay.iptv.domain.repository.SettingsRepository
import com.auroraplay.iptv.domain.repository.WatchProgressRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindConnectionRepository(impl: ConnectionRepositoryImpl): ConnectionRepository

    @Binds @Singleton
    abstract fun bindContentRepository(impl: ContentRepositoryImpl): ContentRepository

    @Binds @Singleton
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds @Singleton
    abstract fun bindFavoriteRepository(impl: FavoriteRepositoryImpl): FavoriteRepository

    @Binds @Singleton
    abstract fun bindWatchProgressRepository(impl: WatchProgressRepositoryImpl): WatchProgressRepository

    @Binds @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
