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
       "screenshots":[{"url":"https://raw.example/a.png","name":"a.png","size":1},
                      {"url":"https://raw.example/b.png","name":"b.png","size":2}],
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

    @Test fun `screenshots parse in order, and absent means empty not null`() {
        val apps = Index.parse(sample)
        val tip = apps.first { it.name == "BrightTip" }
        assertEquals(listOf("https://raw.example/a.png", "https://raw.example/b.png"), tip.screenshots)

        // BrightNoise has no screenshots key at all. Most apps won't, so this
        // has to be an empty list rather than a null the UI must guard.
        val noise = apps.first { it.name == "BrightNoise" }
        assertEquals(0, noise.screenshots.size)
    }

    @Test fun `an index with no screenshots key anywhere still parses`() {
        // The pre-screenshots index format must keep loading, or an app update
        // that lands before the index rebuild shows an empty store.
        val old = """{"format":1,"apps":[{"pkg":"a.b.c","name":"X","repo":"o/r",
          "category":"utilities","summary":"s",
          "latest":{"version":"1.0.0","versionCode":1,"apk":"u","size":1,
                    "sha256":"h","published":"2026-01-01T00:00:00Z","notes":""},
          "downloads":0,"firstSeen":"2026-01-01"}]}"""
        val apps = Index.parse(old)
        assertEquals(1, apps.size)
        assertEquals(0, apps.first().screenshots.size)
    }

    @Test fun `partition splits updatable from up to date`() {
        val apps = Index.parse(sample)   // BrightTip vc=18, BrightNoise vc=9
        val (updates, current) = Index.partitionInstalled(
            apps,
            mapOf("com.gios.lighttip" to 17L, "com.gios.lightnoise" to 9L),
        )
        assertEquals(listOf("BrightTip"), updates.map { it.app.name })
        assertEquals(listOf("BrightNoise"), current.map { it.app.name })
    }

    @Test fun `apps that are not installed appear in neither list`() {
        val apps = Index.parse(sample)
        val (updates, current) = Index.partitionInstalled(apps, emptyMap())
        assertEquals(0, updates.size)
        assertEquals(0, current.size)
    }

    @Test fun `a newer installed build is not offered as an update`() {
        // Sideloading a debug build gives a higher versionCode than the index.
        // That must read as up-to-date, not as a downgrade offer -- Android
        // would refuse the install anyway.
        val apps = Index.parse(sample)
        val (updates, current) = Index.partitionInstalled(
            apps, mapOf("com.gios.lighttip" to 999L)
        )
        assertEquals(0, updates.size)
        assertEquals(1, current.size)
    }

    @Test fun `comparison uses versionCode, not the version name`() {
        // BrightTip is versionName "1.3.18", versionCode 18. An installed code
        // of 9 is OLDER, even though the string "1.1.9" > "1.3.18" is false and
        // naive string comparison of names gets this backwards.
        val apps = Index.parse(sample)
        val (updates, _) = Index.partitionInstalled(apps, mapOf("com.gios.lighttip" to 9L))
        assertEquals(1, updates.size)
        assertTrue(updates.first().updatable)
    }

    @Test fun `update all puts the marketplace itself last`() {
        // Installing BrightMarket kills this process. Anything queued behind it
        // would silently never install, leaving a half-finished batch and no
        // error -- so it has to be the final entry regardless of input order.
        val self = "com.gios.brightmarket"
        val apps = Index.parse(sample)
        val market = apps.first().copy(pkg = self, name = "BrightMarket")

        val ordered = Index.selfLast(listOf(market) + apps, self)
        assertEquals(self, ordered.last().pkg)
        // Everything else keeps its relative order.
        assertEquals(apps.map { it.name }, ordered.dropLast(1).map { it.name })
    }

    @Test fun `selfLast is a no-op when the marketplace is not in the batch`() {
        val apps = Index.parse(sample)
        assertEquals(
            apps.map { it.pkg },
            Index.selfLast(apps, "com.gios.brightmarket").map { it.pkg },
        )
    }

    @Test fun `the marketplace entry is flagged as self`() {
        val self = "com.gios.lighttip"   // stand-in for our own package
        val apps = Index.parse(sample)
        val (updates, _) = Index.partitionInstalled(apps, mapOf(self to 1L), self)
        assertTrue(updates.first().isSelf)
    }
}
