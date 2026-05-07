package com.example.androidmdtodo.data

data class WidgetConfig(
    val appWidgetId: Int,
    val fileUri: String,
    val displayName: String,
    val lastError: String? = null,
)
