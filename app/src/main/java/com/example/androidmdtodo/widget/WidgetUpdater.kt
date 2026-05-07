package com.example.androidmdtodo.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll

object WidgetUpdater {
    suspend fun updateWidget(context: Context, appWidgetId: Int) {
        val glanceId = runCatching { GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId) }.getOrNull()
            ?: return
        ChecklistWidget().update(context, glanceId)
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
