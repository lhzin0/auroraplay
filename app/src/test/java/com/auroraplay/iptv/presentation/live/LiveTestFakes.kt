package com.auroraplay.iptv.presentation.live

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
import kotlinx.coroutines.flow.map

/**
 * Fakes shared by the `presentation.live` ViewModel tests (LiveTvViewModel,
 * EpgGuideViewModel today). `internal`, not `private`, so more than one test
 * class in this package can use the same one without a top-level
 * redeclaration clash (two `private class Foo` in the same package compile
 * to the same class file name).
 */
internal class FakeConnectionRepository(private val flow: MutableStateFlow<XtreamConnection?>) : ConnectionRepository {
    override fun observeConnections(): Flow<List<XtreamConnection>> = unsupported()
    override suspend fun getConnection(id: String): XtreamConnection? = unsupported()
    override suspend fun getDefaultConnection(): XtreamConnection? = unsupported()
    override fun observeDefaultConnection(): Flow<XtreamConnection?> = flow
    override fun addConnection(name: String, serverUrl: String, username: String, password: String, profileId: String?, backupServerUrl: String?, sourceType: String, xmltvUrl: String?): Flow<Resource<XtreamConnection>> = unsupported()
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

/** [categoriesByConnection] and/or [channelsByConnection] default to empty for
 * a test that only needs one of the two. */
internal open class FakeContentRepository(
    private val categoriesByConnection: Map<String, List<Category>> = emptyMap(),
    private val channelsByConnection: Map<String, MutableStateFlow<List<Channel>>> = emptyMap(),
) : ContentRepository {
    override fun syncConnection(connectionId: String) = unsupported<Flow<Resource<SyncStage>>>()
    override fun observeCategories(connectionId: String, type: ContentType): Flow<List<Category>> =
        flowOf(categoriesByConnection[connectionId].orEmpty())
    override fun observeChannels(connectionId: String, categoryId: String?): Flow<List<Channel>> {
        val source = channelsByConnection[connectionId] ?: MutableStateFlow(emptyList())
        return source.map { list -> if (categoryId == null) list else list.filter { it.categoryId == categoryId } }
    }
    override fun observeMovies(connectionId: String, categoryId: String?) = unsupported<Flow<List<Movie>>>()
    override fun observeSeries(connectionId: String, categoryId: String?) = unsupported<Flow<List<Series>>>()
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
