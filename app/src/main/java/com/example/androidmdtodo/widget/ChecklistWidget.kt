package com.example.androidmdtodo.widget

import android.content.Context
import android.appwidget.AppWidgetManager
import android.net.Uri
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.glance.Button
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.androidmdtodo.R
import com.example.androidmdtodo.data.ParsedBlankLine
import com.example.androidmdtodo.data.ParsedHeader
import com.example.androidmdtodo.data.ParsedLine
import com.example.androidmdtodo.data.ParsedListItem
import com.example.androidmdtodo.data.ParsedParagraph
import com.example.androidmdtodo.data.MarkdownChecklistParser
import com.example.androidmdtodo.data.MarkdownFileRepository
import com.example.androidmdtodo.data.ParsedTask
import com.example.androidmdtodo.data.TaskRef
import com.example.androidmdtodo.data.WidgetConfigRepository

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
            val documentRevision = document.hashCode().toLong()
            configRepository.clearError(appWidgetId)
            val items = parser.parseLines(document).mapNotNull { line ->
                toWidgetItem(line, documentRevision)
            }
            WidgetModel.Ready(
                appWidgetId = appWidgetId,
                title = config.displayName,
                items = items,
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
        WidgetRefreshCoordinator.sync(context)
    }
}

private const val maxRenderedItems = 12

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
                showRefresh = true,
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
    showRefresh: Boolean = false,
) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        if (showRefresh) {
            Box(modifier = GlanceModifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                RefreshIcon()
            }
            Spacer(modifier = GlanceModifier.height(4.dp))
        }
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
    val visibleItems = model.items.take(maxRenderedItems)
    val hiddenItemCount = (model.items.size - visibleItems.size).coerceAtLeast(0)

    Column(modifier = GlanceModifier.fillMaxSize()) {
        Box(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = model.title,
                modifier = GlanceModifier.padding(end = 24.dp),
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(day = Color(0xFF1D2A30), night = Color(0xFFF2EFE6)),
                ),
                maxLines = 1,
            )
            Box(modifier = GlanceModifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                RefreshIcon()
            }
        }
        Spacer(modifier = GlanceModifier.height(8.dp))

        if (model.items.isEmpty()) {
            Text(
                text = "Nothing to show.",
                style = TextStyle(
                    color = ColorProvider(day = Color(0xFF4A5C63), night = Color(0xFFB4C5CB)),
                ),
            )
        } else {
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                visibleItems.forEach { item ->
                    DocumentRow(item)
                }
                if (hiddenItemCount > 0) {
                    OverflowRow(hiddenItemCount)
                }
            }
        }
    }
}

@Composable
private fun RefreshIcon() {
    Image(
        provider = ImageProvider(R.drawable.ic_widget_refresh),
        contentDescription = "Refresh",
        modifier = GlanceModifier
            .size(18.dp)
            .clickable(actionRunCallback<ChecklistRefreshActionCallback>()),
    )
}

@Composable
private fun DocumentRow(item: WidgetItem) {
    when (item) {
        is WidgetItem.Task -> TaskRow(item)
        is WidgetItem.Header -> HeaderRow(item)
        is WidgetItem.ListItem -> ListItemRow(item)
        is WidgetItem.Paragraph -> ParagraphRow(item)
        is WidgetItem.Blank -> Spacer(modifier = GlanceModifier.height(6.dp))
    }
}

@Composable
private fun OverflowRow(hiddenItemCount: Int) {
    Text(
        text = "+$hiddenItemCount more",
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        style = TextStyle(
            color = ColorProvider(day = Color(0xFF6A7B81), night = Color(0xFF90A4AB)),
        ),
    )
}

private sealed interface WidgetModel {
    data class Unconfigured(val appWidgetId: Int) : WidgetModel

