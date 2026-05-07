package com.example.androidmdtodo.data

class MarkdownChecklistParser {
    private val taskRegex = Regex("""^(\s*)([-*+])\s+\[( |x|X)]\s*(.*)$""")
    private val headerRegex = Regex("""^\s{0,3}(#{1,6})\s+(.*?)\s*$""")
    private val unorderedListRegex = Regex("""^(\s*)([-*+])\s+(.*)$""")
    private val orderedListRegex = Regex("""^(\s*)(\d+)([.)])\s+(.*)$""")

    fun parse(document: String): List<ParsedTask> {
        return parseLines(document).filterIsInstance<ParsedTask>()
    }

    fun parseLines(document: String): List<ParsedLine> {
        if (document.isEmpty()) return emptyList()

        val lines = splitLines(document)
        val occurrences = mutableMapOf<String, Int>()
        val parsedLines = mutableListOf<ParsedLine>()

        lines.forEachIndexed { index, rawLine ->
            val line = rawLine.removeSuffix("\r")
            if (line.isBlank()) {
                parsedLines += ParsedBlankLine(lineIndex = index)
                return@forEachIndexed
            }

            val taskMatch = taskRegex.matchEntire(line)
            if (taskMatch != null) {
                val normalizedText = normalizeTaskText(taskMatch.groupValues[4])
                val occurrenceIndex = occurrences.getOrDefault(normalizedText, 0)
                occurrences[normalizedText] = occurrenceIndex + 1

                parsedLines += ParsedTask(
                    lineIndex = index,
                    normalizedText = normalizedText,
                    originalLine = line,
                    isChecked = taskMatch.groupValues[3] != " ",
                    displayText = taskMatch.groupValues[4].trim(),
                    bulletMarker = taskMatch.groupValues[2],
                    indentation = taskMatch.groupValues[1],
                    occurrenceIndex = occurrenceIndex,
                )
                return@forEachIndexed
            }

            val headerMatch = headerRegex.matchEntire(line)
            if (headerMatch != null) {
                parsedLines += ParsedHeader(
                    lineIndex = index,
                    level = headerMatch.groupValues[1].length,
                    text = headerMatch.groupValues[2].trim().trimEnd('#').trim(),
                )
                return@forEachIndexed
            }

            val orderedListMatch = orderedListRegex.matchEntire(line)
            if (orderedListMatch != null) {
                parsedLines += ParsedListItem(
                    lineIndex = index,
                    text = orderedListMatch.groupValues[4].trim(),
                    marker = orderedListMatch.groupValues[2] + orderedListMatch.groupValues[3],
                    indentation = orderedListMatch.groupValues[1],
                )
                return@forEachIndexed
            }

            val unorderedListMatch = unorderedListRegex.matchEntire(line)
            if (unorderedListMatch != null) {
                parsedLines += ParsedListItem(
                    lineIndex = index,
                    text = unorderedListMatch.groupValues[3].trim(),
                    marker = unorderedListMatch.groupValues[2],
                    indentation = unorderedListMatch.groupValues[1],
                )
                return@forEachIndexed
            }

            parsedLines += ParsedParagraph(
                lineIndex = index,
                text = line.trim(),
                indentation = line.takeWhile { it.isWhitespace() },
            )
        }

        return parsedLines
    }

    private fun normalizeTaskText(text: String): String {
        return text.trim().replace(Regex("""\s+"""), " ")
    }

    private fun splitLines(document: String): List<String> {
        val normalized = document.split('\n')
        return if (document.endsWith("\n")) normalized.dropLast(1) else normalized
    }
}
