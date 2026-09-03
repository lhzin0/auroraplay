package com.auroraplay.iptv.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class AppUpdateTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val client = GithubUpdateClient(context)
    private fun fixture(name: String): File = File(context.cacheDir, "update-test-$name").apply {
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { input -> outputStream().use(input::copyTo) }
    }
    private fun release(file: File, version: String, code: Int) = AppRelease(version, code, file.length(),
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).hex(), 24, emptyList())

    @Test fun signedReleaseIsAcceptedButWrongHashVersionAndLegacyCertificateAreRejected() = runBlocking {
        val apk = fixture("production-1.33.0.apk")
        val legacy = fixture("legacy-1.32.0.apk")
        try {
            val expected = release(apk, "1.33.0", 89)
            client.verify(apk, expected)
            try { client.verify(apk, expected.copy(sha256 = "00".repeat(32))); fail("Wrong hash") }
            catch (_: IllegalArgumentException) { }
            try { client.verify(apk, expected.copy(versionCode = 12345)); fail("Wrong version") }
            catch (_: IllegalArgumentException) { }
            try { client.verify(legacy, release(legacy, "1.32.0", 88)); fail("Old certificate cannot replace production identity") }
            catch (_: IllegalArgumentException) { }
        } finally { apk.delete(); legacy.delete() }
    }

    @Test fun publishedGithubManifestAndApkCanBeDownloadedAndVerified() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveGithub") == "true")
        val manifest = requireNotNull(client.latestManifest())
        val release = AppRelease.parse(manifest)
        val progress = mutableListOf<Int>()
        val apk = client.download(release) { progress += it }
        try {
            assertTrue(apk.isFile)
            assertEquals(100, progress.last())
            client.verify(apk, release)
        } finally { apk.delete() }
    }
}
