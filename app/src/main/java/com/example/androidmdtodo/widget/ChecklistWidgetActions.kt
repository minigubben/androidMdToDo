package com.example.androidmdtodo.widget

import android.content.Context
import android.net.Uri
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import com.example.androidmdtodo.R
import com.example.androidmdtodo.data.MarkdownChecklistMutator
import com.example.androidmdtodo.data.MarkdownFileRepository
import com.example.androidmdtodo.data.MutationResult
import com.example.androidmdtodo.data.TaskRef
import com.example.androidmdtodo.data.WidgetConfigRepository

class ChecklistToggleActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        val configRepository = WidgetConfigRepository(context)
        val config = configRepository.getConfig(appWidgetId) ?: return

        val lineIndex = parameters[TaskActionKeys.LINE_INDEX] ?: return
        val normalizedText = parameters[TaskActionKeys.NORMALIZED_TEXT] ?: return
        val occurrenceIndex = parameters[TaskActionKeys.OCCURRENCE_INDEX] ?: return

        val fileRepository = MarkdownFileRepository(context)
        val mutator = MarkdownChecklistMutator()

        try {
            val uri = Uri.parse(config.fileUri)
            val original = fileRepository.read(uri)
            when (
                val mutation = mutator.checkTask(
                    original,
                    TaskRef(
                        lineIndex = lineIndex,
                        normalizedText = normalizedText,
                        occurrenceIndex = occurrenceIndex,
                    ),
                )
            ) {
                is MutationResult.Updated -> {
                    fileRepository.write(uri, mutation.document)
                    configRepository.clearError(appWidgetId)
                    WidgetUpdater.updateWidget(context, glanceId)
                    WidgetUpdater.updateWidgets(
                        context,
                        configRepository.getWidgetIdsForUri(config.fileUri)
                            .filterNot { it == appWidgetId },
                    )
                }

                MutationResult.Stale -> {
                    WidgetUpdater.updateWidget(context, glanceId)
                }
            }
        } catch (exception: Exception) {
            configRepository.setError(
                appWidgetId,
                exception.message ?: context.getString(R.string.error_update_failed),
            )
            WidgetUpdater.updateWidget(context, glanceId)
        }
    }
}

class ChecklistRefreshActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        val configRepository = WidgetConfigRepository(context)
        val config = configRepository.getConfig(appWidgetId)

        WidgetRefreshCoordinator.sync(context)
        WidgetRefreshScheduler.requestImmediate(context)
        WidgetUpdater.updateWidget(context, glanceId)

        if (config != null) {
            WidgetUpdater.updateWidgets(
                context,
                configRepository.getWidgetIdsForUri(config.fileUri)
                    .filterNot { it == appWidgetId },
            )
        }
    }
}
