package com.example.androidmdtodo.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

private val Context.widgetDataStore by preferencesDataStore(name = "widget_config")

class WidgetConfigRepository(private val context: Context) {
    suspend fun getConfig(appWidgetId: Int): WidgetConfig? {
        val preferences = readPreferences()
        return preferences.toWidgetConfig(appWidgetId)
    }

    suspend fun getLastTreeUri(): String? {
        return readPreferences()[lastTreeUriKey]
    }

    suspend fun getAllConfigs(): List<WidgetConfig> {
        val preferences = readPreferences()
        val widgetIds = preferences.asMap().keys
            .mapNotNull { it.name.substringAfter("widget.", "").substringBefore(".").toIntOrNull() }
            .distinct()
            .sorted()
        return widgetIds.mapNotNull { widgetId -> preferences.toWidgetConfig(widgetId) }
    }

    suspend fun getWidgetIdsForUri(fileUri: String): List<Int> {
        return getAllConfigs()
            .filter { it.fileUri == fileUri }
            .map { it.appWidgetId }
    }

    suspend fun saveConfig(config: WidgetConfig) {
        context.widgetDataStore.edit { prefs ->
            prefs[uriKey(config.appWidgetId)] = config.fileUri
            prefs[nameKey(config.appWidgetId)] = config.displayName
            if (config.lastError.isNullOrBlank()) {
                prefs.remove(errorKey(config.appWidgetId))
            } else {
                prefs[errorKey(config.appWidgetId)] = config.lastError
            }
        }
    }

    suspend fun setLastTreeUri(uri: String) {
        context.widgetDataStore.edit { prefs ->
            prefs[lastTreeUriKey] = uri
        }
    }

    suspend fun setError(appWidgetId: Int, message: String?) {
        context.widgetDataStore.edit { prefs ->
            if (message.isNullOrBlank()) {
                prefs.remove(errorKey(appWidgetId))
            } else {
                prefs[errorKey(appWidgetId)] = message
            }
        }
    }

    suspend fun clearError(appWidgetId: Int) = setError(appWidgetId, null)

    suspend fun deleteConfig(appWidgetId: Int) {
        context.widgetDataStore.edit { prefs ->
            removeKeys(prefs, appWidgetId)
        }
    }

    private suspend fun readPreferences(): Preferences {
        return context.widgetDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .first()
    }

    private fun Preferences.toWidgetConfig(appWidgetId: Int): WidgetConfig? {
        val fileUri = this[uriKey(appWidgetId)] ?: return null
        val displayName = this[nameKey(appWidgetId)] ?: return null
        return WidgetConfig(
            appWidgetId = appWidgetId,
            fileUri = fileUri,
            displayName = displayName,
            lastError = this[errorKey(appWidgetId)],
        )
    }

    private fun removeKeys(prefs: MutablePreferences, appWidgetId: Int) {
        prefs.remove(uriKey(appWidgetId))
        prefs.remove(nameKey(appWidgetId))
        prefs.remove(errorKey(appWidgetId))
    }

    private fun uriKey(appWidgetId: Int) = stringPreferencesKey("widget.$appWidgetId.uri")
    private fun nameKey(appWidgetId: Int) = stringPreferencesKey("widget.$appWidgetId.name")
    private fun errorKey(appWidgetId: Int) = stringPreferencesKey("widget.$appWidgetId.error")

    private val lastTreeUriKey = stringPreferencesKey("widget.lastTreeUri")
}
