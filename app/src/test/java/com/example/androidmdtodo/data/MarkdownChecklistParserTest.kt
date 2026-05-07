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

    @Test
    fun parsesHeadersParagraphsAndPlainLists() {
        val document = """
            # Today

            Intro paragraph
            - plain bullet
            1. numbered item
            ## Later
            - [ ] Check me
        """.trimIndent()

        val lines = parser.parseLines(document)

        assertTrue(lines[0] is ParsedHeader)
        assertTrue(lines[1] is ParsedBlankLine)
        assertTrue(lines[2] is ParsedParagraph)
        assertTrue(lines[3] is ParsedListItem)
        assertTrue(lines[4] is ParsedListItem)
        assertTrue(lines[5] is ParsedHeader)
        assertTrue(lines[6] is ParsedTask)
        assertEquals("plain bullet", (lines[3] as ParsedListItem).text)
        assertEquals("1.", (lines[4] as ParsedListItem).marker)
    }
}
