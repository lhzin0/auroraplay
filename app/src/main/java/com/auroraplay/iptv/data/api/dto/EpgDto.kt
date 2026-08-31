package com.auroraplay.iptv.data.api.dto

import com.google.gson.annotations.SerializedName

data class ShortEpgResponseDto(
    @SerializedName("epg_listings") val epgListings: List<EpgListingDto>?,
)

data class EpgListingDto(
    @SerializedName("id") val id: String?,
    @SerializedName("title") val title: String?, // base64
    @SerializedName("description") val description: String?, // base64
    @SerializedName("start_timestamp") val startTimestamp: String?,
    @SerializedName("stop_timestamp") val stopTimestamp: String?,
)
