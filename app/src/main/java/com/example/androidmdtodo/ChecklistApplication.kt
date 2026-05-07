package com.example.androidmdtodo

import android.app.Application
import com.example.androidmdtodo.widget.WidgetFileObserverRegistry

class ChecklistApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WidgetFileObserverRegistry.sync(this)
    }
}
