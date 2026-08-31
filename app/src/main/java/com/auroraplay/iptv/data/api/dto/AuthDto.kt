package com.auroraplay.iptv.data.api.dto

import com.google.gson.annotations.SerializedName

/** Response from player_api.php with no "action" param — validates credentials. */
data class AuthResponseDto(
    @SerializedName("user_info") val userInfo: UserInfoDto?,
    @SerializedName("server_info") val serverInfo: ServerInfoDto?,
)

data class UserInfoDto(
    @SerializedName("username") val username: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("auth") val auth: Int?,
    @SerializedName("exp_date") val expDate: String?,
    @SerializedName("is_trial") val isTrial: String?,
    @SerializedName("active_cons") val activeConnections: String?,
    @SerializedName("max_connections") val maxConnections: String?,
)

data class ServerInfoDto(
    @SerializedName("url") val url: String?,
    @SerializedName("port") val port: String?,
    @SerializedName("https_port") val httpsPort: String?,
    @SerializedName("server_protocol") val serverProtocol: String?,
    @SerializedName("timezone") val timezone: String?,
)
