package com.gios.brightmarket

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.gios.brightmarket.data.App
import com.gios.brightmarket.data.Index
import com.gios.brightmarket.data.Obtainium
import com.gios.brightmarket.data.Sort
import com.gios.brightmarket.install.Installer
import com.gios.brightmarket.ui.BrightMarketTheme
import com.gios.brightmarket.ui.DetailScreen
import com.gios.brightmarket.ui.ListScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var apps by mutableStateOf<List<App>>(emptyList())
    private var loading by mutableStateOf(true)
    private var error by mutableStateOf<String?>(null)
    private var sort by mutableStateOf(Sort.UPDATED)
    private var selected by mutableStateOf<App?>(null)
    private var progress by mutableStateOf<Installer.Progress?>(null)
    private var installed by mutableStateOf<Map<String, Long>>(emptyMap())

    /** Obtainium exports are picked with SAF -- no storage permission needed. */
    private val pickExport = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            }
            if (text == null) {
                toast("Couldn't read that file.")
                return@launch
            }
            val result = runCatching {
                Obtainium.match(Obtainium.parse(text), apps)
            }.getOrElse {
                toast("That doesn't look like an Obtainium export.")
                return@launch
            }
            // Say plainly what did NOT come across, rather than reporting a
            // cheerful count and quietly dropping the rest.
            val msg = buildString {
                append("${result.matched.size} of ${result.matched.size + result.unmatched.size} already in BrightMarket")
                if (result.unmatched.isNotEmpty()) {
                    append("; ${result.unmatched.size} not indexed yet")
                }
            }
            toast(msg)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BrightMarketTheme {
                val app = selected
                if (app == null) {
                    ListScreen(
                        apps = Index.sort(apps, sort),
                        sort = sort,
                        installed = installed,
                        loading = loading,
                        error = error,
                        onSort = { sort = it },
                        onOpen = { selected = it; progress = null },
                        onImport = { pickExport.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    )
                } else {
                    DetailScreen(
                        app = app,
                        installedVersionCode = installed[app.pkg],
                        progress = progress,
                        onInstall = { install(app) },
                        onBack = { selected = null },
                    )
                }
            }
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        // Re-read installed versions on every resume: coming back from the
        // system installer dialog is exactly when they change.
        refreshInstalled()
    }

    private fun refresh() {
        lifecycleScope.launch {
            loading = true
            error = null
            runCatching { withContext(Dispatchers.IO) { Index.fetch(BuildConfig.INDEX_URL) } }
                .onSuccess { apps = it; refreshInstalled() }
                .onFailure { error = "Couldn't reach the index. Check your connection." }
            loading = false
        }
    }

    private fun refreshInstalled() {
        installed = apps.mapNotNull { app ->
            Installer.installedVersionCode(this, app.pkg)?.let { app.pkg to it }
        }.toMap()
    }

    private fun install(app: App) {
        lifecycleScope.launch {
            Installer.install(
                ctx = this@MainActivity,
                apkUrl = app.apkUrl,
                expectedSha256 = app.sha256,
                pkg = app.pkg,
                onProgress = { progress = it },
            ).onSuccess { progress = null }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
