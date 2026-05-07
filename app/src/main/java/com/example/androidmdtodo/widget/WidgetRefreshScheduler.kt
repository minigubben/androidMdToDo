package com.example.androidmdtodo.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.androidmdtodo.data.WidgetConfigRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object WidgetRefreshScheduler {
    private const val immediateRefreshWorkName = "widget-immediate-refresh"
    private const val refreshWorkName = "widget-periodic-refresh"
    private val refreshBurstDelaysMillis = listOf(0L, 5_000L, 30_000L, 2 * 60 * 1000L)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun sync(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            val hasConfiguredWidgets = WidgetConfigRepository(appContext).getAllConfigs().isNotEmpty()
            val workManager = WorkManager.getInstance(appContext)

            if (hasConfiguredWidgets) {
                val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES)
                    .build()
                workManager.enqueueUniquePeriodicWork(
                    refreshWorkName,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
            } else {
                workManager.cancelUniqueWork(refreshWorkName)
            }
        }
    }

    fun requestImmediate(context: Context) {
        requestRefreshBurst(context, includeDelayedRefreshes = false)
    }

    fun requestRefreshBurst(
        context: Context,
        includeDelayedRefreshes: Boolean = true,
    ) {
        val appContext = context.applicationContext
        val workManager = WorkManager.getInstance(appContext)
        val delays = if (includeDelayedRefreshes) refreshBurstDelaysMillis else listOf(0L)

        delays.forEach { delayMillis ->
            val workName = if (delayMillis == 0L) {
                immediateRefreshWorkName
            } else {
                "$immediateRefreshWorkName-$delayMillis"
            }
            val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()
            workManager.enqueueUniqueWork(
                workName,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}

class WidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return runCatching {
            val hasConfiguredWidgets = WidgetConfigRepository(applicationContext).getAllConfigs().isNotEmpty()
            if (!hasConfiguredWidgets) {
                return Result.success()
            }

            WidgetRefreshCoordinator.sync(applicationContext)
            WidgetUpdater.updateAll(applicationContext)
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}
