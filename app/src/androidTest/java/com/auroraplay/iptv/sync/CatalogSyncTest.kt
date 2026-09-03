package com.auroraplay.iptv.sync

import android.app.Notification
import android.content.Context
import android.content.Intent
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.auroraplay.iptv.core.util.Resource
import com.auroraplay.iptv.MainActivity
import com.auroraplay.iptv.domain.repository.ContentRepository
import com.auroraplay.iptv.domain.repository.SyncStage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CatalogSyncTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun worker(
        result: Flow<Resource<SyncStage>>,
        progress: MutableList<Data> = mutableListOf(),
        foreground: MutableList<ForegroundInfo> = mutableListOf(),
    ): CatalogSyncWorker {
        val repository = Proxy.newProxyInstance(ContentRepository::class.java.classLoader, arrayOf(ContentRepository::class.java)) { _, method, _ ->
            check(method.name == "syncConnection")
            result
        } as ContentRepository
        return TestListenableWorkerBuilder.from(context, CatalogSyncWorker::class.java)
            .setInputData(workDataOf(CatalogSyncScheduler.CONNECTION_ID to "synthetic-test-connection"))
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                    CatalogSyncWorker(appContext, workerParameters, repository)
            })
            .setProgressUpdater { _, _, data ->
                progress.add(data)
                CallbackToFutureAdapter.getFuture { it.set(null); "test-progress" }
            }
            .setForegroundUpdater { _, _, info ->
                foreground.add(info)
                CallbackToFutureAdapter.getFuture { it.set(null); "test-foreground" }
            }
            .build()
    }

    @Test fun workerPublishesEachStageAndCompletesOnlyAfterDone() = runBlocking {
        val progress = mutableListOf<Data>()
        val notifications = mutableListOf<ForegroundInfo>()
        val stages = SyncStage.entries
        val worker = worker(flow { stages.forEach { emit(Resource.Success(it)) } }, progress, notifications)
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(stages.filter { it != SyncStage.DONE }.map { it.name }, progress.map { it.getString(CatalogSyncScheduler.STAGE) })
        val channels = notifications.first { it.notification.extras.getString(Notification.EXTRA_TEXT)?.contains("1 de 3") == true }.notification
        assertEquals(3, channels.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        assertTrue(channels.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(channels.actions.any { it.title == "Cancelar" })
        val connecting = notifications.first().notification
        assertTrue(connecting.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
    }

    @Test fun failedOrIncompleteFetchCannotReportSuccess() = runBlocking {
        val failed = worker(flowOf(Resource.Success(SyncStage.CONNECTING), Resource.Error("Servidor indisponível"))).doWork()
        assertTrue(failed is ListenableWorker.Result.Failure)
        assertEquals("Servidor indisponível", failed.outputData.getString(CatalogSyncScheduler.ERROR))
        val incomplete = worker(flowOf(Resource.Success(SyncStage.CHANNELS))).doWork()
        assertTrue(incomplete is ListenableWorker.Result.Failure)
    }

    @Test fun cancellationIsNotConvertedIntoSuccessOrFailure() = runBlocking {
        val worker = worker(flow { emit(Resource.Success(SyncStage.MOVIES)); throw CancellationException("test") })
        try { worker.doWork(); fail("Cancellation must propagate to WorkManager") }
        catch (_: CancellationException) { }
    }

    @Test fun terminalWorkStateClearsLoadingAndReportsOutcome() {
        fun info(state: WorkInfo.State, stage: SyncStage? = null) = WorkInfo(
            id = UUID.randomUUID(), state = state, tags = emptySet(),
            progress = workDataOf(CatalogSyncScheduler.STAGE to stage?.name),
        )
        assertEquals(Resource.Success(SyncStage.MOVIES), info(WorkInfo.State.RUNNING, SyncStage.MOVIES).toSyncResource())
        assertEquals(Resource.Success(SyncStage.DONE), info(WorkInfo.State.SUCCEEDED).toSyncResource())
        assertTrue(info(WorkInfo.State.FAILED, SyncStage.SERIES).toSyncResource() is Resource.Error)
        assertTrue(info(WorkInfo.State.CANCELLED, SyncStage.MOVIES).toSyncResource() is Resource.Error)
        assertEquals(Resource.Loading, info(WorkInfo.State.ENQUEUED).toSyncResource())
    }

    @Test fun scheduledWorkUsesHiltAndForegroundServiceThenStopsLoadingOnError() = runBlocking {
        // No real playlist or network is involved: the repository rejects this unknown ID.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        instrumentation.waitForIdleSync()
        val scheduler = CatalogSyncScheduler(context)
        val id = "missing-test-${UUID.randomUUID()}"
        val events = withTimeout(30_000) { scheduler.sync(id).toList() }
        assertEquals(Resource.Loading, events.first())
        val error = events.last() as Resource.Error
        // Distinguishes a real repository result from factory/foreground-service startup errors.
        assertTrue(error.message.contains("sem credenciais"))
        assertFalse(scheduler.observeActive().first().containsKey(id))
    }
}
