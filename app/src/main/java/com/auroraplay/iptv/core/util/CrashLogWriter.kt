package com.auroraplay.iptv.core.util

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A crash leaves zero trace today — no crash-reporting service is wired up
 * (a deliberate, local-only choice), and `adb logcat` is gone the moment the
 * process dies. This writes the stack trace to a small rolling file in app-
 * private storage before letting the crash proceed normally, so there is at
 * least something to read (and share) after the fact.
 */
object CrashLogWriter {
    private const val DIR_NAME = "crash_logs"
    private const val MAX_LOGS = 10

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(appContext, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val dir = logDir(context).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val stringWriter = StringWriter()
        throwable.printStackTrace(PrintWriter(stringWriter))
        File(dir, "$stamp.txt").writeText(
            "AuroraPlay ${com.auroraplay.iptv.BuildConfig.VERSION_NAME} (${com.auroraplay.iptv.BuildConfig.VERSION_CODE})\n" +
                "${Date()}\n" +
                "Thread: ${thread.name}\n\n" +
                stringWriter.toString()
        )
        // Keep only the most recent MAX_LOGS — this is a rolling diagnostic
        // aid, not a permanent record.
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(MAX_LOGS)?.forEach { it.delete() }
    }

    /** Most recent crash log, or null if the app has never crashed (or the
     * log was already cleared). */
    fun latest(context: Context): File? =
        logDir(context).listFiles()?.maxByOrNull { it.lastModified() }

    private fun logDir(context: Context) = File(context.applicationContext.filesDir, DIR_NAME)
}
