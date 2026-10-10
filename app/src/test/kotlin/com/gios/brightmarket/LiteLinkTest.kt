package com.gios.brightmarket

import com.gios.brightmarket.data.Index
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Lite/full links between a Light Tool Library build and the app it is cut down from.
 * Both fields are optional and newer than most indexes a phone has cached, so the absent
 * case is the one that matters most: it must read as "none", never as a crash or a link
 * to nowhere.
 */
class LiteLinkTest {

    private fun entry(pkg: String, name: String, extra: String = "") = """
      {"pkg":"$pkg","name":"$name","repo":"gi-os/$name","category":"productivity",
       "summary":"s",$extra
       "latest":{"version":"1.0.0","versionCode":1,"apk":"u","size":1,
                 "sha256":"h","published":"2026-01-01T00:00:00Z","notes":""},
       "downloads":0,"firstSeen":"2026-01-01"}
    """

    private fun index(vararg entries: String) =
        """{"format":1,"apps":[${entries.joinToString(",")}]}"""

    private val linked = index(
        entry("com.gios.lightnotebook", "BrightNotebook",
            """"liteVersion":"com.gios.brightnotebooklite","""),
        entry("com.gios.brightnotebooklite", "BrightNotebook Lite",
            """"fullVersion":"com.gios.lightnotebook","""),
        entry("com.gios.lighttip", "BrightTip"),
    )

    @Test fun `a Lite app carries its full app's applicationId`() {
        val lite = Index.parse(linked).first { it.pkg == "com.gios.brightnotebooklite" }
        assertEquals("com.gios.lightnotebook", lite.fullVersion)
        assertEquals("", lite.liteVersion)
        assertTrue(lite.isLite)
    }

    @Test fun `a full app carries its Lite build's applicationId`() {
        val full = Index.parse(linked).first { it.pkg == "com.gios.lightnotebook" }
        assertEquals("com.gios.brightnotebooklite", full.liteVersion)
        assertEquals("", full.fullVersion)
        assertFalse(full.isLite)
    }

    @Test fun `an app with neither key has no links`() {
        val tip = Index.parse(linked).first { it.pkg == "com.gios.lighttip" }
        assertEquals("", tip.fullVersion)
        assertEquals("", tip.liteVersion)
        assertFalse(tip.isLite)
    }

    @Test fun `an old index without either field still parses`() {
        val apps = Index.parse(index(entry("a.b.c", "X")))
        assertEquals(1, apps.size)
        assertFalse(apps.first().isLite)
        assertEquals("", apps.first().liteVersion)
    }

    @Test fun `null, blank, non-string and self links read as none`() {
        val apps = Index.parse(index(
            entry("a.null", "N", """"fullVersion":null,"liteVersion":null,"""),
            entry("a.blank", "B", """"fullVersion":"  ","liteVersion":"","""),
            entry("a.num", "Num", """"fullVersion":5,"liteVersion":true,"""),
            entry("a.self", "S", """"fullVersion":"a.self","liteVersion":"a.self","""),
        ))
        apps.forEach {
            assertEquals(it.pkg, "", it.fullVersion)
            assertEquals(it.pkg, "", it.liteVersion)
            assertFalse(it.pkg, it.isLite)
        }
    }

    @Test fun `surrounding whitespace is trimmed`() {
        val app = Index.parse(index(entry("a.lite", "L", """"fullVersion":" a.full ","""))).first()
        assertEquals("a.full", app.fullVersion)
    }

    @Test fun `linked resolves each direction against the catalogue`() {
        val apps = Index.parse(linked)
        val lite = apps.first { it.pkg == "com.gios.brightnotebooklite" }
        val full = apps.first { it.pkg == "com.gios.lightnotebook" }
        assertEquals("BrightNotebook", Index.linked(apps, lite.fullVersion)?.name)
        assertEquals("BrightNotebook Lite", Index.linked(apps, full.liteVersion)?.name)
    }

    @Test fun `linked is null for a blank link or an app not in the catalogue`() {
        val apps = Index.parse(linked)
        assertNull(Index.linked(apps, ""))
        assertNull(Index.linked(apps, "com.example.missing"))
    }

    @Test fun `a Lite app still shows in search and filter like any other`() {
        val apps = Index.parse(linked)
        assertEquals(2, Index.filter(apps, "notebook").size)
    }
}
