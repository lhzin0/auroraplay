package com.auroraplay.iptv.domain.model

data class Series(
    val id: String,
    val connectionId: String,
    val name: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val categoryId: String,
    val categoryName: String,
    val year: String?,
    val genre: String?,
    val plot: String?,
    val rating: Double?,
    val isFavorite: Boolean = false,
    val addedAtMillis: Long = 0L,
    val seasons: List<Season> = emptyList(),
)

data class Season(
    val seasonNumber: Int,
    val name: String,
    val episodes: List<Episode>,
)

data class Episode(
    val id: String,
    val seriesId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val thumbnailUrl: String?,
    val durationLabel: String?,
    val plot: String?,
    val streamUrl: String,
)
