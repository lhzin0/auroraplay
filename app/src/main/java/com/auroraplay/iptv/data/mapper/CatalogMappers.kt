package com.auroraplay.iptv.data.mapper

import com.auroraplay.iptv.core.util.MetadataSanitizer
import com.auroraplay.iptv.data.api.XtreamUrlBuilder
import com.auroraplay.iptv.data.api.dto.CategoryDto
import com.auroraplay.iptv.data.api.dto.EpisodeDto
import com.auroraplay.iptv.data.api.dto.LiveStreamDto
import com.auroraplay.iptv.data.api.dto.SeriesDto
import com.auroraplay.iptv.data.api.dto.VodStreamDto
import com.auroraplay.iptv.data.database.entity.CategoryEntity
import com.auroraplay.iptv.data.database.entity.ChannelEntity
import com.auroraplay.iptv.data.database.entity.EpisodeEntity
import com.auroraplay.iptv.data.database.entity.MovieEntity
import com.auroraplay.iptv.data.database.entity.SeriesEntity
import com.auroraplay.iptv.domain.model.Category
import com.auroraplay.iptv.domain.model.Channel
import com.auroraplay.iptv.domain.model.ContentType
import com.auroraplay.iptv.domain.model.Episode
import com.auroraplay.iptv.domain.model.Movie
import com.auroraplay.iptv.domain.model.Series

fun CategoryDto.toEntity(connectionId: String, type: ContentType) = CategoryEntity(
    id = categoryId,
    connectionId = connectionId,
    // Provider decorations ("➤# DRAMA", "|BR| FILMES") are stripped here so
    // every chip/badge in the UI reads cleanly.
    name = MetadataSanitizer.categoryName(categoryName) ?: categoryName.trim(),
    type = type.name,
)

fun CategoryEntity.toDomain() = Category(
    id = id,
    name = name,
    type = ContentType.valueOf(type),
    connectionId = connectionId,
)

fun LiveStreamDto.toEntity(connectionId: String, categoryName: String) = ChannelEntity(
    id = streamId.toString(),
    connectionId = connectionId,
    name = MetadataSanitizer.title(name),
    logoUrl = streamIcon?.takeIf { it.isNotBlank() },
    categoryId = categoryId.orEmpty(),
    categoryName = MetadataSanitizer.categoryName(categoryName) ?: categoryName.trim(),
    epgChannelId = epgChannelId?.takeIf { it.isNotBlank() },
)

/** [urlBuilder] is null only when the connection has no usable credentials
 * (e.g. a backup restored without the secure store) — the stream URL is then
 * blank and the player surfaces its "sem endereço de reprodução" error rather
 * than a broken request. Audit #4: the URL is assembled here, never stored. */
fun ChannelEntity.toDomain(urlBuilder: XtreamUrlBuilder?) = Channel(
    id = id,
    connectionId = connectionId,
    name = name,
    logoUrl = logoUrl,
    categoryId = categoryId,
    // Also sanitised on read, so rows synced before this cleanup landed
    // still display cleanly without waiting for a re-sync.
    categoryName = MetadataSanitizer.categoryName(categoryName) ?: categoryName,
    streamUrl = urlBuilder?.liveStreamPlayback(id).orEmpty(),
    epgChannelId = epgChannelId,
)

fun VodStreamDto.toEntity(connectionId: String, categoryName: String) = MovieEntity(
    id = streamId.toString(),
    connectionId = connectionId,
    name = MetadataSanitizer.title(name),
    posterUrl = streamIcon?.takeIf { it.isNotBlank() },
    backdropUrl = null,
    categoryId = categoryId.orEmpty(),
    categoryName = MetadataSanitizer.categoryName(categoryName) ?: categoryName.trim(),
    year = MetadataSanitizer.year(year, name),
    genre = null,
    plot = null,
    durationLabel = null,
    rating = rating?.toDoubleOrNull(),
    containerExtension = containerExtension?.takeIf { it.isNotBlank() },
    // Computed from the *raw* name + category, before title() strips "[L]" etc.
    audioLabel = MetadataSanitizer.audioLabelOf(name, categoryName),
    addedAtMillis = added?.toLongOrNull()?.times(1000) ?: System.currentTimeMillis(),
)

/** [urlBuilder] null → blank stream URL (see [ChannelEntity.toDomain]). */
fun MovieEntity.toDomain(urlBuilder: XtreamUrlBuilder?) = Movie(
    id = id,
    connectionId = connectionId,
    // The stored name keeps any "- DUBLADO" / "(Legendado)" tag (the catalog
    // dedup needs it); the display name drops it — and then re-runs title()
    // so a "(2026)" that was only trailing *after* the tag also comes off.
    name = MetadataSanitizer.title(MetadataSanitizer.stripAudioMarkers(name)),
    audioLabel = audioLabel,
    posterUrl = posterUrl,
    backdropUrl = backdropUrl,
    categoryId = categoryId,
    categoryName = MetadataSanitizer.categoryName(categoryName) ?: categoryName,
    year = year,
    genre = genre,
    plot = plot,
    durationLabel = durationLabel,
    rating = rating,
    streamUrl = urlBuilder?.vodStreamPlayback(id, containerExtension ?: "mp4").orEmpty(),
    addedAtMillis = addedAtMillis,
)

