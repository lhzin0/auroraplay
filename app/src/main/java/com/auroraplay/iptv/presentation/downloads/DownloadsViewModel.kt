package com.auroraplay.iptv.presentation.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import com.auroraplay.iptv.player.download.DownloadState
import com.auroraplay.iptv.player.download.DownloadTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One card per movie and per series (not per episode) — [DownloadState]s are
 * folded on their [DownloadState.groupKey]. Movies stay single-item groups;
 * a series gathers all its downloaded episodes under its poster, mirroring
 * how Netflix's Downloads screen collapses a show into one row.
 */
data class DownloadGroup(
    val key: String,
    val title: String,
    val posterUrl: String?,
    val isSeries: Boolean,
    /** Episodes sorted by season/episode; for a movie, the single item. */
    val items: List<DownloadState>,
    val totalBytes: Long,
    val completedCount: Int,
    val anyDownloading: Boolean,
    val allCompleted: Boolean,
) {
    val itemCount: Int get() = items.size
    /** The one item to play when a movie card (or a fully-single group) is tapped. */
    val soleItem: DownloadState? get() = items.singleOrNull()
}

data class DownloadsUiState(
    val isLoading: Boolean = true,
    val groups: List<DownloadGroup> = emptyList(),
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadTracker: DownloadTracker,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            downloadTracker.downloads.collect { map ->
                // Map is insertion-ordered (built by successive `+`), so a
                // later index means a more recently queued download — used to
                // sort finished groups newest-first.
                val recency = map.values.withIndex().associate { (i, d) -> d.contentId to i }

                val groups = map.values
                    .groupBy { it.groupKey }
                    .map { (key, items) ->
                        val sorted = items.sortedWith(compareBy({ it.sortKey }, { it.displayTitle }))
                        DownloadGroup(
                            key = key,
                            title = items.first().groupTitle,
                            posterUrl = items.firstNotNullOfOrNull { it.posterUrl },
                            isSeries = items.first().isSeriesGroup,
                            items = sorted,
                            totalBytes = items.sumOf { it.bytesDownloaded },
                            completedCount = items.count { it.status == Download.STATE_COMPLETED },
                            anyDownloading = items.any { it.status == Download.STATE_DOWNLOADING },
                            allCompleted = items.all { it.status == Download.STATE_COMPLETED },
                        )
                    }
                    .sortedWith(
                        // In-progress groups float to the top; the rest newest-first.
                        compareByDescending<DownloadGroup> { it.anyDownloading }
                            .thenByDescending { g -> g.items.maxOf { recency[it.contentId] ?: 0 } }
                    )

                _uiState.value = DownloadsUiState(isLoading = false, groups = groups)
            }
        }
    }

    fun remove(contentId: String) = downloadTracker.removeDownload(contentId)

    /** Delete every episode of a series card (or the lone movie) at once. */
    fun removeGroup(group: DownloadGroup) = group.items.forEach { downloadTracker.removeDownload(it.contentId) }
}
