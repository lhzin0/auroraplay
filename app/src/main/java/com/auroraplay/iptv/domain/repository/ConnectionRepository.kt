package com.auroraplay.iptv.domain.repository

import com.auroraplay.iptv.core.util.Resource
import com.auroraplay.iptv.domain.model.XtreamConnection
import kotlinx.coroutines.flow.Flow

interface ConnectionRepository {
    fun observeConnections(): Flow<List<XtreamConnection>>
    suspend fun getConnection(id: String): XtreamConnection?
    suspend fun getDefaultConnection(): XtreamConnection?

    /**
     * The active connection as a stream: the one flagged default, else the
     * first. Re-emits when the user switches the default (audit #11), so
     * screens that observe it re-drive their content against the new playlist
     * instead of holding the one captured at ViewModel init.
     */
    fun observeDefaultConnection(): Flow<XtreamConnection?>

    /** Validates credentials against the server, persists connection + encrypted password.
     * [backupServerUrl] is an optional mirror for the same account, tried
     * automatically when the primary is unreachable. */
    fun addConnection(
        name: String,
        serverUrl: String,
        username: String,
        password: String,
        profileId: String?,
        backupServerUrl: String? = null,
    ): Flow<Resource<XtreamConnection>>

    suspend fun updateConnection(connection: XtreamConnection, newPassword: String?)
    suspend fun deleteConnection(id: String)
    suspend fun setDefault(id: String)
    fun testConnection(id: String): Flow<Resource<Unit>>
    suspend fun getPassword(id: String): String?
}
