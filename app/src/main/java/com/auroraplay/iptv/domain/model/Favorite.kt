package com.auroraplay.iptv.domain.model

data class Favorite(
    val contentId: String,
    val type: ContentType,
    val profileId: String,
    val addedAtMillis: Long = System.currentTimeMillis(),
)
