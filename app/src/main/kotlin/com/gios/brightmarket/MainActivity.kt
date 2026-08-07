package com.gios.brightmarket

import android.content.Intent
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
import com.gios.brightmarket.data.Focus
import com.gios.brightmarket.data.Index
import com.gios.brightmarket.data.Obtainium
import com.gios.brightmarket.data.Sort
import com.gios.brightmarket.install.Installer
import com.gios.brightmarket.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var apps by mutableStateOf<List<App>>(emptyList())
    private var loading by mutableStateOf(true)
    private var error by mutableStateOf<String?>(null)
    private var sort by mutableStateOf(Sort.UPDATED)
    private var selected by mutableStateOf<App?>(null)
    private var tabIndex by mutableStateOf(0)
    private var query by mutableStateOf("")
    private var category by mutableStateOf(Index.ALL)
    private var installed by mutableStateOf<Map<String, Long>>(emptyMap())

    private var onboarded by mutableStateOf(false)
    private var focusMode by mutableStateOf(false)
    private var confirmingFocusOff by mutableStateOf(false)

    private var progress by mutableStateOf<Installer.Progress?>(null)

    /**
     * Progress keyed by package. "Update all" runs several installs and one
     * shared field would make every row show whichever reported last.
     */
    private var progressFor by mutableStateOf<Map<String, Installer.Progress>>(emptyMap())

    /** A package named by a QR link, opened once the index has loaded. */
    private var pendingPkg: String? = null

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
            val msg = buildString {
                append("${result.matched.size} of ${result.matched.size + result.unmatched.size} already in BrightMarket")
                if (result.unmatched.isNotEmpty()) append("; ${result.unmatched.size} not indexed yet")
            }
            toast(msg)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onboarded = Focus.onboarded(this)
        focusMode = Focus.enabled(this)
        handleLink(intent)

        setContent {
            BrightMarketTheme {
                if (!onboarded) {
                    OnboardingScreen { focus ->
                        Focus.choose(this, focus)
                        focusMode = focus
                        onboarded = true
                    }
                    return@BrightMarketTheme
                }

                val app = selected
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

                val (updates, upToDate) = Index.partitionInstalled(apps, installed, packageName)

                // Three destinations, which is the SDK's hard ceiling for a
                // bottom bar containing any text item.
                val tabs = if (focusMode) {
                    listOf("Installed", "Updates", "Settings")
                } else {
                    listOf("Browse", "Updates", "Settings")
                }
                val index = tabIndex.coerceIn(0, tabs.lastIndex)

                Column(Modifier.fillMaxSize().background(Light.Background)) {
                    TopBar("BRIGHTMARKET")

                    Column(Modifier.weight(1f)) {
                        when (index) {
                            0 -> if (focusMode) {
                                // Focus mode: only what is already on the phone.
                                // No search, no categories, no way to encounter
                                // an app you don't own.
                                UpdatesScreen(
                                    updates = updates,
                                    upToDate = upToDate,
                                    progressFor = progressFor,
                                    loading = loading,
                                    focusMode = true,
                                    onUpdateAll = { updateAll(updates.map { it.app }) },
                                    onOpen = { selected = it; progress = null },
                                )
                            } else {
                                BrowseScreen(
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
                                )
                            }

                            1 -> UpdatesScreen(
                                updates = updates,
                                upToDate = upToDate,
                                progressFor = progressFor,
                                loading = loading,
                                focusMode = focusMode,
                                onUpdateAll = { updateAll(updates.map { it.app }) },
                                onOpen = { selected = it; progress = null },
                            )

                            else -> SettingsScreen(
                                focusEnabled = focusMode,
                                confirmingFocusOff = confirmingFocusOff,
                                onToggleFocus = {
                                    if (focusMode) {
                                        // Only leaving needs a pause. Entering
                                        // is instant -- friction there would
                                        // discourage the choice this exists for.
                                        confirmingFocusOff = true
                                    } else {
                                        Focus.setEnabled(this@MainActivity, true)
                                        focusMode = true
                                        tabIndex = 0
                                    }
                                },
                                onConfirmFocusOff = {
                                    Focus.setEnabled(this@MainActivity, false)
                                    focusMode = false
                                    confirmingFocusOff = false
                                },
                                onCancelFocusOff = { confirmingFocusOff = false },
                                onImport = {
                                    pickExport.launch(
                                        arrayOf("application/json", "text/plain", "*/*")
                                    )
                                },
                            )
                        }
                    }

                    BottomBar(tabs, index) { tabIndex = it }
                }
            }
        }
        refresh()
    }

    /** QR links arrive while the app is already open far more often than not. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLink(intent)
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the system installer dialog is exactly when installed
        // versions change, and what moves a row out of "needs update".
        refreshInstalled()
    }

    /**
     * Act on a scanned `brightmarket://` link.
     *
     * BrightMarket never opens the camera. The phone already has a QR scanner
     * (Roll), and bundling CameraX plus a decoder here would duplicate it for
     * one screen — and require a camera permission this app has no other reason
     * to hold. Scanning with Roll fires this intent instead.
     */
    private fun handleLink(intent: Intent?) {
        val data = intent?.data?.toString() ?: return
        when (val link = Focus.parseLink(data)) {
            is Focus.Link.OpenApp -> {
                // The index may not have loaded yet; remember it and open once
                // it has, rather than silently dropping the scan.
                val match = apps.firstOrNull { it.pkg == link.pkg }
                if (match != null) selected = match else pendingPkg = link.pkg
            }
            is Focus.Link.SetFocus -> {
                Focus.setEnabled(this, link.on)
                focusMode = link.on
                onboarded = true
                tabIndex = 0
                toast(if (link.on) "Focus mode on." else "Focus mode off.")
            }
            null -> Unit  // not ours; a QR scanner decodes all sorts of things
        }
    }

    private fun refresh() {
        lifecycleScope.launch {
            loading = true
            error = null
            runCatching { withContext(Dispatchers.IO) { Index.fetch(BuildConfig.INDEX_URL) } }
                .onSuccess {
                    apps = it
                    refreshInstalled()
                    pendingPkg?.let { pkg ->
                        selected = apps.firstOrNull { a -> a.pkg == pkg }
                        if (selected == null) toast("That app isn't in the index.")
                        pendingPkg = null
                    }
                }
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
     * Sequential on purpose: each install ends at the system confirmation
     * dialog, and parallel installs would stack those prompts with no
     * indication which app each belongs to.
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
                progressFor = progressFor - app.pkg
            }
            refreshInstalled()
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
