package com.example.androidmdtodo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownChecklistParserTest {
    private val parser = MarkdownChecklistParser()

    @Test
    fun parsesOpenAndCheckedTasks() {
        val document = """
            - [ ] Buy milk
            * [x] Ship build
            + [ ]  Call mom
            Not a task
        """.trimIndent()

        val tasks = parser.parse(document)

        assertEquals(3, tasks.size)
        assertFalse(tasks[0].isChecked)
        assertTrue(tasks[1].isChecked)
        assertEquals("Call mom", tasks[2].displayText)
    }

    @Test
    fun preservesOccurrenceIndexForDuplicateText() {
        val document = """
            - [ ] Repeat
            - [ ] Repeat
        """.trimIndent()

        val tasks = parser.parse(document)

        assertEquals(0, tasks[0].occurrenceIndex)
        assertEquals(1, tasks[1].occurrenceIndex)
    }
}
