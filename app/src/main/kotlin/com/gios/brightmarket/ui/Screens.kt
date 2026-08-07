package com.gios.brightmarket.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.gios.brightmarket.data.App
import com.gios.brightmarket.data.Installed
import com.gios.brightmarket.data.Sort
import com.gios.brightmarket.install.Installer

@Composable
fun TopBar(title: String) {
    // LightGrid: top bar is 3 units tall with a 1-unit horizontal inset, and is
    // separated from content by space rather than a divider.
    Box(
        Modifier
            .fillMaxWidth()
            .height(gridUnits(3f))
            .padding(horizontal = gridUnits(1f)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(title, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun SortRow(current: Sort, onSelect: (Sort) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = gridUnits(1f), vertical = gridUnits(0.5f)),
        horizontalArrangement = Arrangement.spacedBy(gridUnits(1.5f)),
    ) {
        Sort.entries.forEach { sort ->
            val selected = sort == current
            Text(
                text = sort.label,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) Light.Content else Light.ContentSecondary,
                modifier = Modifier.lightClickable { onSelect(sort) },
            )
        }
    }
}

/**
 * LightTextField: an underline at 3 design px across 80% width, no filled
 * container and no floating label. Material's decorated text fields appear
 * nowhere in LightOS, so this is BasicTextField with the underline drawn on.
 */
@Composable
fun SearchField(query: String, onQuery: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = gridUnits(1f), vertical = gridUnits(0.4f))
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                .copy(color = Light.Content),
            cursorBrush = SolidColor(Light.Content),
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .drawUnderline(),
            decorationBox = { inner ->
                Box(Modifier.padding(vertical = gridUnits(0.3f))) {
                    if (query.isEmpty()) {
                        Text(
                            "Search",
                            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                            color = Light.ContentSecondary,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

private fun Modifier.drawUnderline(): Modifier =
    drawBehind {
        val y = size.height
        drawLine(
            color = androidx.compose.ui.graphics.Color(0xFFBBBBBB),
            start = androidx.compose.ui.geometry.Offset(0f, y),
            end = androidx.compose.ui.geometry.Offset(size.width, y),
            strokeWidth = 3f,
        )
    }

/**
 * Categories scroll horizontally rather than wrapping: the LP3 panel is narrow,
 * and a wrapping row would push the list itself below the fold as soon as a
 * handful of categories exist.
 */
@Composable
fun CategoryRow(categories: List<String>, current: String, onSelect: (String) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth().padding(vertical = gridUnits(0.4f)),
        contentPadding = PaddingValues(horizontal = gridUnits(1f)),
        horizontalArrangement = Arrangement.spacedBy(gridUnits(1.2f)),
    ) {
        items(categories) { category ->
            val selected = category.equals(current, ignoreCase = true)
            Text(
                text = category.replaceFirstChar { it.uppercase() },
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) Light.Content else Light.ContentSecondary,
                modifier = Modifier.lightClickable { onSelect(category) },
            )
        }
    }
}

@Composable
fun AppRow(
    app: App,
    installedVersionCode: Long?,
    onClick: () -> Unit,
) {
    // LightOS list rows are `copy` over `detail`.
    Column(
        Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(horizontal = gridUnits(1f), vertical = gridUnits(0.8f))
    ) {
        Text(app.name, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
        val status = when {
            installedVersionCode == null -> app.summary
            app.versionCode > installedVersionCode -> "Update to ${app.version}"
            else -> "Installed"
        }
        Text(
            text = status,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = Light.ContentSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ListScreen(
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
    onImport: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Light.Background)) {
        SearchField(query, onQuery)
        if (categories.size > 1) CategoryRow(categories, category, onCategory)
        SortRow(sort, onSort)

        val filtering = query.isNotBlank() || category != "All"

        when {
            loading && apps.isEmpty() -> Message("Loading…")
            error != null && apps.isEmpty() -> Message(error)
            // Distinguish "nothing matched your filter" from "the index is
            // empty" -- otherwise a typo looks like the store is broken.
            apps.isEmpty() && filtering -> Message("Nothing matches that.")
            apps.isEmpty() -> Message("No apps yet.")
            else -> LazyColumn(Modifier.weight(1f)) {
                items(apps, key = { it.pkg }) { app ->
                    AppRow(app, installed[app.pkg]) { onOpen(app) }
                }
                item {
                    Text(
                        "Import from Obtainium",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = Light.ContentSecondary,
                        modifier = Modifier
                            .lightClickable(onClick = onImport)
                            .padding(horizontal = gridUnits(1f), vertical = gridUnits(1.2f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(gridUnits(1f)), contentAlignment = Alignment.Center) {
        Text(text, color = Light.ContentSecondary)
    }
}

enum class Tab(val label: String) { BROWSE("Browse"), UPDATES("Updates") }

/**
 * LightBottomBar is LightOS's ActionBar: at most 5 icon items, but at most 3 if
 * any item is text. Two text destinations sits comfortably inside that.
 */
@Composable
fun TabBar(current: Tab, updateCount: Int, onSelect: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(gridUnits(4f))
            .padding(horizontal = gridUnits(1f)),
        horizontalArrangement = Arrangement.spacedBy(gridUnits(2f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { tab ->
            val selected = tab == current
            // The count belongs on the label, not in a coloured badge -- there
            // is no accent colour in the palette to spend on one.
            val text = if (tab == Tab.UPDATES && updateCount > 0) {
                "${tab.label} ($updateCount)"
            } else {
                tab.label
            }
            Text(
                text = text.uppercase(),
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                color = if (selected) Light.Content else Light.ContentSecondary,
                modifier = Modifier.lightClickable { onSelect(tab) },
            )
        }
    }
}

@Composable
fun UpdatesScreen(
    updates: List<Installed>,
    upToDate: List<Installed>,
    progressFor: Map<String, Installer.Progress>,
    loading: Boolean,
    onUpdateAll: () -> Unit,
    onOpen: (App) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        if (updates.isEmpty() && upToDate.isEmpty()) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(gridUnits(2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (loading) "Loading…"
                        // Distinguish "nothing to update" from "you have none of
                        // these apps" -- they need different next steps.
                        else "None of these apps are installed yet.",
                        color = Light.ContentSecondary,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                }
            }
            return@LazyColumn
        }

        if (updates.isNotEmpty()) {
            item {
                Text(
                    "UPDATE ALL (${updates.size})",
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    color = Light.Content,
                    modifier = Modifier
                        .lightClickable(
                            // Disabled while anything is mid-install: a second
                            // pass would re-download what's already going.
                            enabled = progressFor.isEmpty(),
                            onClick = onUpdateAll,
                        )
                        .padding(horizontal = gridUnits(1f), vertical = gridUnits(1f)),
                )
            }
            items(updates, key = { it.app.pkg }) { entry ->
                UpdateRow(entry, progressFor[entry.app.pkg]) { onOpen(entry.app) }
            }
        }

        if (upToDate.isNotEmpty()) {
            item {
                Text(
                    "UP TO DATE",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    color = Light.ContentSecondary,
                    modifier = Modifier.padding(
                        horizontal = gridUnits(1f),
                        vertical = gridUnits(1f),
                    ),
                )
            }
            items(upToDate, key = { it.app.pkg }) { entry ->
                UpdateRow(entry, progressFor[entry.app.pkg]) { onOpen(entry.app) }
            }
        }
    }
}

@Composable
private fun UpdateRow(
    entry: Installed,
    progress: Installer.Progress?,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(horizontal = gridUnits(1f), vertical = gridUnits(0.8f))
    ) {
        Text(entry.app.name, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
        val detail = when {
            progress is Installer.Progress.Downloading && progress.total > 0 ->
                "Downloading ${progress.bytes * 100 / progress.total}%"
            progress is Installer.Progress.Downloading -> "Downloading…"
            progress is Installer.Progress.Verifying -> "Verifying…"
            progress is Installer.Progress.AwaitingConfirmation -> "Confirm the install…"
            progress is Installer.Progress.Failed -> progress.reason
            // Show both sides of the comparison: "1.2.10 → 1.3.19" says why the
            // row is here far better than the word "Update" does.
            entry.updatable && entry.isSelf ->
                // Updating the marketplace closes it. Saying so up front is
                // better than the app appearing to crash mid-update.
                "v${versionOf(entry.installedVersionCode)} → v${entry.app.version} · closes Market"
            entry.updatable -> "v${versionOf(entry.installedVersionCode)} → v${entry.app.version}"
            else -> "v${entry.app.version}"
        }
        Text(
            detail,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = Light.ContentSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The installed versionCode is a run number, not a version name, and the phone
 * has no record of the name that shipped with it. Rendering the raw code is
 * more honest than inventing a plausible-looking string.
 */
private fun versionOf(code: Long): String = code.toString()

@Composable
fun DetailScreen(
    app: App,
    installedVersionCode: Long?,
    progress: Installer.Progress?,
    onInstall: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Light.Background)
            .verticalScroll(rememberScrollState())
    ) {
        // The system back gesture/key must leave the page. Without this the only
        // way out was the BACK text at the very bottom, which the screenshot
        // strip pushed below the fold -- the page read as a dead end.
        BackHandler(onBack = onBack)

        Row(
            Modifier
                .fillMaxWidth()
                .height(gridUnits(3f))
                .lightClickable(onClick = onBack)
                .padding(horizontal = gridUnits(1f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "< BACK",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = Light.ContentSecondary,
            )
        }

        Column(Modifier.padding(horizontal = gridUnits(1f))) {
            // The bar's title is set in `fine` and reads as chrome. The product
            // itself needs a real heading, or the page opens with a description
            // and no indication of what it describes.
            Text(
                app.name,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(gridUnits(0.5f)))

            Text(app.summary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(gridUnits(1f)))
            Text(
                "v${app.version}  ·  ${app.size / 1_000_000}MB  ·  ${app.downloads} downloads",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = Light.ContentSecondary,
            )

            if (app.screenshots.isNotEmpty()) {
                Spacer(Modifier.height(gridUnits(1.2f)))
                ScreenshotStrip(app.screenshots)
            }

            Spacer(Modifier.height(gridUnits(1.5f)))

            val label = when {
                progress is Installer.Progress.Downloading ->
                    if (progress.total > 0)
                        "Downloading ${progress.bytes * 100 / progress.total}%"
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
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                color = if (enabled) Light.Content else Light.ContentSecondary,
                modifier = Modifier.lightClickable(enabled = enabled, onClick = onInstall),
            )

            if (progress is Installer.Progress.Downloading && progress.total > 0) {
                Spacer(Modifier.height(gridUnits(0.5f)))
                LinearProgressIndicator(
                    progress = { progress.bytes.toFloat() / progress.total },
                    modifier = Modifier.fillMaxWidth(),
                    color = Light.Content,
                    trackColor = Light.ContentSecondary,
                )
            }
            if (progress is Installer.Progress.Failed) {
                Spacer(Modifier.height(gridUnits(0.5f)))
                Text(
                    progress.reason,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = Light.ContentSecondary,
                )
            }

            if (app.notes.isNotBlank()) {
                Spacer(Modifier.height(gridUnits(1.5f)))
                Text(
                    "What's new",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    color = Light.ContentSecondary,
                )
                Spacer(Modifier.height(gridUnits(0.4f)))
                Text(app.notes, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(gridUnits(2f)))
            Text(
                "BACK",
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                color = Light.ContentSecondary,
                modifier = Modifier.lightClickable(onClick = onBack),
            )
            Spacer(Modifier.height(gridUnits(2f)))
        }
    }
}
