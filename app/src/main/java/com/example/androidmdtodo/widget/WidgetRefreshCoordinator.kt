package com.example.androidmdtodo.widget

import android.content.Context

object WidgetRefreshCoordinator {
    fun sync(context: Context) {
        WidgetFileObserverRegistry.sync(context)
        WidgetRefreshScheduler.sync(context)
    }
}
