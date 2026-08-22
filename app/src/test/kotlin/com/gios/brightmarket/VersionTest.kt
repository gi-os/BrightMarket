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
 * The bug these exist for: comparing a synthesized "versionCode" — the trailing
 * digits of a tag — against the real installed one. It works for the Bright
 * fleet by coincidence of how its CI stamps builds, and silently reports "up to
 * date" forever for everything else.
 */
class VersionTest {

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
                remoteVersionCode = 104,
            ),
        )
        assertFalse(
            Version.updateAvailable(
                installedVersionName = "3.0.104",
                installedVersionCode = 104,
                installedByMarket = null,
                remoteVersion = "3.0.104",
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
                remoteVersionCode = 70,
            ),
        )
        assertTrue(
            Version.updateAvailable(
                installedVersionName = "Marshmallow",
                installedVersionCode = 7,
                installedByMarket = "build-70",
                remoteVersion = "build-71",
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
                remoteVersionCode = 456,
            ),
        )
    }

    @Test
    fun `no remote version means nothing to offer`() {
        assertFalse(
            Version.updateAvailable(null, 1, null, null, 0),
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
