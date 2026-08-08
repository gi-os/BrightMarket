package com.gios.brightmarket

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.core.content.ContextCompat
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import com.gios.brightmarket.data.target
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
    private var nightly by mutableStateOf(false)

    private var progress by mutableStateOf<Installer.Progress?>(null)

    /**
     * Progress keyed by package. "Update all" runs several installs and one
     * shared field would make every row show whichever reported last.
     */
    private var progressFor by mutableStateOf<Map<String, Installer.Progress>>(emptyMap())

    /**
     * Fires when a package is added or replaced anywhere on the phone.
     *
     * onResume alone was not enough. Returning from the system installer dialog
     * resumes this activity, but the install itself finishes a moment later, so
     * the package manager still reported the app as absent and it never moved
     * into the installed section. This is the authoritative signal, and it also
     * catches installs that complete while the app is in the foreground.
     */
    private val packageWatcher = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshInstalled()
            refreshTracked()
        }
    }

    /** A package named by a QR link, opened once the index has loaded. */
    private var pendingPkg: String? = null

    private var scanning by mutableStateOf(false)
    /** The plus menu. Distinct from [scanning]: the camera is one route off it. */
    private var adding by mutableStateOf(false)
    private var trackedRows by mutableStateOf<List<TrackedRow>>(emptyList())
    private var followed by mutableStateOf<Set<String>>(emptySet())

    /**
     * True only for a refresh the user asked for.
     *
     * A refresh that finds nothing new changes nothing on screen, which is
     * indistinguishable from the button not working. The startup fetch must
     * stay silent though -- announcing it would be noise on every launch.
     */
    private var manualRefresh = false

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

        // Registered for the activity's whole life, not just while resumed.
        // The system installer is a full activity, so BrightMarket is STOPPED
        // for the entire install -- a receiver tied to the resumed state gets
        // unregistered at precisely the moment the package broadcast fires,
        // which is why the installed list still wasn't updating.
        ContextCompat.registerReceiver(
            this,
            packageWatcher,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                // Without the data scheme these are never delivered at all:
                // package broadcasts carry a package: URI, and a filter with no
                // scheme matches none of them.
                addDataScheme("package")
            },
            // targetSdk 35 requires an explicit export flag on runtime
            // receivers. These are protected system broadcasts -- nothing
            // outside the system can send them -- so NOT_EXPORTED is right.
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        followed = Followed.all(this)
        onboarded = Focus.onboarded(this)
        focusMode = Focus.enabled(this)
        nightly = Focus.nightly(this)
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

                if (adding) {
                    // Same reasoning as the scanner, minus the urgency: a top
                    // bar is the only way off, and the gesture should work.
                    BackHandler { adding = false }
                    Column(Modifier.fillMaxSize().background(Light.Background)) {
                        TopBar("ADD AN APP", onBack = { adding = false })
                        AddScreen(
                            onSubmit = { text ->
                                adding = false
                                onScanned(text)
                            },
                            onScan = { scanning = true },
                        )
                    }
                    return@BrightMarketTheme
                }

                if (scanning) {
                    // The system gesture has to work here. Without it the only
                    // exits were a small arrow at the top of a screen filled by
                    // a camera and, on this device, one the camera surface was
                    // drawing over -- so there was no way off at all.
                    BackHandler { scanning = false }
                    Column(
                        Modifier.fillMaxSize().background(Light.Background)
                    ) {
                        TopBar("SCAN", onBack = { scanning = false })
                        ScanScreen(
                            onScanned = { text ->
                                scanning = false
                                adding = false
                                onScanned(text)
                            },
                            onCancel = { scanning = false },
                        )
                    }
                    return@BrightMarketTheme
                }

                val app = selected
                if (app != null) {
                    DetailScreen(
                        app = app,
                        installedVersionCode = installed[app.pkg],
                        progress = progress,
                        isSelf = app.pkg == packageName,
                        onInstall = { install(app) },
                        onUninstall = { uninstall(app.pkg) },
                        onBack = { selected = null },
                    )
                    return@BrightMarketTheme
                }

                val (updates, upToDate, notInstalled) =
                    Index.partitionInstalled(apps, installed, packageName, followed, nightly)

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
                        onAdd = { adding = true },
                        onRefresh = {
                            if (!loading) {
                                manualRefresh = true
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
                            nightly = nightly,
                            onToggleNightly = {
                                val next = !nightly
                                Focus.setNightly(this@MainActivity, next)
                                nightly = next
                                toast(
                                    if (next) "Nightly builds on"
                                    else "Official releases only"
                                )
                            },
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
        // Belt and braces alongside the broadcast: cheap, and covers a return
        // from anywhere the receiver couldn't observe.
        refreshInstalled()
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(packageWatcher) }
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
     * Something to add, from the scanner or from the paste field beside it.
     *
     * Three shapes: a brightmarket:// link from the desktop catalogue, a GitHub
     * repo URL — which is what makes this useful for apps nobody has submitted
     * — and a direct link to an .apk.
     */
    private fun onScanned(text: String) {
        val link = Focus.parseLink(text)
        if (link != null) {
            handleLinkValue(link)
            return
        }

        // A bare .apk URL installs once and is not tracked afterwards. Nothing
        // to track: a file on a web server has no releases to watch and no way
        // to say what it will be called next. Saying so at install time is
        // better than adding a row that would never update and look broken.
        if (looksLikeApkUrl(text)) {
            installFromUrl(text.trim())
            return
        }

        val repo = Tracked.repoFromAny(text)
        if (repo == null) {
            toast("Not a BrightMarket link, a GitHub repo, or an .apk URL.")
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

    /**
     * Hand the package to the system uninstaller.
     *
     * ACTION_DELETE rather than a PackageInstaller session: uninstalling needs
     * DELETE_PACKAGES, which is a system permission no sideloaded app can hold,
     * so the dialog is not a fallback here — it is the only route that exists.
     *
     * Nothing is done with the result. The package-removed broadcast registered
     * in onCreate is what refreshes the list, so the same code path handles an
     * uninstall from here and one done from LightOS settings.
     */
    @Suppress("DEPRECATION")
    private fun uninstall(pkg: String) {
        if (pkg == packageName) return

        // ACTION_DELETE first, then ACTION_UNINSTALL_PACKAGE. The second is
        // deprecated but is still what some builds route to, and trying both is
        // cheaper than being wrong on a device I can't test.
        val attempts = listOf(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")),
            Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$pkg")),
        )
        for (intent in attempts) {
            if (runCatching { startActivity(intent); true }.getOrDefault(false)) return
        }
        // Said out loud rather than swallowed. A tap that does nothing and says
        // nothing is indistinguishable from a broken button, which is exactly
        // how this got reported the first time.
        toast("Couldn't open the uninstaller — remove $pkg from Settings")
    }

    /**
     * https and ending in .apk, ignoring any query string.
     *
     * http is refused rather than upgraded: an APK fetched over a connection
     * anyone on the network can rewrite is the one download where that matters
     * most, and there is no hash here to catch it — an unlisted APK has nothing
     * to check against. Silently upgrading to https would also lie about what
     * was asked for.
     */
    private fun looksLikeApkUrl(text: String): Boolean {
        val t = text.trim()
        if (!t.startsWith("https://", ignoreCase = true)) return false
        return t.substringBefore('?').substringBefore('#').endsWith(".apk", ignoreCase = true)
    }

    private fun installFromUrl(url: String) {
        val key = url
        lifecycleScope.launch {
            Installer.install(
                ctx = this@MainActivity,
                apkUrl = url,
                // No hash: nothing generated one. Same footing as a tracked
                // repo, and marked the same way in the list it lands in.
                expectedSha256 = null,
                // Only used to name the file in cacheDir. Empty would make it
                // ".apk" -- a hidden file, and the same one for every download.
                pkg = "direct-" + url.hashCode().toUInt().toString(16),
                onProgress = { p -> progressFor = progressFor + (key to p) },
                // Nothing to record. Tracked.setPkg keys on a repo, and there
                // isn't one here -- a file on a web server has no releases to
                // watch. The install still happens; it just isn't followed
                // afterwards, which is what the caller's comment says.
            )
            progressFor = progressFor - key
            refreshInstalled()
        }
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
                    val outcome = Tracked.resolve(this@MainActivity, e)
                    val resolved = (outcome as? Tracked.Outcome.Ok)?.resolved
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
                        status = when (outcome) {
                            is Tracked.Outcome.Ok -> null
                            is Tracked.Outcome.NoRelease -> "no APK in its releases"
                            is Tracked.Outcome.RateLimited -> {
                                val mins = ((outcome.resetEpochSeconds * 1000 -
                                    System.currentTimeMillis()) / 60000).coerceAtLeast(1)
                                "GitHub rate limit — retry in ${mins}m"
                            }
                            is Tracked.Outcome.Unreachable -> "couldn't reach GitHub"
                        },
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
                    if (manualRefresh) {
                        // Say what the refresh FOUND, not merely that it ran.
                        // "Refreshed" alone leaves you no better informed than
                        // before you pressed it.
                        val (updates, _, _) =
                            Index.partitionInstalled(apps, installed, packageName, followed, nightly)
                        toast(
                            when (updates.size) {
                                0 -> "Up to date · ${apps.size} apps"
                                1 -> "1 update available"
                                else -> "${updates.size} updates available"
                            }
                        )
                    }
                }
                .onFailure {
                    error = "Couldn't reach the index. Check your connection."
                    if (manualRefresh) toast("Couldn't reach the index.")
                }
            manualRefresh = false
            loading = false
        }
    }

    private fun refreshInstalled() {
        installed = apps.mapNotNull { app ->
            Installer.installedVersionCode(this, app.pkg)?.let { app.pkg to it }
        }.toMap()
    }

    private fun install(app: App) {
        // Resolved here rather than passed in, so every route to an install --
        // the detail page, a row, update-all -- lands on the same build. Two
        // places deciding this independently is how a UI ends up promising a
        // nightly and installing stable.
        val t = app.target(nightly)
        lifecycleScope.launch {
            Installer.install(
                ctx = this@MainActivity,
                apkUrl = t.apkUrl,
                expectedSha256 = t.sha256,
                pkg = app.pkg,
                onProgress = { progress = it },
            ).onSuccess {
                progress = null
                // The broadcast is the reliable signal, but the session can also
                // complete without one on some builds; re-reading here costs a
                // package-manager query and closes that gap.
                refreshInstalled()
            }
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
                val t = app.target(nightly)
                Installer.install(
                    ctx = this@MainActivity,
                    apkUrl = t.apkUrl,
                    expectedSha256 = t.sha256,
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
