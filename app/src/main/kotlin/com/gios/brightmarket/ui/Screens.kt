package com.gios.brightmarket.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gios.brightmarket.data.App
import com.gios.brightmarket.data.Installed
import com.gios.brightmarket.data.Sort
import com.gios.brightmarket.data.Version
import com.gios.brightmarket.hw.WheelScroll
import com.gios.brightmarket.install.Installer

// ---------------------------------------------------------------------------
// Chrome
// ---------------------------------------------------------------------------

/**
 * LightGrid top bar: 3 units tall, 1-unit inset, title set in `fine`. LightOS
 * bars are separated from content by space, never by a divider line.
 */
@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    /** A plus, not a viewfinder: adding an app is the action, and scanning is
     *  one of two ways to do it. */
    onAdd: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(gridUnits(Grid.TOP_BAR))
            .padding(horizontal = gridUnits(Grid.INSET)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                Modifier
                    .lightClickable(onClick = onBack)
                    .padding(end = gridUnits(0.4f)),
            ) { IconBack(Light.Content) }
        }
        Text(title, style = MaterialTheme.typography.labelMedium)
        if (onAdd != null || onRefresh != null) {
            Spacer(Modifier.weight(1f))
            if (onRefresh != null) {
                if (refreshing) {
                    // A word, not a spinner: LightOS has no spinner anywhere,
                    // and a dimmed icon alone is too subtle to read as "busy".
                    Text(
                        "…",
                        style = MaterialTheme.typography.labelMedium,
                        color = Light.ContentSecondary,
                    )
                } else {
                    Box(Modifier.lightClickable(onClick = onRefresh)) {
                        IconRefresh(Light.Content)
                    }
                }
                Spacer(Modifier.width(gridUnits(1f)))
            }
            if (onAdd != null) {
                Box(Modifier.lightClickable(onClick = onAdd)) { IconPlus(Light.Content) }
            }
        }
    }
}

/**
 * LightBottomBar — LightOS's ActionBar. 4 units tall, at the BOTTOM, labels in
 * `button` (15% tracking). The SDK allows up to 5 icon items but only 3 when
 * any item is text, so this app has exactly three destinations and no more.
 */
@Composable
fun BottomBar(items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    // Icons, so the bar isn't capped at three: the SDK allows five icon items
    // but only three when any of them is text.
    Row(
        Modifier
            .fillMaxWidth()
            .height(gridUnits(Grid.BOTTOM_BAR))
            .padding(horizontal = gridUnits(Grid.INSET)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { i, label ->
            Box(
                Modifier
                    .weight(1f)
                    .lightClickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                val tint = if (i == selected) Light.Content else Light.ContentSecondary
                when (label) {
                    "Browse" -> IconApps(tint)
                    "Updates" -> IconDownload(tint)
                    else -> IconSettings(tint)
                }
            }
        }
    }
}

/** LightTextField: a 3-design-px underline across 80% width. No filled box. */
@Composable
fun LightTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Light.Content),
        cursorBrush = SolidColor(Light.Content),
        modifier = modifier
            .fillMaxWidth(0.8f)
            .drawBehind {
                drawLine(
                    color = Light.ContentSecondary,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 3f,
                )
            },
        decorationBox = { inner ->
            Box(Modifier.padding(vertical = gridUnits(0.3f))) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Light.ContentSecondary,
                    )
                }
                inner()
            }
        },
    )
}

/** Search is the same field with the list's own insets around it. */
@Composable
fun SearchField(query: String, onQuery: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = gridUnits(Grid.INSET), vertical = gridUnits(0.4f))
    ) {
        LightTextField(query, onQuery, "Search")
    }
}

/**
 * The plus menu.
 *
 * Tapping plus used to open a live viewfinder immediately: the camera came up,
 * with no statement of what it was for and no other option, for a task that
 * often needs no camera at all. This is the page that should have been there —
 * it says what adding an app means, and offers the two ways to do it.
 *
 * The field is first because it's the one that works with a link somebody sent
 * you, which is most of them. Scanning is one tap further along, and still the
 * faster route when the code is in front of you.
 */
