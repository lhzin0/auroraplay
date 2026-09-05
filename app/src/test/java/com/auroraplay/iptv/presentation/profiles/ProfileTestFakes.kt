package com.auroraplay.iptv.presentation.profiles

import com.auroraplay.iptv.core.util.Resource
import com.auroraplay.iptv.domain.model.Category
import com.auroraplay.iptv.domain.model.Channel
import com.auroraplay.iptv.domain.model.ContentType
import com.auroraplay.iptv.domain.model.EpgProgram
import com.auroraplay.iptv.domain.model.Movie
import com.auroraplay.iptv.domain.model.Profile
import com.auroraplay.iptv.domain.model.Series
import com.auroraplay.iptv.domain.model.XtreamConnection
import com.auroraplay.iptv.domain.repository.ConnectionRepository
import com.auroraplay.iptv.domain.repository.ContentRepository
import com.auroraplay.iptv.domain.repository.ProfileRepository
import com.auroraplay.iptv.domain.repository.SearchResults
import com.auroraplay.iptv.domain.repository.SyncStage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/** Fakes shared by the `presentation.profiles` ViewModel tests. */
internal class FakeProfileRepository(initial: List<Profile> = emptyList()) : ProfileRepository {
    private val flow = MutableStateFlow(initial)
    private var activeId: String? = null
    private var nextId = 0

    override fun observeProfiles(): Flow<List<Profile>> = flow

    override suspend fun addProfile(name: String, avatarColorHex: String, avatarEmoji: String, avatarUri: String?, isKids: Boolean): Profile {
        val profile = Profile(
            id = "new-${nextId++}", name = name, avatarColorHex = avatarColorHex,
            avatarEmoji = avatarEmoji, avatarUri = avatarUri, isKids = isKids,
        )
        flow.value = flow.value + profile
        return profile
    }

    override suspend fun updateProfile(profile: Profile) {
        flow.value = flow.value.map { if (it.id == profile.id) profile else it }
    }

    override suspend fun getProfile(id: String): Profile? = flow.value.find { it.id == id }

    override suspend fun deleteProfile(id: String) {
        flow.value = flow.value.filterNot { it.id == id }
    }

    override suspend fun getActiveProfileId(): String? = activeId

    override suspend fun setActiveProfile(id: String) { activeId = id }

    override fun observeActiveProfile(): Flow<Profile?> = unsupported()
}

internal class FakeConnectionRepository(private val default: XtreamConnection?) : ConnectionRepository {
    override fun observeConnections(): Flow<List<XtreamConnection>> = unsupported()
    override suspend fun getConnection(id: String): XtreamConnection? = unsupported()
    override suspend fun getDefaultConnection(): XtreamConnection? = default
    override fun observeDefaultConnection(): Flow<XtreamConnection?> = unsupported()
    override fun addConnection(name: String, serverUrl: String, username: String, password: String, profileId: String?, backupServerUrl: String?, sourceType: String, xmltvUrl: String?): Flow<Resource<XtreamConnection>> = unsupported()
    override suspend fun updateConnection(connection: XtreamConnection, newPassword: String?) = unsupported<Unit>()
    override suspend fun deleteConnection(id: String) = unsupported<Unit>()
    override suspend fun setDefault(id: String) = unsupported<Unit>()
    override fun testConnection(id: String): Flow<Resource<Unit>> = unsupported()
    override suspend fun getPassword(id: String): String? = unsupported()
}

internal class FakeContentRepository(
    private val moviesByConnection: Map<String, List<Movie>> = emptyMap(),
    private val seriesByConnection: Map<String, List<Series>> = emptyMap(),
) : ContentRepository {
    override fun syncConnection(connectionId: String) = unsupported<Flow<Resource<SyncStage>>>()
    override fun observeCategories(connectionId: String, type: ContentType) = unsupported<Flow<List<Category>>>()
    override fun observeChannels(connectionId: String, categoryId: String?) = unsupported<Flow<List<Channel>>>()
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

internal fun <T> unsupported(): T = throw UnsupportedOperationException("Not needed by this test")
