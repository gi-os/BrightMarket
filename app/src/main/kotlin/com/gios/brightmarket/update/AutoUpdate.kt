package com.gios.brightmarket.update

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.gios.brightmarket.BuildConfig
import com.gios.brightmarket.data.Focus
import com.gios.brightmarket.data.Index
import com.gios.brightmarket.data.Installed
import com.gios.brightmarket.data.InstalledVersions
import com.gios.brightmarket.data.Nightly
import com.gios.brightmarket.data.Signer
import com.gios.brightmarket.install.Installer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Updates that install by themselves, when the phone is charging on Wi-Fi.
 *
 * ## What Android allows
 *
 * Since Android 12 an installer can skip the confirmation dialog for an update when
 * all of these hold: it asks to ([PackageInstaller.SessionParams.setRequireUserAction]),
 * it declares UPDATE_PACKAGES_WITHOUT_USER_ACTION, the app targets a recent enough API
 * (31+ on Android 14, which the Light Phone III runs), and it is the app's installer of
 * record or update owner -- or it is updating itself. Anything else comes back as
 * STATUS_PENDING_USER_ACTION, and a background job must not answer that with a dialog
 * at 3 a.m., so the session is abandoned and the app waits in Updates as before.
 *
 * ## What this adds on top, on purpose
 *
 * - **Official releases only.** An app on the nightly channel stays manual.
 * - **A day's soak.** A release under [SOAK_MS] old is left alone. One tap per phone was
 *   the only thing between a bad release and every phone; this puts a day back.
 * - **A kill switch.** An index entry with `"hold": true` is never installed here, so a
 *   rollout can be stopped from the catalogue without shipping anything.
 * - **Not while in use.** On Android 14 the system is asked whether the app is in use or
 *   a call is on; BrightMarket never replaces itself while its own screen is open.
 * - **Itself last**, because installing BrightMarket ends this process.
 */
object AutoUpdate {

    const val TAG = "BMAuto"
    private const val PREFS = "auto_update"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_SUMMARY = "summary"
    private const val KEY_SELF_PENDING = "self_pending"
    private const val KEY_INDEX_OVERRIDE = "debug_index_url"
    private const val WORK = "auto-update"

    /** How old an official release must be before it installs by itself. */
    const val SOAK_MS = 24 * 60 * 60 * 1000L

    /** The message [Installer.install] fails with when Android wanted a tap. */
    const val NEEDS_TAP = "needs a tap"

