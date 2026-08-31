package com.auroraplay.iptv.data.api.dto

import com.google.gson.annotations.SerializedName

data class LiveStreamDto(
    @SerializedName("num") val num: Int? = null,
    @SerializedName("name") val name: String?,
    @SerializedName("stream_id") val streamId: Int,
    @SerializedName("stream_icon") val streamIcon: String?,
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("epg_channel_id") val epgChannelId: String?,
    @SerializedName("added") val added: String? = null,
)
