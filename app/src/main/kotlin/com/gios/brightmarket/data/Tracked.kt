package com.gios.brightmarket.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Apps tracked for updates that aren't in the BrightMarket index.
 *
 * This is what makes BrightMarket a replacement for Obtainium rather than just
 * a store. Plenty of what people sideload will never be in a curated index —
 * someone else's fork, a personal build, an app nobody has submitted. Refusing
 * to track those would mean running two updaters, which defeats the point.
 *
 * They are deliberately *marked* as unlisted rather than blended in. An indexed
 * app has been through the submission checks (public repo, exactly one APK,
 * pinned signer) and carries a sha256 the client verifies. A tracked repo has
 * had none of that — it is whatever the user pointed at. Presenting the two
 * identically would quietly imply a guarantee that only one of them has.
 */
object Tracked {

    private const val PREFS = "tracked"
    private const val KEY = "repos"

    data class Entry(
        /** "owner/name" on GitHub. */
        val repo: String,
        /** applicationId, once known. Null until the first release is read. */
        val pkg: String? = null,
        val name: String = repo.substringAfter('/'),
    )

    /** A resolved release for a tracked repo, shaped like the index's [App]. */
    data class Resolved(
        val entry: Entry,
        val version: String,
        val versionCode: Long,
        val apkUrl: String,
        val size: Long,
        val publishedAt: String,
        val notes: String,
    )

