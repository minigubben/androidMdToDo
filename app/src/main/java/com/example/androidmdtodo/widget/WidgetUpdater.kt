package com.example.androidmdtodo.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll

object WidgetUpdater {
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

    suspend fun updateWidgets(context: Context, appWidgetIds: Iterable<Int>) {
        appWidgetIds.forEach { updateWidget(context, it) }
    }

    suspend fun updateAll(context: Context) {
        runCatching {
            ChecklistWidget().updateAll(context)
        }.onFailure { exception ->
            Log.w("WidgetUpdater", "Failed to refresh all widgets", exception)
        }
    }
}
