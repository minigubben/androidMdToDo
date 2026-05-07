package com.example.androidmdtodo.data

sealed interface ParsedLine {
    val lineIndex: Int
}

data class ParsedTask(
    override val lineIndex: Int,
    val normalizedText: String,
    val originalLine: String,
    val isChecked: Boolean,
    val displayText: String,
    val bulletMarker: String,
    val indentation: String,
    val occurrenceIndex: Int,
) : ParsedLine

data class ParsedHeader(
    override val lineIndex: Int,
    val level: Int,
    val text: String,
) : ParsedLine

data class ParsedListItem(
    override val lineIndex: Int,
    val text: String,
    val marker: String,
    val indentation: String,
) : ParsedLine

data class ParsedParagraph(
    override val lineIndex: Int,
    val text: String,
    val indentation: String,
) : ParsedLine

data class ParsedBlankLine(
    override val lineIndex: Int,
) : ParsedLine

data class TaskRef(
    val lineIndex: Int,
    val normalizedText: String,
    val occurrenceIndex: Int,
)
