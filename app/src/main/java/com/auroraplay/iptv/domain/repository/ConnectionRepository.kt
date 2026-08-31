package com.auroraplay.iptv.domain.repository

import com.auroraplay.iptv.core.util.Resource
import com.auroraplay.iptv.domain.model.XtreamConnection
import kotlinx.coroutines.flow.Flow

interface ConnectionRepository {
    fun observeConnections(): Flow<List<XtreamConnection>>
    suspend fun getConnection(id: String): XtreamConnection?
    suspend fun getDefaultConnection(): XtreamConnection?

    /** Validates credentials against the server, persists connection + encrypted password. */
    fun addConnection(
        name: String,
        serverUrl: String,
        username: String,
        password: String,
        profileId: String?,
    ): Flow<Resource<XtreamConnection>>

    suspend fun updateConnection(connection: XtreamConnection, newPassword: String?)
    suspend fun deleteConnection(id: String)
    suspend fun setDefault(id: String)
    fun testConnection(id: String): Flow<Resource<Unit>>
    suspend fun getPassword(id: String): String?
}
