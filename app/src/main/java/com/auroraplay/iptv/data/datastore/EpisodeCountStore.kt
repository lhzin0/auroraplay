package com.auroraplay.iptv.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.episodeCountDataStore by preferencesDataStore(name = "episode_counts")

/** Key for one series' known-episode-id set. Scoped by connection because an
 * Xtream series id is only unique within a single provider — two playlists can
 * reuse the same numeric id for different shows (audit #6). Connection ids are
 * UUIDs and series ids numeric, so a slash appears in neither and delimits
 * unambiguously. */
internal fun episodeIdsKeyName(connectionId: String, seriesId: String): String =
    "ids/" + connectionId + "/" + seriesId

/**
 * The set of episode ids each favorited series had the last time the
 * background check ran, keyed by connection + series. A series gaining an id
 * it didn't have before is what triggers the "new episode available"
 * notification — Xtream never marks an episode as new, and a bare count would
 * miss the case where one episode is added the same week another is dropped.
 */
@Singleton
class EpisodeCountStore @Inject constructor(
    private val context: Context,
) {
    /** null = this series has never been checked on this connection before
     * (so the very first check never notifies); an empty set = checked, had
     * no episodes. */
    suspend fun getKnownEpisodeIds(connectionId: String, seriesId: String): Set<String>? =
        context.episodeCountDataStore.data.first()[stringSetPreferencesKey(episodeIdsKeyName(connectionId, seriesId))]

    suspend fun setKnownEpisodeIds(connectionId: String, seriesId: String, ids: Set<String>) {
        context.episodeCountDataStore.edit {
            it[stringSetPreferencesKey(episodeIdsKeyName(connectionId, seriesId))] = ids
        }
    }
}

/**
 * True when [current] contains an episode id that [known] did not. Never fires
 * on the first check ([known] is null) or when episodes were only removed.
 */
internal fun hasNewEpisodes(current: Set<String>, known: Set<String>?): Boolean =
    known != null && current.any { it !in known }
