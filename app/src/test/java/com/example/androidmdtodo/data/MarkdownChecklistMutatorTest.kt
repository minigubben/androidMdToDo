package com.example.androidmdtodo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownChecklistMutatorTest {
    private val mutator = MarkdownChecklistMutator()

    @Test
    fun updatesOnlyTheTargetLine() {
        val document = """
            - [ ] Repeat
            - [ ] Repeat
        """.trimIndent()

        val result = mutator.checkTask(
            document = document,
            ref = TaskRef(
                lineIndex = 1,
                normalizedText = "Repeat",
                occurrenceIndex = 1,
            ),
        )

        assertTrue(result is MutationResult.Updated)
        val updated = result as MutationResult.Updated
        assertEquals(
            """
                - [ ] Repeat
                - [x] Repeat
            """.trimIndent(),
            updated.document,
        )
    }

    @Test
    fun returnsStaleWhenTaskIsGone() {
        val result = mutator.checkTask(
            document = "- [x] Done already",
            ref = TaskRef(
                lineIndex = 0,
                normalizedText = "Done already",
                occurrenceIndex = 0,
            ),
        )

        assertTrue(result is MutationResult.Stale)
    }
}