fun SeriesDto.toEntity(connectionId: String, categoryName: String) = SeriesEntity(
    id = seriesId.toString(),
    connectionId = connectionId,
    name = MetadataSanitizer.title(name),
    posterUrl = cover?.takeIf { it.isNotBlank() },
    backdropUrl = backdropPath?.firstOrNull(),
    categoryId = categoryId.orEmpty(),
    categoryName = MetadataSanitizer.categoryName(categoryName) ?: categoryName.trim(),
    year = MetadataSanitizer.year(null, name, releaseDate),
    genre = MetadataSanitizer.categoryName(genre),
    plot = MetadataSanitizer.text(plot),
    rating = rating?.toDoubleOrNull(),
    // Computed from the *raw* name + category, before title() strips "[L]" etc.
    audioLabel = MetadataSanitizer.audioLabelOf(name, categoryName),
    addedAtMillis = lastModified?.toLongOrNull()?.times(1000) ?: System.currentTimeMillis(),
)

fun SeriesEntity.toDomain() = Series(
    id = id,
    connectionId = connectionId,
    name = MetadataSanitizer.title(MetadataSanitizer.stripAudioMarkers(name)),
    audioLabel = audioLabel,
    posterUrl = posterUrl,
    backdropUrl = backdropUrl,
    categoryId = categoryId,
    categoryName = MetadataSanitizer.categoryName(categoryName) ?: categoryName,
    year = year,
    genre = genre,
    plot = plot,
    rating = rating,
    addedAtMillis = addedAtMillis,
)

fun EpisodeDto.toEntity(seriesId: String, connectionId: String, seasonNumber: Int) = EpisodeEntity(
    id = id,
    seriesId = seriesId,
    connectionId = connectionId,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNum ?: 0,
    title = MetadataSanitizer.episodeTitle(title) ?: "Episódio ${episodeNum ?: 0}",
    thumbnailUrl = info?.movieImage,
    durationLabel = MetadataSanitizer.duration(info?.duration),
    plot = MetadataSanitizer.text(info?.plot),
    containerExtension = containerExtension?.takeIf { it.isNotBlank() },
)

private fun String?.orKeep(existing: String?): String? =
    this?.takeIf { it.isNotBlank() } ?: existing

/**
 * Audit #9: a re-sync must not wipe metadata that later enrichment
 * (get_vod_info / get_series_info / TMDB) filled in. A freshly-listed VOD row
 * has genre / plot / backdrop / duration / rating null; if the provider's new
 * listing still omits a field but the stored row has it, keep the stored
 * value. Fields the provider always sends (name, category, poster, container,
 * audio tag, addedAt) always take the incoming value.
 */
internal fun MovieEntity.mergedWith(existing: MovieEntity?): MovieEntity {
    if (existing == null) return this
    return copy(
        posterUrl = posterUrl.orKeep(existing.posterUrl),
        backdropUrl = backdropUrl.orKeep(existing.backdropUrl),
        year = year.orKeep(existing.year),
        genre = genre.orKeep(existing.genre),
        plot = plot.orKeep(existing.plot),
        durationLabel = durationLabel.orKeep(existing.durationLabel),
        rating = rating ?: existing.rating,
    )
}

/** Series counterpart of [mergedWith]. Also carries the episode-fetch
 * timestamp (audit #7) forward — a catalog sync never touches episodes, so it
 * must not reset their freshness. */
internal fun SeriesEntity.mergedWith(existing: SeriesEntity?): SeriesEntity {
    if (existing == null) return this
    return copy(
        posterUrl = posterUrl.orKeep(existing.posterUrl),
        backdropUrl = backdropUrl.orKeep(existing.backdropUrl),
        year = year.orKeep(existing.year),
        genre = genre.orKeep(existing.genre),
        plot = plot.orKeep(existing.plot),
        rating = rating ?: existing.rating,
        episodesSyncedAtMillis = maxOf(episodesSyncedAtMillis, existing.episodesSyncedAtMillis),
    )
}

/** [urlBuilder] null → blank stream URL (see [ChannelEntity.toDomain]). */
fun EpisodeEntity.toDomain(urlBuilder: XtreamUrlBuilder?) = Episode(
    id = id,
    seriesId = seriesId,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    // Also cleaned on read, so episodes synced before this landed still show
    // "..." instead of "... (2026) [L] S01 E01".
    title = MetadataSanitizer.episodeTitle(title) ?: title,
    thumbnailUrl = thumbnailUrl,
    durationLabel = durationLabel,
    plot = plot,
    streamUrl = urlBuilder?.seriesEpisodePlayback(id, containerExtension ?: "mp4").orEmpty(),
)
