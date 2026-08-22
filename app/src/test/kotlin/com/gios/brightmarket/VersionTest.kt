package com.gios.brightmarket

import com.gios.brightmarket.data.Version
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deciding that a release is newer than what is installed.
 *
 * Two different situations, and the difference is the whole design.
 *
 * An **indexed** app carries a versionCode the index builder read out of the
 * APK itself. It is the same kind of number PackageManager reports, so it
 * settles the question outright and nothing else gets a vote.
 *
 * A **tracked** repo has no such number. Nothing parsed its APK, so the code is
 * guessed from the digits in the tag, and for anything but a monotonic run
 * number that guess is noise. Those cases fall through to the layered rule, and
 * are what most of these tests cover — hence `remoteVersionCodeIsReal = false`
 * nearly everywhere below.
 */
class VersionTest {

    // ---- indexed apps: the real number decides ------------------------------

    @Test
    fun `a real versionCode settles it, whatever the strings say`() {
        // BrightMusic. The tag is `build-131` and the versionName belongs to the
        // upstream fork, so the two strings disagree on every release including
        // the one already installed. Reading that as an update offered a
        // permanent update to the version you were already on.
        assertFalse(
            Version.updateAvailable(
                installedVersionName = "1.6.0",
                installedVersionCode = 131,
                installedByMarket = null,
                remoteVersion = "build-131",
                remoteVersionCode = 131,
                remoteVersionCodeIsReal = true,
            ),
        )
        assertTrue(
            Version.updateAvailable(
                installedVersionName = "1.6.0",
                installedVersionCode = 131,
                installedByMarket = null,
                remoteVersion = "build-132",
                remoteVersionCode = 132,
                remoteVersionCodeIsReal = true,
            ),
        )
    }

    @Test
    fun `a real versionCode also refuses a downgrade`() {
        assertFalse(
            Version.updateAvailable(
                installedVersionName = "1.6.0",
                installedVersionCode = 131,
                installedByMarket = null,
                remoteVersion = "build-130",
                remoteVersionCode = 130,
                remoteVersionCodeIsReal = true,
            ),
        )
    }

    // ---- tracked repos: same shape, different numbers -----------------------

    @Test
    fun `a build tag against a fork's own versionName is not an update`() {
        // Same trap as BrightMusic, on the tracked path where the code is only a
        // guess. Equal codes are the only evidence available, and they agree.
        assertFalse(
            Version.updateAvailable(
                installedVersionName = "1.6.0",
                installedVersionCode = 131,
                installedByMarket = null,
                remoteVersion = "build-131",
                remoteVersionCode = 131,
                remoteVersionCodeIsReal = false,
            ),
        )
    }

    @Test
    fun `build tags that share a shape compare on their numbers`() {
        assertTrue(Version.compareSameShape("build-71", "build-70")!! > 0)
        assertTrue(Version.compareSameShape("build-70", "build-71")!! < 0)
        assertEquals(0, Version.compareSameShape("build-70", "build-70"))
    }

    @Test
    fun `versions from different schemes share no shape`() {
        // The point of the shape test: these two were never the same kind of
        // string, so their difference says nothing about which is newer.
        assertNull(Version.compareSameShape("build-131", "1.6.0"))
    }

    // ---- the bug -----------------------------------------------------------

    @Test
    fun `semantic version rolling over is an update`() {
        // v1.2.10 -> v1.3.0 under the old rule compared 0 against 10 and said
        // no. This is the exact shape that sent people to another updater.
        assertTrue(
            Version.updateAvailable(
                installedVersionName = "1.2.10",
                installedVersionCode = 10,
                installedByMarket = null,
                remoteVersion = "v1.3.0",
                remoteVersionCodeIsReal = false,
                remoteVersionCode = 0,
            ),
        )
    }

    @Test
    fun `a date tag rolling over is an update`() {
        assertTrue(
            Version.updateAvailable(
                installedVersionName = "2026.07.30",
                installedVersionCode = 30,
                installedByMarket = null,
                remoteVersion = "2026.08.01",
                remoteVersionCodeIsReal = false,
                remoteVersionCode = 1,
            ),
        )
    }

    // ---- no regression for the Bright fleet --------------------------------

