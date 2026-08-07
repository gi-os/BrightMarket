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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.gios.brightmarket.data.App
import com.gios.brightmarket.data.Installed
import com.gios.brightmarket.data.Sort
import com.gios.brightmarket.install.Installer

// ---------------------------------------------------------------------------
// Chrome
// ---------------------------------------------------------------------------

/**
 * LightGrid top bar: 3 units tall, 1-unit inset, title set in `fine`. LightOS
 * bars are separated from content by space, never by a divider line.
 */
@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, onScan: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(gridUnits(Grid.TOP_BAR))
            .padding(horizontal = gridUnits(Grid.INSET)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Text(
                "<",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .lightClickable(onClick = onBack)
                    .padding(end = gridUnits(0.6f)),
            )
        }
        Text(title, style = MaterialTheme.typography.labelMedium)
        if (onScan != null) {
            Spacer(Modifier.weight(1f))
            Text(
                "SCAN",
                style = MaterialTheme.typography.labelMedium,
                color = Light.ContentSecondary,
                modifier = Modifier.lightClickable(onClick = onScan),
            )
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
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (i == selected) Light.Content else Light.ContentSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** LightTextField: a 3-design-px underline across 80% width. No filled box. */
@Composable
fun SearchField(query: String, onQuery: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = gridUnits(Grid.INSET), vertical = gridUnits(0.4f))
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Light.Content),
            cursorBrush = SolidColor(Light.Content),
            modifier = Modifier
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
                    if (query.isEmpty()) {
                        Text(
                            "Search",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Light.ContentSecondary,
                        )
                    }
                    inner()
                }
            },
        )
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
    confirmingFocusOff: Boolean,
    onToggleFocus: () -> Unit,
    onConfirmFocusOff: () -> Unit,
    onCancelFocusOff: () -> Unit,
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
                "Off. You can browse and search on the phone."
            },
            style = MaterialTheme.typography.bodySmall,
            color = Light.ContentSecondary,
        )
        Spacer(Modifier.height(gridUnits(0.6f)))

        if (confirmingFocusOff) {
            // Leaving focus mode is the direction that needs a pause. Turning it
            // ON is instant -- friction there would only discourage the choice
            // the feature exists to support.
            Text(
                "Turn browsing back on?",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(gridUnits(0.4f)))
            Row {
                Text(
                    "YES, TURN OFF",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.lightClickable(onClick = onConfirmFocusOff),
                )
                Spacer(Modifier.width(gridUnits(1.5f)))
                Text(
                    "CANCEL",
                    style = MaterialTheme.typography.labelLarge,
                    color = Light.ContentSecondary,
                    modifier = Modifier.lightClickable(onClick = onCancelFocusOff),
                )
            }
        } else {
            Text(
                if (focusEnabled) "TURN OFF" else "TURN ON",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.lightClickable(onClick = onToggleFocus),
            )
        }

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
fun AppRow(app: App, installedVersionCode: Long?, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(horizontal = gridUnits(Grid.INSET), vertical = gridUnits(0.8f))
    ) {
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
                    "build $installedVersionCode → v${app.version}"
                else -> "v${app.version}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = Light.ContentSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
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
            else -> LazyColumn(Modifier.weight(1f)) {
                items(apps, key = { it.pkg }) { app ->
                    AppRow(app, installed[app.pkg]) { onOpen(app) }
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
    tracked: List<TrackedRow>,
    progressFor: Map<String, Installer.Progress>,
    loading: Boolean,
    focusMode: Boolean,
    onUpdateAll: () -> Unit,
    onOpen: (App) -> Unit,
    onInstallTracked: (TrackedRow) -> Unit,
    onForgetTracked: (TrackedRow) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        if (updates.isEmpty() && upToDate.isEmpty()) {
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

        if (tracked.isNotEmpty()) {
            // Named, not blended in. An indexed app passed the submission
            // checks and carries a hash the client verifies; a tracked repo has
            // had none of that. Showing them identically would imply a
            // guarantee only one of them has.
            item { SectionHeader("NOT IN BRIGHTMARKET (${tracked.size})") }
            items(tracked, key = { it.repo }) { row ->
                TrackedRowView(row, progressFor[row.pkg ?: row.repo], onInstallTracked, onForgetTracked)
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
) {
    val updatable: Boolean
        get() = versionCode != null && installedVersionCode != null &&
            versionCode > installedVersionCode
}

@Composable
private fun TrackedRowView(
    row: TrackedRow,
    progress: Installer.Progress?,
    onInstall: (TrackedRow) -> Unit,
    onForget: (TrackedRow) -> Unit,
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
                row.apkUrl == null -> "${row.repo} · no APK release found"
                row.updatable -> "${row.repo} · update to ${row.version}"
                row.installedVersionCode != null -> "${row.repo} · ${row.version}"
                else -> "${row.repo} · ${row.version} · not installed"
            },
            style = MaterialTheme.typography.bodySmall,
            color = Light.ContentSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(gridUnits(0.2f)))
        Text(
            "FORGET",
            style = MaterialTheme.typography.labelSmall,
            color = Light.ContentSecondary,
            modifier = Modifier.lightClickable { onForget(row) },
        )
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
    Column(
        Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(horizontal = gridUnits(Grid.INSET), vertical = gridUnits(0.8f))
    ) {
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
                    "build ${entry.installedVersionCode} → v${entry.app.version} · closes Market"
                entry.updatable ->
                    "build ${entry.installedVersionCode} → v${entry.app.version}"
                else -> "v${entry.app.version}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = Light.ContentSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
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
    onInstall: () -> Unit,
    onBack: () -> Unit,
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
        TopBar(app.name.uppercase(), onBack = onBack)

        Column(Modifier.padding(horizontal = gridUnits(Grid.INSET))) {
            Text(app.summary, style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(gridUnits(0.8f)))
            Text(
                "v${app.version}  ·  ${app.size / 1_000_000}MB  ·  ${app.downloads} downloads",
                style = MaterialTheme.typography.bodySmall,
                color = Light.ContentSecondary,
            )

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
                modifier = Modifier.lightClickable(enabled = enabled, onClick = onInstall),
            )

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

            if (app.notes.isNotBlank()) {
                Spacer(Modifier.height(gridUnits(1.5f)))
                Text(
                    "What's new",
                    style = MaterialTheme.typography.titleMedium,
                    color = Light.ContentSecondary,
                )
                Spacer(Modifier.height(gridUnits(0.4f)))
                Text(app.notes, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(gridUnits(2f)))
        }
    }
}
