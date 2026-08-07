package com.gios.brightmarket.data

import android.content.Context

/**
 * Indexed apps the user has said they care about — today, everything an
 * Obtainium import matched.
 *
 * Without this, importing an export was invisible for exactly the apps
 * BrightMarket *does* index: they matched, and then nothing happened on screen,
 * because the Updates tab only ever showed what PackageManager reported as
 * installed. Anything not currently on the phone silently went nowhere, which
 * reads as the import having skipped them.
 *
 * Following is not installing. A followed app that isn't on the phone is listed
 * so it can be installed in one tap and is watched for updates from then on.
 */
object Followed {

    private const val PREFS = "followed"
    private const val KEY = "pkgs"

    fun all(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(KEY, emptySet())?.toSet() ?: emptySet()

    fun add(ctx: Context, pkgs: Collection<String>): Int {
        val existing = all(ctx)
        val merged = existing + pkgs.filter { it.isNotBlank() }
        if (merged.size != existing.size) {
            // A defensive copy: SharedPreferences does not promise to copy the
            // set it is handed, and mutating it afterwards is undefined.
            prefs(ctx).edit().putStringSet(KEY, HashSet(merged)).apply()
        }
        return merged.size - existing.size
    }

    fun remove(ctx: Context, pkg: String) {
        prefs(ctx).edit().putStringSet(KEY, HashSet(all(ctx) - pkg)).apply()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
