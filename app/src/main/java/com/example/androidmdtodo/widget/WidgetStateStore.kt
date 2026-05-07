package com.example.androidmdtodo.widget

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.state.updateAppWidgetState
import com.example.androidmdtodo.R
import com.example.androidmdtodo.data.MarkdownFileRepository
import com.example.androidmdtodo.data.WidgetConfigRepository

internal object WidgetStateKeys {
    val mode = stringPreferencesKey("widget.mode")
    val title = stringPreferencesKey("widget.title")
    val message = stringPreferencesKey("widget.message")
    val document = stringPreferencesKey("widget.document")

    const val modeUnconfigured = "unconfigured"
    const val modeReady = "ready"
    const val modeError = "error"
}

internal object WidgetStateStore {
    suspend fun sync(
        context: Context,
        glanceId: GlanceId,
        appWidgetId: Int,
    ) {
        val appContext = context.applicationContext
        val configRepository = WidgetConfigRepository(appContext)
        val fileRepository = MarkdownFileRepository(appContext)

        updateAppWidgetState(appContext, glanceId) { state ->
            val config = configRepository.getConfig(appWidgetId)
            if (config == null) {
                state.setUnconfigured()
                return@updateAppWidgetState
            }

            runCatching {
                val document = fileRepository.read(Uri.parse(config.fileUri))
                configRepository.clearError(appWidgetId)
                state.setReady(config.displayName, document)
            }.getOrElse { exception ->
                state.setError(
                    title = config.displayName,
                    message = config.lastError
                        ?: exception.message
                        ?: appContext.getString(R.string.error_unknown),
                )
            }
        }
    }

    private fun MutablePreferences.setUnconfigured() {
        this[WidgetStateKeys.mode] = WidgetStateKeys.modeUnconfigured
        remove(WidgetStateKeys.title)
        remove(WidgetStateKeys.message)
        remove(WidgetStateKeys.document)
    }

    private fun MutablePreferences.setReady(title: String, document: String) {
        this[WidgetStateKeys.mode] = WidgetStateKeys.modeReady
        this[WidgetStateKeys.title] = title
        this[WidgetStateKeys.document] = document
        remove(WidgetStateKeys.message)
    }

    private fun MutablePreferences.setError(title: String, message: String) {
        this[WidgetStateKeys.mode] = WidgetStateKeys.modeError
        this[WidgetStateKeys.title] = title
        this[WidgetStateKeys.message] = message
        remove(WidgetStateKeys.document)
    }
}
