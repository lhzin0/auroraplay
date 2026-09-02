package com.auroraplay.iptv.data.backup

import com.auroraplay.iptv.data.database.entity.ConnectionEntity
import com.auroraplay.iptv.data.database.entity.FavoriteEntity
import com.auroraplay.iptv.data.database.entity.ProfileEntity
import com.auroraplay.iptv.data.database.entity.WatchProgressEntity
import com.auroraplay.iptv.domain.repository.AppSettings
import com.google.gson.Gson
import com.google.gson.JsonParser

/** Version 2 adds portable playlist passwords; version 1 remains readable. */
internal data class BackupSnapshot(
    val version: Int = 2,
    val savedAt: Long = 0L,
    val activeProfileId: String? = null,
    val profiles: List<ProfileEntity> = emptyList(),
    val connections: List<ConnectionEntity> = emptyList(),
    val favorites: List<FavoriteEntity> = emptyList(),
    val watchProgress: List<WatchProgressEntity> = emptyList(),
    val settings: AppSettings? = null,
    val connectionPasswords: Map<String, String> = emptyMap(),
)

internal object BackupSnapshotCodec {
    const val MAX_BYTES = 20 * 1024 * 1024
    private val gson = Gson()

    fun encode(snapshot: BackupSnapshot): String = gson.toJson(snapshot.copy(
        settings = snapshot.settings?.copy(tmdbApiKey = null),
        profiles = snapshot.profiles.map { it.copy(avatarUri = null, biometricEnabled = false) },
    )).also { decode(it) }

    fun decode(json: String): BackupSnapshot {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Backup maior que 20 MB." }
        try {
            val root = JsonParser.parseString(json).asJsonObject
            val version = root.get("version")?.asInt
            require(version == 1 || version == 2)
            require(root.get("savedAt")?.asLong?.let { it > 0 } == true)
            // Gson can inject null into Kotlin non-null fields. Validate before writing.
            mapOf(
                "profiles" to listOf("id", "name", "avatarColorHex", "avatarEmoji", "createdAtMillis"),
                "connections" to listOf("id", "name", "serverUrl", "username"),
                "favorites" to listOf("contentId", "type", "profileId", "addedAtMillis"),
                "watchProgress" to listOf("contentId", "type", "profileId", "positionMillis", "durationMillis", "lastWatchedMillis"),
            ).forEach { (key, required) ->
                root.getAsJsonArray(key).forEach { row ->
                    required.forEach { field -> require(row.asJsonObject.get(field)?.isJsonPrimitive == true) }
                }
            }
            // Read password values strictly: Gson otherwise coerces numbers to strings.
            val passwords = if (version == 2) {
                val objectValue = requireNotNull(root.get("connectionPasswords"))
                require(objectValue.isJsonObject)
                objectValue.asJsonObject.entrySet().associate { (id, value) ->
                    require(id.isNotBlank() && value.isJsonPrimitive && value.asJsonPrimitive.isString)
                    id to value.asString
                }
            } else emptyMap()
            val snapshot = gson.fromJson(root, BackupSnapshot::class.java).copy(connectionPasswords = passwords)
            val ids = snapshot.profiles.map { it.id }.toSet()
            require(ids.size == snapshot.profiles.size && ids.none { it.isBlank() })
            require(snapshot.connections.map { it.id }.distinct().size == snapshot.connections.size)
            require(passwords.keys.all { key -> snapshot.connections.any { it.id == key } })
            require(snapshot.connections.all { it.id.isNotBlank() && (it.profileId == null || it.profileId in ids) })
            require(snapshot.favorites.all { it.profileId in ids && it.contentId.isNotBlank() && it.type in CONTENT_TYPES })
            require(snapshot.watchProgress.all {
                it.profileId in ids && it.contentId.isNotBlank() && it.type in CONTENT_TYPES &&
                    it.positionMillis >= 0 && it.durationMillis >= 0 && it.lastWatchedMillis >= 0
            })
            snapshot.settings?.let {
                require(it.accentColorHex.matches(Regex("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")))
                require(it.playbackQuality in setOf("auto", "high", "medium", "low"))
                require(it.cardSizeScale.isFinite() && it.cardSizeScale in 0.5f..2f)
                require(it.seekSeconds in setOf(5, 10) && it.autoSyncHours in setOf(0, 12, 24, 168))
            }
            return snapshot.copy(settings = snapshot.settings?.copy(tmdbApiKey = null))
        } catch (e: Exception) {
            throw IllegalArgumentException("Backup inválido ou incompatível. Nenhum dado foi restaurado.", e)
        }
    }

    private val CONTENT_TYPES = setOf("LIVE", "MOVIE", "SERIES")
}
