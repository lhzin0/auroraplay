package com.auroraplay.iptv.player.download

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/** Snapshot of a single download, keyed by our own contentId (movie id or episode id).
 * [playbackContentType]/[playbackId] carry exactly what Screen.Player's route needs
 * ("MOVIE"/movie.id or "SERIES"/"seriesId:episodeId"), decoded from the download's
 * own stored data — the Downloads screen can offer "assistir" without ever touching
 * the catalog or needing an active connection.
 *
 * [groupKey]/[groupTitle]/[posterUrl] let the Downloads screen fold every episode
 * of a series into one card (with its poster) instead of a flat list of episodes,
 * and show movies as their own cards — again with zero catalog lookups, since the
 * poster URL travelled with the download when it was queued. [sortKey] orders
 * episodes within a series card (season * 1000 + episode). */
data class DownloadState(
    /** The Media3 download id — composite `connectionId|type|contentId` for
     * anything queued since audit #3c, or the bare contentId for older ones.
     * Use this to remove or look one up. */
    val key: String,
    /** Our own raw content id (movie id, or episode id), for display and the
     * offline playback route — not unique across connections/types on its own. */
    val contentId: String,
    /** Connection this download belongs to ("" for pre-#3c downloads). */
    val connectionId: String,
    val status: Int, // Download.STATE_*
    val progressPercent: Float,
    /** False when the source never sent a Content-Length (common on Xtream/
     * IPTV VOD streams) — Media3 has no way to compute a percentage then, and
     * [progressPercent] would otherwise silently read as a fake, permanent 0%
     * even while bytes are actively flowing in. UI should show an
     * indeterminate spinner + [bytesDownloaded] instead of a stuck percentage
     * when this is false. */
    val hasKnownPercentage: Boolean,
    val bytesDownloaded: Long,
    val displayTitle: String,
    val playbackContentType: String,
    val playbackId: String,
    val posterUrl: String?,
    /** "movie:<id>" or "series:<seriesId>" — everything with the same key is one card. */
    val groupKey: String,
    /** Card title: the movie name, or the series name shared by all its episodes. */
    val groupTitle: String,
    val sortKey: Int,
) {
    val isSeriesGroup: Boolean get() = groupKey.startsWith("series:")
}

/** Inverse of the sortKey a series download is queued with
 * (`season * 1000 + episode`). Null for a movie (sortKey 0) or a malformed
 * value, so the caller can decide whether to render "T_ E_". */
fun seasonEpisodeFromSortKey(sortKey: Int): Pair<Int, Int>? =
    if (sortKey <= 0) null else (sortKey / 1000) to (sortKey % 1000)

/** The message to show instead of starting offline playback, or null when the
 * download is playable (audit #8). Pure so every branch is unit-tested. */
fun offlineLoadFailure(exists: Boolean, allowedForProfile: Boolean, isComplete: Boolean): String? = when {
    !exists -> "Este download não está mais disponível."
    !allowedForProfile -> "Este conteúdo não está disponível neste perfil."
    !isComplete -> "Este download ainda não terminou. Conecte-se à internet para concluí-lo."
    else -> null
}

/** Self-contained description of a downloaded item for offline playback —
 * see [DownloadTracker.offlineDownload]. */
data class OfflineDownload(
    val key: String,
    val uri: String,
    val title: String,
    val playbackContentType: String,
    val playbackId: String,
    val posterUrl: String?,
    val connectionId: String,
    val contentId: String,
    val sortKey: Int,
    /** The local file finished downloading and is non-empty. */
    val isComplete: Boolean,
)