    fun all(ctx: Context): List<Entry> {
        val raw = prefs(ctx).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Entry(
                    repo = o.getString("repo"),
                    pkg = o.optString("pkg").takeIf { it.isNotBlank() },
                    name = o.optString("name").takeIf { it.isNotBlank() }
                        ?: o.getString("repo").substringAfter('/'),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(ctx: Context, entries: List<Entry>) {
        val arr = JSONArray()
        // Deduplicated case-insensitively: GitHub treats owner/name that way,
        // and the same repo added twice would show as two rows that update each
        // other in a loop.
        entries.distinctBy { it.repo.lowercase() }.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("repo", e.repo)
                    e.pkg?.let { put("pkg", it) }
                    put("name", e.name)
                }
            )
        }
        prefs(ctx).edit().putString(KEY, arr.toString()).apply()
    }

    /** Record what an installed APK turned out to be, so updates can be compared. */
    fun setPkg(ctx: Context, repo: String, pkg: String) {
        save(ctx, all(ctx).map { if (it.repo.equals(repo, true)) it.copy(pkg = pkg) else it })
    }

    fun add(ctx: Context, entry: Entry): Boolean {
        val existing = all(ctx)
        if (existing.any { it.repo.equals(entry.repo, ignoreCase = true) }) return false
        save(ctx, existing + entry)
        return true
    }

    fun remove(ctx: Context, repo: String) {
        save(ctx, all(ctx).filterNot { it.repo.equals(repo, ignoreCase = true) })
    }

    /**
     * "owner/name" from anything that looks like a GitHub URL, or from the bare
     * form typed by hand. Returns null for anything else — a QR scanner decodes
     * a lot of things that aren't repos.
     */
    fun repoFromAny(text: String): String? {
        val t = text.trim().removeSuffix(".git")
        val m = Regex("""github\.com[/:]([\w.\-]+)/([\w.\-]+)""").find(t)
        if (m != null) return "${m.groupValues[1]}/${m.groupValues[2]}"
        // Bare owner/name, but only if it really looks like one: no spaces, one
        // slash, and not a URL for somewhere else entirely.
        val bare = Regex("""^([\w.\-]+)/([\w.\-]+)$""").find(t)
        return bare?.let { "${it.groupValues[1]}/${it.groupValues[2]}" }
    }

    /**
     * The last run of digits anywhere in a release tag.
     *
     * This used to be `tag.substringAfterLast('.')`, which assumes a
     * dot-separated version and returns the whole tag when there isn't one.
     * `build-70` came back as "build-70", parsed as null, and became 0 — so
     * `updatable` was `0 > installed`, false forever. A tracked app on that tag
     * scheme could never show an update, silently, and looked exactly like an
     * app that was simply up to date.
     *
     * Not a cosmetic case: `build-NN` is what this whole portfolio's older CI
     * emits. BrightMusic and BrightLibrary both use it.
     *
     * Returns 0 when a tag carries no digits at all, which is honest — there is
     * nothing to compare — and leaves the app offering the install and letting
     * Android refuse a downgrade.
     */
    fun versionCodeFromTag(tag: String): Long =
        Regex("""\d+""").findAll(tag).lastOrNull()?.value?.toLongOrNull() ?: 0L

    /**
     * Choose which asset is "the" APK when a release publishes more than one.
     *
     * This is not a hypothetical. Obtainium's own releases ship nine: three
     * ABI splits, a "-fdroid-" flavor of each, and a universal build -- and
     * the F-Droid flavor is a **different applicationId**
     * (`dev.imranr.obtainium.fdroid` vs `dev.imranr.obtainium`), not just a
     * different build of the same app. `firstOrNull` over the raw asset list
     * took whichever GitHub happened to upload first, which is unspecified
     * and can change release to release. The day it picked the fdroid variant
     * while this phone's Obtainium -- and this repo's own cached applicationId
     * -- was the regular one, `Installer.install` was asked to write a
     * `dev.imranr.obtainium.fdroid` archive into a session opened for
     * `dev.imranr.obtainium`. Android notices that mismatch and refuses with
     * `INSTALL_FAILED_INVALID_APK`, which names nothing -- it looks exactly
     * like a corrupt download, and the file (checked byte-for-byte against
     * its own published sha256) was not corrupt at all.
     *
     * Preference order, cheapest signal first, all from the filename alone
     * (nothing here has been downloaded yet):
     *  1. Skip anything naming a flavor that exists specifically to be a
     *     *different* build for a *different* distributor -- "fdroid" is the
     *     one seen in the wild, so it is the one handled by name. A repo that
     *     publishes only flavored builds still gets one, via the fallback.
     *  2. Skip anything naming a specific ABI. A universal build installs
     *     everywhere; an ABI split installs only on a matching device, and
     *     nothing here knows the device's ABI to match it correctly, so the
     *     safe move is to prefer not needing to.
     *  3. Prefer the larger file. A universal build bundles every ABI's native
     *     code, so it is reliably the biggest asset in a split release -- an
     *     ordering that needs no ABI knowledge at all.
     *  4. If every candidate got skipped by 1 or 2 (a release with only
     *     flavored or only split builds), fall back to the first one rather
     *     than returning nothing -- a flavor-swapped app is still an app;
     *     Installer.install's own mismatch check is what actually protects
     *     against a wrong pick surviving all the way to an install.
     */
    fun pickApk(assets: List<JSONObject>): JSONObject? {
        val apks = assets.filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
        if (apks.isEmpty()) return null
        val abiNames = listOf("arm64-v8a", "armeabi-v7a", "armeabi", "x86_64", "x86")
        fun isFlavored(name: String) = name.contains("fdroid", ignoreCase = true)
        fun isAbiSplit(name: String) = abiNames.any { name.contains(it, ignoreCase = true) }

        val plain = apks.filterNot { isFlavored(it.optString("name")) }
            .filterNot { isAbiSplit(it.optString("name")) }
        val candidates = plain.ifEmpty { apks }
        return candidates.maxByOrNull { it.optLong("size") }
    }

    /**
     * [pickApk]'s logic for a list of hrefs instead of asset JSON -- the shape
     * [withoutApi] has to work with, since the web fallback has no size field
     * to prefer by. Filename-only, same flavor/ABI avoidance, first-remaining
     * as the tiebreak instead of largest.
     */
    fun pickApkHref(hrefs: List<String>): String? {
        if (hrefs.isEmpty()) return null
        val abiNames = listOf("arm64-v8a", "armeabi-v7a", "armeabi", "x86_64", "x86")
        fun name(href: String) = href.substringAfterLast('/')
        fun isFlavored(href: String) = name(href).contains("fdroid", ignoreCase = true)
        fun isAbiSplit(href: String) = abiNames.any { name(href).contains(it, ignoreCase = true) }

        val plain = hrefs.filterNot { isFlavored(it) }.filterNot { isAbiSplit(it) }
        return plain.firstOrNull() ?: hrefs.first()
    }

    /**
     * Ask GitHub for a tracked repo's newest release.
     *
     * versionCode can't be read here the way the index does it — that needs the
     * APK parsed server-side. The client instead compares the release's
     * published date against what it last saw, and falls back to offering the
     * install and letting Android refuse a downgrade. Honest about being weaker
     * than the indexed path, which is part of why unlisted apps are marked.
     */
    /**
     * What happened when we asked GitHub about a repo.
     *
     * This used to be a nullable Resolved, so "this repo publishes no APK",
     * "GitHub is rate-limiting us" and "the phone is offline" were the same
     * value — null — and the UI printed the first of those for all three. A
     * user with ten tracked apps saw "no APK release found" against every one
     * of them, none of which was true.
     */
    sealed interface Outcome {
        data class Ok(val resolved: Resolved) : Outcome
        /** Reached GitHub; it genuinely has no published release with an APK. */
        object NoRelease : Outcome
        /**
         * Unauthenticated callers get 60 requests an hour, per IP -- and on a
         * carrier NAT that IP is shared, so this can happen having made none.
         */
        data class RateLimited(val resetEpochSeconds: Long, val detail: String) : Outcome
        data class Unreachable(val detail: String) : Outcome
    }

    /**
     * @param force skip the freshness window and really ask GitHub.
     *
     * Without this, asking for a refresh inside [FRESH_FOR_MS] returned the
     * cached answer without a request — so a release published twenty minutes
     * ago stayed invisible however many times the user pulled to refresh, and
     * the app looked broken next to any other updater. The rate-limit reason
     * for the cache is real, which is why the window still applies to automatic
     * checks; it just no longer overrules someone deliberately asking.
     *
     * A forced check still sends `If-None-Match`, because a 304 is free and
     * genuinely means nothing changed.
     */
    fun resolve(
        ctx: Context,
        entry: Entry,
        token: String? = null,
        force: Boolean = false,
    ): Outcome {
        val cached = cacheFor(ctx, entry.repo)
        // Nothing changes minute to minute, and every request spends a sixtieth
        // of the hourly allowance. A repo checked recently is not checked again.
        if (!force && cached != null &&
            System.currentTimeMillis() - cached.checkedAt < FRESH_FOR_MS
        ) {
            return cached.outcome
        }

        val conn = (URL("https://api.github.com/repos/${entry.repo}/releases?per_page=10")
            .openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "BrightMarket")
            // A 304 does not count against the rate limit at all, so asking
            // "has this changed?" is free where asking "what is it?" is not.
            cached?.etag?.let { setRequestProperty("If-None-Match", it) }
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
        return try {
            val code = conn.responseCode

            if (code == 304 && cached != null) {
                store(ctx, entry.repo, cached.copy(checkedAt = System.currentTimeMillis()))
                return cached.outcome
            }

            // 403 and 429 both carry the remaining count. Zero means the hour is
            // spent; anything else is a different refusal.
            if (code == 403 || code == 429) {
                val remaining = conn.getHeaderField("X-RateLimit-Remaining")?.toLongOrNull()
                val reset = conn.getHeaderField("X-RateLimit-Reset")?.toLongOrNull() ?: 0L
                val limit = conn.getHeaderField("X-RateLimit-Limit") ?: "?"

                // The API is spent, but github.com itself isn't rate limited at
                // all. Ask it instead.
                val (resolved, fallbackNote) = withoutApi(entry)
                if (resolved != null) {
                    val ok = Outcome.Ok(resolved)
                    store(ctx, entry.repo, Cached(null, System.currentTimeMillis(), ok))
                    return ok
                }

                // Both routes are gone. Everything known about why, in one line,
                // because the person reading it will not have the phone.
                val detail = "api HTTP $code, ratelimit $remaining/$limit reset=$reset; " +
                    "web fallback: $fallbackNote"
                val outcome =
                    if (remaining == 0L) Outcome.RateLimited(reset, detail)
                    else Outcome.Unreachable(detail)
                // Deliberately not cached as a result: being throttled says
                // nothing about the repo, and caching it would outlive the
                // throttle. The previous answer, if any, is kept instead.
                return cached?.outcome ?: outcome
            }

            if (code !in 200..299) {
                val (resolved, fallbackNote) = withoutApi(entry)
                if (resolved != null) {
                    val ok = Outcome.Ok(resolved)
                    store(ctx, entry.repo, Cached(null, System.currentTimeMillis(), ok))
                    return ok
                }
                return cached?.outcome
                    ?: Outcome.Unreachable("api HTTP $code; web fallback: $fallbackNote")
            }
            val arr = JSONArray(conn.inputStream.bufferedReader().readText())
            for (i in 0 until arr.length()) {
                val rel = arr.getJSONObject(i)
                if (rel.optBoolean("draft") || rel.optBoolean("prerelease")) continue
                val assets = rel.optJSONArray("assets") ?: continue
                val apk = pickApk((0 until assets.length()).map { assets.getJSONObject(it) })
                    ?: continue

                val tag = rel.optString("tag_name").removePrefix("v")
                val ok = Outcome.Ok(
                    Resolved(
                        entry = entry,
                        version = tag,
                        versionCode = versionCodeFromTag(tag),
                        apkUrl = apk.optString("browser_download_url"),
                        size = apk.optLong("size"),
                        publishedAt = rel.optString("published_at"),
                        notes = rel.optString("body").take(4000),
                    )
                )
                store(ctx, entry.repo, Cached(conn.getHeaderField("ETag"), System.currentTimeMillis(), ok))
                return ok
            }
            // Reached GitHub, read the whole list, found no APK. The one case
            // where "no APK release found" is a true statement.
            store(ctx, entry.repo, Cached(conn.getHeaderField("ETag"), System.currentTimeMillis(), Outcome.NoRelease))
            Outcome.NoRelease
        } catch (e: Exception) {
            // Offline, DNS, timeout. Says nothing about the repo, so an older
            // answer is better than replacing it with a wrong one.
            cached?.outcome
                ?: Outcome.Unreachable("${e::class.java.simpleName}: ${e.message}")
        } finally {
            conn.disconnect()
        }
    }

    /**
     * The same question, asked of github.com rather than api.github.com.
     *
     * The API allows 60 requests an hour **per source IP**, and on a mobile
     * carrier that IP is shared with everyone else behind the same NAT. So the
     * budget can be gone before this phone has made a single request — which is
     * why one tracked repo could work and then everything fail at once, with no
     * change in what the user did.
     *
     * The ordinary web pages carry no rate limit. Two requests:
     *
     *  1. `/releases/latest` redirects to the newest release that is NOT a
     *     prerelease, which is exactly the selection the API path makes. The
     *     releases Atom feed would be one request instead of two, but it lists
     *     prereleases indistinguishably, and offering someone a beta because it
     *     was cheaper to find is not a trade worth making.
     *  2. `/releases/expanded_assets/<tag>` is the fragment the releases page
     *     lazy-loads, and it names the assets.
     *
     * Returns null rather than throwing: this is already the unhappy path, and
     * the caller has a real answer to fall back on.
     */
    private fun withoutApi(entry: Entry): Pair<Resolved?, String> = runCatching {
        val latest = (URL("https://github.com/${entry.repo}/releases/latest")
            .openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 30_000
            requestMethod = "HEAD"
            setRequestProperty("User-Agent", "BrightMarket")
        }
        val location = try {
            if (latest.responseCode !in 300..399) {
                return null to "releases/latest gave HTTP ${latest.responseCode}, expected a redirect"
            }
            latest.getHeaderField("Location")
                ?: return null to "releases/latest redirected with no Location header"
        } finally {
            latest.disconnect()
        }
        // ".../releases/tag/v1.4.0"
        val tag = location.substringAfterLast('/').takeIf { it.isNotBlank() }
            ?: return null to "no tag in redirect target '$location'"

        val page = (URL("https://github.com/${entry.repo}/releases/expanded_assets/$tag")
            .openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "BrightMarket")
        }
        val html = try {
            if (page.responseCode !in 200..299) {
                return null to "expanded_assets/$tag gave HTTP ${page.responseCode}"
            }
            page.inputStream.bufferedReader().readText()
        } finally {
            page.disconnect()
        }

        // Same multi-asset problem as the API path (see pickApk) but blinder:
        // this page names files, not bytes, so there is no size to break a tie
        // with. Filename-only preference is still strictly better than "first
        // link on the page", which is upload order and not a choice anyone made.
        val hrefs = Regex("""href="(/[^"]+\.apk)"""", RegexOption.IGNORE_CASE)
            .findAll(html).map { it.groupValues[1] }.toList()
        val href = pickApkHref(hrefs)
            ?: return null to "no .apk linked on $tag (${html.length} bytes read)"

        val version = tag.removePrefix("v")
        Resolved(
            entry = entry,
            version = version,
            versionCode = versionCodeFromTag(version),
            apkUrl = "https://github.com$href",
            // Not on the page, and not worth a third request. Size is shown, not
            // relied on; the installer reads the real length as it downloads.
            size = 0L,
            publishedAt = "",
            notes = "",
        ) to "ok"
    }.getOrElse { null to "${it::class.java.simpleName}: ${it.message}" }

    // -----------------------------------------------------------------------
    // The cache. Its whole purpose is spending fewer of the sixty requests an
    // hour that an unauthenticated caller gets -- shared across every tracked
    // repo, and refreshTracked() asks about all of them at once.
    // -----------------------------------------------------------------------

    private const val FRESH_FOR_MS = 30 * 60 * 1000L
    private const val CACHE = "tracked_cache"

    private data class Cached(val etag: String?, val checkedAt: Long, val outcome: Outcome)

    private fun cacheFor(ctx: Context, repo: String): Cached? {
        val raw = ctx.getSharedPreferences(CACHE, Context.MODE_PRIVATE).getString(repo, null)
            ?: return null
        return runCatching {
            val o = JSONObject(raw)
            val outcome = when (o.getString("kind")) {
                "ok" -> Outcome.Ok(
                    Resolved(
                        entry = Entry(repo = repo),
                        version = o.getString("version"),
                        versionCode = o.getLong("versionCode"),
                        apkUrl = o.getString("apkUrl"),
                        size = o.optLong("size"),
                        publishedAt = o.optString("publishedAt"),
                        notes = o.optString("notes"),
                    )
                )
                "none" -> Outcome.NoRelease
                else -> return null
            }
            Cached(o.optString("etag").takeIf { it.isNotBlank() }, o.optLong("checkedAt"), outcome)
        }.getOrNull()
    }

    private fun store(ctx: Context, repo: String, cached: Cached) {
        val o = JSONObject().apply {
            cached.etag?.let { put("etag", it) }
            put("checkedAt", cached.checkedAt)
            when (val r = cached.outcome) {
                is Outcome.Ok -> {
                    put("kind", "ok")
                    put("version", r.resolved.version)
                    put("versionCode", r.resolved.versionCode)
                    put("apkUrl", r.resolved.apkUrl)
                    put("size", r.resolved.size)
                    put("publishedAt", r.resolved.publishedAt)
                    put("notes", r.resolved.notes)
                }
                // Only a definite answer is worth remembering. A throttle or a
                // dropped connection is about us, not the repo.
                Outcome.NoRelease -> put("kind", "none")
                else -> return
            }
        }
        ctx.getSharedPreferences(CACHE, Context.MODE_PRIVATE).edit().putString(repo, o.toString()).apply()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
