package com.example.androidmdtodo

import android.app.Application
import com.example.androidmdtodo.work.WidgetRefreshWorker

class ChecklistApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WidgetRefreshWorker.schedule(this)
    }
}
