package com.auroraplay.iptv.core.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A single source of truth for "is there internet right now" — every screen
 * that shows its own offline state was reacting only to whatever one call
 * happened to fail, never to the network itself, so there was no consistent
 * "you're offline" message anywhere in the app.
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val isOnline: Flow<Boolean> = callbackFlow {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // "Online" = the default network reports the INTERNET capability.
        //
        // We deliberately do NOT also require NET_CAPABILITY_VALIDATED.
        // Emulators — and plenty of real networks — take a while to run
        // Android's captive-portal validation probe, or never pass it because
        // the probe host is unreachable, and until then VALIDATED is absent
        // even though traffic flows fine. Requiring it pinned the app's
        // "Sem conexão com a internet" banner on permanently. A captive
        // portal briefly slipping through as "online" is a much smaller
        // problem here: this flow only drives a banner and blocks nothing.
        fun NetworkCapabilities?.hasInternet(): Boolean =
            this != null && (hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))

        fun currentlyOnline(): Boolean =
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork).hasInternet()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(connectivityManager.getNetworkCapabilities(network).hasInternet())
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.hasInternet())
            }

            override fun onLost(network: Network) {
                // The default network went away — fall back to whatever the
                // system now considers active (may be another network, may be
                // nothing).
                trySend(currentlyOnline())
            }

            override fun onUnavailable() {
                trySend(false)
            }
        }

        trySend(currentlyOnline())
        // registerDefaultNetworkCallback tracks the one network the app's
        // traffic actually uses, instead of an empty NetworkRequest that
        // matched every network on the device regardless of which was default.
        connectivityManager.registerDefaultNetworkCallback(callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