@Composable
fun AddScreen(onSubmit: (String) -> Unit, onScan: () -> Unit) {
    var typed by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(Light.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = gridUnits(Grid.INSET)),
    ) {
        Spacer(Modifier.height(gridUnits(1f)))
        Text("PASTE A LINK", style = MaterialTheme.typography.titleMedium, color = Light.ContentSecondary)
        Spacer(Modifier.height(gridUnits(0.4f)))
        LightTextField(
            value = typed,
            onValueChange = { typed = it },
            placeholder = "https://…",
        )
        Spacer(Modifier.height(gridUnits(0.6f)))
        Text(
            "ADD",
            style = MaterialTheme.typography.labelLarge,
            color = if (typed.isBlank()) Light.ContentSecondary else Light.Content,
            modifier = Modifier
                .lightClickable(enabled = typed.isNotBlank()) { onSubmit(typed.trim()) }
                .padding(vertical = gridUnits(0.4f), horizontal = gridUnits(0.1f)),
        )
        Spacer(Modifier.height(gridUnits(0.4f)))
        Text(
            "A GitHub repo, a brightmarket.gzl.dev link, or a direct .apk URL.",
            style = MaterialTheme.typography.bodySmall,
            color = Light.ContentSecondary,
        )

        Spacer(Modifier.height(gridUnits(2f)))
        Text("SCAN A CODE", style = MaterialTheme.typography.titleMedium, color = Light.ContentSecondary)
        Spacer(Modifier.height(gridUnits(0.4f)))
        Text(
            "From the desktop catalog, or any QR with a repo link in it.",
            style = MaterialTheme.typography.bodySmall,
            color = Light.ContentSecondary,
        )
        Spacer(Modifier.height(gridUnits(0.6f)))
        Text(
            "OPEN THE CAMERA",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .lightClickable(onClick = onScan)
                .padding(vertical = gridUnits(0.4f), horizontal = gridUnits(0.1f)),
        )
        Spacer(Modifier.height(gridUnits(2f)))
    }
}

// ---------------------------------------------------------------------------
// Onboarding
// ---------------------------------------------------------------------------

/**
 * Shown once, before the app is usable. The two modes are a real choice about
 * how the phone behaves, so it is made deliberately rather than defaulted into
 * — the whole point of focus mode is lost if you arrive in browsing mode
 * without having decided.
 */
@Composable
fun OnboardingScreen(onChoose: (focus: Boolean) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Light.Background)
            .verticalScroll(rememberScrollState()),
    ) {
        TopBar("BRIGHTMARKET")
        Column(Modifier.padding(horizontal = gridUnits(Grid.INSET))) {
            Text("How do you want to use this?", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(gridUnits(1.5f)))

            Choice(
                title = "Browse here",
                body = "Search and discover apps on the phone. Everything works offline.",
                onClick = { onChoose(false) },
            )
            Spacer(Modifier.height(gridUnits(1.5f)))
            Choice(
                title = "Focus mode",
                body = "No browsing on the phone. You discover apps on a desktop and " +
                    "send them over by QR code. Your installed apps and their updates " +
                    "stay available here.",
                onClick = { onChoose(true) },
            )

            Spacer(Modifier.height(gridUnits(1.5f)))
            Text(
                "You can change this later in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = Light.ContentSecondary,
            )
            Spacer(Modifier.height(gridUnits(2f)))
        }
    }
}

@Composable
private fun Choice(title: String, body: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(vertical = gridUnits(0.6f))
    ) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(gridUnits(0.3f)))
        Text(body, style = MaterialTheme.typography.bodySmall, color = Light.ContentSecondary)
    }
}

// ---------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------

