package com.example.androidmdtodo.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.androidmdtodo.R
import com.example.androidmdtodo.data.MarkdownFileRepository
import com.example.androidmdtodo.data.WidgetConfig
import com.example.androidmdtodo.data.WidgetConfigRepository
import com.example.androidmdtodo.work.WidgetRefreshWorker
import kotlinx.coroutines.launch

class ChecklistWidgetConfigActivity : ComponentActivity() {
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var hadExistingConfig: Boolean = false

    private val openDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                finishForCancel()
                return@registerForActivityResult
            }

            val uri = result.data?.data
            if (uri == null) {
                finishForCancel()
                return@registerForActivityResult
            }

            persistSelection(uri, result.data?.flags ?: 0)
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
        findViewById<Button>(R.id.pickFileButton).setOnClickListener { launchPicker() }

        lifecycleScope.launch {
            hadExistingConfig = WidgetConfigRepository(this@ChecklistWidgetConfigActivity)
                .getConfig(appWidgetId) != null
            if (savedInstanceState == null) {
                launchPicker()
            }
        }
    }

    private fun launchPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("text/markdown", "text/plain", "text/*", "application/octet-stream"),
            )
        }
        openDocumentLauncher.launch(intent)
    }

    private fun persistSelection(uri: Uri, flags: Int) {
        val takeFlags = flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                if (takeFlags == 0) {
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                } else {
                    takeFlags
                },
            )
        }

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
            WidgetRefreshWorker.schedule(this@ChecklistWidgetConfigActivity)
            WidgetUpdater.updateWidget(this@ChecklistWidgetConfigActivity, appWidgetId)

            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(Activity.RESULT_OK, resultValue)
            finish()
        }
    }

    private fun finishForCancel() {
        if (hadExistingConfig) {
            finish()
        } else {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    companion object {
        fun intent(context: Context, appWidgetId: Int): Intent {
            return Intent(context, ChecklistWidgetConfigActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
        }
    }
}
