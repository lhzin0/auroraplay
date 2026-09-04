package com.auroraplay.iptv.domain.model

@androidx.compose.runtime.Immutable
data class Favorite(
    val connectionId: String,
    val contentId: String,
    val type: ContentType,
    val profileId: String,
    val addedAtMillis: Long = System.currentTimeMillis(),
)
