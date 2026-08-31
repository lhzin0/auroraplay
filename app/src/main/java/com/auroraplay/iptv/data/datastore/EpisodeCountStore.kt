package com.auroraplay.iptv.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.episodeCountDataStore by preferencesDataStore(name = "episode_counts")

/**
 * One integer per series id: how many episodes it had the last time the
 * background check ran. A series growing past its stored count is what
 * triggers the "new episode available" notification — there's no other
 * signal for this in the Xtream API, which never marks an episode as new.
 */
@Singleton
class EpisodeCountStore @Inject constructor(
    private val context: Context,
) {
    suspend fun getKnownEpisodeCount(seriesId: String): Int? =
        context.episodeCountDataStore.data.first()[intPreferencesKey(seriesId)]

    suspend fun setKnownEpisodeCount(seriesId: String, count: Int) {
        context.episodeCountDataStore.edit { it[intPreferencesKey(seriesId)] = count }
    }
}
