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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.gios.brightmarket.data.App
import com.gios.brightmarket.data.Installed
import com.gios.brightmarket.data.Sort
import com.gios.brightmarket.data.Target
import com.gios.brightmarket.data.Version
import com.gios.brightmarket.data.target
import com.gios.brightmarket.hw.WheelScroll
import com.gios.brightmarket.install.Installer
import java.util.Locale

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
    /** How many apps have been given a channel of their own. */
    nightlyOverrides: Int = 0,
    /** Whether this phone is counted in the catalogue's install figures. */
    pulseEnabled: Boolean = true,
    /** The literal bytes the next count would send. Read only when asked for. */
    pulseSample: () -> String = { "" },
    onToggleFocus: () -> Unit,
    onToggleNightly: () -> Unit,
    onTogglePulse: () -> Unit = {},
    /** Whether updates install by themselves. */
    autoUpdate: Boolean = false,
    /** What the last automatic run did, in one line, or null before the first. */
    autoUpdateLast: String? = null,
    onToggleAutoUpdate: () -> Unit = {},
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
                "Nightly by default. Every build as it's made, including the ones " +
                    "that turn out to be wrong. Newer, and rougher."
            } else {
                "Official releases by default. Nightly builds are made on every " +
                    "change and aren't offered to you."
            },
            style = MaterialTheme.typography.bodySmall,
            color = Light.ContentSecondary,
        )
        Spacer(Modifier.height(gridUnits(0.4f)))
        // Says where the real control is. A phone-wide channel is the wrong unit:
        // people opt into nightlies for one app they are helping test, not for
        // the keyboard. This switch is only what an app falls back to.
        Text(
            if (nightlyOverrides == 0) {
                "This is the default. Any app that publishes nightlies can be set " +
                    "on its own page."
            } else {
                "$nightlyOverrides app${if (nightlyOverrides == 1) "" else "s"} " +
                    "set on its own page, which wins over this."
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
            if (nightly) "DEFAULT TO OFFICIAL RELEASES" else "DEFAULT TO NIGHTLY BUILDS",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .lightClickable(onClick = onToggleNightly)
                .padding(vertical = gridUnits(0.4f)),
        )

        Spacer(Modifier.height(gridUnits(2f)))
        Text("AUTOMATIC UPDATES", style = MaterialTheme.typography.titleMedium, color = Light.ContentSecondary)
        Spacer(Modifier.height(gridUnits(0.4f)))
        Text(
            if (autoUpdate) {
                "On. Official releases install by themselves while the phone charges " +
                    "on Wi-Fi, once they are a day old, and never while the app is in use."
            } else {
                "Off. Updates wait for you in the Updates tab."
            },
            style = MaterialTheme.typography.bodySmall,
            color = Light.ContentSecondary,
        )
        if (autoUpdate) {
            Spacer(Modifier.height(gridUnits(0.4f)))
            // Android decides this, not BrightMarket: only an app BrightMarket installed
            // can be updated without a tap. Saying so up front beats a setting that
            // looks like it skipped half the phone.
            Text(
                "An app you installed some other way asks for one tap the first time. " +
                    "After that it updates by itself too. Nightly builds stay manual.",
                style = MaterialTheme.typography.bodySmall,
                color = Light.ContentSecondary,
            )
            if (autoUpdateLast != null) {
                Spacer(Modifier.height(gridUnits(0.4f)))
                Text(autoUpdateLast, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(gridUnits(0.6f)))
        Text(
            if (autoUpdate) "TURN OFF AUTOMATIC UPDATES" else "TURN ON AUTOMATIC UPDATES",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .lightClickable(onClick = onToggleAutoUpdate)
                .padding(vertical = gridUnits(0.4f)),
        )

        Spacer(Modifier.height(gridUnits(2f)))
        Text("COUNTING", style = MaterialTheme.typography.titleMedium, color = Light.ContentSecondary)
        Spacer(Modifier.height(gridUnits(0.4f)))
        // Download counts cannot say how many people have an app: a mirror
        // pulling a release all month reads the same as a thousand installs.
        // This is the thing that can say it, and it is worth being exact about
        // what it counts -- installs made through BrightMarket, on this phone,
        // of apps the catalogue already lists in public.
        Text(
            if (pulseEnabled) {
                "On. When you install, update or remove a catalogue app, this " +
                    "sends the app's name and the version it moved to. Nothing else."
            } else {
                "Off. This phone is not counted."
            },
            style = MaterialTheme.typography.bodySmall,
            color = Light.ContentSecondary,
        )
        Spacer(Modifier.height(gridUnits(0.4f)))
        Text(
            "There is no install id, no device id and no account, so two of " +
                "these can't be recognised as coming from the same phone.",
            style = MaterialTheme.typography.bodySmall,
            color = Light.ContentSecondary,
        )
        Spacer(Modifier.height(gridUnits(0.6f)))

        // Shown rather than described. A sentence claiming anonymity is worth
        // less than the bytes, and the bytes are short enough to read.
        var showSample by remember { mutableStateOf(false) }
        var sample by remember { mutableStateOf("") }
        if (showSample) {
            Text(
                sample,
                style = MaterialTheme.typography.bodySmall,
                color = Light.ContentSecondary,
            )
            Spacer(Modifier.height(gridUnits(0.6f)))
        }
        Text(
            if (showSample) "HIDE WHAT IS SENT" else "SHOW WHAT IS SENT",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .lightClickable(onClick = {
                    if (!showSample) sample = pulseSample()
                    showSample = !showSample
                })
                .padding(vertical = gridUnits(0.4f)),
        )
        Spacer(Modifier.height(gridUnits(0.2f)))
        Text(
            if (pulseEnabled) "DON'T COUNT THIS PHONE" else "COUNT THIS PHONE",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .lightClickable(onClick = onTogglePulse)
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
    /**
     * What this app would install on its own channel, and whether that counts as
     * an update. Both are passed in, never worked out here: a row that decides
     * for itself is a row that can disagree with the page it opens.
     */
    target: Target = app.target(false),
    updatable: Boolean = installedVersionCode != null && target.versionCode > installedVersionCode,
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
                updatable -> "UPDATE"
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
                updatable ->
                    "${Version.installedLabel(null, installedVersion, installedVersionCode)} → " +
                        Version.display(target.version) +
                        if (target.nightly) " · nightly" else ""
                else -> "v${target.version}" + if (target.nightly) " · nightly" else ""
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
    /** What an app would install, on that app's own channel. */
    targetOf: (App) -> Target = { it.target(false) },
    /** The one update verdict, shared with the Updates tab and the detail page. */
    updatableOf: (App) -> Boolean = { false },
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
                    AppRow(
                        app = app,
                        installedVersionCode = installed[app.pkg],
                        installedVersion = installedVersionOf(app.pkg),
                        target = targetOf(app),
                        updatable = updatableOf(app),
                    ) { onOpen(app) }
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
    onTogglePrerelease: (TrackedRow) -> Unit = {},
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
                    onTogglePrerelease,
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
    /** Whether this repo is set to take pre-releases. */
    val prereleaseOn: Boolean = false,
    /** Whether the release found is itself a pre-release. */
    val isPrerelease: Boolean = false,
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
    onTogglePrerelease: (TrackedRow) -> Unit,
    refreshing: Boolean,
) {
    // Named on the row so a pre-release is never offered as if it were stable.
    val pre = if (row.isPrerelease) " · pre-release" else ""
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
                        " → ${Version.display(row.version)}$pre"
                row.installedVersionCode != null -> "${row.repo} · ${row.version}$pre"
                else -> "${row.repo} · ${row.version}$pre · not installed"
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
            Spacer(Modifier.width(gridUnits(1f)))
            // The channel, on the row it is about -- the same per-app rule as
            // the NIGHTLY switch on an indexed app's page. Bright when on, so a
            // repo taking pre-releases can be spotted down the list.
            Text(
                if (row.prereleaseOn) "PRE-RELEASES: ON" else "PRE-RELEASES: OFF",
                style = MaterialTheme.typography.labelSmall,
                color = if (row.prereleaseOn) Light.Content else Light.ContentSecondary,
                modifier = if (refreshing) {
                    Modifier
                } else {
                    Modifier.lightClickable { onTogglePrerelease(row) }
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

/**
 * BrightMarket's own update, on the page people land on.
 *
 * It used to be only a row in the Updates tab, and 42 of the 138 phones
 * counted on 2026-09-28 (30%) were still on an older BrightMarket. The app
 * that updates everything else was the one app people did not update.
 *
 * Inverted, white on black, because this is the one row that changes what
 * every other row can do. It paints its own background: a bar that relies on
 * its parent's shows whatever is behind it.
 */
@Composable
fun SelfUpdateBar(entry: Installed, progress: Installer.Progress?, onUpdate: () -> Unit) {
    val busy = progress != null && progress !is Installer.Progress.Failed
    Row(
        Modifier
            .fillMaxWidth()
            .background(Light.Content)
            .lightClickable(enabled = !busy, onClick = onUpdate)
            .padding(horizontal = gridUnits(Grid.INSET), vertical = gridUnits(0.7f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "BRIGHTMARKET UPDATE",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.14f.em),
                color = Light.Background,
            )
            Text(
                when {
                    progress is Installer.Progress.Downloading && progress.total > 0 ->
                        "Downloading ${progress.bytes * 100 / progress.total}%"
                    progress is Installer.Progress.Downloading -> "Downloading…"
                    progress is Installer.Progress.Verifying -> "Verifying…"
                    progress is Installer.Progress.AwaitingConfirmation -> "Confirm the install…"
                    progress is Installer.Progress.Failed -> progress.reason
                    else -> "${entry.installedLabel} → ${Version.display(entry.target.version)}" +
                        (if (entry.target.nightly) " · nightly" else "") + " · closes Market"
                },
                style = MaterialTheme.typography.bodySmall,
                color = Light.Background,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(gridUnits(0.8f)))
        Text(
            if (busy) "…" else "UPDATE",
            style = MaterialTheme.typography.labelLarge,
            color = Light.Background,
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
                // The copy on the phone is signed by somebody else, so nothing here can replace it
                // and no update will be offered. Saying so beats a row that silently never moves:
                // the same applicationId is shipped by more than one project, and the fix is to
                // uninstall whichever one you don't want.
                entry.foreign -> "${entry.installedLabel} · another build, signed elsewhere"
                else -> "v${entry.target.version}"
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

/**
 * A drawn rule.
 *
 * LightOS separates a bar from content with space and never with a line -- but
 * a rule is the one structural device it does use *inside* content, and this
 * page needs it four times over. Drawn rather than Material's divider, which
 * brings its own thickness and colour tokens.
 */
@Composable
private fun Rule(color: Color = Light.ContentSecondary, fraction: Float = 1f) {
    Canvas(
        Modifier
            .fillMaxWidth(fraction)
            .height(1.dp)
    ) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = size.height,
        )
    }
}

/** The same rule stood on end, dividing the cells of the stat rail. */
@Composable
private fun VRule(color: Color = Light.ContentSecondary) {
    Canvas(
        Modifier
            .width(1.dp)
            .fillMaxHeight()
    ) {
        drawLine(
            color = color,
            start = Offset(size.width / 2, 0f),
            end = Offset(size.width / 2, size.height),
            strokeWidth = size.width,
        )
    }
}

/**
 * The numbers the decision is made on, in one rail of hairlines.
 *
 * These used to be a single comma-separated line under the summary --
 * "v1.23.02 · 7MB · 1284 downloads" -- which is four facts written as one
 * sentence, so none of them could be found without reading all of them. In
 * cells they can be scanned, and the size sits next to the button that spends
 * it.
 *
 * Hairlines, not a card: nothing here is filled, rounded or raised, and the
 * rules are the same 1dp the icon frame uses.
 */
@Composable
private fun StatRail(stats: List<Pair<String, String>>) {
    Column {
        Rule()
        // Intrinsic height so the cell dividers can fill the row. Without it
        // fillMaxHeight inside a Row of unconstrained height measures as zero
        // and the vertical rules simply don't draw.
        Row(Modifier.height(IntrinsicSize.Min)) {
            stats.forEach { (key, value) ->
                VRule()
                Column(
                    Modifier
                        .weight(1f)
                        .padding(
                            start = gridUnits(0.6f),
                            end = gridUnits(0.2f),
                            top = gridUnits(0.5f),
                            bottom = gridUnits(0.6f),
                        )
                ) {
                    Text(
                        key,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.14f.em),
                        color = Light.ContentSecondary,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(gridUnits(0.2f)))
                    Text(
                        value,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            VRule()
        }
        Rule()
    }
}

/**
 * The one thing on this page anyone came to press.
 *
 * Full width, 3.8 units tall, between two rules, and above the screenshots so
 * it is reachable without scrolling. The bar carries the state: the label and
 * the hint both change as an install runs, while the bar itself never moves and
 * never changes size, so a thumb that found it once does not have to find it
 * again. It replaced a bare word of text whose tap target was the width of the
 * word.
 */
@Composable
private fun ActionBar(
    label: String,
    hint: String,
    units: Float = 3.8f,
    labelColor: Color = Light.Content,
    ruleColor: Color = Light.Content,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(Modifier.lightClickable(enabled = enabled, onClick = onClick)) {
        Rule(ruleColor)
        Row(
            Modifier
                .fillMaxWidth()
                .height(gridUnits(units))
                .padding(horizontal = gridUnits(0.2f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = labelColor,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (hint.isNotBlank()) {
                Spacer(Modifier.width(gridUnits(0.4f)))
                Text(
                    hint.uppercase(),
                    style = MaterialTheme.typography.bodySmall.copy(letterSpacing = 0.1f.em),
                    color = Light.ContentSecondary,
                    maxLines = 1,
                )
            }
        }
        Rule(ruleColor)
    }
}

private val MONTHS = listOf(
    "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC",
)

/**
 * "2026-09-05T11:22:33Z" -> "05 SEP". A rail cell has room for a day and a
 * month and nothing else.
 *
 * Substring rather than a date parser, on purpose: this is the release date as
 * GitHub published it, and running it through a formatter would shift it into
 * the phone's timezone -- turning "published on the 1st" into "31 AUG" for
 * anyone west of UTC, for a date nobody is doing arithmetic on.
 */
private fun shortDate(iso: String): String {
    if (iso.length < 10) return "—"
    val month = iso.substring(5, 7).toIntOrNull() ?: return "—"
    if (month !in 1..12) return "—"
    return "${iso.substring(8, 10)} ${MONTHS[month - 1]}"
}

/**
 * Download size to one decimal.
 *
 * The line this replaced divided by a million in integer arithmetic, so every
 * app under 1MB read "0MB" and 7.4 and 7.9 were the same number.
 */
private fun megabytes(bytes: Long): String =
    if (bytes <= 0) "—" else String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)

private fun thousands(n: Int): String = String.format(Locale.US, "%,d", n)

@Composable
fun DetailScreen(
    app: App,
    installedVersionCode: Long?,
    /**
     * The build pressing the button will actually install, resolved on this
     * app's own channel.
     *
     * This page used to read [App.version] and [App.versionCode] directly while
     * `install()` installed `app.target(channel)`. On the nightly channel those
     * are two different builds, so the page compared the *stable* release
     * against what was on the phone, decided there was nothing to do, and
     * labelled the button INSTALLED — for an app the Updates tab was listing as
     * needing an update. Update-all worked because it never asked this page.
     * There is one resolved target now, and the label and the installer both
     * read it.
     */
    target: Target,
    /** The one update verdict, shared with the Updates tab. Never recomputed here. */
    updatable: Boolean,
    progress: Installer.Progress?,
    /** BrightMarket can't uninstall the process running this screen. */
    isSelf: Boolean,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onBack: () -> Unit,
    /**
     * What is on the phone right now, named the way the Updates tab names it.
     *
     * Supplied rather than derived, because deriving it needs both
     * PackageManager's versionName and the release string BrightMarket recorded
     * installing, and the Activity is where both of those live. Null falls back
     * to the versionCode, which is the fallback [Version.installedLabel] makes
     * anyway.
     */
    installedLabel: String? = null,
    /** Re-check this one app, rather than the whole catalog. */
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
    /** Hand this app's ADB setup to BrightControl. Null when it needs none. */
    onActivateAdb: (() -> Unit)? = null,
    /** False when BrightControl isn't on the phone, which changes what we offer. */
    controlInstalled: Boolean = true,
    /** Open BrightControl's own page, so it can be installed. */
    onOpenControl: (() -> Unit)? = null,
    /** True when this app is on the nightly channel. */
    nightlyOn: Boolean = false,
    /**
     * Put this one app on or off nightlies. Null when it publishes no
     * prerelease, because then there is nothing to choose between.
     */
    onToggleNightly: (() -> Unit)? = null,
) {
    // -1 for "no screenshot open". Keyed on the package so opening a different
    // app's page can't inherit an index from the last one.
    var zoom by remember(app.pkg) { mutableStateOf(-1) }

    // Whether BrightControl has already been launched for this app on this
    // visit. It is the only thing this side can honestly know: BrightControl
    // reports nothing back, so the bar says "handed over" and never "granted".
    var handedOver by remember(app.pkg) { mutableStateOf(false) }

    // The system back gesture must leave the page. Without this the only way out
    // was a link at the very bottom, which the screenshot strip pushed below the
    // fold -- the page read as a dead end. An open screenshot takes the press
    // first, so back closes the picture rather than the page under it.
    BackHandler {
        if (zoom >= 0) zoom = -1 else onBack()
    }

    // The date belonging to the build the button will install, which on the
    // nightly channel is not the stable release's date.
    val publishedAt =
        if (target.nightly) app.preview?.publishedAt.orEmpty().ifBlank { app.publishedAt }
        else app.publishedAt

    val enabled = progress == null || progress is Installer.Progress.Failed

    // Label, hint and the sentence under the bar, all out of one decision.
    // They were three separate conditionals before, which is how the button and
    // the text beneath it came to describe different things.
    val (action, hint, status) = when {
        progress is Installer.Progress.Downloading -> Triple(
            if (progress.total > 0) "Downloading ${progress.bytes * 100 / progress.total}%"
            else "Downloading…",
            if (progress.total > 0) {
                String.format(Locale.US, "%.1f", progress.bytes / 1_000_000.0) +
                    " / " + megabytes(progress.total)
            } else "",
            "Checking the download against the hash in the index once it lands. " +
                "Nothing installs until you confirm it.",
        )
        progress is Installer.Progress.Verifying -> Triple(
            "Verifying…",
            "SHA-256",
            "Matching the file against the hash the index builder generated from " +
                "the release asset.",
        )
        progress is Installer.Progress.AwaitingConfirmation -> Triple(
            "Confirm the install…",
            "System dialog",
            "Android's own installer has it now. This screen is waiting for the result.",
        )
        progress is Installer.Progress.Failed -> Triple("Retry", "", progress.reason)
        installedVersionCode == null -> Triple(
            "Install",
            megabytes(target.size),
            "Not on the phone yet. Downloads from the GitHub release, checked " +
                "against the hash in the index, then Android asks you to confirm.",
        )
        updatable -> Triple(
            "Update",
            (installedLabel ?: Version.installedLabel(null, null, installedVersionCode ?: 0L)) +
                " → " + Version.display(target.version),
            Version.display(target.version) +
                (
                    if (shortDate(publishedAt) == "—") " is the newest release."
                    else " was published ${shortDate(publishedAt)}."
                    ) +
                " " + megabytes(target.size) + " over your connection, then the " +
                "phone asks you to confirm.",
        )
        else -> Triple(
            "Installed",
            "Up to date",
            "On the phone and current. It checks again whenever you pull the " +
                "catalog down.",
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Light.Background)
    ) {
        Column(
            Modifier
                .fillMaxSize()
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

                        // Provenance in one line: where the build comes from,
                        // what it forks, and the package it installs as. No
                        // browser on the phone, so this is text rather than a
                        // link -- owner/repo is enough to find upstream from
                        // anywhere, and the package name is what you would go
                        // looking for in Settings. The version, size and
                        // download count used to be crammed in here; they are
                        // the rail below now.
                        Spacer(Modifier.height(gridUnits(0.6f)))
                        Text(
                            buildString {
                                append(app.repo)
                                if (app.upstream.isNotBlank()) {
                                    append("  ·  fork of ${app.upstream}")
                                }
                                append("  ·  ${app.pkg}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Light.ContentSecondary,
                        )
                    }
                }

                Spacer(Modifier.height(gridUnits(1.2f)))
                StatRail(
                    listOf(
                        // Named for the channel it came off, so a nightly build
                        // number is never read as a release.
                        (if (target.nightly) "NIGHTLY" else "VERSION") to
                            (Version.normalize(target.version) ?: "—"),
                        "SIZE" to megabytes(target.size),
                        // Users REPLACE gets here rather than joining them. A fifth
                        // cell on a 240dp screen clips its own label, and of the two
                        // this is the one worth the space: a get is bytes leaving a
                        // release, which a mirror runs up without a phone existing.
                        // Gets stay on the web catalogue, where there is room for both.
                        //
                        // Zero means nobody has reported yet, not nobody uses it, so
                        // it falls back rather than printing "0 USERS" to someone who
                        // is holding the app in their hand.
                        if (app.users > 0) "USERS" to thousands(app.users)
                        else "GETS" to thousands(app.downloads),
                        "UPDATED" to shortDate(publishedAt),
                    )
                )

                Spacer(Modifier.height(gridUnits(1.2f)))
                ActionBar(
                    label = action,
                    hint = hint,
                    labelColor = if (installedVersionCode != null && !updatable && progress == null) {
                        Light.ContentSecondary
                    } else {
                        Light.Content
                    },
                    enabled = enabled,
                    onClick = onInstall,
                )

                if (progress is Installer.Progress.Downloading && progress.total > 0) {
                    Spacer(Modifier.height(gridUnits(0.4f)))
                    // Drawn, not Material's LinearProgressIndicator: that component
                    // animates and carries a tonal track, neither of which exists in
                    // LightOS.
                    Canvas(
                        Modifier
                            .fillMaxWidth(0.8f)
                            .height(gridUnits(0.2f))
                    ) {
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

                // What the bar is doing, in a sentence. Every state has one: a
                // drawn line and a spinner both say "wait", and neither says
                // what for.
                Spacer(Modifier.height(gridUnits(0.6f)))
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = Light.ContentSecondary,
                )

                // Below the button, not above it. The strip used to sit between
                // the summary and the install, which put sixteen units of
                // pictures in front of the one thing the page is for.
                if (app.screenshots.isNotEmpty()) {
                    Spacer(Modifier.height(gridUnits(1.5f)))
                    ScreenshotStrip(app.screenshots, onOpen = { zoom = it })
                }

                // Apps that need a grant LightOS has no screen for. The README says to run these
                // from a computer; BrightControl can run them here instead.
                if (app.adbSetup.isNotEmpty()) {
                    Spacer(Modifier.height(gridUnits(1.5f)))
                    Text(
                        "Needs ADB setup",
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

                    // The commands themselves, set behind a rule. They are
                    // quoted material -- someone else's words, which the person
                    // approving them should be able to tell apart from ours --
                    // and indenting them is how print has always said so.
                    Spacer(Modifier.height(gridUnits(0.7f)))
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        VRule()
                        Column(Modifier.padding(start = gridUnits(0.6f))) {
                            app.adbSetup.forEach { line ->
                                Text(
                                    line,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Light.ContentSecondary,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(gridUnits(0.4f)))
                            }
                        }
                    }

                    // The same bar in secondary weight, because the order is
                    // install first and grant second. Half a unit shorter than
                    // the install bar, so a thumb travelling down the page
                    // cannot mistake one for the other.
                    Spacer(Modifier.height(gridUnits(0.8f)))
                    ActionBar(
                        label = when {
                            !controlInstalled -> "Get BrightControl"
                            handedOver -> "Handed over…"
                            else -> "Activate ADB"
                        },
                        hint = when {
                            !controlInstalled -> "Not installed"
                            handedOver -> "BrightControl"
                            app.adbSetup.size == 1 -> "1 grant"
                            else -> "${app.adbSetup.size} grants"
                        },
                        units = 3.3f,
                        labelColor = if (handedOver && controlInstalled) {
                            Light.ContentSecondary
                        } else {
                            Light.Content
                        },
                        ruleColor = Light.ContentSecondary,
                        onClick = {
                            if (controlInstalled) {
                                handedOver = true
                                onActivateAdb?.invoke()
                            } else {
                                onOpenControl?.invoke()
                            }
                        },
                    )
                    Spacer(Modifier.height(gridUnits(0.6f)))
                    Text(
                        when {
                            !controlInstalled ->
                                "The commands are here either way — a computer with adb can " +
                                    "run them as they read."
                            handedOver ->
                                "BrightControl rebuilds each line pinned to ${app.pkg} and " +
                                    "refuses anything that names a different package. Tap again " +
                                    "if it didn't come up."
                            else -> "Nothing runs until you confirm it in BrightControl."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Light.ContentSecondary,
                    )
                }

                if (target.notes.isNotBlank()) {
                    Spacer(Modifier.height(gridUnits(1.5f)))
                    Text(
                        "What's new",
                        style = MaterialTheme.typography.titleMedium,
                        color = Light.ContentSecondary,
                    )
                    Spacer(Modifier.height(gridUnits(0.4f)))
                    MarkdownText(target.notes, style = MaterialTheme.typography.bodySmall)
                }

                // The channel, beside the app it is about. A single phone-wide switch
                // put a dialler and a keyboard on prereleases in order to test one
                // app, which is not what anyone opting in was asking for. Shown only
                // when the app actually publishes prereleases: an on/off pair that
                // resolves to the same build either way is a control that does
                // nothing.
                if (onToggleNightly != null) {
                    Spacer(Modifier.height(gridUnits(1.6f)))
                    Text(
                        if (nightlyOn) "NIGHTLY: ON" else "NIGHTLY: OFF",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2f.em),
                        color = if (nightlyOn) Light.Content else Light.ContentSecondary,
                        modifier = Modifier
                            .lightClickable(onClick = onToggleNightly)
                            .padding(
                                top = gridUnits(0.4f),
                                bottom = gridUnits(0.4f),
                                end = gridUnits(2f),
                            ),
                    )
                    Text(
                        if (nightlyOn) {
                            "Prereleases for this app only. Every build as it is made, " +
                                "including the ones that turn out to be wrong."
                        } else {
                            "Official releases for this app. It also publishes nightlies."
                        },
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
                    // cannot creep back up into the space. It is also last on the
                    // page now, a full screen below the install bar.
                    Spacer(Modifier.height(gridUnits(1.6f)))
                    Rule(fraction = 0.35f)
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

                Spacer(Modifier.height(gridUnits(2f)))
            }
        }

        // Over the page, top bar included: at full size the picture is the
        // screen, and a bar left showing above it would be the only chrome in
        // the app sitting on top of content.
        if (zoom >= 0) {
            ScreenshotZoom(app.screenshots, zoom) { zoom = -1 }
        }
    }
}
