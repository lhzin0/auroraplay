package com.auroraplay.iptv.presentation.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraplay.iptv.core.util.Resource
import com.auroraplay.iptv.domain.model.XtreamConnection
import com.auroraplay.iptv.domain.repository.SyncStage
import com.auroraplay.iptv.domain.usecase.ConnectXtreamUseCase
import com.auroraplay.iptv.domain.usecase.SyncContentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AddConnectionStep { FORM, CONNECTING, SYNC_CHANNELS, SYNC_MOVIES, SYNC_SERIES, DONE, ERROR }

data class AddConnectionUiState(
    val step: AddConnectionStep = AddConnectionStep.FORM,
    val errorMessage: String? = null,
)

@HiltViewModel
class AddConnectionViewModel @Inject constructor(
    private val connectXtreamUseCase: ConnectXtreamUseCase,
    private val syncContentUseCase: SyncContentUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddConnectionUiState())
    val uiState: StateFlow<AddConnectionUiState> = _uiState.asStateFlow()

    fun connect(name: String, serverUrl: String, username: String, password: String, profileId: String?) {
        viewModelScope.launch {
            _uiState.value = AddConnectionUiState(step = AddConnectionStep.CONNECTING)
            connectXtreamUseCase(name, serverUrl, username, password, profileId).collect { resource ->
                when (resource) {
                    is Resource.Success -> startSync(resource.data)
                    is Resource.Error -> _uiState.value = AddConnectionUiState(AddConnectionStep.ERROR, resource.message)
                    else -> Unit
                }
            }
        }
    }

    private suspend fun startSync(connection: XtreamConnection) {
        syncContentUseCase(connection.id).collect { resource ->
            when (resource) {
                is Resource.Success -> {
                    val step = when (resource.data) {
                        SyncStage.CONNECTING -> AddConnectionStep.CONNECTING
                        SyncStage.CHANNELS -> AddConnectionStep.SYNC_CHANNELS
                        SyncStage.MOVIES -> AddConnectionStep.SYNC_MOVIES
                        SyncStage.SERIES -> AddConnectionStep.SYNC_SERIES
                        SyncStage.DONE -> AddConnectionStep.DONE
                    }
                    _uiState.value = AddConnectionUiState(step = step)
                }
                is Resource.Error -> _uiState.value = AddConnectionUiState(AddConnectionStep.ERROR, resource.message)
                else -> Unit
            }
        }
    }

    fun resetToForm() {
        _uiState.value = AddConnectionUiState(step = AddConnectionStep.FORM)
    }
}
