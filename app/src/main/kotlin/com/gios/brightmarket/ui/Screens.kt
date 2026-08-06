package com.gios.brightmarket.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.gios.brightmarket.data.App
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
    installed: Map<String, Long>,
    loading: Boolean,
    error: String?,
    onSort: (Sort) -> Unit,
    onOpen: (App) -> Unit,
    onImport: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Light.Background)) {
        TopBar("BRIGHTMARKET")
        SortRow(sort, onSort)

        when {
            loading && apps.isEmpty() -> Message("Loading…")
            error != null && apps.isEmpty() -> Message(error)
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
        TopBar(app.name.uppercase())

        Column(Modifier.padding(horizontal = gridUnits(1f))) {
            Text(app.summary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(gridUnits(1f)))
            Text(
                "v${app.version}  ·  ${app.size / 1_000_000}MB  ·  ${app.downloads} downloads",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = Light.ContentSecondary,
            )

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
