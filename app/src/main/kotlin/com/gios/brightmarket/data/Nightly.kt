package com.gios.brightmarket.data

import android.content.Context

/**
 * Which apps are on the nightly channel, one app at a time.
 *
 * ## Why this is per app and not one switch
 *
 * The channel was a single global preference, and a single global preference is
 * the wrong shape for what people actually want from it. Nobody wants every app
 * on the phone to be a prerelease. They want the one app they are helping test
 * to be, and everything else to stay on official releases — a dialler and a
 * keyboard on nightlies is not enthusiasm, it is a phone that stops working on
 * a Tuesday.
 *
 * So the choice lives beside the app it is about, on that app's own page.
 *
 * ## Three states, not two
 *
 * [choice] returns null for an app nobody has decided about, which is different
 * from an app deliberately kept on official releases. Only an explicit choice is
 * stored; anything unset falls back to the Settings default, so the existing
 * global preference keeps meaning what it meant and turning it off does not
 * silently discard per-app opt-ins.
 */
object Nightly {

    private const val PREFS = "nightly_apps"

    /** The explicit choice for [pkg], or null if nobody has made one. */
    fun choice(ctx: Context, pkg: String): Boolean? {
        val p = prefs(ctx)
        return if (p.contains(pkg)) p.getBoolean(pkg, false) else null
    }

    /** The choice for [pkg], falling back to the Settings default. */
    fun enabled(ctx: Context, pkg: String, default: Boolean): Boolean =
        choice(ctx, pkg) ?: default

    fun set(ctx: Context, pkg: String, on: Boolean) {
        if (pkg.isBlank()) return
        prefs(ctx).edit().putBoolean(pkg, on).apply()
    }

    /** Back to following the Settings default. */
    fun clear(ctx: Context, pkg: String) {
        prefs(ctx).edit().remove(pkg).apply()
    }

    /**
     * Every explicit choice, as one map.
     *
     * Read once into UI state rather than queried per row: the browse list draws
     * every app in the catalogue and a SharedPreferences lookup inside a
     * composable runs on every recomposition of every row.
     */
    fun all(ctx: Context): Map<String, Boolean> =
        prefs(ctx).all.mapNotNull { (k, v) -> (v as? Boolean)?.let { k to it } }.toMap()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
