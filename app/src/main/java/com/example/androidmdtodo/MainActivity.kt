package com.example.androidmdtodo

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.example.androidmdtodo.widget.ChecklistWidgetReceiver
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusView = findViewById<TextView>(R.id.statusText)
        val pinButton = findViewById<Button>(R.id.pinWidgetButton)

        pinButton.setOnClickListener {
            lifecycleScope.launch {
                val pinned = GlanceAppWidgetManager(this@MainActivity)
                    .requestPinGlanceAppWidget(ChecklistWidgetReceiver::class.java)
                statusView.text = if (pinned) {
                    getString(R.string.pin_widget_requested)
                } else {
                    getString(R.string.pin_widget_unavailable)
                }
            }
        }
    }
}
