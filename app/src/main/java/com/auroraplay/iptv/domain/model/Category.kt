package com.auroraplay.iptv.domain.model

enum class ContentType { LIVE, MOVIE, SERIES }

@androidx.compose.runtime.Immutable
data class Category(
    val id: String,
    val name: String,
    val type: ContentType,
    val connectionId: String,
)
