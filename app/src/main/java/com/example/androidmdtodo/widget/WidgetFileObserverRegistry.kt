package com.example.androidmdtodo.widget

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.androidmdtodo.data.WidgetConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object WidgetFileObserverRegistry {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val observerMutex = Mutex()
    private val observers = mutableMapOf<String, ContentObserver>()

    fun sync(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            val uris = WidgetConfigRepository(appContext)
                .getAllConfigs()
                .map { it.fileUri }
                .toSet()

            observerMutex.withLock {
                val resolver = appContext.contentResolver

                (observers.keys - uris).forEach { uriString ->
                    observers.remove(uriString)?.let(resolver::unregisterContentObserver)
                }

                (uris - observers.keys).forEach { uriString ->
                    val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return@forEach
                    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) {
                            handleChange(appContext, uriString)
                        }

                        override fun onChange(selfChange: Boolean, uri: Uri?) {
                            handleChange(appContext, uriString)
                        }
                    }

                    runCatching {
                        resolver.registerContentObserver(uri, false, observer)
                    }.onSuccess {
                        observers[uriString] = observer
                    }.onFailure { exception ->
                        Log.w("WidgetObserver", "Failed to observe $uriString", exception)
                    }
                }
            }
        }
    }

    private fun handleChange(context: Context, fileUri: String) {
        scope.launch {
            val widgetIds = WidgetConfigRepository(context).getWidgetIdsForUri(fileUri)
            WidgetUpdater.updateWidgets(context, widgetIds)
        }
    }
}
