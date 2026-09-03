package com.auroraplay.iptv.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class GithubUpdateClient @Inject constructor(@param:ApplicationContext private val context: Context) {
    // No cookies, credentials, logging, cache, or shared IPTV client.
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    val directory: File get() = File(context.filesDir, "updates").apply { mkdirs() }
    fun file(release: AppRelease) = File(directory, release.fileName)

    suspend fun latestManifest(): String? = withContext(Dispatchers.IO) {
        open(AppRelease.MANIFEST_URL).use { response ->
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) throw IOException("GitHub indisponível (${response.code}).")
            val out = ByteArrayOutputStream()
            response.body!!.byteStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(out.size() + count <= 128 * 1024)
                    out.write(buffer, 0, count)
                }
            }
            out.toString("UTF-8").removePrefix("\uFEFF").also(AppRelease::parse)
        }
    }

    private fun open(initialUrl: String): Response {
        var url = initialUrl
        repeat(6) {
            require(AppRelease.trustedTransportUrl(url))
            val response = client.newCall(Request.Builder().url(url)
                .header("User-Agent", "AuroraPlay-Updater")
                .header("Accept", "application/octet-stream").build()).execute()
            if (!response.isRedirect) return response
            val next = response.header("Location")?.let(response.request.url::resolve)?.toString()
            response.close()
            url = next ?: throw IOException("Redirecionamento inválido do GitHub.")
        }
        throw IOException("Muitos redirecionamentos do GitHub.")
    }

    suspend fun download(release: AppRelease, progress: suspend (Int) -> Unit): File = withContext(Dispatchers.IO) {
        require(release.minSdk <= Build.VERSION.SDK_INT)
        val partial = File(directory, "${release.fileName}.part")
        try {
            open(release.downloadUrl).use { response ->
                if (!response.isSuccessful) throw IOException("Não foi possível baixar a versão (${response.code}).")
                val body = response.body ?: throw IOException("Arquivo vazio.")
                if (body.contentLength() >= 0) require(body.contentLength() == release.sizeBytes)
                var read = 0L
                var lastPercent = -1
                body.byteStream().use { input -> partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        read += count
                        require(read <= release.sizeBytes)
                        output.write(buffer, 0, count)
                        val percent = (read * 100 / release.sizeBytes).toInt()
                        if (percent != lastPercent) { lastPercent = percent; progress(percent) }
                    }
                    output.flush()
                } }
            }
            verify(partial, release)
            currentCoroutineContext().ensureActive()
            val target = file(release)
            if (!partial.renameTo(target)) throw IOException("Não foi possível guardar a atualização.")
            target
        } finally { partial.delete() }
    }

    @Suppress("DEPRECATION")
    suspend fun verify(apk: File, release: AppRelease) = withContext(Dispatchers.IO) {
        require(apk.length() == release.sizeBytes)
        val digest = MessageDigest.getInstance("SHA-256")
        apk.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        require(digest.digest().hex() == release.sha256)
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val info = requireNotNull(context.packageManager.getPackageArchiveInfo(apk.path, flags))
        require(info.packageName == AppRelease.PACKAGE)
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        require(code == release.versionCode.toLong() && info.versionName == release.version)
        require(info.applicationInfo?.minSdkVersion?.let { it <= Build.VERSION.SDK_INT } == true)
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        val expected = if (Build.VERSION.SDK_INT >= 28) PRODUCTION_CERTIFICATE else LEGACY_CERTIFICATE
        require(signatures?.size == 1 && MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray()).hex() == expected)
        // Android's package installer independently verifies the APK cryptographic signature.
    }

    companion object {
        const val PRODUCTION_CERTIFICATE = "c3a9a8b7a2ebdf68415765c17f82da39910c4f414ad00f696985e669ec65323b"
        const val LEGACY_CERTIFICATE = "38d4c19104024257daef47ebea8e60c5c764fe8423fb768b728caebedd00efcb"
    }
}
internal fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
