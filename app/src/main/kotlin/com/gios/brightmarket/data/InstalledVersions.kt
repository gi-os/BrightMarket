package com.gios.brightmarket.data

import android.content.Context

/**
 * The release string BrightMarket last installed for a package.
 *
 * This is the only completely reliable input [Version.updateAvailable] has.
 * Everything else compares a release tag against a versionName or a synthesized
 * number, and those are different kinds of thing that only sometimes agree. Two
 * release strings from the same repo always agree, because they came from the
 * same place.
 *
 * Written in two steps on purpose. [markPending] records at the moment the
 * session is committed, which is *before* the user has approved anything, and
 * [confirm] promotes it only once PackageInstaller reports success. Recording in
 * one step at commit time would mean a cancelled install still claimed the new
 * version — and this record's whole job is to say "no update needed", so a
 * wrong one hides a real update, which is the exact bug this file exists to
 * prevent.
 */
object InstalledVersions {

    private const val PREFS = "installed_versions"
    private const val PENDING = "pending:"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** What we installed for [pkg], or null if it did not come through here. */
    fun get(ctx: Context, pkg: String): String? =
        prefs(ctx).getString(pkg, null)?.takeIf { it.isNotBlank() }

    /** About to hand this version to PackageInstaller. Not yet true. */
    fun markPending(ctx: Context, pkg: String, version: String) {
        if (pkg.isBlank() || version.isBlank()) return
        prefs(ctx).edit().putString(PENDING + pkg, version).apply()
    }

    /**
     * PackageInstaller says it landed. Promote the pending record.
     *
     * The package name is all the success broadcast carries, which is why the
     * version has to have been parked under it beforehand.
     */
    fun confirm(ctx: Context, pkg: String) {
        if (pkg.isBlank()) return
        val p = prefs(ctx)
        val pending = p.getString(PENDING + pkg, null) ?: return
        p.edit().putString(pkg, pending).remove(PENDING + pkg).apply()
    }

    /** The install did not happen. Drop the claim rather than leaving it to rot. */
    fun clearPending(ctx: Context, pkg: String) {
        if (pkg.isBlank()) return
        prefs(ctx).edit().remove(PENDING + pkg).apply()
    }

    /** Forget a package entirely, for an uninstall. */
    fun forget(ctx: Context, pkg: String) {
        prefs(ctx).edit().remove(pkg).remove(PENDING + pkg).apply()
    }
}
