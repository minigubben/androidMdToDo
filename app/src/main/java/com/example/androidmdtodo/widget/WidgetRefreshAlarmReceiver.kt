package com.example.androidmdtodo.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WidgetRefreshAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REFRESH_WIDGETS) {
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        scope.launch {
            try {
                WidgetRefreshCoordinator.sync(appContext)
                WidgetUpdater.updateAll(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGETS = "com.example.androidmdtodo.action.REFRESH_WIDGETS"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
