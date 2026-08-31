package com.auroraplay.iptv.core.util

object Constants {
    const val DATABASE_NAME = "auroraplay.db"
    const val SETTINGS_DATASTORE = "aurora_settings"
    const val SECURE_PREFS_FILE = "aurora_secure_credentials"

    // Xtream Codes API actions (player_api.php)
    const val ACTION_LIVE_CATEGORIES = "get_live_categories"
    const val ACTION_LIVE_STREAMS = "get_live_streams"
    const val ACTION_VOD_CATEGORIES = "get_vod_categories"
    const val ACTION_VOD_STREAMS = "get_vod_streams"
    const val ACTION_VOD_INFO = "get_vod_info"
    const val ACTION_SERIES_CATEGORIES = "get_series_categories"
    const val ACTION_SERIES = "get_series"
    const val ACTION_SERIES_INFO = "get_series_info"
    const val ACTION_SHORT_EPG = "get_short_epg"

    const val STREAM_TYPE_LIVE = "live"
    const val STREAM_TYPE_MOVIE = "movie"
    const val STREAM_TYPE_SERIES = "series"

    const val CONTINUE_WATCHING_MIN_PROGRESS = 0.02f
    const val CONTINUE_WATCHING_MAX_PROGRESS = 0.95f

    const val SYNC_STALE_AFTER_MINUTES = 360L // 6h
}