/**
 * Bridges Media3's DownloadManager (which only knows about stream URLs) to
 * the app's own content ids, so screens can ask "is this movie/episode
 * downloaded?" and get a reactive answer, and PlayerManager can resolve
 * offline playback with zero network calls (see PlaybackCacheReadOnly in
 * DownloadModule).
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Singleton
class DownloadTracker @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager,
) {
    private val _downloads = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadState>> = _downloads

    // Runs on the app main looper — the thread DownloadManager was built on and
    // delivers its listener callbacks on, so currentDownloads (its in-memory
    // list) can be read here without a race. The one blocking call (the on-disk
    // index read) is pushed to IO explicitly.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        scope.launch {
            // Seed with whatever is already on disk from a previous app session,
            // filled in *under* the current map so it can't clobber a live entry.
            val initial = withContext(Dispatchers.IO) {
                val map = mutableMapOf<String, DownloadState>()
                downloadManager.downloadIndex.getDownloads().use { cursor ->
                    while (cursor.moveToNext()) {
                        val download = cursor.download
                        map[download.request.id] = download.toState()
                    }
                }
                map
            }
            _downloads.value = initial + _downloads.value
        }

        downloadManager.addListener(
            object : DownloadManager.Listener {
                override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) {
                    _downloads.value += (download.request.id to download.toState())
                }

                override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                    _downloads.value -= download.request.id
                }
            },
        )

        // Media3's DownloadManager.Listener only fires on state *transitions*
        // (queued → downloading → completed), never as bytes arrive — so without
        // this poll the "Baixando X%" / progress ring freezes at its value from
        // the last transition (0%) for the whole download and reads as stuck.
        scope.launch {
            while (isActive) {
                val active = downloadManager.currentDownloads
                if (active.isEmpty()) {
                    delay(5.seconds)
                } else {
                    _downloads.value += active.associateBy({ it.request.id }) { it.toState() }
                    delay(1.seconds)
                }
            }
        }
    }

    private fun Download.toState(): DownloadState {
        // Fields joined with a unit separator — never realistically typed by
        // a person, unlike a colon or comma, so a title containing either
        // can't accidentally split the encoding.
        val raw = runCatching { String(request.data, Charsets.UTF_8) }.getOrDefault("")
        val parts = raw.split(DATA_SEPARATOR)
        val title = parts.getOrNull(2)?.ifBlank { null } ?: "Download"
        return DownloadState(
            key = request.id,
            contentId = parts.getOrNull(7)?.ifBlank { null } ?: request.id,
            connectionId = parts.getOrNull(8)?.ifBlank { null } ?: "",
            status = state,
            progressPercent = percentDownloaded.takeIf { (it in 0f..100f) } ?: 0f,
            hasKnownPercentage = percentDownloaded in 0f..100f,
            bytesDownloaded = bytesDownloaded,
            playbackContentType = parts.getOrNull(0)?.ifBlank { null } ?: "MOVIE",
            playbackId = parts.getOrNull(1)?.ifBlank { null } ?: request.id,
            displayTitle = title,
            // Indices 3..6 are absent on downloads queued before grouping
            // existed — fall back so each old download simply becomes its own
            // one-item card, exactly the flat behavior it had before.
            posterUrl = parts.getOrNull(3)?.ifBlank { null },
            groupKey = parts.getOrNull(4)?.ifBlank { null } ?: "movie:${request.id}",
            groupTitle = parts.getOrNull(5)?.ifBlank { null } ?: title,
            sortKey = parts.getOrNull(6)?.toIntOrNull() ?: 0,
        )
    }

    /** [key] is [DownloadState.key] — the composite `downloadKey(...)`. */
    fun stateFor(key: String): DownloadState? = _downloads.value[key]

    /**
     * Everything the player needs to play a downloaded item with **zero**
     * dependency on the active connection or the remote catalog (audit #8):
     * the exact stream URI the download was queued with (CacheDataSource keys
     * on it), the playback route params, the poster and title that travelled
     * with the download, and whether the local file is actually complete.
     * Reads the on-disk index directly so it works on a cold start and after
     * the connection was deleted. Null = no such download any more.
     */
    suspend fun offlineDownload(key: String): OfflineDownload? = withContext(Dispatchers.IO) {
        val download = runCatching { downloadManager.downloadIndex.getDownload(key) }.getOrNull()
            ?: return@withContext null
        val state = download.toState()
        OfflineDownload(
            key = key,
            uri = download.request.uri.toString(),
            title = state.displayTitle,
            playbackContentType = state.playbackContentType,
            playbackId = state.playbackId,
            posterUrl = state.posterUrl,
            connectionId = state.connectionId,
            contentId = state.contentId,
            sortKey = state.sortKey,
            isComplete = download.state == Download.STATE_COMPLETED && download.bytesDownloaded > 0L,
        )
    }

    fun isDownloadedFlow(key: String): Flow<Boolean> =
        downloads.map { it[key]?.status == Download.STATE_COMPLETED }

    fun isDownloaded(key: String): Boolean = _downloads.value[key]?.status == Download.STATE_COMPLETED
    fun isDownloading(key: String): Boolean = _downloads.value[key]?.status == Download.STATE_DOWNLOADING

    /** Same URL used for streaming — CacheDataSource resolves it from the
     * local download cache automatically once complete, so there is no
     * separate "local file path" concept the rest of the app needs to know about.
     *
     * @param playbackContentType/[playbackId] exactly what Screen.Player.createRoute
     *   needs to resume this later from the Downloads screen — "MOVIE"/movie.id, or
     *   "SERIES"/"seriesId:episodeId". */
    fun startDownload(
        connectionId: String,
        contentId: String,
        title: String,
        streamUrl: String,
        playbackContentType: String,
        playbackId: String,
        posterUrl: String? = null,
        groupKey: String = "movie:$contentId",
        groupTitle: String = title,
        sortKey: Int = 0,
    ) {
        val data = listOf(
            playbackContentType,
            playbackId,
            title,
            posterUrl.orEmpty(),
            groupKey,
            groupTitle,
            sortKey.toString(),
            contentId,
            connectionId,
        ).joinToString(DATA_SEPARATOR)
        val request = DownloadRequest.Builder(downloadKey(connectionId, playbackContentType, contentId), streamUrl.toUri())
            .setData(data.toByteArray())
            .build()
        DownloadService.sendAddDownload(context, AuroraDownloadService::class.java, request, false)
    }

    /** [key] is [DownloadState.key]. */
    fun removeDownload(key: String) {
        DownloadService.sendRemoveDownload(context, AuroraDownloadService::class.java, key, false)
    }

    /**
     * Applied once at app start (from the persisted setting) and again the
     * instant the toggle changes in Settings — DownloadManager.requirements
     * is a mutable property, so this takes effect immediately, including
     * pausing a download already in progress the moment Wi-Fi drops if
     * "somente Wi-Fi" is on.
     */
    fun applyWifiOnlyPreference(wifiOnly: Boolean) {
        downloadManager.requirements = androidx.media3.exoplayer.scheduler.Requirements(
            if (wifiOnly) androidx.media3.exoplayer.scheduler.Requirements.NETWORK_UNMETERED
            else androidx.media3.exoplayer.scheduler.Requirements.NETWORK,
        )
    }

    companion object {
        private const val DATA_SEPARATOR = "\u0001"

        /** The Media3 download id: connectionId|type|contentId, globally unique so
         * two playlists that reuse an Xtream numeric id do not collide (audit #3c). */
        fun downloadKey(connectionId: String, playbackContentType: String, contentId: String): String =
            "$connectionId$DATA_SEPARATOR$playbackContentType$DATA_SEPARATOR$contentId"
    }
}
