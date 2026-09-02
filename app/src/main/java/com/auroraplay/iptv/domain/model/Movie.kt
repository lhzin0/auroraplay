package com.auroraplay.iptv.domain.model

data class Movie(
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
    val durationLabel: String?,
    val rating: Double?,
    val streamUrl: String,
    /** "Legendado" when the provider only offers a subtitled version — shown
     * on the detail page so nobody expects dubbed audio. null otherwise. */
    val audioLabel: String? = null,
    val isFavorite: Boolean = false,
    val addedAtMillis: Long = 0L,
)
