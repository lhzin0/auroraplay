package com.auroraplay.iptv.core.util

import android.util.Log
import com.auroraplay.iptv.BuildConfig

/**
 * Thin wrapper over [Log] so every call site shares one tag prefix instead of
 * ad-hoc `TAG` constants. `d` is stripped from release builds — it's dev
 * chatter, not something worth shipping; `w`/`e` stay on in release too,
 * since a rare failure logged there is exactly what `adb logcat` needs to
 * diagnose a report from a real install. Never pass credentials or PINs.
 */
object AppLog {
    private const val PREFIX = "Aurora"

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d("$PREFIX/$tag", message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w("$PREFIX/$tag", message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e("$PREFIX/$tag", message, throwable)
    }
}
