package com.auroraplay.iptv.data.repository

import com.auroraplay.iptv.core.util.Resource
import com.auroraplay.iptv.data.api.XtreamApiService
import com.auroraplay.iptv.data.api.XtreamUrlBuilder
import com.auroraplay.iptv.data.database.dao.ConnectionDao
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
    private val dao: ConnectionDao,
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
            )
            if (hasNoConnections) dao.clearDefaults()
            secureStore.savePassword(id, password)
            try { dao.upsert(entity) }
            catch (e: Exception) { secureStore.deletePassword(id); throw e }

            emit(Resource.Success(entity.toDomain()))
        } catch (e: Exception) {
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
            )
        )
        if (!newPassword.isNullOrBlank()) {
            secureStore.savePassword(connection.id, newPassword)
        }
    }

    override suspend fun deleteConnection(id: String) {
        dao.delete(id)
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
            val urlBuilder = XtreamUrlBuilder(entity.serverUrl, entity.username, password)
            val auth = api.authenticate(urlBuilder.auth())
            val isAuthOk = auth.userInfo?.auth == 1 || auth.userInfo?.status.equals("Active", ignoreCase = true)
            dao.updateStatus(id, if (isAuthOk) ConnectionStatus.ONLINE.name else ConnectionStatus.OFFLINE.name)
            if (isAuthOk) emit(Resource.Success(Unit)) else emit(Resource.Error("Servidor recusou as credenciais."))
        } catch (e: Exception) {
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
