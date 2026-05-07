package com.example.androidmdtodo.data

class MarkdownChecklistParser {
    private val taskRegex = Regex("""^(\s*)([-*+])\s+\[( |x|X)]\s*(.*)$""")

    fun parse(document: String): List<ParsedTask> {
        if (document.isEmpty()) return emptyList()

        val lines = splitLines(document)
        val occurrences = mutableMapOf<String, Int>()
        val parsedTasks = mutableListOf<ParsedTask>()

        lines.forEachIndexed { index, rawLine ->
            val line = rawLine.removeSuffix("\r")
            val match = taskRegex.matchEntire(line) ?: return@forEachIndexed

            val normalizedText = normalizeTaskText(match.groupValues[4])
            val occurrenceIndex = occurrences.getOrDefault(normalizedText, 0)
            occurrences[normalizedText] = occurrenceIndex + 1

            parsedTasks += ParsedTask(
                lineIndex = index,
                normalizedText = normalizedText,
                originalLine = line,
                isChecked = match.groupValues[3] != " ",
                displayText = match.groupValues[4].trim(),
                bulletMarker = match.groupValues[2],
                indentation = match.groupValues[1],
                occurrenceIndex = occurrenceIndex,
            )
        }

        return parsedTasks
    }

    private fun normalizeTaskText(text: String): String {
        return text.trim().replace(Regex("""\s+"""), " ")
    }

    private fun splitLines(document: String): List<String> {
        val normalized = document.split('\n')
        return if (document.endsWith("\n")) normalized.dropLast(1) else normalized
    }
}
