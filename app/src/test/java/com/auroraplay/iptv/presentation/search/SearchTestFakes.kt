package com.auroraplay.iptv.presentation.search

import com.auroraplay.iptv.core.util.Resource
import com.auroraplay.iptv.domain.model.Category
import com.auroraplay.iptv.domain.model.Channel
import com.auroraplay.iptv.domain.model.ContentType
import com.auroraplay.iptv.domain.model.EpgProgram
import com.auroraplay.iptv.domain.model.Movie
import com.auroraplay.iptv.domain.model.Profile
import com.auroraplay.iptv.domain.model.Series
import com.auroraplay.iptv.domain.model.WatchProgress
import com.auroraplay.iptv.domain.model.XtreamConnection
import com.auroraplay.iptv.domain.repository.AppSettings
import com.auroraplay.iptv.domain.repository.ConnectionRepository
import com.auroraplay.iptv.domain.repository.ContentRepository
import com.auroraplay.iptv.domain.repository.ProfileRepository
import com.auroraplay.iptv.domain.repository.SearchResults
import com.auroraplay.iptv.domain.repository.SettingsRepository
import com.auroraplay.iptv.domain.repository.SyncStage
import com.auroraplay.iptv.domain.repository.WatchProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Fakes shared by the `presentation.search` ViewModel tests. */
internal class FakeConnectionRepository(private val flow: MutableStateFlow<XtreamConnection?>) : ConnectionRepository {
    override fun observeConnections(): Flow<List<XtreamConnection>> = unsupported()
    override suspend fun getConnection(id: String): XtreamConnection? = unsupported()
    override suspend fun getDefaultConnection(): XtreamConnection? = unsupported()
    override fun observeDefaultConnection(): Flow<XtreamConnection?> = flow
    override fun addConnection(name: String, serverUrl: String, username: String, password: String, profileId: String?): Flow<Resource<XtreamConnection>> = unsupported()
    override suspend fun updateConnection(connection: XtreamConnection, newPassword: String?) = unsupported<Unit>()
    override suspend fun deleteConnection(id: String) = unsupported<Unit>()
    override suspend fun setDefault(id: String) = unsupported<Unit>()
    override fun testConnection(id: String): Flow<Resource<Unit>> = unsupported()
    override suspend fun getPassword(id: String): String? = unsupported()
}

internal class FakeProfileRepository(private val flow: MutableStateFlow<Profile?>) : ProfileRepository {
    override fun observeProfiles(): Flow<List<Profile>> = unsupported()
    override suspend fun addProfile(name: String, avatarColorHex: String, avatarEmoji: String, avatarUri: String?, isKids: Boolean): Profile = unsupported()
    override suspend fun updateProfile(profile: Profile) = unsupported<Unit>()
    override suspend fun getProfile(id: String): Profile? = unsupported()
    override suspend fun deleteProfile(id: String) = unsupported<Unit>()
    override suspend fun getActiveProfileId(): String? = unsupported()
    override suspend fun setActiveProfile(id: String) = unsupported<Unit>()
    override fun observeActiveProfile(): Flow<Profile?> = flow
}

internal class FakeContentRepository(
    private val moviesByConnection: Map<String, List<Movie>> = emptyMap(),
    private val seriesByConnection: Map<String, List<Series>> = emptyMap(),
    private val channelsByConnection: Map<String, List<Channel>> = emptyMap(),
) : ContentRepository {
    override fun syncConnection(connectionId: String) = unsupported<Flow<Resource<SyncStage>>>()
    override fun observeCategories(connectionId: String, type: ContentType) = unsupported<Flow<List<Category>>>()
    override fun observeChannels(connectionId: String, categoryId: String?): Flow<List<Channel>> =
        flowOf(channelsByConnection[connectionId].orEmpty())
    override fun observeMovies(connectionId: String, categoryId: String?): Flow<List<Movie>> =
        flowOf(moviesByConnection[connectionId].orEmpty())
    override fun observeSeries(connectionId: String, categoryId: String?): Flow<List<Series>> =
        flowOf(seriesByConnection[connectionId].orEmpty())
    override suspend fun getSeriesDetail(connectionId: String, seriesId: String, forceRefresh: Boolean, allowStaleRefresh: Boolean): Series? = unsupported()
    override suspend fun getMovieDetail(connectionId: String, movieId: String): Movie? = unsupported()
    override suspend fun refreshSeriesEpisodes(connectionId: String, seriesId: String): List<String>? = unsupported()
    override suspend fun getCachedMovie(connectionId: String, movieId: String): Movie? = unsupported()
    override suspend fun getCachedSeries(connectionId: String, seriesId: String): Series? = unsupported()
    override suspend fun getLastSyncMillis(connectionId: String): Long? = unsupported()
    override fun search(connectionId: String, query: String): Flow<SearchResults> = unsupported()
    override suspend fun getShortEpg(connectionId: String, channelId: String): Pair<EpgProgram?, EpgProgram?> = unsupported()
    override suspend fun getEpgTimeline(connectionId: String, channelId: String, limit: Int): List<EpgProgram> = unsupported()
}