@Composable
fun SettingsScreen(
    focusEnabled: Boolean,
    nightly: Boolean,
    onToggleFocus: () -> Unit,
    onToggleNightly: () -> Unit,
    onScan: () -> Unit,
    onImport: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Light.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = gridUnits(Grid.INSET)),
    ) {
        Spacer(Modifier.height(gridUnits(1f)))
        Text("FOCUS MODE", style = MaterialTheme.typography.titleMedium, color = Light.ContentSecondary)
        Spacer(Modifier.height(gridUnits(0.4f)))
        Text(
            if (focusEnabled) {
                "On. Browsing is off. Apps arrive by QR from the desktop."
            } else {
                "Off. You can browse and search on the phone. " +
                    "Turning it on can only be undone with the QR code."
            },
            style = MaterialTheme.typography.bodySmall,
            color = Light.ContentSecondary,
        )
        Spacer(Modifier.height(gridUnits(0.6f)))

        if (focusEnabled) {
            // No off switch here. Focus mode is only lifted by scanning the QR
            // from the desktop catalog, which is the whole point: the thing
            // you'd have to go and find is a different screen in a different
            // room, and that pause is the feature.
            //
            // Worth being straight about what this is: a commitment device, not
            // a security boundary. The link is a plain brightmarket://focus/off
            // and anyone determined can construct one. It makes the easy path
            // the intentional one; it does not stop the device's owner, and
            // pretending otherwise would invite someone to rely on it.
            Text(
                "To turn this off, scan the OFF code at",
                style = MaterialTheme.typography.bodySmall,
                color = Light.ContentSecondary,
            )
            Spacer(Modifier.height(gridUnits(0.2f)))
            Text(
                "brightmarket.gzl.dev/browse.html",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(gridUnits(0.6f)))
            Text(
                "SCAN",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.lightClickable(onClick = onScan),
            )
        } else {
            Text(
                "TURN ON",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.lightClickable(onClick = onToggleFocus),
            )
        }

        Spacer(Modifier.height(gridUnits(2f)))
        Text("UPDATES", style = MaterialTheme.typography.titleMedium, color = Light.ContentSecondary)
        Spacer(Modifier.height(gridUnits(0.4f)))
        Text(
            if (nightly) {
                "Nightly. You get every build as it's made, including the ones " +
                    "that turn out to be wrong. Newer, and rougher."
            } else {
                "Official releases only. Nightly builds are made on every change " +
                    "and aren't offered to you."
            },
            style = MaterialTheme.typography.bodySmall,
            color = Light.ContentSecondary,
        )
        Spacer(Modifier.height(gridUnits(0.6f)))
        // Reversible, unlike focus mode, so it is a plain switch. Turning it
        // off doesn't downgrade anything -- Android won't install backwards --
        // it just stops offering nightlies, and the next official release
        // catches you up.
        Text(
            if (nightly) "USE OFFICIAL RELEASES" else "USE NIGHTLY BUILDS",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .lightClickable(onClick = onToggleNightly)
                .padding(vertical = gridUnits(0.4f)),
        )

        Spacer(Modifier.height(gridUnits(2f)))
        Text("IMPORT", style = MaterialTheme.typography.titleMedium, color = Light.ContentSecondary)
        Spacer(Modifier.height(gridUnits(0.4f)))
        Text(
            "Import from Obtainium",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.lightClickable(onClick = onImport),
        )
        Spacer(Modifier.height(gridUnits(2f)))
    }
}

// ---------------------------------------------------------------------------
// Lists
// ---------------------------------------------------------------------------

@Composable
fun SortRow(current: Sort, onSelect: (Sort) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = gridUnits(Grid.INSET), vertical = gridUnits(0.5f)),
        horizontalArrangement = Arrangement.spacedBy(gridUnits(1.5f)),
    ) {
        Sort.entries.forEach { sort ->
            val selected = sort == current
            Text(
                text = sort.label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) Light.Content else Light.ContentSecondary,
                modifier = Modifier.lightClickable { onSelect(sort) },
            )
        }
    }
}

