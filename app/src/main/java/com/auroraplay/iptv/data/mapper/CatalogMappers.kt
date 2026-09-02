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

fun LiveStreamDto.toEntity(connectionId: String, categoryName: String, urlBuilder: XtreamUrlBuilder) = ChannelEntity(
    id = streamId.toString(),
    connectionId = connectionId,
    name = MetadataSanitizer.title(name),
    logoUrl = streamIcon?.takeIf { it.isNotBlank() },
    categoryId = categoryId.orEmpty(),
    categoryName = MetadataSanitizer.categoryName(categoryName) ?: categoryName.trim(),
    streamUrl = urlBuilder.liveStreamPlayback(streamId.toString()),
    epgChannelId = epgChannelId?.takeIf { it.isNotBlank() },
)

fun ChannelEntity.toDomain() = Channel(
    id = id,
    connectionId = connectionId,
    name = name,
    logoUrl = logoUrl,
    categoryId = categoryId,
    // Also sanitised on read, so rows synced before this cleanup landed
    // still display cleanly without waiting for a re-sync.
    categoryName = MetadataSanitizer.categoryName(categoryName) ?: categoryName,
    streamUrl = streamUrl,
    epgChannelId = epgChannelId,
)

fun VodStreamDto.toEntity(connectionId: String, categoryName: String, urlBuilder: XtreamUrlBuilder) = MovieEntity(
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
    streamUrl = urlBuilder.vodStreamPlayback(streamId.toString(), containerExtension ?: "mp4"),
    addedAtMillis = added?.toLongOrNull()?.times(1000) ?: System.currentTimeMillis(),
)

fun MovieEntity.toDomain() = Movie(
    id = id,
    connectionId = connectionId,
    // The stored name keeps any "- DUBLADO" / "(Legendado)" tag (the catalog
    // dedup needs it); the display name drops it — and then re-runs title()
    // so a "(2026)" that was only trailing *after* the tag also comes off.
    name = MetadataSanitizer.title(MetadataSanitizer.stripAudioMarkers(name)),
    audioLabel = MetadataSanitizer.audioLabelOf(name, categoryName),
    posterUrl = posterUrl,
    backdropUrl = backdropUrl,
    categoryId = categoryId,
    categoryName = MetadataSanitizer.categoryName(categoryName) ?: categoryName,
    year = year,
    genre = genre,
    plot = plot,
    durationLabel = durationLabel,
    rating = rating,
    streamUrl = streamUrl,
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
    addedAtMillis = lastModified?.toLongOrNull()?.times(1000) ?: System.currentTimeMillis(),
)

fun SeriesEntity.toDomain() = Series(
    id = id,
    connectionId = connectionId,
    name = MetadataSanitizer.title(MetadataSanitizer.stripAudioMarkers(name)),
    audioLabel = MetadataSanitizer.audioLabelOf(name, categoryName),
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

fun EpisodeDto.toEntity(seriesId: String, connectionId: String, seasonNumber: Int, urlBuilder: XtreamUrlBuilder) = EpisodeEntity(
    id = id,
    seriesId = seriesId,
    connectionId = connectionId,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNum ?: 0,
    title = MetadataSanitizer.text(title) ?: "Episódio ${episodeNum ?: 0}",
    thumbnailUrl = info?.movieImage,
    durationLabel = MetadataSanitizer.duration(info?.duration),
    plot = MetadataSanitizer.text(info?.plot),
    streamUrl = urlBuilder.seriesEpisodePlayback(id, containerExtension ?: "mp4"),
)

fun EpisodeEntity.toDomain() = Episode(
    id = id,
    seriesId = seriesId,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    title = title,
    thumbnailUrl = thumbnailUrl,
    durationLabel = durationLabel,
    plot = plot,
    streamUrl = streamUrl,
)