    data class Ready(
        val appWidgetId: Int,
        val title: String,
        val items: List<WidgetItem>,
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

private sealed interface WidgetItem {
    val stableId: Long

    data class Task(
        override val stableId: Long,
        val text: String,
        val ref: TaskRef,
        val isChecked: Boolean,
        val indentLevel: Int,
    ) : WidgetItem

    data class Header(
        override val stableId: Long,
        val text: String,
        val level: Int,
    ) : WidgetItem

    data class ListItem(
        override val stableId: Long,
        val text: String,
        val marker: String,
        val indentLevel: Int,
    ) : WidgetItem

    data class Paragraph(
        override val stableId: Long,
        val text: String,
        val indentLevel: Int,
    ) : WidgetItem

    data class Blank(
        override val stableId: Long,
    ) : WidgetItem
}

private fun toWidgetItem(line: ParsedLine, documentRevision: Long): WidgetItem? {
    val stableId = (documentRevision shl 32) xor line.lineIndex.toLong()

    return when (line) {
        is ParsedTask -> {
            if (line.isChecked) {
                null
            } else {
                WidgetItem.Task(
                    stableId = stableId,
                    text = line.displayText,
                    ref = TaskRef(
                        lineIndex = line.lineIndex,
                        normalizedText = line.normalizedText,
                        occurrenceIndex = line.occurrenceIndex,
                    ),
                    isChecked = line.isChecked,
                    indentLevel = indentLevel(line.indentation),
                )
            }
        }

        is ParsedHeader -> WidgetItem.Header(
            stableId = stableId,
            text = line.text,
            level = line.level,
        )

        is ParsedListItem -> WidgetItem.ListItem(
            stableId = stableId,
            text = line.text,
            marker = line.marker,
            indentLevel = indentLevel(line.indentation),
        )

        is ParsedParagraph -> WidgetItem.Paragraph(
            stableId = stableId,
            text = line.text,
            indentLevel = indentLevel(line.indentation),
        )

        is ParsedBlankLine -> WidgetItem.Blank(
            stableId = stableId,
        )
    }
}

private fun indentLevel(indentation: String): Int {
    return (indentation.length / 2).coerceAtMost(6)
}

private fun indentPadding(indentLevel: Int): Int {
    return indentLevel * 10
}

@Composable
private fun TaskRow(task: WidgetItem.Task) {
    val text = if (task.isChecked) "☑ ${task.text}" else "☐ ${task.text}"
    val modifier = GlanceModifier
        .fillMaxWidth()
        .padding(start = indentPadding(task.indentLevel).dp, top = 4.dp, bottom = 4.dp)

    val interactiveModifier = if (task.isChecked) {
        modifier
    } else {
        modifier.clickable(
            actionRunCallback<ChecklistToggleActionCallback>(
                parameters = actionParametersOf(
                    TaskActionKeys.LINE_INDEX to task.ref.lineIndex,
                    TaskActionKeys.NORMALIZED_TEXT to task.ref.normalizedText,
                    TaskActionKeys.OCCURRENCE_INDEX to task.ref.occurrenceIndex,
                ),
            ),
        )
    }

    Text(
        text = text,
        modifier = interactiveModifier,
        style = TextStyle(
            color = ColorProvider(
                day = if (task.isChecked) Color(0xFF7B8A90) else Color(0xFF24333A),
                night = if (task.isChecked) Color(0xFF7E9097) else Color(0xFFE5E2DA),
            ),
        ),
    )
}

@Composable
private fun HeaderRow(header: WidgetItem.Header) {
    val fontWeight = if (header.level <= 2) FontWeight.Bold else FontWeight.Medium
    Text(
        text = header.text,
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        style = TextStyle(
            fontWeight = fontWeight,
            color = ColorProvider(day = Color(0xFF1D2A30), night = Color(0xFFF2EFE6)),
        ),
        maxLines = if (header.level == 1) 2 else 1,
    )
}

@Composable
private fun ListItemRow(item: WidgetItem.ListItem) {
    Text(
        text = "${item.marker} ${item.text}",
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(start = indentPadding(item.indentLevel).dp, top = 3.dp, bottom = 3.dp),
        style = TextStyle(
            color = ColorProvider(day = Color(0xFF32434A), night = Color(0xFFD6DAD2)),
        ),
    )
}

@Composable
private fun ParagraphRow(item: WidgetItem.Paragraph) {
    Text(
        text = item.text,
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(start = indentPadding(item.indentLevel).dp, top = 2.dp, bottom = 2.dp),
        style = TextStyle(
            color = ColorProvider(day = Color(0xFF4A5C63), night = Color(0xFFB4C5CB)),
        ),
    )
}