@Composable
fun CategoryRow(categories: List<String>, current: String, onSelect: (String) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth().padding(vertical = gridUnits(0.4f)),
        contentPadding = PaddingValues(horizontal = gridUnits(Grid.INSET)),
        horizontalArrangement = Arrangement.spacedBy(gridUnits(1.2f)),
    ) {
        items(categories) { category ->
            val selected = category.equals(current, ignoreCase = true)
            Text(
                text = category.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) Light.Content else Light.ContentSecondary,
                modifier = Modifier.lightClickable { onSelect(category) },
            )
        }
    }
}

/** List rows are `copy` over `detail` — the SDK's own convention. */
@Composable
fun AppRow(
    app: App,
    installedVersionCode: Long?,
    /** The release string on the phone, when one is known. */
    installedVersion: String? = null,
    onClick: () -> Unit,
) {
    // Icon beside the text rather than above it, and the two text lines stay
    // exactly as they were: the row keeps the height it always had, so a
    // fifty-nine app list still scrolls the same distance.
    Row(
        Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(horizontal = gridUnits(Grid.INSET), vertical = gridUnits(0.8f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.icon, app.name, gridUnits(Grid.ICON))
        Spacer(Modifier.width(gridUnits(0.8f)))
        Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(app.name, style = MaterialTheme.typography.bodyLarge)
            val tag = when {
                installedVersionCode == null -> null
                app.versionCode > installedVersionCode -> "UPDATE"
                else -> "INSTALLED"
            }
            if (tag != null) {
                Spacer(Modifier.width(gridUnits(0.5f)))
                Text(
                    tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (tag == "UPDATE") Light.Content else Light.ContentSecondary,
                )
            }
        }
        Text(
            text = when {
                installedVersionCode == null -> app.summary
                app.versionCode > installedVersionCode ->
                    "${Version.installedLabel(null, installedVersion, installedVersionCode)} → " +
                        Version.display(app.version)
                else -> "v${app.version}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = Light.ContentSecondary,
            // Four, not two. The catalogue's summaries say what an app does now
            // -- BrightControl's names six subsystems -- and two lines ended
            // most of them in an ellipsis, which is the one place where a
            // description is no use at all. The bound in the index repo's
            // validator is 320 characters, which is what four lines hold.
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        }
    }
}

@Composable
fun BrowseScreen(
    apps: List<App>,
    sort: Sort,
    query: String,
    category: String,
    categories: List<String>,
    installed: Map<String, Long>,
    /** The release string on the phone for a package, when it can be read. */
    installedVersionOf: (String) -> String? = { null },
    loading: Boolean,
    error: String?,
    onQuery: (String) -> Unit,
    onCategory: (String) -> Unit,
    onSort: (Sort) -> Unit,
    onOpen: (App) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        SearchField(query, onQuery)
        if (categories.size > 1) CategoryRow(categories, category, onCategory)
        SortRow(sort, onSort)

        val filtering = query.isNotBlank() || category != "All"
        when {
            loading && apps.isEmpty() -> Message("Loading…")
            error != null && apps.isEmpty() -> Message(error)
            apps.isEmpty() && filtering -> Message("Nothing matches that.")
            apps.isEmpty() -> Message("No apps yet.")
            else -> {
                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                WheelScroll(listState)
                LazyColumn(Modifier.weight(1f), state = listState) {
                items(apps, key = { it.pkg }) { app ->
                    AppRow(app, installed[app.pkg], installedVersionOf(app.pkg)) { onOpen(app) }
                }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(gridUnits(Grid.INSET)), contentAlignment = Alignment.Center) {
        Text(text, color = Light.ContentSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun UpdatesScreen(
    updates: List<Installed>,
    upToDate: List<Installed>,
    notInstalled: List<App>,
    tracked: List<TrackedRow>,
    progressFor: Map<String, Installer.Progress>,
    loading: Boolean,
    focusMode: Boolean,
    onUpdateAll: () -> Unit,
    onOpen: (App) -> Unit,
    onInstallTracked: (TrackedRow) -> Unit,
    onForgetTracked: (TrackedRow) -> Unit,
    onRefreshTracked: (TrackedRow) -> Unit = {},
    refreshingRepo: String? = null,
    onRemoveFollowed: (App) -> Unit,
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // The wheel scrolls it: on a 472dp panel this list runs well past the fold,
    // and reaching over it to drag is the gesture the wheel exists to replace.
    WheelScroll(listState)

    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        if (updates.isEmpty() && upToDate.isEmpty() && notInstalled.isEmpty() && tracked.isEmpty()) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(gridUnits(2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        when {
                            loading -> "Loading…"
                            focusMode ->
                                "Nothing installed yet. Browse on a desktop and scan the QR."
                            else -> "None of these apps are installed yet."
                        },
                        color = Light.ContentSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            return@LazyColumn
        }

        if (updates.isNotEmpty()) {
            item { SectionHeader("NEEDS UPDATE (${updates.size})") }
            item {
                Text(
                    "UPDATE ALL (${updates.size})",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .lightClickable(enabled = progressFor.isEmpty(), onClick = onUpdateAll)
                        .padding(horizontal = gridUnits(Grid.INSET), vertical = gridUnits(0.8f)),
                )
            }
            items(updates, key = { it.app.pkg }) { entry ->
                UpdateRow(entry, progressFor[entry.app.pkg]) { onOpen(entry.app) }
            }
        }

        if (upToDate.isNotEmpty()) {
            item { SectionHeader("INSTALLED, UP TO DATE (${upToDate.size})") }
            items(upToDate, key = { it.app.pkg }) { entry ->
                UpdateRow(entry, progressFor[entry.app.pkg]) { onOpen(entry.app) }
            }
        }

        if (notInstalled.isNotEmpty()) {
            // Imported or followed, but not on the phone. Without this an
            // Obtainium import did nothing visible for the apps BrightMarket
            // does index, which read as the import having skipped them.
            item { SectionHeader("IN YOUR LIST, NOT INSTALLED (${notInstalled.size})") }
            items(notInstalled, key = { it.pkg }) { app ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .lightClickable { onOpen(app) }
                        .padding(horizontal = gridUnits(Grid.INSET), vertical = gridUnits(0.8f))
                ) {
                    Text(app.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "v${app.version} · not installed",
                        style = MaterialTheme.typography.bodySmall,
                        color = Light.ContentSecondary,
                    )
                    Spacer(Modifier.height(gridUnits(0.2f)))
                    Text(
                        "REMOVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Light.ContentSecondary,
                        modifier = Modifier.lightClickable { onRemoveFollowed(app) },
                    )
                }
            }
        }

        if (tracked.isNotEmpty()) {
            // Named, not blended in. An indexed app passed the submission
            // checks and carries a hash the client verifies; a tracked repo has
            // had none of that. Showing them identically would imply a
            // guarantee only one of them has.
            item { SectionHeader("NOT IN BRIGHTMARKET (${tracked.size})") }
            items(tracked, key = { it.repo }) { row ->
                TrackedRowView(
                    row,
                    progressFor[row.pkg ?: row.repo],
                    onInstallTracked,
                    onForgetTracked,
                    onRefreshTracked,
                    refreshing = refreshingRepo.equals(row.repo, ignoreCase = true),
                )
            }
        }
    }
}

/** A tracked repo, resolved (or not) against its GitHub releases. */
data class TrackedRow(
    val repo: String,
    val name: String,
    val pkg: String?,
    val version: String?,
    val apkUrl: String?,
    val installedVersionCode: Long?,
    val versionCode: Long?,
    /** Why there is no version, when there isn't one. Null when all is well. */
    val status: String? = null,
    /** PackageManager's versionName, for the string comparison. */
    val installedVersionName: String? = null,
    /** The release string BrightMarket recorded installing, if it installed it. */
    val installedByMarket: String? = null,
) {
    val updatable: Boolean
        get() = installedVersionCode != null && Version.updateAvailable(
            installedVersionName = installedVersionName,
            installedVersionCode = installedVersionCode,
            installedByMarket = installedByMarket,
            remoteVersion = version,
            remoteVersionCode = versionCode ?: 0L,
            // Guessed from the digits in the tag: nothing here has parsed the
            // APK, so this number is a hint rather than an answer.
            remoteVersionCodeIsReal = false,
        )
}

@Composable
private fun TrackedRowView(
    row: TrackedRow,
    progress: Installer.Progress?,
    onInstall: (TrackedRow) -> Unit,
    onForget: (TrackedRow) -> Unit,
    onRefresh: (TrackedRow) -> Unit,
    refreshing: Boolean,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .lightClickable { if (row.apkUrl != null) onInstall(row) }
            .padding(horizontal = gridUnits(Grid.INSET), vertical = gridUnits(0.8f))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(row.name, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.width(gridUnits(0.5f)))
            Text(
                "UNLISTED",
                style = MaterialTheme.typography.labelSmall,
                color = Light.ContentSecondary,
            )
        }
        Text(
            text = when {
                progress is Installer.Progress.Downloading -> "Downloading…"
                progress is Installer.Progress.AwaitingConfirmation -> "Confirm the install…"
                progress is Installer.Progress.Failed -> progress.reason
                // Was "no APK release found" for every failure, including the
                // ones that say nothing about the repo at all.
                row.status != null -> "${row.repo} · ${row.status}"
                row.updatable ->
                    "${row.repo} · ${Version.installedLabel(row.installedByMarket, row.installedVersionName, row.installedVersionCode ?: 0L)}" +
                        " → ${Version.display(row.version)}"
                row.installedVersionCode != null -> "${row.repo} · ${row.version}"
                else -> "${row.repo} · ${row.version} · not installed"
            },
            style = MaterialTheme.typography.bodySmall,
            color = Light.ContentSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(gridUnits(0.2f)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Re-checks this one repo. Every tracked repo costs its own request,
            // so the all-apps refresh is the expensive way to ask about one.
            Text(
                if (refreshing) "CHECKING…" else "CHECK NOW",
                style = MaterialTheme.typography.labelSmall,
                color = Light.ContentSecondary,
                modifier = if (refreshing) {
                    Modifier
                } else {
                    Modifier.lightClickable { onRefresh(row) }
                },
            )
            Spacer(Modifier.width(gridUnits(1f)))
            Text(
                "FORGET",
                style = MaterialTheme.typography.labelSmall,
                color = Light.ContentSecondary,
                modifier = Modifier.lightClickable { onForget(row) },
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = Light.ContentSecondary,
        modifier = Modifier
            .padding(horizontal = gridUnits(Grid.INSET))
            .padding(top = gridUnits(1f), bottom = gridUnits(0.3f)),
    )
}

@Composable
private fun UpdateRow(entry: Installed, progress: Installer.Progress?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(horizontal = gridUnits(Grid.INSET), vertical = gridUnits(0.8f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(entry.app.icon, entry.app.name, gridUnits(Grid.ICON))
        Spacer(Modifier.width(gridUnits(0.8f)))
        Column(Modifier.weight(1f)) {
        Text(entry.app.name, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = when {
                progress is Installer.Progress.Downloading && progress.total > 0 ->
                    "Downloading ${progress.bytes * 100 / progress.total}%"
                progress is Installer.Progress.Downloading -> "Downloading…"
                progress is Installer.Progress.Verifying -> "Verifying…"
                progress is Installer.Progress.AwaitingConfirmation -> "Confirm the install…"
                progress is Installer.Progress.Failed -> progress.reason
                entry.updatable && entry.isSelf ->
                    "${entry.installedLabel} → ${Version.display(entry.target.version)} · closes Market"
                entry.updatable && entry.target.nightly ->
                    "${entry.installedLabel} → ${Version.display(entry.target.version)} · nightly"
                entry.updatable ->
                    "${entry.installedLabel} → ${Version.display(entry.target.version)}"
                else -> "v${entry.app.version}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = Light.ContentSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        }
    }
}

// ---------------------------------------------------------------------------
// Detail
// ---------------------------------------------------------------------------

@Composable
fun DetailScreen(
    app: App,
    installedVersionCode: Long?,
    progress: Installer.Progress?,
    /** BrightMarket can't uninstall the process running this screen. */
    isSelf: Boolean,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onBack: () -> Unit,
    /** Re-check this one app, rather than the whole catalog. */
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
    /** Hand this app's ADB setup to BrightControl. Null when it needs none. */
    onActivateAdb: (() -> Unit)? = null,
    /** False when BrightControl isn't on the phone, which changes what we offer. */
    controlInstalled: Boolean = true,
    /** Open BrightControl's own page, so it can be installed. */
    onOpenControl: (() -> Unit)? = null,
) {
    // The system back gesture must leave the page. Without this the only way out
    // was a link at the very bottom, which the screenshot strip pushed below the
    // fold -- the page read as a dead end.
    BackHandler(onBack = onBack)

    Column(
        Modifier
            .fillMaxSize()
            .background(Light.Background)
            .verticalScroll(rememberScrollState())
    ) {
        TopBar(
            app.name.uppercase(),
            onBack = onBack,
            onRefresh = onRefresh,
            refreshing = refreshing,
        )

        Column(Modifier.padding(horizontal = gridUnits(Grid.INSET))) {
            // The icon at four units, beside the summary rather than above it.
            // The name is already in the top bar; repeating it under a centred
            // mark would push the install button off the first screen, and that
            // button is the only reason anyone opens this page.
            Row(verticalAlignment = Alignment.Top) {
                AppIcon(app.icon, app.name, gridUnits(4f))
                Spacer(Modifier.width(gridUnits(0.8f)))
                Column {
                    MarkdownText(app.summary, style = MaterialTheme.typography.bodyMedium)

                    Spacer(Modifier.height(gridUnits(0.8f)))
                    Text(
                        "v${app.version}  ·  ${app.size / 1_000_000}MB  ·  ${app.downloads} downloads",
                        style = MaterialTheme.typography.bodySmall,
                        color = Light.ContentSecondary,
                    )

                    // Credit where the app came from. No browser on the phone,
                    // so this is a line of text, not a link -- the full
                    // owner/repo is enough to find upstream from anywhere.
                    if (app.upstream.isNotBlank()) {
                        Spacer(Modifier.height(gridUnits(0.3f)))
                        Text(
                            "Fork of github.com/${app.upstream}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Light.ContentSecondary,
                        )
                    }
                }
            }

            if (app.screenshots.isNotEmpty()) {
                Spacer(Modifier.height(gridUnits(1.2f)))
                ScreenshotStrip(app.screenshots)
            }

            Spacer(Modifier.height(gridUnits(1.5f)))

            val label = when {
                progress is Installer.Progress.Downloading ->
                    if (progress.total > 0) "Downloading ${progress.bytes * 100 / progress.total}%"
                    else "Downloading…"
                progress is Installer.Progress.Verifying -> "Verifying…"
                progress is Installer.Progress.AwaitingConfirmation -> "Confirm the install…"
                progress is Installer.Progress.Failed -> "Retry"
                installedVersionCode == null -> "Install"
                app.versionCode > installedVersionCode -> "Update"
                else -> "Installed"
            }
            val enabled = progress == null || progress is Installer.Progress.Failed
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) Light.Content else Light.ContentSecondary,
                // The one you came here to press, so it gets a real target
                // rather than the bare glyph box it had.
                modifier = Modifier
                    .lightClickable(enabled = enabled, onClick = onInstall)
                    .padding(vertical = gridUnits(0.4f), horizontal = gridUnits(0.1f)),
            )

            // Apps that need a grant LightOS has no screen for. The README says to run these
            // from a computer; BrightControl can run them here instead.
            if (app.adbSetup.isNotEmpty()) {
                Spacer(Modifier.height(gridUnits(1.2f)))
                Text(
                    "NEEDS ADB SETUP",
                    style = MaterialTheme.typography.titleMedium,
                    color = Light.ContentSecondary,
                )
                Spacer(Modifier.height(gridUnits(0.4f)))
                Text(
                    if (controlInstalled) {
                        "This app needs permissions the phone has no settings screen for. " +
                            "BrightControl can grant them without a computer. It will show you " +
                            "exactly what runs before anything happens."
                    } else {
                        "This app needs permissions the phone has no settings screen for. " +
                            "Granting them without a computer needs BrightControl, which isn't " +
                            "installed. Install it and finish its ADB setup first."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Light.ContentSecondary,
                )
                Spacer(Modifier.height(gridUnits(0.5f)))
                app.adbSetup.forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = Light.ContentSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(gridUnits(0.2f)))
                }
                Spacer(Modifier.height(gridUnits(0.4f)))
                Text(
                    text = if (controlInstalled) "ACTIVATE ADB" else "GET BRIGHTCONTROL",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .lightClickable {
                            if (controlInstalled) onActivateAdb?.invoke() else onOpenControl?.invoke()
                        }
                        .padding(vertical = gridUnits(0.4f), horizontal = gridUnits(0.1f)),
                )
            }

            if (progress is Installer.Progress.Downloading && progress.total > 0) {
                Spacer(Modifier.height(gridUnits(0.4f)))
                // Drawn, not Material's LinearProgressIndicator: that component
                // animates and carries a tonal track, neither of which exists in
                // LightOS.
                Canvas(Modifier.fillMaxWidth(0.8f).height(gridUnits(0.2f))) {
                    drawLine(
                        color = Light.ContentSecondary,
                        start = Offset(0f, size.height / 2),
                        end = Offset(size.width, size.height / 2),
                        strokeWidth = size.height,
                    )
                    drawLine(
                        color = Light.Content,
                        start = Offset(0f, size.height / 2),
                        end = Offset(
                            size.width * progress.bytes.toFloat() / progress.total,
                            size.height / 2,
                        ),
                        strokeWidth = size.height,
                    )
                }
            }
            if (progress is Installer.Progress.Failed) {
                Spacer(Modifier.height(gridUnits(0.4f)))
                Text(
                    progress.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = Light.ContentSecondary,
                )
            }

            // Only once it is actually on the phone, and never for BrightMarket
            // itself.
            if (installedVersionCode != null && !isSelf && progress == null) {
                // There was 0.6 units of spacer here and 0.5 of it was eaten by
                // the uninstall's own top padding, which extends its tap target
                // upwards -- leaving about 1dp of real gap between "update" and
                // "remove this app", against the 8dp Android asks for. Hence the
                // fat-finger.
                //
                // Now: a wide gap, a rule to say this is a different kind of
                // thing, another gap, and no top padding on the target so it
                // cannot creep back up into the space.
                Spacer(Modifier.height(gridUnits(1.6f)))
                Canvas(Modifier.fillMaxWidth(0.35f).height(1.dp)) {
                    drawLine(
                        color = Light.ContentSecondary,
                        start = Offset(0f, size.height / 2),
                        end = Offset(size.width, size.height / 2),
                        strokeWidth = size.height,
                    )
                }
                Spacer(Modifier.height(gridUnits(1.2f)))
                Text(
                    "UNINSTALL",
                    style = MaterialTheme.typography.labelLarge,
                    color = Light.ContentSecondary,
                    modifier = Modifier
                        .lightClickable(onClick = onUninstall)
                        // No top padding, on purpose: padding inside the
                        // clickable is part of what you can hit, and upwards is
                        // exactly where it must not grow.
                        .padding(bottom = gridUnits(0.6f), end = gridUnits(2f)),
                )
            }

            if (app.notes.isNotBlank()) {
                Spacer(Modifier.height(gridUnits(1.5f)))
                Text(
                    "What's new",
                    style = MaterialTheme.typography.titleMedium,
                    color = Light.ContentSecondary,
                )
                Spacer(Modifier.height(gridUnits(0.4f)))
                MarkdownText(app.notes, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(gridUnits(2f)))
        }
    }
}
