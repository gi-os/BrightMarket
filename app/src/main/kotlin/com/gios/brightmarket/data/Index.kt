package com.gios.brightmarket.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * One app as the index describes it. Everything here is derived by the index
 * builder from the GitHub API -- nothing is hand-maintained except name,
 * category and summary.
 */
data class App(
    val pkg: String,
    val name: String,
    val repo: String,
    val category: String,
    val summary: String,
    val version: String,
    /**
     * The trailing segment of the release tag (v1.3.18 -> 18), which every
     * Bright app's CI stamps from its run number. This is compared against
     * PackageManager's longVersionCode to decide "update available" -- it is
     * NOT the human-facing version string.
     */
    val versionCode: Long,
    val apkUrl: String,
    val size: Long,
    val sha256: String,
    val publishedAt: String,
    val notes: String,
    val downloads: Int,
    val firstSeen: String,
    /**
     * Raw URLs into the app's own repo, in the order the developer named the
     * files. Empty for most apps -- the UI must treat that as normal, not as a
     * loading state.
     */
    val screenshots: List<String> = emptyList(),
)

enum class Sort(val label: String) {
    UPDATED("Updated"),
    NEW("New"),
    POPULAR("Popular"),
}

object Index {

    fun parse(json: String): List<App> {
        val root = JSONObject(json)
        val arr = root.getJSONArray("apps")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val latest = o.getJSONObject("latest")
            App(
                pkg = o.getString("pkg"),
                name = o.getString("name"),
                repo = o.getString("repo"),
                category = o.optString("category", "utilities"),
                summary = o.optString("summary", ""),
                version = latest.getString("version"),
                versionCode = latest.getLong("versionCode"),
                apkUrl = latest.getString("apk"),
                size = latest.getLong("size"),
                sha256 = latest.getString("sha256"),
                publishedAt = latest.optString("published", ""),
                notes = latest.optString("notes", ""),
                downloads = o.optInt("downloads", 0),
                firstSeen = o.optString("firstSeen", ""),
                screenshots = o.optJSONArray("screenshots")?.let { arr ->
                    (0 until arr.length()).mapNotNull { j ->
                        arr.optJSONObject(j)?.optString("url")?.takeIf { it.isNotBlank() }
                    }
                } ?: emptyList(),
            )
        }
    }

    fun fetch(url: String): List<App> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode !in 200..299) {
                throw java.io.IOException("index returned HTTP ${conn.responseCode}")
            }
            return parse(conn.inputStream.bufferedReader().readText())
        } finally {
            conn.disconnect()
        }
    }

    /** "All" plus every category actually present, so the row never offers an
     *  empty filter and never hides a category someone submitted. */
    fun categories(apps: List<App>): List<String> =
        listOf(ALL) + apps.map { it.category }.distinct().sorted()

    const val ALL = "All"

    /**
     * Free-text search across the fields a person would actually type: the
     * name, the summary, the category, and the applicationId. The applicationId
     * is included deliberately -- half these apps are still `com.gios.light*`
     * after the Bright rename, so someone searching "light" should still find
     * them rather than getting nothing.
     *
     * Blank query and [ALL] category are both no-ops, so this is safe to call
     * unconditionally on every recomposition.
     */
    fun filter(apps: List<App>, query: String, category: String = ALL): List<App> {
        val q = query.trim().lowercase()
        return apps.filter { app ->
            val matchesCategory = category == ALL || app.category.equals(category, true)
            val matchesQuery = q.isEmpty() ||
                app.name.lowercase().contains(q) ||
                app.summary.lowercase().contains(q) ||
                app.category.lowercase().contains(q) ||
                app.pkg.lowercase().contains(q)
            matchesCategory && matchesQuery
        }
    }

    fun sort(apps: List<App>, by: Sort): List<App> = when (by) {
        // ISO-8601 timestamps from the GitHub API, so lexicographic ordering is
        // chronological and there is nothing to parse.
        Sort.UPDATED -> apps.sortedByDescending { it.publishedAt }
        Sort.NEW -> apps.sortedByDescending { it.firstSeen }
        Sort.POPULAR -> apps.sortedByDescending { it.downloads }
    }
}
