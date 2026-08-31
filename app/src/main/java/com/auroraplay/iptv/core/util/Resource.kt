package com.auroraplay.iptv.core.util

/**
 * Generic wrapper for UI state produced by use cases / repositories.
 * Keeps loading / success / error / empty handling consistent across
 * every screen, as required by the "estados da interface" spec.
 */
sealed class Resource<out T> {
    data object Loading : Resource<Nothing>()
    data class Success<T>(val data: T) : Resource<T>()
    data class Error(val message: String, val throwable: Throwable? = null) : Resource<Nothing>()
    data object Empty : Resource<Nothing>()
}

inline fun <T> Resource<T>.onSuccess(action: (T) -> Unit): Resource<T> {
    if (this is Resource.Success) action(data)
    return this
}
