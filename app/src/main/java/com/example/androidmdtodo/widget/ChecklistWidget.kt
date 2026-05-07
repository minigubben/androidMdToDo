package com.example.androidmdtodo.widget

import android.content.Context
import android.appwidget.AppWidgetManager
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.Button
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.androidmdtodo.R
import com.example.androidmdtodo.data.MarkdownChecklistParser
import com.example.androidmdtodo.data.MarkdownFileRepository
import com.example.androidmdtodo.data.TaskRef
import com.example.androidmdtodo.data.WidgetConfigRepository
import com.example.androidmdtodo.data.WidgetTask

class ChecklistWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(180.dp, 120.dp)))

    override suspend fun provideGlance(context: Context, id: androidx.glance.GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val configRepository = WidgetConfigRepository(context)
        val fileRepository = MarkdownFileRepository(context)
        val parser = MarkdownChecklistParser()

        val model = runCatching {
            val config = configRepository.getConfig(appWidgetId)
                ?: return@runCatching WidgetModel.Unconfigured(appWidgetId)
            val document = fileRepository.read(Uri.parse(config.fileUri))
            configRepository.clearError(appWidgetId)
            val openTasks = parser.parse(document)
                .filterNot { it.isChecked }
                .map {
                    WidgetTask(
                        text = it.displayText,
                        ref = TaskRef(
                            lineIndex = it.lineIndex,
                            normalizedText = it.normalizedText,
                            occurrenceIndex = it.occurrenceIndex,
                        ),
                    )
                }
            WidgetModel.Ready(
                appWidgetId = appWidgetId,
                title = config.displayName,
                fileUri = config.fileUri,
                tasks = openTasks,
            )
        }.getOrElse { exception ->
            val config = configRepository.getConfig(appWidgetId)
            WidgetModel.Error(
                appWidgetId = appWidgetId,
                title = config?.displayName ?: context.getString(R.string.widget_name),
                message = config?.lastError ?: exception.message ?: context.getString(R.string.error_unknown),
            )
        }

        provideContent {
            GlanceTheme {
                ChecklistWidgetContent(model = model)
            }
        }
    }

    override suspend fun onDelete(context: Context, glanceId: androidx.glance.GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        WidgetConfigRepository(context).deleteConfig(appWidgetId)
    }
}

@Composable
private fun ChecklistWidgetContent(model: WidgetModel) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(day = Color(0xFFF4F1E8), night = Color(0xFF152126)))
            .padding(12.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        when (model) {
            is WidgetModel.Unconfigured -> EmptyState(
                title = "Checklist",
                message = "Choose a markdown file to start.",
                buttonLabel = "Choose file",
                appWidgetId = model.appWidgetId,
            )

            is WidgetModel.Error -> EmptyState(
                title = model.title,
                message = model.message,
                buttonLabel = "Choose file",
                appWidgetId = model.appWidgetId,
            )

            is WidgetModel.Ready -> ReadyState(model)
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    message: String,
    buttonLabel: String,
    appWidgetId: Int,
) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                color = ColorProvider(day = Color(0xFF1D2A30), night = Color(0xFFF2EFE6)),
            ),
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            text = message,
            style = TextStyle(
                color = ColorProvider(day = Color(0xFF4A5C63), night = Color(0xFFB4C5CB)),
            ),
        )
        Spacer(modifier = GlanceModifier.height(12.dp))
        Button(
            text = buttonLabel,
            onClick = actionStartActivity<ChecklistWidgetConfigActivity>(
                parameters = actionParametersOf(
                    ConfigActionKeys.APP_WIDGET_ID to appWidgetId,
                ),
            ),
        )
    }
}

@Composable
private fun ReadyState(model: WidgetModel.Ready) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Text(
            text = model.title,
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                color = ColorProvider(day = Color(0xFF1D2A30), night = Color(0xFFF2EFE6)),
            ),
            maxLines = 1,
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Button(
            text = "Refresh",
            onClick = actionRunCallback<ChecklistRefreshActionCallback>(),
        )
        Spacer(modifier = GlanceModifier.height(8.dp))

        if (model.tasks.isEmpty()) {
            Text(
                text = "No open tasks.",
                style = TextStyle(
                    color = ColorProvider(day = Color(0xFF4A5C63), night = Color(0xFFB4C5CB)),
                ),
            )
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxWidth()) {
                items(
                    items = model.tasks,
                    itemId = { task -> (task.ref.lineIndex.toLong() shl 32) + task.ref.occurrenceIndex.toLong() },
                ) { task ->
                    TaskRow(task)
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: WidgetTask) {
    val action = actionRunCallback<ChecklistToggleActionCallback>(
        parameters = actionParametersOf(
            TaskActionKeys.LINE_INDEX to task.ref.lineIndex,
            TaskActionKeys.NORMALIZED_TEXT to task.ref.normalizedText,
            TaskActionKeys.OCCURRENCE_INDEX to task.ref.occurrenceIndex,
        ),
    )

    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Button(
            text = "☐ ${task.text}",
            onClick = action,
        )
    }
}

sealed interface WidgetModel {
    data class Unconfigured(val appWidgetId: Int) : WidgetModel

    data class Ready(
        val appWidgetId: Int,
        val title: String,
        val fileUri: String,
        val tasks: List<WidgetTask>,
    ) : WidgetModel

    data class Error(
        val appWidgetId: Int,
        val title: String,
        val message: String,
    ) : WidgetModel
}

object TaskActionKeys {
    val LINE_INDEX = ActionParameters.Key<Int>("lineIndex")
    val NORMALIZED_TEXT = ActionParameters.Key<String>("normalizedText")
    val OCCURRENCE_INDEX = ActionParameters.Key<Int>("occurrenceIndex")
}

object ConfigActionKeys {
    const val EXTRA_APP_WIDGET_ID = "appWidgetId"
    val APP_WIDGET_ID = ActionParameters.Key<Int>(EXTRA_APP_WIDGET_ID)
}