    @Test
    fun `the monotonic run-number scheme still works`() {
        // Tag v3.0.104, CI stamps versionCode 104. Codes really are comparable.
        assertTrue(
            Version.updateAvailable(
                installedVersionName = "3.0.102",
                installedVersionCode = 102,
                installedByMarket = null,
                remoteVersion = "3.0.104",
                remoteVersionCodeIsReal = false,
                remoteVersionCode = 104,
            ),
        )
        assertFalse(
            Version.updateAvailable(
                installedVersionName = "3.0.104",
                installedVersionCode = 104,
                installedByMarket = null,
                remoteVersion = "3.0.104",
                remoteVersionCodeIsReal = false,
                remoteVersionCode = 104,
            ),
        )
    }

    @Test
    fun `a v prefix is not a difference`() {
        assertFalse(
            Version.updateAvailable(
                installedVersionName = "1.4.2",
                installedVersionCode = 42,
                installedByMarket = null,
                remoteVersion = "v1.4.2",
                remoteVersionCodeIsReal = false,
                remoteVersionCode = 2,
            ),
        )
    }

    // ---- not offering downgrades -------------------------------------------

    @Test
    fun `an older release is not an update`() {
        assertFalse(
            Version.updateAvailable(
                installedVersionName = "2.1.0",
                installedVersionCode = 0,
                installedByMarket = null,
                remoteVersion = "2.0.9",
                remoteVersionCodeIsReal = false,
                remoteVersionCode = 9,
            ),
        )
    }

    // ---- what we installed wins -------------------------------------------

    @Test
    fun `the recorded release beats a versionName that never matches the tag`() {
        // An app whose versionName is nothing like its tag: without the record,
        // the strings differ every time and it would offer an update forever.
        assertFalse(
            Version.updateAvailable(
                installedVersionName = "Marshmallow",
                installedVersionCode = 7,
                installedByMarket = "build-70",
                remoteVersion = "build-70",
                remoteVersionCodeIsReal = false,
                remoteVersionCode = 70,
            ),
        )
        assertTrue(
            Version.updateAvailable(
                installedVersionName = "Marshmallow",
                installedVersionCode = 7,
                installedByMarket = "build-70",
                remoteVersion = "build-71",
                remoteVersionCodeIsReal = false,
                remoteVersionCode = 71,
            ),
        )
    }

    @Test
    fun `the recorded release also refuses a downgrade`() {
        assertFalse(
            Version.updateAvailable(
                installedVersionName = "1.9.0",
                installedVersionCode = 0,
                installedByMarket = "1.9.0",
                remoteVersion = "1.8.0",
                remoteVersionCodeIsReal = false,
                remoteVersionCode = 0,
            ),
        )
    }

    // ---- unknowns ----------------------------------------------------------

    @Test
    fun `an unparseable but different tag is offered rather than hidden`() {
        // A needless reinstall costs a download. A missed update costs the point
        // of the app, so the tie breaks toward offering it.
        assertTrue(
            Version.updateAvailable(
                installedVersionName = "nightly-abc123",
                installedVersionCode = 123,
                installedByMarket = null,
                remoteVersion = "nightly-def456",
                remoteVersionCodeIsReal = false,
                remoteVersionCode = 456,
            ),
        )
    }

    @Test
    fun `no remote version means nothing to offer`() {
        assertFalse(
            Version.updateAvailable(null, 1, null, null, 0, remoteVersionCodeIsReal = false),
        )
    }

    // ---- the comparator itself ---------------------------------------------

    @Test
    fun `numeric comparison reads segments, not text`() {
        assertTrue(Version.compareNumeric("1.10.0", "1.9.0")!! > 0)
        assertEquals(0, Version.compareNumeric("1.4", "1.4.0"))
        assertTrue(Version.compareNumeric("2.0.0", "10.0.0")!! < 0)
    }

    @Test
    fun `a suffix never makes a version look older`() {
        assertEquals(0, Version.compareNumeric("1.4.2-beta", "1.4.2"))
    }

    @Test
    fun `versions with no leading number are not comparable`() {
        assertNull(Version.compareNumeric("build-70", "build-71"))
    }
}
