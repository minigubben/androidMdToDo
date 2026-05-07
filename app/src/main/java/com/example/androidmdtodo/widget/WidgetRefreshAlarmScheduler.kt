package com.example.androidmdtodo.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.example.androidmdtodo.data.WidgetConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object WidgetRefreshAlarmScheduler {
    private const val refreshIntervalMillis = 2 * 60 * 1000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun sync(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            val hasConfiguredWidgets = WidgetConfigRepository(appContext).getAllConfigs().isNotEmpty()
            val alarmManager = appContext.getSystemService(AlarmManager::class.java)
            val pendingIntent = buildPendingIntent(appContext)

            if (hasConfiguredWidgets) {
                alarmManager.setInexactRepeating(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + refreshIntervalMillis,
                    refreshIntervalMillis,
                    pendingIntent,
                )
            } else {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WidgetRefreshAlarmReceiver::class.java).apply {
            action = WidgetRefreshAlarmReceiver.ACTION_REFRESH_WIDGETS
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
