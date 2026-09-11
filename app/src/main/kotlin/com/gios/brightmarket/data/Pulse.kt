package com.gios.brightmarket.data

import android.content.Context
import android.content.pm.PackageManager
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Anonymous install counting, so "how many people use these apps" has an answer.
 *
 * GitHub's download counters cannot answer it. They count bytes leaving a
 * release, so a mirror, a crawler and a person installing all read the same:
 * Ritual drew 87 downloads a day for a month from something that was never a
 * phone. This counts installs instead.
 *
 * **Nothing identifying is sent, because nothing identifying exists here.** No
 * install id, no device id, no advertising id, no account, no secret, no hash of
 * any of those. What goes out is a transition:
 *
 * ```
 * {"e":"<random, this event only>","app":"com.x.y","frm":"","dst":"1.2"}   installed
 * {"e":"...","app":"com.x.y","frm":"?","dst":"1.2"}                        already had it
 * {"e":"...","app":"com.x.y","frm":"1.1","dst":"1.2"}                      updated
 * {"e":"...","app":"com.x.y","frm":"1.2","dst":""}                         removed
 * ```
 *
 * Two events from this phone share nothing, not even a random number, so
 * nothing at the other end can group them back together into "this phone".
 * That is a property of the design rather than a promise about the server.
 *
 * The live figure is then arithmetic: everyone who arrived at a version minus
 * everyone who left it. Updates cancel, so it stays exact without anyone being
 * counted.
 *
 * The scope is the catalogue and only the catalogue. Every package named here is
 * one the index already lists publicly, which is why this does not turn into a
 * list of what is on your phone.
 */
object Pulse {

    private const val PREFS = "pulse"
    private const val KEY_ON = "on"
    /** Written, never read: it says a snapshot exists, which the snapshot says too. */
    private const val KEY_SNAPSHOT = "snapshot"
    private const val KEY_OUTBOX = "outbox"

    private const val ENDPOINT = "https://brightmarket-portal.gman6849.workers.dev/pulse"

    /** Matches the worker's own ceiling. Beyond this the oldest events go. */
    private const val OUTBOX_MAX = 300

