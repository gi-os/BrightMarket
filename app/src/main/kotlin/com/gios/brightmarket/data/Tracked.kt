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
     * Ask GitHub for a tracked repo's newest release.
     *
     * versionCode can't be read here the way the index does it — that needs the
     * APK parsed server-side. The client instead compares the release's
     * published date against what it last saw, and falls back to offering the
     * install and letting Android refuse a downgrade. Honest about being weaker
     * than the indexed path, which is part of why unlisted apps are marked.
     */
    fun resolve(entry: Entry, token: String? = null): Resolved? {
        val conn = (URL("https://api.github.com/repos/${entry.repo}/releases?per_page=10")
            .openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "BrightMarket")
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
        return try {
            if (conn.responseCode !in 200..299) return null
            val arr = JSONArray(conn.inputStream.bufferedReader().readText())
            for (i in 0 until arr.length()) {
                val rel = arr.getJSONObject(i)
                if (rel.optBoolean("draft") || rel.optBoolean("prerelease")) continue
                val assets = rel.optJSONArray("assets") ?: continue
                val apk = (0 until assets.length())
                    .map { assets.getJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                    ?: continue

                val tag = rel.optString("tag_name").removePrefix("v")
                return Resolved(
                    entry = entry,
                    version = tag,
                    versionCode = tag.substringAfterLast('.').toLongOrNull() ?: 0L,
                    apkUrl = apk.optString("browser_download_url"),
                    size = apk.optLong("size"),
                    publishedAt = rel.optString("published_at"),
                    notes = rel.optString("body").take(4000),
                )
            }
            null
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
