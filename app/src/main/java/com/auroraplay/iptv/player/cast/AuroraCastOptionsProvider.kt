package com.auroraplay.iptv.player.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.NotificationOptions

/**
 * Required entry point for the Google Cast SDK (declared in AndroidManifest).
 * Uses the stock "Default Media Receiver" app id, which plays standard
 * HLS/DASH/MP4 streams out of the box — no custom Cast receiver app needed
 * for a personal Xtream client. Note: some Xtream servers embed the
 * username/password directly in the query string; a handful of Cast
 * receivers are picky about non-standard query params, so cross-check
 * playback on your actual TV/Chromecast device before relying on this.
 */
class AuroraCastOptionsProvider : OptionsProvider {

    companion object {
        // Google's public "Default Media Receiver" app id — plays generic
        // HLS/DASH/progressive streams without requiring a custom receiver.
        const val DEFAULT_RECEIVER_APP_ID = "CC1AD845"
    }

    override fun getCastOptions(context: Context): CastOptions {
        val notificationOptions = NotificationOptions.Builder().build()
        val mediaOptions = CastMediaOptions.Builder()
            .setNotificationOptions(notificationOptions)
            .build()

        return CastOptions.Builder()
            .setReceiverApplicationId(DEFAULT_RECEIVER_APP_ID)
            .setCastMediaOptions(mediaOptions)
            .setResumeSavedSession(true)
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
