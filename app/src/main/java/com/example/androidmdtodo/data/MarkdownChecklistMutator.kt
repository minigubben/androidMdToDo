package com.example.androidmdtodo.data

class MarkdownChecklistMutator(
    private val parser: MarkdownChecklistParser = MarkdownChecklistParser(),
) {
    private val uncheckedRegex = Regex("""^(\s*[-*+]\s+)\[( )](.*)$""")

    fun checkTask(document: String, ref: TaskRef): MutationResult {
        val tasks = parser.parse(document)
        val target = findTarget(tasks, ref) ?: return MutationResult.Stale

        val lineEnding = when {
            document.contains("\r\n") -> "\r\n"
            else -> "\n"
        }
        val hasTrailingNewline = document.endsWith("\n")
        val lines = parserLines(document).toMutableList()
        val rawLine = lines[target.lineIndex]
        val line = rawLine.removeSuffix("\r")
        val updatedLine = uncheckedRegex.replace(line, "$1[x]$3")
        if (updatedLine == line) {
            return MutationResult.Stale
        }

        lines[target.lineIndex] = if (rawLine.endsWith("\r")) "$updatedLine\r" else updatedLine
        val rewritten = buildString {
            append(lines.joinToString(separator = lineEnding))
            if (hasTrailingNewline) {
                append(lineEnding)
            }
        }
        return MutationResult.Updated(rewritten)
    }

    private fun findTarget(tasks: List<ParsedTask>, ref: TaskRef): ParsedTask? {
        val exact = tasks.firstOrNull {
            !it.isChecked && it.lineIndex == ref.lineIndex && it.normalizedText == ref.normalizedText
        }
        if (exact != null) {
            return exact
        }

        val candidates = tasks.filter { !it.isChecked && it.normalizedText == ref.normalizedText }
        return candidates.getOrNull(ref.occurrenceIndex)
    }

    private fun parserLines(document: String): List<String> {
        val split = document.split('\n')
        return if (document.endsWith("\n")) split.dropLast(1) else split
    }
}

sealed interface MutationResult {
    data class Updated(val document: String) : MutationResult
    data object Stale : MutationResult
}
