package com.gios.brightmarket

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.gios.brightmarket.data.App
import com.gios.brightmarket.data.Index
import com.gios.brightmarket.data.Obtainium
import com.gios.brightmarket.data.Sort
import com.gios.brightmarket.install.Installer
import com.gios.brightmarket.ui.BrightMarketTheme
import com.gios.brightmarket.ui.DetailScreen
import com.gios.brightmarket.ui.Light
import com.gios.brightmarket.ui.ListScreen
import com.gios.brightmarket.ui.Tab
import com.gios.brightmarket.ui.TabBar
import com.gios.brightmarket.ui.TopBar
import com.gios.brightmarket.ui.UpdatesScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var apps by mutableStateOf<List<App>>(emptyList())
    private var loading by mutableStateOf(true)
    private var error by mutableStateOf<String?>(null)
    private var sort by mutableStateOf(Sort.UPDATED)
    private var selected by mutableStateOf<App?>(null)
    private var tab by mutableStateOf(Tab.BROWSE)
    private var query by mutableStateOf("")
    private var category by mutableStateOf(Index.ALL)
    private var installed by mutableStateOf<Map<String, Long>>(emptyMap())

    /** Progress for the app open on the detail screen. */
    private var progress by mutableStateOf<Installer.Progress?>(null)

    /**
     * Progress keyed by package, for the Updates tab. "Update all" runs several
     * installs in sequence, and one shared field would make every row display
     * whichever install reported last.
     */
    private var progressFor by mutableStateOf<Map<String, Installer.Progress>>(emptyMap())

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
                val (updates, upToDate) = Index.partitionInstalled(apps, installed, packageName)

                if (app != null) {
                    DetailScreen(
                        app = app,
                        installedVersionCode = installed[app.pkg],
                        progress = progress,
                        onInstall = { install(app) },
                        onBack = { selected = null },
                    )
                    return@BrightMarketTheme
                }

                Column(Modifier.fillMaxSize().background(Light.Background)) {
                    TopBar("BRIGHTMARKET")
                    TabBar(tab, updates.size) { tab = it }

                    when (tab) {
                        Tab.BROWSE -> ListScreen(
                            // Filter first, then sort: sorting the whole index
                            // and then filtering would do the expensive half
                            // twice.
                            apps = Index.sort(Index.filter(apps, query, category), sort),
                            sort = sort,
                            query = query,
                            category = category,
                            categories = Index.categories(apps),
                            installed = installed,
                            loading = loading,
                            error = error,
                            onQuery = { query = it },
                            onCategory = { category = it },
                            onSort = { sort = it },
                            onOpen = { selected = it; progress = null },
                            onImport = {
                                pickExport.launch(
                                    arrayOf("application/json", "text/plain", "*/*")
                                )
                            },
                        )

                        Tab.UPDATES -> UpdatesScreen(
                            updates = updates,
                            upToDate = upToDate,
                            progressFor = progressFor,
                            loading = loading,
                            onUpdateAll = { updateAll(updates.map { it.app }) },
                            onOpen = { selected = it; progress = null },
                        )
                    }
                }
            }
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        // Re-read installed versions on every resume: coming back from the
        // system installer dialog is exactly when they change, and it is what
        // moves a row from "update" to "up to date".
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

    /**
     * Update several apps one at a time.
     *
     * Deliberately sequential. Each install ends in the system's confirmation
     * dialog, and firing them in parallel would stack those dialogs on top of
     * each other -- the user would tap through a pile of prompts with no idea
     * which app each one belongs to. It also keeps only one download on the
     * wire, which matters on the LP3's connection.
     */
    private fun updateAll(targets: List<App>) {
        lifecycleScope.launch {
            // Self last: installing BrightMarket kills this process, so anything
            // queued behind it would never run. See Index.selfLast.
            for (app in Index.selfLast(targets, packageName)) {
                Installer.install(
                    ctx = this@MainActivity,
                    apkUrl = app.apkUrl,
                    expectedSha256 = app.sha256,
                    pkg = app.pkg,
                    onProgress = { p -> progressFor = progressFor + (app.pkg to p) },
                )
                // Clear this app's indicator whatever happened. A failure is
                // already reported by the installer's own broadcast, and leaving
                // a stale "Downloading 100%" on the row is worse than nothing.
                progressFor = progressFor - app.pkg
            }
            refreshInstalled()
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
