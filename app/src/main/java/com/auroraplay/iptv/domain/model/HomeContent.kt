package com.auroraplay.iptv.domain.model

/** Any playable item, used to render homogeneous carousels/search results across types. */
@androidx.compose.runtime.Immutable
sealed class MediaItem {
    abstract val id: String
    abstract val title: String
    abstract val imageUrl: String?

    @androidx.compose.runtime.Immutable
    data class ChannelItem(val channel: Channel) : MediaItem() {
        override val id = channel.id
        override val title = channel.name
        override val imageUrl = channel.logoUrl
    }
    @androidx.compose.runtime.Immutable
    data class MovieItem(val movie: Movie) : MediaItem() {
        override val id = movie.id
        override val title = movie.name
        override val imageUrl = movie.posterUrl
    }
    @androidx.compose.runtime.Immutable
    data class SeriesItem(val series: Series) : MediaItem() {
        override val id = series.id
        override val title = series.name
        override val imageUrl = series.posterUrl
    }
}

@androidx.compose.runtime.Immutable
data class HomeSection(
    val id: String,
    val title: String,
    val items: List<MediaItem>,
    /** Landscape ("continue watching"/live) rows read better than 2:3 posters. */
    val layout: SectionLayout = SectionLayout.POSTER,
)

enum class SectionLayout { POSTER, LANDSCAPE, CHANNEL }

/** A continue-watching entry: the item plus where the user stopped. */
@androidx.compose.runtime.Immutable
data class ResumeInfo(
    val contentId: String,
    val fraction: Float,
    val positionMillis: Long,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
) {
    /** "Continuar de 32:41" / "T1 E3 • 32:41" */
    fun label(): String {
        val time = (positionMillis / 1000).let { total ->
            val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
            if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        }
        return if (seasonNumber != null && episodeNumber != null) "T$seasonNumber E$episodeNumber • $time"
        else "Continuar de $time"
    }
}

@androidx.compose.runtime.Immutable
data class HomeContent(
    /** Rotating highlights shown in the hero carousel. */
    val heroItems: List<MediaItem>,
    val sections: List<HomeSection>,
    /** Keyed by MediaItem.id so any card can render its own progress bar. */
    val resumeByItemId: Map<String, ResumeInfo> = emptyMap(),
)
