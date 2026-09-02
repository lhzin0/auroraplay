package com.auroraplay.iptv.data.backup

import com.auroraplay.iptv.data.database.entity.ConnectionEntity
import com.auroraplay.iptv.data.database.entity.FavoriteEntity
import com.auroraplay.iptv.data.database.entity.ProfileEntity
import com.auroraplay.iptv.data.database.entity.WatchProgressEntity
import com.auroraplay.iptv.domain.repository.AppSettings
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class BackupSnapshotTest {
    private fun snapshot() = BackupSnapshot(
        savedAt = 123456L, activeProfileId = "p",
        profiles = listOf(ProfileEntity("p", "Ana", "#7C5CFF", "A", "content://private/avatar", false, 12L, "pin-hash", true)),
        connections = listOf(ConnectionEntity("c", "TV", "https://example.com", "ana", true, profileId = "p")),
        favorites = listOf(FavoriteEntity("42", "MOVIE", "p", 123)),
        watchProgress = listOf(WatchProgressEntity("42", "MOVIE", "p", 1000, 6000, null, null, 123)),
        settings = AppSettings(tmdbApiKey = "SECRET_API_KEY"),
        connectionPasswords = mapOf("c" to "test-only-ç@ss/word\"\\"),
    )

    @Test fun portableSnapshotPreservesRecordsAndRemovesDeviceDataAndApiKey() {
        val json = BackupSnapshotCodec.encode(snapshot())
        val result = BackupSnapshotCodec.decode(json)
        assertFalse(json.contains("SECRET_API_KEY"))
        assertFalse(json.contains("content://"))
        assertFalse(json.contains("accessToken"))
        assertEquals(snapshot().connectionPasswords, result.connectionPasswords)
        assertEquals(snapshot().connections, result.connections)
        assertEquals(snapshot().favorites, result.favorites)
        assertEquals(snapshot().watchProgress, result.watchProgress)
        assertEquals("pin-hash", result.profiles.single().pinHash)
        assertFalse(result.profiles.single().biometricEnabled)
        assertNull(result.profiles.single().avatarUri)
        assertNull(result.settings?.tmdbApiKey)
    }

    @Test fun existingVersionOneSnapshotIsReadable() {
        val old = JsonParser.parseString(Gson().toJson(snapshot())).asJsonObject.apply {
            addProperty("version", 1)
            remove("connectionPasswords")
        }.toString()
        val decoded = BackupSnapshotCodec.decode(old)
        assertEquals("Ana", decoded.profiles.single().name)
        assertTrue(decoded.connectionPasswords.isEmpty())
    }

    @Test fun rejectsFutureVersionTruncationAndMissingOrNullArrays() {
        val valid = BackupSnapshotCodec.encode(snapshot())
        val invalid = listOf(
            valid.replace("\"version\":2", "\"version\":3"),
            valid.dropLast(10),
            "{}",
            JsonParser.parseString(valid).asJsonObject.apply { remove("profiles") }.toString(),
            JsonParser.parseString(valid).asJsonObject.apply { add("connections", com.google.gson.JsonNull.INSTANCE) }.toString(),
        )
        invalid.forEach { json -> assertThrows(IllegalArgumentException::class.java) { BackupSnapshotCodec.decode(json) } }
    }

    @Test fun rejectsNullRequiredFieldAndUnknownProfileReference() {
        val valid = BackupSnapshotCodec.encode(snapshot())
        val nullName = JsonParser.parseString(valid).asJsonObject.apply {
            getAsJsonArray("profiles")[0].asJsonObject.add("name", com.google.gson.JsonNull.INSTANCE)
        }.toString()
        assertThrows(IllegalArgumentException::class.java) { BackupSnapshotCodec.decode(nullName) }
        assertThrows(IllegalArgumentException::class.java) {
            BackupSnapshotCodec.encode(snapshot().copy(favorites = listOf(FavoriteEntity("42", "MOVIE", "unknown", 1))))
        }
    }

    @Test fun rejectsInvalidSettingsAndContentTypesBeforeRestore() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupSnapshotCodec.encode(snapshot().copy(settings = AppSettings(accentColorHex = "broken")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupSnapshotCodec.encode(snapshot().copy(favorites = listOf(FavoriteEntity("42", "EPISODE", "p", 1))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupSnapshotCodec.encode(snapshot().copy(watchProgress = listOf(snapshot().watchProgress.single().copy(positionMillis = -1))))
        }
    }

    @Test fun boundedFileReadRejectsOversizedBody() {
        assertArrayEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3).inputStream().readBytesBounded(3))
        assertThrows(IllegalArgumentException::class.java) { byteArrayOf(1, 2, 3, 4).inputStream().readBytesBounded(3) }
    }

    @Test fun downloadedMediaAndCatalogAreNotSerialized() {
        val root = JsonParser.parseString(BackupSnapshotCodec.encode(snapshot())).asJsonObject
        assertEquals(setOf("version", "savedAt", "activeProfileId", "profiles", "connections", "favorites", "watchProgress", "settings", "connectionPasswords"), root.keySet())
        assertFalse(root.has("downloads"))
        assertFalse(root.has("catalog"))
        assertFalse(root.has("files"))
    }

    @Test fun rejectsMissingMalformedOrUnrelatedPasswordsInVersionTwo() {
        val valid = BackupSnapshotCodec.encode(snapshot())
        val invalid = listOf(
            JsonParser.parseString(valid).asJsonObject.apply { remove("connectionPasswords") },
            JsonParser.parseString(valid).asJsonObject.apply { add("connectionPasswords", com.google.gson.JsonNull.INSTANCE) },
            JsonParser.parseString(valid).asJsonObject.apply { getAsJsonObject("connectionPasswords").addProperty("c", 1234) },
            JsonParser.parseString(valid).asJsonObject.apply { getAsJsonObject("connectionPasswords").addProperty("unrelated", "test-only") },
        )
        invalid.forEach { root ->
            assertThrows(IllegalArgumentException::class.java) { BackupSnapshotCodec.decode(root.toString()) }
        }
    }
}
