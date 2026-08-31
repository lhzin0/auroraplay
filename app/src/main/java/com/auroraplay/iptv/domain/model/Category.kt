package com.auroraplay.iptv.domain.model

enum class ContentType { LIVE, MOVIE, SERIES }

data class Category(
    val id: String,
    val name: String,
    val type: ContentType,
    val connectionId: String,
)
