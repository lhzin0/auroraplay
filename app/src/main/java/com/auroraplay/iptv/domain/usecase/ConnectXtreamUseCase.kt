package com.auroraplay.iptv.domain.usecase

import com.auroraplay.iptv.core.util.Resource
import com.auroraplay.iptv.domain.model.XtreamConnection
import com.auroraplay.iptv.domain.repository.ConnectionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ConnectXtreamUseCase @Inject constructor(
    private val connectionRepository: ConnectionRepository,
) {
    operator fun invoke(
        name: String,
        serverUrl: String,
        username: String,
        password: String,
        profileId: String?,
    ): Flow<Resource<XtreamConnection>> {
        val normalizedUrl = serverUrl.trim().removeSuffix("/")
        return connectionRepository.addConnection(name.trim(), normalizedUrl, username.trim(), password, profileId)
    }
}
