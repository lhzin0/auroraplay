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
    val sourceType: ConnectionSourceType = ConnectionSourceType.XTREAM,
    /** Optional XMLTV guide URL, importable for either [sourceType]. */
    val xmltvUrl: String? = null,
)

enum class ConnectionStatus { ONLINE, OFFLINE, UNKNOWN, CONNECTING }

/** XTREAM: the usual username/password panel API. M3U: [XtreamConnection.serverUrl]
 * is the playlist URL itself and carries live channels only — no movies,
 * series, or Xtream-side EPG, since a flat M3U file has no reliable way to
 * tell those apart or an API to ask. */
enum class ConnectionSourceType { XTREAM, M3U }
