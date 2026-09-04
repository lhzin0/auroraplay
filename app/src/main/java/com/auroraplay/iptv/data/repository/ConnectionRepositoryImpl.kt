package com.auroraplay.iptv.data.repository

import androidx.room.withTransaction
import com.auroraplay.iptv.core.util.AppLog
import com.auroraplay.iptv.core.util.Resource
import com.auroraplay.iptv.data.api.XtreamApiService
import com.auroraplay.iptv.data.api.XtreamUrlBuilder
import com.auroraplay.iptv.data.database.AppDatabase
import com.auroraplay.iptv.data.database.dao.CategoryDao
import com.auroraplay.iptv.data.database.dao.ChannelDao
import com.auroraplay.iptv.data.database.dao.ConnectionDao
import com.auroraplay.iptv.data.database.dao.EpisodeDao
import com.auroraplay.iptv.data.database.dao.MovieDao
import com.auroraplay.iptv.data.database.dao.SeriesDao
import com.auroraplay.iptv.data.database.entity.ConnectionEntity
import com.auroraplay.iptv.data.datastore.SecureCredentialStore
import com.auroraplay.iptv.data.mapper.toDomain
import com.auroraplay.iptv.domain.model.ConnectionStatus
import com.auroraplay.iptv.domain.model.XtreamConnection
import com.auroraplay.iptv.domain.repository.ConnectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val dao: ConnectionDao,
    private val categoryDao: CategoryDao,
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val episodeDao: EpisodeDao,
    private val api: XtreamApiService,
    private val secureStore: SecureCredentialStore,
) : ConnectionRepository {

    override fun observeConnections(): Flow<List<XtreamConnection>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeDefaultConnection(): Flow<XtreamConnection?> =
        observeConnections()
            .map { list -> list.firstOrNull { it.isDefault } ?: list.firstOrNull() }
            .distinctUntilChanged()

    override suspend fun getConnection(id: String): XtreamConnection? =
        dao.getById(id)?.let { it.toDomain() }

    override suspend fun getDefaultConnection(): XtreamConnection? =
        dao.getDefault()?.let { it.toDomain() }

    override fun addConnection(
        name: String,
        serverUrl: String,
        username: String,
        password: String,
        profileId: String?,
        backupServerUrl: String?,
    ): Flow<Resource<XtreamConnection>> = flow {
        emit(Resource.Loading)
        try {
            val urlBuilder = XtreamUrlBuilder(serverUrl, username, password)
            val auth = api.authenticate(urlBuilder.auth())
            val isAuthOk = auth.userInfo?.auth == 1 || auth.userInfo?.status.equals("Active", ignoreCase = true)
            if (!isAuthOk) {
                emit(Resource.Error("Usuário ou senha inválidos, ou servidor indisponível."))
                return@flow
            }

            val id = UUID.randomUUID().toString()
            val hasNoConnections = dao.observeAll().first().isEmpty()
            val entity = ConnectionEntity(
                id = id,
                name = name.ifBlank { "Minha conexão" },
                serverUrl = serverUrl,
                username = username,
                isDefault = hasNoConnections,
                status = ConnectionStatus.ONLINE.name,
                lastSyncMillis = null,
                profileId = profileId,
                backupServerUrl = backupServerUrl?.trim()?.ifBlank { null },
            )
            if (hasNoConnections) dao.clearDefaults()
            secureStore.savePassword(id, password)
            try { dao.upsert(entity) }
            catch (e: Exception) { secureStore.deletePassword(id); throw e }

            emit(Resource.Success(entity.toDomain()))
        } catch (e: Exception) {
            AppLog.w("Connection", "addConnection failed for $serverUrl", e)
            emit(Resource.Error(mapConnectionError(e), e))
        }
    }

    override suspend fun updateConnection(connection: XtreamConnection, newPassword: String?) {
        val existing = dao.getById(connection.id) ?: return
        dao.upsert(
            existing.copy(
                name = connection.name,
                serverUrl = connection.serverUrl,
                username = connection.username,
                backupServerUrl = connection.backupServerUrl?.trim()?.ifBlank { null },
            )
        )
        if (!newPassword.isNullOrBlank()) {
            secureStore.savePassword(connection.id, newPassword)
        }
    }

    override suspend fun deleteConnection(id: String) {
        deleteConnectionAtomically(db, dao, categoryDao, channelDao, movieDao, seriesDao, episodeDao, id)
        secureStore.deletePassword(id)
    }

    override suspend fun setDefault(id: String) {
        dao.clearDefaults()
        dao.markDefault(id)
    }

    override fun testConnection(id: String): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading)
        val entity = dao.getById(id)
        val password = secureStore.getPassword(id)
        if (entity == null || password == null) {
            emit(Resource.Error("Conexão sem credenciais disponíveis. Importe um backup completo ou cadastre a playlist novamente."))
            return@flow
        }
        try {
            val urlBuilder = try {
                XtreamUrlBuilder(entity.serverUrl, entity.username, password).also { api.authenticate(it.auth()) }
            } catch (e: java.io.IOException) {
                val backupUrl = entity.backupServerUrl?.trim()
                if (backupUrl.isNullOrBlank()) throw e
                AppLog.w("Connection", "primary server unreachable for $id, trying backup", e)
                XtreamUrlBuilder(backupUrl, entity.username, password)
            }
            val auth = api.authenticate(urlBuilder.auth())
            val isAuthOk = auth.userInfo?.auth == 1 || auth.userInfo?.status.equals("Active", ignoreCase = true)
            dao.updateStatus(id, if (isAuthOk) ConnectionStatus.ONLINE.name else ConnectionStatus.OFFLINE.name)
            if (isAuthOk) emit(Resource.Success(Unit)) else emit(Resource.Error("Servidor recusou as credenciais."))
        } catch (e: Exception) {
            AppLog.w("Connection", "testConnection failed for $id", e)
            dao.updateStatus(id, ConnectionStatus.OFFLINE.name)
            emit(Resource.Error(mapConnectionError(e), e))
        }
    }

    override suspend fun getPassword(id: String): String? = secureStore.getPassword(id)

    private fun mapConnectionError(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> "Não foi possível conectar ao servidor."
        is java.net.SocketTimeoutException -> "O servidor demorou para responder. Tente novamente."
        else -> "Não foi possível conectar ao servidor."
    }
}

/**
 * Audit #16: delete a connection + its re-syncable catalog orphans, and, when
 * it was the default and other playlists remain, promote another — all in one
 * transaction so the app is never left "connected to nothing" while playlists
 * exist. `favorites` / `watch_progress` for the connection are personal data
 * and are intentionally NOT deleted here. Extracted so an instrumented test
 * can exercise it against a real in-memory database without the network deps.
 */
internal suspend fun deleteConnectionAtomically(
    db: AppDatabase,
    dao: ConnectionDao,
    categoryDao: CategoryDao,
    channelDao: ChannelDao,
    movieDao: MovieDao,
    seriesDao: SeriesDao,
    episodeDao: EpisodeDao,
    id: String,
) {
    db.withTransaction {
        val wasDefault = dao.getById(id)?.isDefault == true
        dao.delete(id)
        categoryDao.clearAll(id)
        channelDao.clear(id)
        movieDao.clear(id)
        seriesDao.clear(id)
        episodeDao.clearForConnection(id)
        if (wasDefault) {
            dao.getAllOnce().firstOrNull()?.let { next ->
                dao.clearDefaults()
                dao.markDefault(next.id)
            }
        }
    }
}
