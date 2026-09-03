package com.auroraplay.iptv.update

import com.google.gson.JsonParser
import java.net.URI

internal data class AppRelease(
    val version: String, val versionCode: Int, val sizeBytes: Long,
    val sha256: String, val minSdk: Int, val notes: List<String>,
) {
    val fileName get() = "AuroraPlay-$version.apk"
    val downloadUrl get() = "$REPOSITORY/releases/download/v$version/$fileName"
    companion object {
        const val PACKAGE = "com.auroraplay.iptv"
        const val REPOSITORY = "https://github.com/lhzin0/auroraplay"
        const val WEBSITE = "https://lhzin0.github.io/auroraplay/"
        const val MANIFEST_URL = "$REPOSITORY/releases/latest/download/release.json"
        const val MAX_APK_BYTES = 200L * 1024 * 1024
        fun parse(json: String): AppRelease {
            require(json.toByteArray().size <= 128 * 1024)
            val root = JsonParser.parseString(json).asJsonObject
            fun string(key: String): String {
                val v = requireNotNull(root[key]).asJsonPrimitive
                require(v.isString)
                return v.asString
            }
            fun integer(key: String): Long {
                val v = requireNotNull(root[key]).asJsonPrimitive
                require(v.isNumber && v.toString().matches(Regex("[0-9]+")))
                return v.toString().toLong()
            }
            require(string("applicationId") == PACKAGE)
            val version = string("version")
            require(version.matches(Regex("[0-9]{1,4}\\.[0-9]{1,4}\\.[0-9]{1,4}")))
            val code = integer("versionCode").also { require(it in 1..Int.MAX_VALUE.toLong()) }.toInt()
            val size = integer("sizeBytes").also { require(it in 1..MAX_APK_BYTES) }
            val sdk = integer("minSdk").also { require(it in 24..100) }.toInt()
            val hash = string("sha256").lowercase().also { require(it.matches(Regex("[a-f0-9]{64}"))) }
            val notes = root.getAsJsonArray("notes").also { require(it.size() <= 20) }.map {
                require(it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.length <= 1000)
                it.asString
            }
            val release = AppRelease(version, code, size, hash, sdk, notes)
            require(string("fileName") == release.fileName)
            val url = string("downloadUrl")
            require(url == release.downloadUrl || url == "./downloads/${release.fileName}")
            return release
        }
        fun trustedTransportUrl(value: String): Boolean = runCatching {
            val uri = URI(value)
            uri.scheme == "https" && uri.userInfo == null && uri.fragment == null &&
                uri.port in setOf(-1, 443) && uri.host in setOf(
                    "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com",
                    "github-releases.githubusercontent.com",
                )
        }.getOrDefault(false)
    }
}
