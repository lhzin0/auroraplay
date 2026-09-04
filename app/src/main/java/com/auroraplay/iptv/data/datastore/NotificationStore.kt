package com.auroraplay.iptv.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class AppNotification(
    val id: String,
    val title: String,
    val message: String,
    val timestampMillis: Long,
    val read: Boolean = false,
)

private val Context.notificationDataStore by preferencesDataStore(name = "app_notifications")
private val LIST_KEY = stringPreferencesKey("list")

/**
 * A small in-app history so the bell icon has something to show beyond
 * whatever the system notification tray currently has — those get swiped
 * away and forgotten, this doesn't. Capped at 30 so it can't grow forever.
 */
@Singleton
class NotificationStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val gson = Gson()

    val notifications: Flow<List<AppNotification>> = context.notificationDataStore.data.map { prefs ->
        decode(prefs[LIST_KEY])
    }

    suspend fun add(title: String, message: String) {
        context.notificationDataStore.edit { prefs ->
            val current = decode(prefs[LIST_KEY])
            val updated = listOf(
                AppNotification(
                    id = "${System.currentTimeMillis()}",
                    title = title,
                    message = message,
                    timestampMillis = System.currentTimeMillis(),
                )
            ) + current
            prefs[LIST_KEY] = gson.toJson(updated.take(30))
        }
    }

    suspend fun markAllRead() {
        context.notificationDataStore.edit { prefs ->
            val current = decode(prefs[LIST_KEY]).map { it.copy(read = true) }
            prefs[LIST_KEY] = gson.toJson(current)
        }
    }

    private fun decode(json: String?): List<AppNotification> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val type = com.google.gson.reflect.TypeToken.getParameterized(List::class.java, AppNotification::class.java).type
            gson.fromJson<List<AppNotification>>(json, type)
        }.getOrDefault(emptyList())
    }
}
