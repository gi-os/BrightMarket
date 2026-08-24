package com.gios.brightmarket

import com.gios.brightmarket.data.Version
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The "old -> new" line under an app that has an update.
 *
 * It read `build 214 -> v1.4.2`: a versionCode on the left, a release name on
 * the right. Nobody recognizes a versionCode as the version they are running,
 * so both halves have to be release names for the line to say anything.
 */
class VersionLabelTest {

    @Test
    fun `the recorded release wins`() {
        assertEquals(
            "v1.4.1",
            Version.installedLabel("v1.4.1", "1.4.1-dirty", 214),
        )
    }

    @Test
    fun `PackageManager's name is next`() {
        assertEquals("v1.4.1", Version.installedLabel(null, "1.4.1", 214))
    }

    @Test
    fun `the code is the last resort, not the default`() {
        assertEquals("build 214", Version.installedLabel(null, null, 214))
        assertEquals("build 214", Version.installedLabel("  ", "", 214))
    }

    @Test
    fun `display normalizes exactly one v`() {
        assertEquals("v1.4.2", Version.display("v1.4.2"))
        assertEquals("v1.4.2", Version.display("1.4.2"))
        assertEquals("unknown", Version.display(null))
    }
}
