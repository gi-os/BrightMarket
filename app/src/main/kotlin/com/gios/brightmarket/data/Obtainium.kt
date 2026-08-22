package com.gios.brightmarket.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Import an Obtainium export so switching to BrightMarket costs nothing.
 *
 * Obtainium's export is a JSON object with an "apps" array; each entry has an
 * "app" object carrying at least `id` (the applicationId) and `url` (the source,
 * usually a GitHub repo). Older exports were a bare top-level array. Both shapes
 * turn up in the wild, so both are handled -- an import that throws on someone's
 * year-old backup is worse than no import at all.
 *
 * Nothing here writes anything. The result is a diff the user confirms:
 * everything already in the index becomes a tracked app; everything else is
 * reported so they know exactly what did NOT come across, rather than silently
 * losing apps.
 */
object Obtainium {

    data class Entry(val pkg: String?, val repo: String?)

    data class ImportResult(
        /** In the Obtainium export and in BrightMarket's index -- trackable now. */
        val matched: List<App>,
        /** In the export but not in the index. Named so the user can submit them. */
        val unmatched: List<Entry>,
    )

    fun parse(json: String): List<Entry> {
        val trimmed = json.trim()
        val apps: JSONArray = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            else -> JSONObject(trimmed).optJSONArray("apps") ?: JSONArray()
        }

        return (0 until apps.length()).mapNotNull { i ->
            // Entries are either {"app": {...}} or the app object inline.
            val raw = apps.optJSONObject(i) ?: return@mapNotNull null
            val app = raw.optJSONObject("app") ?: raw
            val pkg = app.optString("id").takeIf { it.isNotBlank() }
            val url = app.optString("url").takeIf { it.isNotBlank() }
            if (pkg == null && url == null) null else Entry(pkg, repoFromUrl(url))
        }
    }

    /** "https://github.com/gi-os/BrightTip" -> "gi-os/BrightTip". */
    fun repoFromUrl(url: String?): String? {
        if (url == null) return null
        val m = Regex("""github\.com/([\w.\-]+/[\w.\-]+)""").find(url) ?: return null
        return m.groupValues[1].removeSuffix(".git")
    }

    /**
     * Everything in the export that BrightMarket doesn't index, as trackable
     * repos.
     *
     * The earlier behavior — report a count of what didn't match and drop it —
     * made the import a partial migration, which is the worst kind: it looks
     * finished. If someone is replacing Obtainium, the apps it was watching
     * have to keep being watched, whether or not anyone has submitted them.
     */
    fun trackable(unmatched: List<Entry>): List<Tracked.Entry> =
        unmatched.mapNotNull { e ->
            e.repo?.let { Tracked.Entry(repo = it, pkg = e.pkg) }
        }

    fun match(entries: List<Entry>, index: List<App>): ImportResult {
        val byPkg = index.associateBy { it.pkg }
        val byRepo = index.associateBy { it.repo.lowercase() }

        val matched = LinkedHashMap<String, App>()
        val unmatched = mutableListOf<Entry>()

        for (e in entries) {
            // Match on applicationId first: it's the app's real identity, and it
            // survives the repo renames that broke naive URL matching.
            val hit = e.pkg?.let { byPkg[it] } ?: e.repo?.let { byRepo[it.lowercase()] }
            if (hit != null) matched[hit.pkg] = hit else unmatched += e
        }
        return ImportResult(matched.values.toList(), unmatched)
    }
}
