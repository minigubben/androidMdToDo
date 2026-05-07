package com.example.androidmdtodo.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.documentfile.provider.DocumentFile
import com.example.androidmdtodo.R
import com.example.androidmdtodo.data.MarkdownFileRepository
import com.example.androidmdtodo.data.WidgetConfig
import com.example.androidmdtodo.data.WidgetConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChecklistWidgetConfigActivity : ComponentActivity() {
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var treeUri: Uri? = null
    private val navigationStack = mutableListOf<DirectoryNode>()
    private val entries = mutableListOf<BrowserEntry>()

    private lateinit var pickFolderButton: Button
    private lateinit var upButton: Button
    private lateinit var currentFolderValue: TextView
    private lateinit var emptyStateText: TextView
    private lateinit var fileListView: ListView
    private lateinit var fileListAdapter: ArrayAdapter<String>

    private val openDocumentTreeLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) {
                return@registerForActivityResult
            }

            persistTreeSelection(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_config)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            intent?.getIntExtra(
                ConfigActionKeys.EXTRA_APP_WIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            ) ?: AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setResult(Activity.RESULT_CANCELED)
        findViewById<TextView>(R.id.configTitle).text = getString(R.string.widget_config_title)

        pickFolderButton = findViewById<Button>(R.id.pickFolderButton).apply {
            setOnClickListener { launchFolderPicker() }
        }
        upButton = findViewById<Button>(R.id.upButton).apply {
            setOnClickListener { navigateUp() }
        }
        currentFolderValue = findViewById(R.id.currentFolderValue)
        emptyStateText = findViewById(R.id.emptyStateText)
        fileListView = findViewById<ListView>(R.id.fileListView).apply {
            fileListAdapter = ArrayAdapter(
                this@ChecklistWidgetConfigActivity,
                R.layout.file_browser_item,
                mutableListOf(),
            )
            adapter = fileListAdapter
            setOnItemClickListener { _, _, position, _ ->
                val entry = entries.getOrNull(position) ?: return@setOnItemClickListener
                if (entry.isDirectory) {
                    openDirectory(entry.document)
                } else {
                    persistSelection(entry.document.uri)
                }
            }
        }

        if (restoreState(savedInstanceState)) {
            renderCurrentDirectory()
            return
        }

        lifecycleScope.launch {
            val configRepository = WidgetConfigRepository(this@ChecklistWidgetConfigActivity)
            val lastTreeUri = configRepository.getLastTreeUri()?.let(Uri::parse)
            if (lastTreeUri != null && hasPersistedAccess(lastTreeUri)) {
                openRootDirectory(lastTreeUri, rememberSelection = false)
            } else if (savedInstanceState == null) {
                launchFolderPicker()
            }
        }
    }

    private fun launchFolderPicker() {
        openDocumentTreeLauncher.launch(treeUri)
    }

    private fun persistTreeSelection(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        openRootDirectory(uri, rememberSelection = true)
    }

    private fun openRootDirectory(uri: Uri, rememberSelection: Boolean) {
        val root = DocumentFile.fromTreeUri(this, uri)
        if (root == null || !root.isDirectory) {
            showDirectoryError(getString(R.string.widget_config_failed_directory))
            return
        }

        val rootDocumentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?: DocumentsContract.getDocumentId(root.uri)
        val scopedRootUri = DocumentsContract.buildDocumentUriUsingTree(uri, rootDocumentId)

        treeUri = uri
        navigationStack.clear()
        navigationStack += DirectoryNode(
            uri = scopedRootUri,
            label = root.name ?: uri.lastPathSegment ?: getString(R.string.widget_name),
        )

        if (rememberSelection) {
            lifecycleScope.launch {
                WidgetConfigRepository(this@ChecklistWidgetConfigActivity).setLastTreeUri(uri.toString())
            }
        }

        renderCurrentDirectory()
    }

    private fun openDirectory(directory: DocumentFile) {
        val rootUri = treeUri ?: return
        val documentId = runCatching { DocumentsContract.getDocumentId(directory.uri) }.getOrNull() ?: return
        val scopedUri = DocumentsContract.buildDocumentUriUsingTree(rootUri, documentId)
        navigationStack += DirectoryNode(
            uri = scopedUri,
            label = directory.name ?: directory.uri.lastPathSegment ?: getString(R.string.widget_name),
        )
        renderCurrentDirectory()
    }

    private fun navigateUp() {
        if (navigationStack.size <= 1) {
            return
        }
        navigationStack.removeLast()
        renderCurrentDirectory()
    }

    private fun renderCurrentDirectory() {
        val currentNode = navigationStack.lastOrNull()
        if (currentNode == null) {
            currentFolderValue.text = getString(R.string.widget_config_no_folder)
            entries.clear()
            fileListAdapter.clear()
            upButton.isEnabled = false
            emptyStateText.visibility = View.VISIBLE
            fileListView.visibility = View.GONE
            return
        }

        currentFolderValue.text = navigationStack.joinToString(" / ") { it.label }
        upButton.isEnabled = navigationStack.size > 1
        emptyStateText.visibility = View.VISIBLE
        emptyStateText.text = getString(R.string.widget_config_loading)
        fileListView.visibility = View.GONE

        lifecycleScope.launch {
            val listedEntries = withContext(Dispatchers.IO) {
                loadEntries(currentNode.uri)
            }

            if (navigationStack.lastOrNull()?.uri != currentNode.uri) {
                return@launch
            }

            if (listedEntries == null) {
                showDirectoryError(getString(R.string.widget_config_failed_directory))
                return@launch
            }

            entries.clear()
            entries += listedEntries
            fileListAdapter.clear()
            fileListAdapter.addAll(listedEntries.map(::formatEntryLabel))
            fileListAdapter.notifyDataSetChanged()

            if (listedEntries.isEmpty()) {
                emptyStateText.visibility = View.VISIBLE
                emptyStateText.text = getString(R.string.widget_config_empty_directory)
                fileListView.visibility = View.GONE
            } else {
                emptyStateText.visibility = View.GONE
                fileListView.visibility = View.VISIBLE
            }
        }
    }

    private fun loadEntries(directoryUri: Uri): List<BrowserEntry>? {
        val directory = DocumentFile.fromTreeUri(this, directoryUri) ?: return null
        if (!directory.isDirectory) {
            return null
        }

        return directory.listFiles()
            .filter { file ->
                file.isDirectory || (file.isFile && isMarkdownFile(file.name))
            }
            .map { file ->
                BrowserEntry(
                    document = file,
                    label = file.name ?: file.uri.lastPathSegment ?: getString(R.string.widget_name),
                    isDirectory = file.isDirectory,
                )
            }
            .sortedWith(
                compareBy<BrowserEntry>({ !it.isDirectory }, { it.label.lowercase() }),
            )
    }

    private fun formatEntryLabel(entry: BrowserEntry): String {
        val prefix = if (entry.isDirectory) {
            getString(R.string.widget_config_folder_prefix)
        } else {
            getString(R.string.widget_config_file_prefix)
        }
        return "$prefix: ${entry.label}"
    }

    private fun showDirectoryError(message: String) {
        entries.clear()
        fileListAdapter.clear()
        if (navigationStack.isEmpty()) {
            currentFolderValue.text = getString(R.string.widget_config_no_folder)
        }
        emptyStateText.visibility = View.VISIBLE
        emptyStateText.text = message
        fileListView.visibility = View.GONE
        upButton.isEnabled = navigationStack.size > 1
    }

    private fun isMarkdownFile(name: String?): Boolean {
        val normalizedName = name?.lowercase() ?: return false
        return normalizedName.endsWith(".md") || normalizedName.endsWith(".markdown")
    }

    private fun hasPersistedAccess(uri: Uri): Boolean {
        return contentResolver.persistedUriPermissions.any { permission ->
            permission.isReadPermission && permission.uri == uri
        }
    }

    private fun restoreState(savedInstanceState: Bundle?): Boolean {
        savedInstanceState ?: return false

        val restoredTreeUri = savedInstanceState.getString(STATE_TREE_URI)?.let(Uri::parse) ?: return false
        if (!hasPersistedAccess(restoredTreeUri)) {
            return false
        }

        val restoredUris = savedInstanceState.getStringArrayList(STATE_DIRECTORY_URIS).orEmpty()
        val restoredLabels = savedInstanceState.getStringArrayList(STATE_DIRECTORY_LABELS).orEmpty()
        if (restoredUris.isEmpty()) {
            return false
        }

        treeUri = restoredTreeUri
        navigationStack.clear()
        restoredUris.forEachIndexed { index, uriString ->
            navigationStack += DirectoryNode(
                uri = Uri.parse(uriString),
                label = restoredLabels.getOrElse(index) { Uri.parse(uriString).lastPathSegment ?: getString(R.string.widget_name) },
            )
        }
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_TREE_URI, treeUri?.toString())
        outState.putStringArrayList(
            STATE_DIRECTORY_URIS,
            ArrayList(navigationStack.map { it.uri.toString() }),
        )
        outState.putStringArrayList(
            STATE_DIRECTORY_LABELS,
            ArrayList(navigationStack.map { it.label }),
        )
    }

    private fun persistSelection(uri: Uri) {
        lifecycleScope.launch {
            val fileRepository = MarkdownFileRepository(this@ChecklistWidgetConfigActivity)
            val configRepository = WidgetConfigRepository(this@ChecklistWidgetConfigActivity)
            val displayName = fileRepository.resolveDisplayName(uri)
            configRepository.saveConfig(
                WidgetConfig(
                    appWidgetId = appWidgetId,
                    fileUri = uri.toString(),
                    displayName = displayName,
                    lastError = null,
                ),
            )
            WidgetFileObserverRegistry.sync(this@ChecklistWidgetConfigActivity)
            WidgetUpdater.updateAll(this@ChecklistWidgetConfigActivity)

            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(Activity.RESULT_OK, resultValue)
            finish()
        }
    }

    companion object {
        private const val STATE_TREE_URI = "state.treeUri"
        private const val STATE_DIRECTORY_URIS = "state.directoryUris"
        private const val STATE_DIRECTORY_LABELS = "state.directoryLabels"

        fun intent(context: Context, appWidgetId: Int): Intent {
            return Intent(context, ChecklistWidgetConfigActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
        }
    }

    private data class DirectoryNode(
        val uri: Uri,
        val label: String,
    )

    private data class BrowserEntry(
        val document: DocumentFile,
        val label: String,
        val isDirectory: Boolean,
    )
}