internal class FakeWatchProgressRepository(
    private val continueWatchingByConnection: Map<String, List<WatchProgress>> = emptyMap(),
) : WatchProgressRepository {
    override fun observeContinueWatching(connectionId: String, profileId: String): Flow<List<WatchProgress>> =
        flowOf(continueWatchingByConnection[connectionId].orEmpty())
    override suspend fun getProgress(connectionId: String, profileId: String, contentId: String, type: ContentType): WatchProgress? = unsupported()
    override suspend fun getLatestSeriesProgress(connectionId: String, profileId: String, seriesId: String): WatchProgress? = unsupported()
    override suspend fun saveProgress(progress: WatchProgress) = unsupported<Unit>()
    override suspend fun removeProgress(connectionId: String, profileId: String, contentId: String, type: ContentType) = unsupported<Unit>()
    override fun observeWatchHistory(profileId: String): Flow<List<WatchProgress>> = unsupported()
    override suspend fun clearWatchHistory(profileId: String) = unsupported<Unit>()
    override suspend fun deleteHistoryItem(profileId: String, contentId: String, type: ContentType) = unsupported<Unit>()
    override suspend fun deleteSeriesFromHistory(profileId: String, seriesId: String) = unsupported<Unit>()
    override suspend fun removeFromContinueWatching(connectionId: String, profileId: String, contentId: String, isSeries: Boolean) = unsupported<Unit>()
    override fun observeChannelHistory(connectionId: String, profileId: String): Flow<List<WatchProgress>> = unsupported()
    override suspend fun recordChannelWatch(connectionId: String, profileId: String, channelId: String) = unsupported<Unit>()
}

/** Records every [addRecentSearch]/[clearRecentSearches] call and reflects them
 * back reactively through [recentSearches], the way the real DataStore-backed
 * implementation would. */
internal class FakeSettingsRepository : SettingsRepository {
    private val byProfile = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val addedSearches = mutableListOf<Pair<String, String>>()
    val clearedProfiles = mutableListOf<String>()

    override fun observeSettings(): Flow<AppSettings> = unsupported()
    override suspend fun updateAccentColor(hex: String) = unsupported<Unit>()
    override suspend fun updateCardSizeScale(scale: Float) = unsupported<Unit>()
    override suspend fun updateAnimationsEnabled(enabled: Boolean) = unsupported<Unit>()
    override suspend fun updateAutoPlayNext(enabled: Boolean) = unsupported<Unit>()
    override suspend fun updatePlaybackQuality(quality: String) = unsupported<Unit>()
    override suspend fun updatePreferredAudioLang(lang: String?) = unsupported<Unit>()
    override suspend fun updatePreferredSubtitleLang(lang: String?) = unsupported<Unit>()
    override suspend fun updateTmdbApiKey(key: String) = unsupported<Unit>()
    override suspend fun updateNotifyNewEpisodes(enabled: Boolean) = unsupported<Unit>()
    override suspend fun updateDownloadWifiOnly(enabled: Boolean) = unsupported<Unit>()
    override suspend fun updateSeekSeconds(seconds: Int) = unsupported<Unit>()
    override suspend fun updateFrostGlass(enabled: Boolean) = unsupported<Unit>()
    override suspend fun updateAutoSyncHours(hours: Int) = unsupported<Unit>()
    override suspend fun updatePipEnabled(enabled: Boolean) = unsupported<Unit>()
    override suspend fun updateCinemaMode(enabled: Boolean) = unsupported<Unit>()
    override suspend fun restoreFrom(settings: AppSettings) = unsupported<Unit>()
    override suspend fun clearCache() = unsupported<Unit>()
    override suspend fun restoreDefaults() = unsupported<Unit>()

    override fun recentSearches(profileId: String): Flow<List<String>> =
        byProfile.map { it[profileId].orEmpty() }

    override suspend fun addRecentSearch(profileId: String, query: String) {
        addedSearches += profileId to query
        byProfile.value = byProfile.value + (profileId to (byProfile.value[profileId].orEmpty() + query))
    }

    override suspend fun clearRecentSearches(profileId: String) {
        clearedProfiles += profileId
        byProfile.value = byProfile.value + (profileId to emptyList())
    }
}

internal fun <T> unsupported(): T = throw UnsupportedOperationException("Not needed by SearchViewModel")
