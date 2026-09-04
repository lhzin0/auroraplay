package com.auroraplay.iptv.presentation.connections

import androidx.lifecycle.ViewModel
import androidx.annotation.Keep
import androidx.lifecycle.viewModelScope
import com.auroraplay.iptv.core.util.Resource
import com.auroraplay.iptv.domain.model.XtreamConnection
import com.auroraplay.iptv.domain.repository.ConnectionRepository
import com.auroraplay.iptv.domain.repository.SyncStage
import com.auroraplay.iptv.domain.usecase.SyncContentUseCase
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ConnectionsUiState(
    val connections: List<XtreamConnection> = emptyList(),
    val isLoading: Boolean = true,
    val activeSyncs: Map<String, SyncStage?> = emptyMap(),
)

/** Backup file schema — plain JSON so it's readable/portable, versioned in
 * case a future field needs a migration path when reading an older file. */
@Keep
private data class ConnectionBackupEntry(val name: String, val serverUrl: String, val username: String, val password: String)
@Keep
private data class ConnectionBackupFile(val version: Int = 1, val connections: List<ConnectionBackupEntry>)

data class ImportResult(val imported: Int, val failed: Int)

@HiltViewModel
class ConnectionsViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val syncContentUseCase: SyncContentUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionsUiState())
    val uiState: StateFlow<ConnectionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(connectionRepository.observeConnections(), syncContentUseCase.observeActive()) { list, syncs ->
                ConnectionsUiState(connections = list, isLoading = false, activeSyncs = syncs)
            }.collect { _uiState.value = it }
        }
    }

    fun setDefault(id: String) = viewModelScope.launch { connectionRepository.setDefault(id) }
    fun delete(id: String) = viewModelScope.launch {
        syncContentUseCase.cancel(id)
        connectionRepository.deleteConnection(id)
    }

    fun testConnection(id: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            connectionRepository.testConnection(id).collect { resource ->
                when (resource) {
                    is Resource.Success -> onResult(true, null)
                    is Resource.Error -> onResult(false, resource.message)
                    else -> Unit
                }
            }
        }
    }

    fun syncNow(id: String, onStage: (SyncStage) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try { syncContentUseCase(id).collect { resource ->
                when (resource) {
                    is Resource.Success -> onStage(resource.data)
                    is Resource.Error -> onError(resource.message)
                    else -> Unit
                }
            } } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { onError("Não foi possível iniciar a sincronização. Tente novamente.") }
        }
    }

    /** Builds the backup JSON text; null means there was nothing to export. */
    fun exportConnections(onError: (String) -> Unit, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val entries = mutableListOf<ConnectionBackupEntry>()
            for (connection in _uiState.value.connections) {
                val password = connectionRepository.getPassword(connection.id)
                if (password == null) {
                    onError("Backup não salvo: uma conexão antiga está sem senha. Importe um backup completo ou cadastre novamente essa conexão pelo botão +.")
                    return@launch
                }
                entries += ConnectionBackupEntry(connection.name, connection.serverUrl, connection.username, password)
            }
            onResult(if (entries.isEmpty()) null else Gson().toJson(ConnectionBackupFile(connections = entries)))
        }
    }

    /**
     * Re-adds every entry through [ConnectionRepository.addConnection] — the
     * same path a manually typed connection goes through — so an imported
     * server gets the same live credential check instead of trusting the
     * file blindly.
     */
    fun importConnections(json: String, onResult: (ImportResult) -> Unit) {
        viewModelScope.launch {
            val backup = withContext(Dispatchers.Default) {
                runCatching { Gson().fromJson(json, ConnectionBackupFile::class.java) }.getOrNull()
            }
            val entries = backup?.connections.orEmpty()
            if (entries.isEmpty()) {
                onResult(ImportResult(imported = 0, failed = 0))
                return@launch
            }
            var imported = 0
            var failed = 0
            entries.forEach { entry ->
                var succeeded = false
                runCatching {
                    connectionRepository.addConnection(
                        name = entry.name,
                        serverUrl = entry.serverUrl,
                        username = entry.username,
                        password = entry.password,
                        profileId = null,
                    ).collect { resource -> if (resource is Resource.Success) succeeded = true }
                }
                if (succeeded) imported++ else failed++
            }
            onResult(ImportResult(imported, failed))
        }
    }
}
