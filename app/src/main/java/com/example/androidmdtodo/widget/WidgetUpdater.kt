package com.example.androidmdtodo.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.example.androidmdtodo.data.WidgetConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object WidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun updateWidget(context: Context, glanceId: GlanceId) {
        runCatching {
            ChecklistWidget().update(context, glanceId)
        }.onFailure { exception ->
            Log.w("WidgetUpdater", "Failed to refresh widget", exception)
        }
    }

    suspend fun updateWidget(context: Context, appWidgetId: Int) {
        val glanceId = runCatching { GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId) }.getOrNull()
            ?: return
        updateWidget(context, glanceId)
    }

    fun refreshConfiguredWidget(context: Context, appWidgetId: Int) {
        val appContext = context.applicationContext
        scope.launch {
            WidgetRefreshScheduler.requestImmediate(appContext)
            WidgetUpdater.updateAll(appContext)

            repeat(30) { attempt ->
                val glanceId = runCatching {
                    GlanceAppWidgetManager(appContext).getGlanceIdBy(appWidgetId)
                }.getOrNull()

                if (glanceId != null) {
                    updateWidget(appContext, glanceId)
                    return@launch
                }

                if (attempt < 29) {
                    if (attempt % 5 == 4) {
                        WidgetRefreshScheduler.requestImmediate(appContext)
                        WidgetUpdater.updateAll(appContext)
                    }
                    delay(1000)
                }
            }
            Log.w("WidgetUpdater", "Timed out waiting for widget $appWidgetId to become available")
        }
    }

    suspend fun updateWidgets(context: Context, appWidgetIds: Iterable<Int>) {
        appWidgetIds.forEach { updateWidget(context, it) }
    }

    suspend fun refreshWidgetFamily(context: Context, appWidgetId: Int, fileUri: String?) {
        val appContext = context.applicationContext
        val widgetIds = if (fileUri != null) {
            WidgetConfigRepository(appContext).getWidgetIdsForUri(fileUri).ifEmpty { listOf(appWidgetId) }
        } else {
            listOf(appWidgetId)
        }

        repeat(5) { attempt ->
            updateWidgets(appContext, widgetIds)
            updateAll(appContext)

            if (attempt < 4) {
                delay(750)
            }
        }
    }

    suspend fun updateAll(context: Context) {
        runCatching {
            ChecklistWidget().updateAll(context)
        }.onFailure { exception ->
            Log.w("WidgetUpdater", "Failed to refresh all widgets", exception)
        }
    }
}
