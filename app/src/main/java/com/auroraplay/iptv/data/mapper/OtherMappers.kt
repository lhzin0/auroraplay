package com.auroraplay.iptv.data.mapper

import com.auroraplay.iptv.data.api.dto.EpgListingDto
import com.auroraplay.iptv.data.database.entity.ConnectionEntity
import com.auroraplay.iptv.data.database.entity.FavoriteEntity
import com.auroraplay.iptv.data.database.entity.ProfileEntity
import com.auroraplay.iptv.data.database.entity.WatchProgressEntity
import com.auroraplay.iptv.domain.model.ConnectionStatus
import com.auroraplay.iptv.domain.model.ContentType
import com.auroraplay.iptv.domain.model.EpgProgram
import com.auroraplay.iptv.domain.model.Favorite
import com.auroraplay.iptv.domain.model.Profile
import com.auroraplay.iptv.domain.model.WatchProgress
import com.auroraplay.iptv.domain.model.XtreamConnection

fun ConnectionEntity.toDomain() = XtreamConnection(
    id = id,
    name = name,
    serverUrl = serverUrl,
    username = username,
    isDefault = isDefault,
    status = runCatching { ConnectionStatus.valueOf(status) }.getOrDefault(ConnectionStatus.UNKNOWN),
    lastSyncMillis = lastSyncMillis,
    profileId = profileId,
    backupServerUrl = backupServerUrl,
)

fun ProfileEntity.toDomain() = Profile(
    id = id,
    name = name,
    avatarColorHex = avatarColorHex,
    avatarEmoji = avatarEmoji,
    avatarUri = avatarUri,
    isKids = isKids,
    createdAtMillis = createdAtMillis,
    pinHash = pinHash,
    biometricEnabled = biometricEnabled,
)

fun FavoriteEntity.toDomain() = Favorite(
    connectionId = connectionId,
    contentId = contentId,
    type = ContentType.valueOf(type),
    profileId = profileId,
    addedAtMillis = addedAtMillis,
)

fun WatchProgressEntity.toDomain() = WatchProgress(
    connectionId = connectionId,
    contentId = contentId,
    type = ContentType.valueOf(type),
    profileId = profileId,
    positionMillis = positionMillis,
    durationMillis = durationMillis,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    lastWatchedMillis = lastWatchedMillis,
    title = title,
    posterUrl = posterUrl,
)

fun WatchProgress.toEntity() = WatchProgressEntity(
    connectionId = connectionId,
    contentId = contentId,
    type = type.name,
    profileId = profileId,
    positionMillis = positionMillis,
    durationMillis = durationMillis,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    lastWatchedMillis = lastWatchedMillis,
    title = title,
    posterUrl = posterUrl,
)

/** Xtream's get_short_epg sends title/description base64-encoded and start/stop
 * as Unix-epoch-seconds strings; null if either timestamp is missing/unparseable,
 * since a listing with no real time range isn't useful for a progress bar. */
fun EpgListingDto.toDomain(): EpgProgram? {
    val startSeconds = startTimestamp?.toLongOrNull() ?: return null
    val stopSeconds = stopTimestamp?.toLongOrNull() ?: return null
    return EpgProgram(
        id = id ?: "$startSeconds",
        title = title?.let { decodeEpgBase64(it) }.orEmpty(),
        description = description?.let { decodeEpgBase64(it) }.orEmpty(),
        startMillis = startSeconds * 1000,
        endMillis = stopSeconds * 1000,
    )
}

private fun decodeEpgBase64(value: String): String =
    runCatching { String(android.util.Base64.decode(value, android.util.Base64.DEFAULT), Charsets.UTF_8) }
        .getOrDefault(value)
        .trim()
