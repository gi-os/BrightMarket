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
    /**
     * The community project this app forks, as "owner/repo". Blank for
     * original apps; the detail page shows a credit line when present.
     */
    val upstream: String = "",
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
    /**
     * The newest prerelease, when the app publishes one. Null for almost every
     * app — treat that as "there is no nightly", not as a loading state.
     *
     * Deliberately a separate object rather than a flag on [version]: an app on
     * the nightly channel still needs to know what stable is, so that turning
     * the channel off can put you back on it.
     */
    val preview: Preview? = null,
)

/** A prerelease build, checked exactly as hard as a stable one. */
data class Preview(
    val version: String,
    val versionCode: Long,
    val apkUrl: String,
    val size: Long,
    val sha256: String,
    val publishedAt: String,
    val notes: String,
)

enum class Sort(val label: String) {
    UPDATED("Updated"),
    NEW("New"),
    POPULAR("Popular"),
}

/**
 * What the Updates tab needs to know about one installed app.
 *
 * `installedVersionCode` is null only for apps in the index that aren't on the
 * phone -- those never appear here.
 */
/**
 * What an app would install on a given channel.
 *
 * A nightly is only taken when it is genuinely ahead of stable. Otherwise an
 * official release cut after the last nightly would be ignored by everyone on
 * the nightly channel — they would sit on an older build than everybody else,
 * which is the opposite of what opting in means.
 */
data class Target(
    val version: String,
    val versionCode: Long,
    val apkUrl: String,
    val sha256: String,
    val notes: String,
    val nightly: Boolean,
)

fun App.target(nightly: Boolean): Target {
    val pv = preview
    return if (nightly && pv != null && pv.versionCode > versionCode) {
        Target(pv.version, pv.versionCode, pv.apkUrl, pv.sha256, pv.notes, true)
    } else {
        Target(version, versionCode, apkUrl, sha256, notes, false)
    }
}

data class Installed(
    val app: App,
    val installedVersionCode: Long,
    /** True for BrightMarket's own entry -- updating it closes the app. */
    val isSelf: Boolean = false,
    /** Resolved once, at partition time, so the UI and the installer agree. */
    val target: Target = app.target(false),
    /** PackageManager's versionName, for the string comparison. */
    val installedVersionName: String? = null,
    /** The release string BrightMarket recorded installing, if it installed it. */
    val installedByMarket: String? = null,
) {
    val updatable: Boolean
        get() = Version.updateAvailable(
            installedVersionName = installedVersionName,
            installedVersionCode = installedVersionCode,
            installedByMarket = installedByMarket,
            remoteVersion = target.version,
            remoteVersionCode = target.versionCode,
        )
}

object Index {

    /**
     * Split the index into "has an update" and "up to date", given what the
     * package manager reports.
     *
     * Comparison is on versionCode, never the version *name*. Names are
     * marketing strings -- "1.10.0" sorts before "1.9.0" as text -- while every
     * Bright app's CI stamps versionCode from its monotonic run number. Getting
     * this wrong reads to the user as "no update available", forever, silently.
     */
    /**
     * Split the index three ways: has an update, installed and current, and
     * followed-but-not-installed.
     *
     * The third list is what makes an import visible. Before it existed, only
     * what PackageManager reported was ever shown, so importing an export did
     * nothing on screen for the apps BrightMarket actually indexes — which read
     * as the import having skipped them.
     *
     * Comparison used to be versionCode only, on the reasoning that names are
     * marketing strings while every Bright app's CI stamps versionCode from its
     * monotonic run number. True for the Bright fleet, and wrong for everyone
     * else: the index's versionCode is the *trailing digits of the tag*, so an
     * app on ordinary semantic versioning going v1.2.10 -> v1.3.0 compared 0
     * against 10 and reported no update, forever, silently. See [Version].
     */
    fun partitionInstalled(
        apps: List<App>,
        installed: Map<String, Long>,
        selfPkg: String = "",
        followed: Set<String> = emptySet(),
        nightly: Boolean = false,
        /** PackageManager's versionName for a package, when it can be read. */
        versionNameOf: (String) -> String? = { null },
        /** What BrightMarket recorded installing for a package. */
        marketVersionOf: (String) -> String? = { null },
    ): Triple<List<Installed>, List<Installed>, List<App>> {
        val present = apps.mapNotNull { app ->
            installed[app.pkg]?.let {
                Installed(
                    app = app,
                    installedVersionCode = it,
                    isSelf = app.pkg == selfPkg,
                    target = app.target(nightly),
                    installedVersionName = versionNameOf(app.pkg),
                    installedByMarket = marketVersionOf(app.pkg),
                )
            }
        }
        val (updates, current) = present.partition { it.updatable }
        val notInstalled = apps
            .filter { it.pkg in followed && it.pkg !in installed }
            .sortedBy { it.name.lowercase() }
        return Triple(
            updates.sortedByDescending { it.app.publishedAt },
            current.sortedBy { it.app.name.lowercase() },
            notInstalled,
        )
    }

    /**
     * Order a batch of updates so the marketplace updates itself LAST.
     *
     * Installing BrightMarket replaces the running package, which kills this
     * process immediately. If it were anywhere but last in an "update all"
     * run, every app after it would silently never install and the user would
     * be left with a half-finished batch and no error. Putting it at the end
     * means the worst case is that it is the only one interrupted -- and by
     * then it has already done its job.
     */
    fun selfLast(targets: List<App>, selfPkg: String): List<App> =
        targets.sortedBy { it.pkg == selfPkg }


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
                upstream = o.optString("upstream", ""),
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
                preview = o.optJSONObject("preview")?.let { pv ->
                    // Every field or none. A half-read preview would offer an
                    // install with no hash to check it against, which is the
                    // one thing the nightly channel must not become.
                    val url = pv.optString("apk")
                    val sha = pv.optString("sha256")
                    if (url.isBlank() || sha.isBlank()) null
                    else Preview(
                        version = pv.optString("version"),
                        versionCode = pv.optLong("versionCode"),
                        apkUrl = url,
                        size = pv.optLong("size"),
                        sha256 = sha,
                        publishedAt = pv.optString("published", ""),
                        notes = pv.optString("notes", ""),
                    )
                },
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
