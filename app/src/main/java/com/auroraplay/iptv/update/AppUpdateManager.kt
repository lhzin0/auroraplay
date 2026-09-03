package com.auroraplay.iptv.update

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import androidx.work.*
import com.auroraplay.iptv.BuildConfig
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

internal data class UpdateState(
    val manifest: String?, val readyManifest: String?, val workId: String?,
    val autoDownload: Boolean, val checkedAt: Long, val error: String?, val cancelledCode: Int,
) {
    val release get() = manifest?.let { runCatching { AppRelease.parse(it) }.getOrNull() }
    val ready get() = readyManifest?.let { runCatching { AppRelease.parse(it) }.getOrNull() }
}

@Singleton
internal class AppUpdateManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val client: GithubUpdateClient,
) {
    private val prefs = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    private fun snapshot() = UpdateState(prefs.getString("manifest", null), prefs.getString("ready", null),
        prefs.getString("work", null), prefs.getBoolean("auto", true), prefs.getLong("checked", 0),
        prefs.getString("error", null), prefs.getInt("cancelled", 0))
    private val state = MutableStateFlow(snapshot())
    val updates = state.asStateFlow()
    private val downloadMutex = Mutex()
    private val checkMutex = Mutex()
    private val workManager get() = WorkManager.getInstance(context)

    private fun change(block: android.content.SharedPreferences.Editor.() -> Unit) {
        if (!prefs.edit().apply(block).commit()) throw IOException("Não foi possível guardar o estado da atualização.")
        state.value = snapshot()
    }

    suspend fun start() = withContext(Dispatchers.IO) {
        if (BuildConfig.DEBUG) return@withContext
        state.value.ready?.takeIf { it.versionCode <= BuildConfig.VERSION_CODE }?.let {
            client.file(it).delete()
            change { remove("ready"); remove("work"); remove("error") }
        }
        val periodic = PeriodicWorkRequestBuilder<AppUpdateCheckWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        workManager.enqueueUniquePeriodicWork(CHECK_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, periodic)
        if (System.currentTimeMillis() - state.value.checkedAt > TimeUnit.HOURS.toMillis(24)) {
            workManager.enqueueUniqueWork(CHECK_NOW, ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<AppUpdateCheckWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
    }

    suspend fun check(): String = withContext(Dispatchers.IO) { checkMutex.withLock {
        try {
            val manifest = client.latestManifest()
            val release = manifest?.let(AppRelease::parse)
            change { putString("manifest", manifest); putLong("checked", System.currentTimeMillis()); remove("error") }
            if (release == null) return@withLock "Nenhuma versão publicada no GitHub ainda."
            if (release.versionCode <= BuildConfig.VERSION_CODE) return@withLock "Você está usando a versão mais recente."
            if (release.minSdk > Build.VERSION.SDK_INT) return@withLock "A nova versão exige um Android mais recente."
            if (state.value.ready?.versionCode == release.versionCode && client.file(release).isFile) {
                return@withLock "A versão ${release.version} está pronta para instalar."
            }
            AppUpdateNotifications(context).notify("Nova versão ${release.version}", "Abra as atualizações do AuroraPlay para acompanhar.")
            if (state.value.autoDownload && state.value.cancelledCode != release.versionCode) download(release, automatic = true)
            "Nova versão ${release.version} disponível."
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            change { putLong("checked", System.currentTimeMillis()); putString("error", "Não foi possível consultar o GitHub. Confira a conexão e tente novamente.") }
            throw e
        }
    } }

    suspend fun setAutoDownload(enabled: Boolean) = withContext(Dispatchers.IO) {
        change { putBoolean("auto", enabled); putInt("cancelled", 0) }
        if (!enabled) {
            val info = state.value.workId?.let { workManager.getWorkInfoByIdFlow(UUID.fromString(it)).first() }
            if (info?.state == WorkInfo.State.ENQUEUED && "automatic_update" in info.tags) workManager.cancelWorkById(info.id).await()
        }
        if (enabled) state.value.release?.takeIf { it.versionCode > BuildConfig.VERSION_CODE && it.minSdk <= Build.VERSION.SDK_INT }
            ?.let { if (state.value.ready?.versionCode != it.versionCode) download(it, automatic = true) }
    }

    suspend fun download(release: AppRelease, automatic: Boolean) = withContext(Dispatchers.IO) { downloadMutex.withLock {
        require(release.versionCode > BuildConfig.VERSION_CODE && release.minSdk <= Build.VERSION.SDK_INT)
        val active = state.value.workId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?.let { workManager.getWorkInfoByIdFlow(it).first() }
        if (active != null && !active.state.isFinished) {
            if (automatic) return@withLock
            // A manual tap can start a queued Wi-Fi download on the current network.
            if (active.state == WorkInfo.State.RUNNING) return@withLock
            workManager.cancelWorkById(active.id).await()
        }
        val request = OneTimeWorkRequestBuilder<AppUpdateDownloadWorker>()
            .addTag(if (automatic) "automatic_update" else "manual_update")
            .setInputData(workDataOf("manifest" to release.workerManifest()))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (automatic) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build()
        workManager.enqueueUniqueWork(DOWNLOAD, ExistingWorkPolicy.KEEP, request).await()
        change { putString("work", request.id.toString()); putInt("cancelled", 0); remove("error") }
    } }

    suspend fun cancel() = withContext(Dispatchers.IO) {
        change { putInt("cancelled", state.value.release?.versionCode ?: 0) }
        workManager.cancelUniqueWork(DOWNLOAD).await()
    }

    suspend fun downloadFile(release: AppRelease, progress: suspend (Int) -> Unit) {
        client.download(release, progress)
        withContext(Dispatchers.IO) {
            val old = state.value.ready
            change { putString("ready", release.workerManifest()); remove("error") }
            if (old != null && old.fileName != release.fileName) client.file(old).delete()
        }
    }

    suspend fun installIntent(): Intent = withContext(Dispatchers.IO) {
        val release = requireNotNull(state.value.ready)
        require(release.versionCode > BuildConfig.VERSION_CODE && context.packageName == AppRelease.PACKAGE)
        val apk = client.file(release)
        client.verify(apk, release)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    companion object {
        const val DOWNLOAD = "app_update_download"
        const val CHECK_NOW = "app_update_check_now"
        const val CHECK_PERIODIC = "app_update_check_daily"
    }
}

internal fun AppRelease.workerManifest(): String = JsonObject().apply {
    addProperty("applicationId", AppRelease.PACKAGE); addProperty("version", version)
    addProperty("versionCode", versionCode); addProperty("sizeBytes", sizeBytes)
    addProperty("sha256", sha256); addProperty("minSdk", minSdk)
    addProperty("fileName", fileName); addProperty("downloadUrl", downloadUrl)
    add("notes", JsonArray())
}.toString()
