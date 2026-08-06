package com.gios.brightmarket

import com.gios.brightmarket.data.Index
import com.gios.brightmarket.data.Obtainium
import com.gios.brightmarket.data.Sort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These are pure JVM checks over parsing and ordering -- the parts that fail
 * silently on a phone. A wrong versionCode looks like "no update available"
 * forever, and a wrong Obtainium match silently drops someone's app list, so
 * neither is left to be discovered by hand.
 */
class IndexTest {

    private val sample = """
    {"format":1,"generated":"2026-08-06T23:03:08Z","apps":[
      {"pkg":"com.gios.lighttip","name":"BrightTip","repo":"gi-os/BrightTip",
       "category":"productivity","summary":"Tip calculator.",
       "latest":{"version":"1.3.18","versionCode":18,
                 "apk":"https://example/LightTip-v1.3.18.apk","size":4250807,
                 "sha256":"abc","published":"2026-08-05T23:25:49Z","notes":"n"},
       "downloads":16,"firstSeen":"2026-01-02"},
      {"pkg":"com.gios.lightnoise","name":"BrightNoise","repo":"gi-os/BrightNoise",
       "category":"media","summary":"White noise.",
       "latest":{"version":"1.1.9","versionCode":9,
                 "apk":"https://example/LightNoise-v1.1.9.apk","size":22593751,
                 "sha256":"def","published":"2026-08-03T00:48:43Z","notes":""},
       "downloads":99,"firstSeen":"2026-07-30"}
    ]}
    """.trimIndent()

    @Test fun `parses both apps with their real fields`() {
        val apps = Index.parse(sample)
        assertEquals(2, apps.size)
        val tip = apps.first()
        assertEquals("com.gios.lighttip", tip.pkg)
        assertEquals("BrightTip", tip.name)
        // The name says Bright but the applicationId still says light -- that
        // mismatch is deliberate and must survive parsing.
        assertTrue(tip.pkg.contains("light"))
        assertEquals(18L, tip.versionCode)
        assertEquals(16, tip.downloads)
    }

    @Test fun `each sort actually orders by its own field`() {
        val apps = Index.parse(sample)
        assertEquals("BrightTip", Index.sort(apps, Sort.UPDATED).first().name)
        assertEquals("BrightNoise", Index.sort(apps, Sort.NEW).first().name)
        assertEquals("BrightNoise", Index.sort(apps, Sort.POPULAR).first().name)
    }

    @Test fun `obtainium import reads the modern export shape`() {
        val export = """
        {"apps":[{"app":{"id":"com.gios.lighttip","url":"https://github.com/gi-os/BrightTip"}},
                 {"app":{"id":"com.example.unknown","url":"https://github.com/someone/Else"}}]}
        """.trimIndent()
        val result = Obtainium.match(Obtainium.parse(export), Index.parse(sample))
        assertEquals(1, result.matched.size)
        assertEquals("BrightTip", result.matched.first().name)
        // The one we can't place is reported, never silently dropped.
        assertEquals(1, result.unmatched.size)
    }

    @Test fun `obtainium import still reads the legacy bare-array export`() {
        val legacy = """[{"id":"com.gios.lightnoise","url":"https://github.com/gi-os/BrightNoise"}]"""
        val result = Obtainium.match(Obtainium.parse(legacy), Index.parse(sample))
        assertEquals(1, result.matched.size)
        assertEquals("BrightNoise", result.matched.first().name)
    }

    @Test fun `a renamed repo still matches on applicationId`() {
        // Obtainium exports made before the Light-to-Bright rename carry the OLD
        // repo URL. The applicationId never changed, so the match must survive.
        val stale = """[{"id":"com.gios.lighttip","url":"https://github.com/gi-os/LightTip"}]"""
        val result = Obtainium.match(Obtainium.parse(stale), Index.parse(sample))
        assertEquals(1, result.matched.size)
        assertEquals(0, result.unmatched.size)
    }

    @Test fun `repoFromUrl strips git suffix and ignores non-github urls`() {
        assertEquals("gi-os/BrightTip", Obtainium.repoFromUrl("https://github.com/gi-os/BrightTip.git"))
        assertEquals(null, Obtainium.repoFromUrl("https://gitlab.com/a/b"))
    }

    @Test fun `search matches name, summary and category`() {
        val apps = Index.parse(sample)
        assertEquals(1, Index.filter(apps, "tip").size)
        assertEquals(1, Index.filter(apps, "white noise").size)   // summary
        assertEquals(1, Index.filter(apps, "media").size)         // category
        assertEquals(2, Index.filter(apps, "").size)              // blank is a no-op
    }

    @Test fun `search matches the applicationId, which still says light`() {
        // Half the portfolio is still com.gios.light* after the Bright rename.
        // Someone typing "light" must still find those apps.
        val apps = Index.parse(sample)
        assertEquals(2, Index.filter(apps, "light").size)
        assertEquals(1, Index.filter(apps, "com.gios.lighttip").size)
    }

    @Test fun `search is case and whitespace insensitive`() {
        val apps = Index.parse(sample)
        assertEquals(1, Index.filter(apps, "  BRIGHTtip  ").size)
    }

    @Test fun `category filter narrows, and All does not`() {
        val apps = Index.parse(sample)
        assertEquals(2, Index.filter(apps, "", Index.ALL).size)
        assertEquals(1, Index.filter(apps, "", "media").size)
        assertEquals(0, Index.filter(apps, "", "games").size)
    }

    @Test fun `query and category combine rather than override`() {
        val apps = Index.parse(sample)
        // "tip" exists, but not inside the media category.
        assertEquals(0, Index.filter(apps, "tip", "media").size)
        assertEquals(1, Index.filter(apps, "tip", "productivity").size)
    }

    @Test fun `categories lists All first, then every category present`() {
        val cats = Index.categories(Index.parse(sample))
        assertEquals(Index.ALL, cats.first())
        assertTrue(cats.containsAll(listOf("media", "productivity")))
        // No duplicates, and nothing invented.
        assertEquals(cats.size, cats.distinct().size)
    }

    @Test fun `filtering an empty index is safe`() {
        assertEquals(0, Index.filter(emptyList(), "anything").size)
        assertEquals(listOf(Index.ALL), Index.categories(emptyList()))
    }
}
