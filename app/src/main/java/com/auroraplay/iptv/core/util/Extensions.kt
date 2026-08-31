package com.auroraplay.iptv.core.util

import java.util.concurrent.TimeUnit

/** Formats milliseconds as "há X minutos / horas / dias" for last-sync labels. */
fun Long.toRelativeTimeLabel(nowMillis: Long = System.currentTimeMillis()): String {
    val diff = (nowMillis - this).coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        minutes < 1 -> "agora mesmo"
        minutes < 60 -> "há $minutes min"
        hours < 24 -> "há $hours h"
        else -> "há $days d"
    }
}

/** Formats a duration in seconds as mm:ss or hh:mm:ss for player UI and progress labels. */
fun Long.toTimeLabel(): String {
    val totalSeconds = this
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun Float.coercedProgress(): Float = this.coerceIn(0f, 1f)

/** Human-readable size ("142 MB", "1,3 GB") — used for download progress when
 * the source never sent a Content-Length and a percentage isn't available. */
fun Long.toFileSizeLabel(): String {
    if (this < 1024) return "$this B"
    val units = listOf("KB", "MB", "GB")
    var value = this / 1024.0
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return "%.1f %s".format(value, units[unitIndex]).replace(".", ",")
}
