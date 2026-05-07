package com.example.androidmdtodo

import android.app.Application
import com.example.androidmdtodo.widget.WidgetRefreshCoordinator

class ChecklistApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WidgetRefreshCoordinator.sync(this)
    }
}