    /** No prior version is known: the app was already on the phone when counting started. */
    private const val UNKNOWN = "?"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ON, true)

    /** Off means off: the queue goes too, rather than waiting to be sent later. */
    fun setEnabled(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ON, on).apply()
        if (!on) prefs(ctx).edit().remove(KEY_OUTBOX).apply()
    }

    /**
     * Look at the catalogue apps on the phone, queue whatever changed since last
     * time, and try to send.
     *
     * Cheap enough to call on every index refresh: it is a package-manager
     * lookup per listed app and, on the overwhelming majority of runs, nothing
     * changed and nothing is sent.
     *
     * Runs on a thread of its own rather than through the app's `guarded` scope
     * because a failure here is not the user's problem and must never reach the
     * screen. A missed send is retried on the next launch.
     */
    fun sync(ctx: Context, catalogue: List<String>) {
        if (!enabled(ctx) || catalogue.isEmpty()) return
        val app = ctx.applicationContext
        Thread {
            runCatching { syncBlocking(app, catalogue) }
        }.apply { isDaemon = true }.start()
    }

    internal fun syncBlocking(ctx: Context, catalogue: List<String>) {
        val current = scan(ctx, catalogue)
        val previous = readMap(prefs(ctx).getString(KEY_SNAPSHOT, null))

        val events = diff(ctx, previous, current)

        // Queued and the snapshot advanced BEFORE the send is attempted. The
        // diff is computed once and once only: if the network is down the
        // events wait in the outbox rather than being recomputed from a
        // snapshot that never moved, which would send the same install twice
        // under two different ids and count it twice.
        if (events.isNotEmpty()) enqueue(ctx, events)
        prefs(ctx).edit().putString(KEY_SNAPSHOT, writeMap(current)).apply()

        flush(ctx)
    }

    /** Installed catalogue apps, package to version name. */
    private fun scan(ctx: Context, catalogue: List<String>): Map<String, String> {
        val pm = ctx.packageManager
        val out = LinkedHashMap<String, String>()
        for (pkg in catalogue.distinct()) {
            val info = runCatching { pm.getPackageInfo(pkg, 0) }.getOrNull() ?: continue
            out[pkg] = version(info.versionName)
        }
        return out
    }

    private fun diff(
        ctx: Context,
        previous: Map<String, String>,
        current: Map<String, String>,
    ): List<JSONObject> {
        val events = mutableListOf<JSONObject>()

        for ((pkg, now) in current) {
            val before = previous[pkg]
            when {
                before == null -> {
                    // New to us. Not necessarily new to the phone: on the very
                    // first run everything is new to us, and an app added to the
                    // catalogue today may have been installed for a year. The
                    // package manager knows which, so ask it instead of
                    // guessing, and count a first install as an install only
                    // when it really is one. Without this the day counting
                    // starts reads as a thousand installs that never happened.
                    events += event(pkg, if (isFreshInstall(ctx, pkg)) "" else UNKNOWN, now)
                }
                before != now -> events += event(pkg, before, now)
            }
        }
        for ((pkg, before) in previous) {
            if (!current.containsKey(pkg)) events += event(pkg, before, "")
        }

        return events
    }

    /**
     * True when the phone has never updated this package, which is the only
     * honest signal available that an install is genuinely new.
     */
    private fun isFreshInstall(ctx: Context, pkg: String): Boolean {
        val info = runCatching { ctx.packageManager.getPackageInfo(pkg, 0) }.getOrNull()
            ?: return false
        return info.firstInstallTime == info.lastUpdateTime
    }

    private fun event(pkg: String, frm: String, dst: String) = JSONObject().apply {
        // Random per event, not per phone. Its only job is to let a retry after
        // a dropped connection be recognised as the same event instead of a
        // second install.
        put("e", UUID.randomUUID().toString().replace("-", ""))
        put("app", pkg)
        put("frm", frm)
        put("dst", dst)
    }

    /** The worker accepts `[A-Za-z0-9._+-]{1,24}`; anything else is the client's problem. */
    private fun version(raw: String?): String {
        val cleaned = (raw ?: "").trim().map { c ->
            if (c.isLetterOrDigit() || c in "._+-") c else '-'
        }.joinToString("").take(24)
        return cleaned.ifBlank { "0" }
    }

    private fun enqueue(ctx: Context, events: List<JSONObject>) {
        val outbox = JSONArray(prefs(ctx).getString(KEY_OUTBOX, "[]"))
        for (e in events) outbox.put(e)
        val trimmed = JSONArray()
        val from = maxOf(0, outbox.length() - OUTBOX_MAX)
        for (i in from until outbox.length()) trimmed.put(outbox.get(i))
        prefs(ctx).edit().putString(KEY_OUTBOX, trimmed.toString()).apply()
    }

    private fun flush(ctx: Context) {
        val outbox = JSONArray(prefs(ctx).getString(KEY_OUTBOX, "[]"))
        if (outbox.length() == 0) return
        val body = JSONObject().put("v", 1).put("events", outbox).toString()
        if (post(body)) prefs(ctx).edit().remove(KEY_OUTBOX).apply()
    }

    private fun post(body: String): Boolean {
        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        return try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            conn.disconnect()
        }
    }

    private fun readMap(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                for (k in obj.keys()) put(k, obj.optString(k))
            }
        }.getOrDefault(emptyMap())
    }

    private fun writeMap(map: Map<String, String>): String =
        JSONObject().apply { for ((k, v) in map) put(k, v) }.toString()

    /**
     * Exactly what the next send would contain, for the settings screen. Shown
     * rather than described: a sentence about anonymity is worth less than the
     * literal bytes.
     */
    fun preview(ctx: Context, catalogue: List<String>): String {
        val outbox = JSONArray(prefs(ctx).getString(KEY_OUTBOX, "[]"))
        if (outbox.length() > 0) return JSONObject().put("v", 1).put("events", outbox).toString(2)
        // Nothing queued, so show the shape the next change would take, using
        // this app's own package rather than inventing one.
        val current = scan(ctx, catalogue)
        val pkg = ctx.packageName
        val sample = event(pkg, "", current[pkg] ?: "1.0")
        return JSONObject()
            .put("v", 1)
            .put("events", JSONArray().put(sample))
            .toString(2)
    }
}
