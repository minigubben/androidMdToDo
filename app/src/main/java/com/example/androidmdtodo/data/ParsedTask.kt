package com.example.androidmdtodo.data

data class ParsedTask(
    val lineIndex: Int,
    val normalizedText: String,
    val originalLine: String,
    val isChecked: Boolean,
    val displayText: String,
    val bulletMarker: String,
    val indentation: String,
    val occurrenceIndex: Int,
)

data class TaskRef(
    val lineIndex: Int,
    val normalizedText: String,
    val occurrenceIndex: Int,
)

data class WidgetTask(
    val text: String,
    val ref: TaskRef,
)
