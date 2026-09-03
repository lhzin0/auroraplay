package com.auroraplay.iptv.update

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class AppReleaseTest {
    private val valid = AppRelease("1.34.0", 90, 6500000, "ab".repeat(32), 24, emptyList()).workerManifest()

    @Test fun releaseUsesOnlyPinnedGithubRepositoryAndSupportsBuildManifest() {
        val release = AppRelease.parse(valid)
        assertEquals("https://github.com/lhzin0/auroraplay/releases/download/v1.34.0/AuroraPlay-1.34.0.apk", release.downloadUrl)
        val source = JsonParser.parseString(valid).asJsonObject.apply { addProperty("downloadUrl", "./downloads/AuroraPlay-1.34.0.apk") }
        assertEquals(release, AppRelease.parse(source.toString()))
    }

    @Test fun wrongPackageUnsafeFilenameHashSizeAndOverflowAreRejected() {
        for ((key, value) in listOf("applicationId" to "com.other.app", "version" to "../../escape", "fileName" to "../secret.apk", "sha256" to "abcd", "downloadUrl" to "https://evil.example/app.apk")) {
            val changed = JsonParser.parseString(valid).asJsonObject.apply { addProperty(key, value) }
            assertThrows(IllegalArgumentException::class.java) { AppRelease.parse(changed.toString()) }
        }
        for ((key, value) in listOf("sizeBytes" to "0", "sizeBytes" to "999999999999", "versionCode" to "999999999999999999999999999999", "versionCode" to "1.5", "minSdk" to "0")) {
            val changed = JsonParser.parseString(valid).asJsonObject.apply { add(key, JsonParser.parseString(value)) }
            assertThrows(IllegalArgumentException::class.java) { AppRelease.parse(changed.toString()) }
        }
    }

    @Test fun redirectsCannotDowngradeOrLeakToArbitraryHosts() {
        assertTrue(AppRelease.trustedTransportUrl("https://release-assets.githubusercontent.com/123/asset?signature=public-download"))
        for (url in listOf("http://github.com/file", "https://github.com.evil.example/file", "https://github.com@evil.example/file", "https://user:password@github.com/file", "https://github.com:444/file", "file:///tmp/app.apk")) {
            assertFalse(url, AppRelease.trustedTransportUrl(url))
        }
    }
}
