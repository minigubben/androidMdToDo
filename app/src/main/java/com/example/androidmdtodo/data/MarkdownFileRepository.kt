package com.example.androidmdtodo.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException

class MarkdownFileRepository(private val context: Context) {
    fun read(uri: Uri): String {
        val resolver = context.contentResolver
        resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
            return reader.readText()
        }
        throw IOException("Unable to open markdown file for reading.")
    }

    fun write(uri: Uri, contents: String) {
        val resolver = context.contentResolver
        resolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
            writer.write(contents)
            writer.flush()
            return
        }
        throw IOException("Unable to open markdown file for writing.")
    }

    fun resolveDisplayName(uri: Uri): String {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index)
            }
        }
        return uri.lastPathSegment ?: "Checklist"
    }
}
