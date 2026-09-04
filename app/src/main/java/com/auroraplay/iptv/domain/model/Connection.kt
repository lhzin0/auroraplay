package com.auroraplay.iptv.domain.model

/** A user-configured Xtream connection. Credentials never leave the device. */
@androidx.compose.runtime.Immutable
data class XtreamConnection(
    val id: String,
    val name: String,
    val serverUrl: String,
    val username: String,
    // password is intentionally not part of the domain model exposed to the UI;
    // it is stored only in EncryptedSharedPreferences and read by the repository
    // when performing network calls.
    val isDefault: Boolean = false,
    val status: ConnectionStatus = ConnectionStatus.UNKNOWN,
    val lastSyncMillis: Long? = null,
    val profileId: String? = null,
    /** Optional mirror server for the same account — tried automatically when
     * [serverUrl] is unreachable (a network failure, not a credentials
     * rejection: retrying the same bad login elsewhere won't help). */
    val backupServerUrl: String? = null,
)

enum class ConnectionStatus { ONLINE, OFFLINE, UNKNOWN, CONNECTING }
