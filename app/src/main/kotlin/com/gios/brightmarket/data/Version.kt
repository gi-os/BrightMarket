package com.gios.brightmarket.data

/**
 * Deciding whether a published release is newer than what is on the phone.
 *
 * ## Why this is not just `versionCode > installed`
 *
 * That was the rule, and the reasoning behind it was sound for exactly one
 * family of apps. Every Bright app's CI stamps `versionCode` from its monotonic
 * run number and tags the release `vBASE.RUN`, so the trailing segment of the
 * tag *is* the installed versionCode and comparing them works.
 *
 * Nothing else on GitHub does that. The index and the tracked-repo path both
 * synthesize a "versionCode" from the digits in the tag, and for an app on
 * ordinary semantic versioning that number is meaningless:
 *
 * ```
 * v1.2.10  ->  10        installed
 * v1.3.0   ->   0        the update
 * 0 > 10 is false        so no update, forever, silently
 * ```
 *
 * Which is precisely what it looks like from the outside: an app that is up to
 * date according to BrightMarket and out of date according to anything else.
 * Whole version schemes are invisible to it — a date tag (`2026.08.21` -> 21),
 * a bare `v2` (2), anything where the last number is not monotonic.
 *
 * ## The rule now
 *
 * Obtainium's answer, which is the right one: **a version that is not the
 * installed version is an update**. Numeric comparison is demoted to what it is
 * actually good for, which is refusing to call an older release an update.
 *
 * The checks run in order, strongest evidence first:
 *
 *  1. **What we installed.** When BrightMarket installed the app it recorded
 *     the release string it installed. Comparing against that is exact and
 *     survives any tag scheme, because both sides come from the same place.
 *  2. **The old versionCode rule**, kept as-is. For the Bright fleet the codes
 *     really are comparable, and this keeps that path behaving exactly as
 *     before rather than rebuilding it on weaker evidence.
 *  3. **The version strings differ.** The general case, guarded so that a
 *     release that is provably older is not offered as an update.
 */
object Version {

    /** Strip the decoration that separates a tag from the version inside it. */
    fun normalize(raw: String?): String? =
        raw?.trim()
            ?.removePrefix("v")
            ?.removePrefix("V")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    /**
     * Compare two dotted-numeric versions, or null when they are not both in a
     * shape where comparing means anything.
     *
     * Only the leading run of `number(.number)*` is read, so `1.4.2-beta` and
     * `1.4.2` compare on `1.4.2` and a suffix never makes a version look older
     * than it is. Missing trailing segments count as zero: `1.4` equals `1.4.0`.
     */
    fun compareNumeric(a: String?, b: String?): Int? {
        val left = numericParts(a) ?: return null
        val right = numericParts(b) ?: return null
        for (i in 0 until maxOf(left.size, right.size)) {
            val l = left.getOrElse(i) { 0L }
            val r = right.getOrElse(i) { 0L }
            if (l != r) return l.compareTo(r)
        }
        return 0
    }

    private fun numericParts(raw: String?): List<Long>? {
        val v = normalize(raw) ?: return null
        val head = Regex("""^\d+(?:\.\d+)*""").find(v)?.value ?: return null
        return head.split('.').mapNotNull { it.toLongOrNull() }.takeIf { it.isNotEmpty() }
    }

    /**
     * Whether [remoteVersion] should be offered as an update.
     *
     * @param installedVersionName what PackageManager reports, which for most
     *   apps is the same string the tag carries and for some is nothing like it.
     * @param installedVersionCode what PackageManager reports. Real, monotonic,
     *   and only comparable to [remoteVersionCode] when that one is real too.
     * @param installedByMarket the release string BrightMarket recorded when it
     *   installed this app, or null if it was installed some other way or before
     *   this was being recorded.
     * @param remoteVersionCode the synthesized code. Trustworthy for the Bright
     *   fleet, meaningless elsewhere, which is why it can only ever say yes here
     *   and never no.
     */
    fun updateAvailable(
        installedVersionName: String?,
        installedVersionCode: Long,
        installedByMarket: String?,
        remoteVersion: String?,
        remoteVersionCode: Long,
    ): Boolean {
        val remote = normalize(remoteVersion) ?: return false

        // 1. Exact: both strings came from a release tag, so they are the same
        //    kind of thing and inequality means a different release.
        installedByMarket?.let { recorded ->
            val known = normalize(recorded) ?: return@let
            if (remote == known) return false
            return compareNumeric(remote, known)?.let { it > 0 } ?: true
        }

        // 2. When both versions are readable as numbers they settle it outright,
        //    in both directions. This has to come before the versionCode rule
        //    below: that code is synthesized from the digits in the tag, so for
        //    an app going 2.1.0 -> 2.0.9 it reads 9 against 0 and calls a
        //    downgrade an update.
        val installed = normalize(installedVersionName)
        compareNumeric(remote, installed)?.let { return it > 0 }

        // 3. Not comparable as numbers. The original rule gets its say here,
        //    where it can only ever add an update rather than assert a bad one.
        if (remoteVersionCode > installedVersionCode) return true

        // 4. Different strings mean a different release. Offer it: a needless
        //    reinstall costs a download, a missed update costs the whole point
        //    of the app.
        if (installed == null) return false
        return remote != installed
    }
}
