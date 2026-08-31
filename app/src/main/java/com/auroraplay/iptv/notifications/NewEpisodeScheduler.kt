package com.auroraplay.iptv.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Every 6 hours is a compromise: frequent enough that "new episode" feels
 * timely, infrequent enough not to be a battery/data complaint. WorkManager
 * itself may still space runs out further under Doze.
 */
object NewEpisodeScheduler {
    private const val WORK_NAME = "new_episode_check"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<NewEpisodeCheckWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            // KEEP: reusing an already-scheduled job means every app launch
            // doesn't reset its clock and push the first real check back out.
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
