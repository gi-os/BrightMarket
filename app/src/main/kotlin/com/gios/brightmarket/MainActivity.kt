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
import com.gios.brightmarket.data.Followed
import com.gios.brightmarket.data.Index
import com.gios.brightmarket.data.Obtainium
import com.gios.brightmarket.data.Sort
import com.gios.brightmarket.data.Tracked
import com.gios.brightmarket.install.Installer
import com.gios.brightmarket.ui.*
import com.gios.light.common.report.LightReport
import com.gios.light.common.report.ReportOverlay
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

    private var progress by mutableStateOf<Installer.Progress?>(null)

    /**
     * Progress keyed by package. "Update all" runs several installs and one
     * shared field would make every row show whichever reported last.
     */
    private var progressFor by mutableStateOf<Map<String, Installer.Progress>>(emptyMap())

    /** A package named by a QR link, opened once the index has loaded. */
    private var pendingPkg: String? = null

    private var scanning by mutableStateOf(false)
    private var trackedRows by mutableStateOf<List<TrackedRow>>(emptyList())
    private var followed by mutableStateOf<Set<String>>(emptySet())

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
            val entries = runCatching { Obtainium.parse(text) }.getOrElse {
                toast("That doesn't look like an Obtainium export.")
                return@launch
            }
            if (entries.isEmpty()) {
                toast("No apps found in that file.")
                return@launch
            }
            val result = Obtainium.match(entries, apps)

            // Actually keep the ones BrightMarket doesn't index. Reporting a
            // count and dropping them made this a migration that only looked
            // finished: someone replacing Obtainium would have quietly stopped
            // getting updates for everything it was watching.
            // Follow the ones BrightMarket DOES index. Matching them was
            // previously invisible: the Updates tab only listed what
            // PackageManager reported, so anything not already on the phone
            // went nowhere and the import looked like it had skipped them.
            val nowFollowed = Followed.add(this@MainActivity, result.matched.map { it.pkg })
            followed = Followed.all(this@MainActivity)

            val toTrack = Obtainium.trackable(result.unmatched)
            val existing = Tracked.all(this@MainActivity)
            val merged = (existing + toTrack).distinctBy { it.repo.lowercase() }
            val added = merged.size - existing.size
            if (added > 0) Tracked.save(this@MainActivity, merged)

            // An entry with no GitHub repo has no update source, so it cannot be
            // tracked at all. Say so rather than letting it vanish into the gap
            // between the two numbers.
            val unusable = result.unmatched.size - toTrack.size

            toast(
                buildString {
                    append("Imported ${entries.size}: ")
                    append("${result.matched.size} in BrightMarket")
                    if (nowFollowed > 0) append(" ($nowFollowed new)")
                    if (added > 0) append(", $added now tracked")
                    if (unusable > 0) append(", $unusable with no GitHub repo skipped")
                }
            )
            refreshTracked()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Shake to report, from light-common: the same gesture and the same
        // light-reports repo every other app in the portfolio uses.
        LightReport.install(
            context = this,
            appName = "BrightMarket",
            label = "market",
            token = BuildConfig.REPORT_TOKEN,
        )

        followed = Followed.all(this)
        onboarded = Focus.onboarded(this)
        focusMode = Focus.enabled(this)
        handleLink(intent)

        setContent {
            BrightMarketTheme {
                ReportOverlay()
                if (!onboarded) {
                    OnboardingScreen { focus ->
                        Focus.choose(this, focus)
                        focusMode = focus
                        onboarded = true
                    }
                    return@BrightMarketTheme
                }

                if (scanning) {
                    // Full screen: a viewfinder squeezed under the chrome is
                    // harder to aim, and there is nothing else to do here.
                    androidx.compose.foundation.layout.Box(
                        Modifier.fillMaxSize().background(Light.Background)
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            TopBar("SCAN", onBack = { scanning = false })
                            ScanScreen { text ->
                                scanning = false
                                onScanned(text)
                            }
                        }
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

                val (updates, upToDate, notInstalled) =
                    Index.partitionInstalled(apps, installed, packageName, followed)

                // Three destinations, which is the SDK's hard ceiling for a
                // bottom bar containing any text item.
                val tabs = if (focusMode) {
                    // No "Installed" tab: it rendered the same screen as Updates,
                    // so it was a duplicate of itself sitting in a bar that only
                    // allows three items.
                    listOf("Updates", "Settings")
                } else {
                    listOf("Browse", "Updates", "Settings")
                }
                val index = tabIndex.coerceIn(0, tabs.lastIndex)

                Column(Modifier.fillMaxSize().background(Light.Background)) {
                    TopBar(
                        "BRIGHTMARKET",
                        onScan = { scanning = true },
                        onRefresh = {
                            if (!loading) {
                                refresh()
                                refreshTracked()
                            }
                        },
                        refreshing = loading,
                    )

                    Column(Modifier.weight(1f)) {
                        when (tabs[index]) {
                        "Browse" -> BrowseScreen(
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

                        "Updates" -> UpdatesScreen(
                            updates = updates,
                            upToDate = upToDate,
                            notInstalled = notInstalled,
                            tracked = trackedRows,
                            progressFor = progressFor,
                            loading = loading,
                            focusMode = focusMode,
                            onUpdateAll = { updateAll(updates.map { it.app }) },
                            onOpen = { selected = it; progress = null },
                            onInstallTracked = ::installTracked,
                            onForgetTracked = ::forgetTracked,
                            onRemoveFollowed = { app ->
                                Followed.remove(this@MainActivity, app.pkg)
                                followed = Followed.all(this@MainActivity)
                            },
                        )

                        else -> SettingsScreen(
                            focusEnabled = focusMode,
                            onToggleFocus = {
                                // Only ON is reachable from here. Off is the QR.
                                Focus.setEnabled(this@MainActivity, true)
                                focusMode = true
                                tabIndex = 0
                            },
                            onScan = { scanning = true },
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
        refreshTracked()
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
        Focus.parseLink(data)?.let { handleLinkValue(it) }
    }

    private fun handleLinkValue(link: Focus.Link) {
        when (link) {
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
        }
    }

    /**
     * A scanned code. Two shapes are accepted: a brightmarket:// link from the
     * desktop catalogue, and a plain GitHub repo URL — the second is what makes
     * this useful for apps nobody has submitted.
     */
    private fun onScanned(text: String) {
        val link = Focus.parseLink(text)
        if (link != null) {
            handleLinkValue(link)
            return
        }
        val repo = Tracked.repoFromAny(text)
        if (repo == null) {
            toast("That code isn't a BrightMarket link or a GitHub repo.")
            return
        }
        if (apps.any { it.repo.equals(repo, ignoreCase = true) }) {
            // Already indexed: open it properly rather than tracking a duplicate
            // that would then update itself alongside the indexed copy.
            selected = apps.first { it.repo.equals(repo, ignoreCase = true) }
            return
        }
        val added = Tracked.add(this, Tracked.Entry(repo = repo))
        toast(if (added) "Tracking $repo" else "Already tracking $repo")
        refreshTracked()
    }

    private fun installTracked(row: TrackedRow) {
        val url = row.apkUrl ?: return
        lifecycleScope.launch {
            val key = row.pkg ?: row.repo
            Installer.install(
                ctx = this@MainActivity,
                apkUrl = url,
                // No hash exists for an unlisted app; see Installer.install.
                expectedSha256 = null,
                pkg = row.pkg ?: "",
                onProgress = { p -> progressFor = progressFor + (key to p) },
                onIdentified = { id, _ ->
                    // A GitHub release doesn't say which package it installs, so
                    // this is the only moment we can find out -- and without it
                    // a tracked app can never be compared against what's on the
                    // phone, so it would never show an update.
                    if (row.pkg == null) Tracked.setPkg(this@MainActivity, row.repo, id)
                },
            )
            progressFor = progressFor - key
            refreshInstalled()
            refreshTracked()
        }
    }

    private fun forgetTracked(row: TrackedRow) {
        Tracked.remove(this, row.repo)
        refreshTracked()
        toast("Stopped tracking ${row.repo}")
    }

    private fun refreshTracked() {
        lifecycleScope.launch {
            val entries = Tracked.all(this@MainActivity)
            val rows = withContext(Dispatchers.IO) {
                entries.map { e ->
                    val resolved = Tracked.resolve(e)
                    val pkg = e.pkg ?: resolved?.entry?.pkg
                    TrackedRow(
                        repo = e.repo,
                        name = e.name,
                        pkg = pkg,
                        version = resolved?.version,
                        apkUrl = resolved?.apkUrl,
                        installedVersionCode = pkg?.let {
                            Installer.installedVersionCode(this@MainActivity, it)
                        },
                        versionCode = resolved?.versionCode,
                    )
                }
            }
            trackedRows = rows
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