    fun enabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, on).commit()
        sync(ctx)
    }

    /**
     * Make WorkManager agree with the setting. Safe to call on every launch: the periodic
     * request is unique, and UPDATE keeps its place in the schedule.
     */
    fun sync(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        if (!enabled(ctx)) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val constraints = Constraints.Builder()
            // Wi-Fi, so an update never spends someone's data plan.
            .setRequiredNetworkType(NetworkType.UNMETERED)
            // Charging, so in practice overnight, when nobody is using the phone.
            .setRequiresCharging(true)
            .setRequiresBatteryNotLow(true)
            .build()
        wm.enqueueUniquePeriodicWork(
            WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<AutoUpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build(),
        )
    }

    // -----------------------------------------------------------------------
    // The decision, kept free of Android so it can be tested on the JVM.
    // -----------------------------------------------------------------------

    data class Decision(val update: Installed, val install: Boolean, val reason: String)

    /**
     * Which updates to install now, in order, and why the rest are left.
     *
     * @param ownerOf the package Android treats as this app's installer (update owner, or
     *   installer of record), or null when it cannot be read.
     */
    fun plan(
        updates: List<Installed>,
        nowMs: Long,
        selfPkg: String,
        ownerOf: (String) -> String?,
    ): List<Decision> = updates.map { u ->
        val app = u.app
        val published = runCatching { Instant.parse(app.publishedAt).toEpochMilli() }.getOrNull()
        val reason = when {
            !u.updatable -> "not an update"
            u.target.nightly -> "nightly channel"
            app.hold -> "held in the catalogue"
            published == null -> "no release date"
            nowMs - published < SOAK_MS -> "released under a day ago"
            // Android would ask for a tap; finding that out costs a download, so don't.
            app.pkg != selfPkg && ownerOf(app.pkg) != selfPkg -> NEEDS_TAP
            else -> null
        }
        Decision(u, reason == null, reason ?: "install")
    }.sortedBy { it.update.app.pkg == selfPkg }

    // -----------------------------------------------------------------------
    // One run.
    // -----------------------------------------------------------------------

    data class Summary(
        val at: Long,
        val updated: List<String> = emptyList(),
        val needsTap: List<String> = emptyList(),
        val failed: List<String> = emptyList(),
        val error: String? = null,
    ) {
        /** One line for Settings. */
        fun line(): String {
            if (error != null) return "Last check couldn't finish: $error."
            val parts = buildList {
                add(if (updated.isEmpty()) "nothing installed" else "updated ${updated.joinToString()}")
                if (needsTap.isNotEmpty()) {
                    add("${needsTap.size} need${if (needsTap.size == 1) "s" else ""} a tap in Updates")
                }
                if (failed.isNotEmpty()) add("${failed.size} failed")
            }
            return "Last check: " + parts.joinToString(" · ") + "."
        }
    }

    suspend fun runOnce(ctx: Context): Summary = withContext(Dispatchers.IO) {
        val self = ctx.packageName
        val apps = try {
            Index.fetch(indexUrl(ctx))
        } catch (e: Exception) {
            val s = Summary(System.currentTimeMillis(), error = "couldn't reach the catalogue")
            save(ctx, s)
            Log.w(TAG, "index: ${e.message}")
            return@withContext s
        }
        val installed = apps.mapNotNull { a ->
            Installer.installedVersionCode(ctx, a.pkg)?.let { a.pkg to it }
        }.toMap()
        val fallback = Focus.nightly(ctx)
        val choices = Nightly.all(ctx)
        val (updates, _, _) = Index.partitionInstalled(
            apps, installed, self, emptySet(),
            nightlyFor = { choices[it] ?: fallback },
            versionNameOf = { Installer.installedVersionName(ctx, it) },
            marketVersionOf = { InstalledVersions.get(ctx, it) },
            signerOf = { Signer.of(ctx, it) },
        )

        val updated = mutableListOf<String>()
        val needsTap = mutableListOf<String>()
        val failed = mutableListOf<String>()
        fun summary() = Summary(System.currentTimeMillis(), updated.toList(), needsTap.toList(), failed.toList())

        for (d in plan(updates, System.currentTimeMillis(), self) { ownerOf(ctx, it) }) {
            val app = d.update.app
            if (!d.install) {
                Log.i(TAG, "skip ${app.pkg}: ${d.reason}")
                if (d.reason == NEEDS_TAP) needsTap += app.name
                continue
            }
            if (app.pkg == self) {
                if (selfOnScreen()) {
                    Log.i(TAG, "skip ${app.pkg}: open on screen")
                    continue
                }
            } else if (Build.VERSION.SDK_INT >= 34) {
                when (notInUse(ctx, app.pkg)) {
                    true -> Unit
                    false -> { Log.i(TAG, "skip ${app.pkg}: in use"); continue }
                    null -> { Log.i(TAG, "skip ${app.pkg}: couldn't ask whether it is in use"); continue }
                }
            }
            val t = d.update.target
            if (app.pkg == self) {
                // This process ends the moment the session commits, so the summary and a
                // note to finish the job go to disk first. See [selfReplaced].
                save(ctx, summary())
                prefs(ctx).edit().putString(KEY_SELF_PENDING, app.name + " " + t.version).commit()
                Log.i(TAG, "self: committing ${t.version}")
            }
            val result = Installer.install(
                ctx = ctx,
                apkUrl = t.apkUrl,
                expectedSha256 = t.sha256,
                pkg = app.pkg,
                releaseVersion = t.version,
                awaitResult = true,
                unattended = true,
            )
            val msg = result.exceptionOrNull()?.message
            when {
                result.isSuccess -> { updated += app.name; Log.i(TAG, "installed ${app.pkg} ${t.version}") }
                msg == NEEDS_TAP -> { needsTap += app.name; Log.i(TAG, "skip ${app.pkg}: $NEEDS_TAP (system)") }
                else -> { failed += app.name; Log.w(TAG, "failed ${app.pkg}: $msg") }
            }
            if (app.pkg == self && !result.isSuccess) prefs(ctx).edit().remove(KEY_SELF_PENDING).commit()
        }
        val s = summary()
        save(ctx, s)
        Log.i(TAG, "done: ${s.line()}")
        s
    }

    /**
     * After BrightMarket was replaced. The run that replaced it ended with the old
     * process, so the new one records the result and puts the schedule back.
     */
    fun selfReplaced(ctx: Context) {
        val pending = prefs(ctx).getString(KEY_SELF_PENDING, null)
        if (pending != null) {
            prefs(ctx).edit().remove(KEY_SELF_PENDING).commit()
            val last = summary(ctx) ?: Summary(System.currentTimeMillis())
            save(ctx, last.copy(updated = last.updated + pending.substringBeforeLast(' ')))
            InstalledVersions.confirm(ctx, ctx.packageName)
            Log.i(TAG, "self: now ${pending.substringAfterLast(' ')}")
        }
        sync(ctx)
    }

    /** The package Android treats as the installer for [pkg]: update owner first. */
    private fun ownerOf(ctx: Context, pkg: String): String? = runCatching {
        val info = ctx.packageManager.getInstallSourceInfo(pkg)
        (if (Build.VERSION.SDK_INT >= 34) info.updateOwnerPackageName else null)
            ?: info.installingPackageName
    }.getOrNull()

    private fun selfOnScreen(): Boolean {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
    }

    /** True when the app is not in use and no call is on; null if the system won't say. */
    @RequiresApi(34)
    private suspend fun notInUse(ctx: Context, pkg: String): Boolean? = runCatching {
        val constraints = PackageInstaller.InstallConstraints.Builder()
            .setAppNotInteractingRequired()
            .setNotInCallRequired()
            .build()
        withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<Boolean> { cont ->
                ctx.packageManager.packageInstaller.checkInstallConstraints(
                    listOf(pkg), constraints, ctx.mainExecutor,
                ) { r -> if (cont.isActive) cont.resume(r.areAllConstraintsSatisfied()) }
            }
        }
    }.onFailure { Log.w(TAG, "constraints for $pkg: ${it::class.java.simpleName}: ${it.message}") }
        .getOrNull()

    /** The catalogue to read. A debug build can be pointed at a test one. */
    fun indexUrl(ctx: Context): String =
        (if (BuildConfig.DEBUG) prefs(ctx).getString(KEY_INDEX_OVERRIDE, null) else null)
            ?: BuildConfig.INDEX_URL

    /** Debug builds only; a release build never reads it. */
    fun setIndexOverride(ctx: Context, url: String?) {
        prefs(ctx).edit().apply {
            if (url == null) remove(KEY_INDEX_OVERRIDE) else putString(KEY_INDEX_OVERRIDE, url)
        }.commit()
    }

    fun summary(ctx: Context): Summary? {
        val raw = prefs(ctx).getString(KEY_SUMMARY, null) ?: return null
        return runCatching {
            val o = org.json.JSONObject(raw)
            fun list(k: String) =
                o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            Summary(o.getLong("at"), list("updated"), list("needsTap"), list("failed"),
                o.optString("error").ifBlank { null })
        }.getOrNull()
    }

    private fun save(ctx: Context, s: Summary) {
        val o = org.json.JSONObject().apply {
            put("at", s.at)
            put("updated", org.json.JSONArray(s.updated))
            put("needsTap", org.json.JSONArray(s.needsTap))
            put("failed", org.json.JSONArray(s.failed))
            s.error?.let { put("error", it) }
        }
        prefs(ctx).edit().putString(KEY_SUMMARY, o.toString()).commit()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
