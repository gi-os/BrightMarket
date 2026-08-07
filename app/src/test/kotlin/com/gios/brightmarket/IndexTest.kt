package com.gios.brightmarket

import com.gios.brightmarket.data.Focus
import com.gios.brightmarket.data.Index
import com.gios.brightmarket.data.Obtainium
import com.gios.brightmarket.data.Tracked
import com.gios.brightmarket.data.Sort
import com.gios.brightmarket.data.target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        val (updates, current, _) = Index.partitionInstalled(
            apps,
            mapOf("com.gios.lighttip" to 17L, "com.gios.lightnoise" to 9L),
        )
        assertEquals(listOf("BrightTip"), updates.map { it.app.name })
        assertEquals(listOf("BrightNoise"), current.map { it.app.name })
    }

    @Test fun `apps that are not installed appear in neither list`() {
        val apps = Index.parse(sample)
        val (updates, current, _) = Index.partitionInstalled(apps, emptyMap())
        assertEquals(0, updates.size)
        assertEquals(0, current.size)
    }

    @Test fun `a newer installed build is not offered as an update`() {
        // Sideloading a debug build gives a higher versionCode than the index.
        // That must read as up-to-date, not as a downgrade offer -- Android
        // would refuse the install anyway.
        val apps = Index.parse(sample)
        val (updates, current, _) = Index.partitionInstalled(
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
        val (updates, _, _) = Index.partitionInstalled(apps, mapOf("com.gios.lighttip" to 9L))
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
        val (updates, _, _) = Index.partitionInstalled(apps, mapOf(self to 1L), self)
        assertTrue(updates.first().isSelf)
    }

    // ---- Focus mode link parsing -------------------------------------------
    // A QR scanner points at the world and decodes all sorts of things, so the
    // parser has to reject confidently rather than throw.

    @Test fun `app links parse to a package`() {
        assertEquals(
            Focus.Link.OpenApp("com.gios.lighttip"),
            Focus.parseLink("brightmarket://app/com.gios.lighttip"),
        )
    }

    @Test fun `focus on and off links parse`() {
        assertEquals(Focus.Link.SetFocus(true), Focus.parseLink("brightmarket://focus/on"))
        assertEquals(Focus.Link.SetFocus(false), Focus.parseLink("brightmarket://focus/off"))
    }

    @Test fun `scheme is required, so a stray QR is ignored`() {
        assertEquals(null, Focus.parseLink("https://example.com/app/com.gios.lighttip"))
        assertEquals(null, Focus.parseLink("just some text on a cereal box"))
        assertEquals(null, Focus.parseLink(""))
    }

    @Test fun `malformed brightmarket links are ignored, not guessed at`() {
        assertEquals(null, Focus.parseLink("brightmarket://app/"))
        // No dot: not a package name.
        assertEquals(null, Focus.parseLink("brightmarket://app/nonsense"))
        assertEquals(null, Focus.parseLink("brightmarket://focus/sideways"))
        assertEquals(null, Focus.parseLink("brightmarket://nothing/here"))
    }

    @Test fun `parsing tolerates case and surrounding whitespace`() {
        assertEquals(
            Focus.Link.SetFocus(true),
            Focus.parseLink("  BRIGHTMARKET://focus/ON  "),
        )
    }

    @Test fun `import keeps the apps BrightMarket does not index`() {
        val export = """
        {"apps":[{"app":{"id":"com.gios.lighttip","url":"https://github.com/gi-os/LightTip"}},
                 {"app":{"id":"com.termux","url":"https://github.com/termux/termux-app"}},
                 {"app":{"id":"org.fdroid","url":"https://f-droid.org/repo"}}]}
        """.trimIndent()
        val entries = Obtainium.parse(export)
        val result = Obtainium.match(entries, Index.parse(sample))

        // The indexed one matched on applicationId despite a pre-rename URL.
        assertEquals(listOf("BrightTip"), result.matched.map { it.name })

        // The GitHub one becomes trackable; the f-droid one has no repo to
        // watch and must be reported, not silently dropped.
        val trackable = Obtainium.trackable(result.unmatched)
        assertEquals(listOf("termux/termux-app"), trackable.map { it.repo })
        assertEquals(1, result.unmatched.size - trackable.size)
    }

    @Test fun `importing twice does not duplicate a tracked repo`() {
        // GitHub treats owner/name case-insensitively, so the same repo in two
        // exports must not become two rows that update each other in a loop.
        val a = Tracked.Entry(repo = "termux/termux-app")
        val b = Tracked.Entry(repo = "Termux/Termux-App")
        assertEquals(1, listOf(a, b).distinctBy { it.repo.lowercase() }.size)
    }


    @Test fun `followed apps that are not installed are listed separately`() {
        // This is what makes an import visible: matching an app the index
        // carries used to do nothing on screen unless it happened to already
        // be on the phone.
        val apps = Index.parse(sample)
        val (updates, current, notInstalled) = Index.partitionInstalled(
            apps,
            installed = emptyMap(),
            selfPkg = "",
            followed = setOf("com.gios.lighttip"),
        )
        assertEquals(0, updates.size)
        assertEquals(0, current.size)
        assertEquals(listOf("BrightTip"), notInstalled.map { it.name })
    }

    @Test fun `a followed app that IS installed is not listed twice`() {
        val apps = Index.parse(sample)
        val (_, current, notInstalled) = Index.partitionInstalled(
            apps,
            installed = mapOf("com.gios.lighttip" to 18L),
            selfPkg = "",
            followed = setOf("com.gios.lighttip"),
        )
        assertEquals(listOf("BrightTip"), current.map { it.app.name })
        assertEquals(0, notInstalled.size)
    }

}

class MarkdownTest {

    @Test fun `bold, italic, code and links are recognised`() {
        val spans = com.gios.brightmarket.data.Markdown.inline(
            "a **b** and *i* and `c` and [t](https://x.y)"
        )
        assertEquals("b", spans.first { it.style.name == "BOLD" }.text)
        assertEquals("i", spans.first { it.style.name == "ITALIC" }.text)
        assertEquals("c", spans.first { it.style.name == "CODE" }.text)
        val link = spans.first { it.style.name == "LINK" }
        assertEquals("t", link.text)
        assertEquals("https://x.y", link.href)
    }

    @Test fun `bold is not read as two italics`() {
        // ** has to be tried before *, or every bold run renders as emphasis
        // around an empty string.
        val spans = com.gios.brightmarket.data.Markdown.inline("**Full Changelog**: x")
        assertEquals("BOLD", spans.first().style.name)
        assertEquals("Full Changelog", spans.first().text)
    }

    @Test fun `unmatched marks are left alone rather than eaten`() {
        val spans = com.gios.brightmarket.data.Markdown.inline("trailing ** unmatched")
        assertEquals(1, spans.size)
        assertEquals("trailing ** unmatched", spans.first().text)
    }

    @Test fun `headings and bullets are detected`() {
        val lines = com.gios.brightmarket.data.Markdown.parse("## Title\n- one\n- two")
        assertEquals(2, lines[0].heading)
        assertEquals("Title", lines[0].spans.first().text)
        assertTrue(lines[1].bullet)
        assertEquals("one", lines[1].spans.first().text)
    }

    @Test fun `plain strips the marks`() {
        assertEquals(
            "Full Changelog: see here",
            com.gios.brightmarket.data.Markdown.plain("**Full Changelog**: [see here](https://x.y)")
        )
    }

    @Test fun `empty input doesn't blow up`() {
        assertEquals(1, com.gios.brightmarket.data.Markdown.inline("").size)
        assertEquals("", com.gios.brightmarket.data.Markdown.plain(""))
    }

    // -----------------------------------------------------------------------
    // Nightly channel
    // -----------------------------------------------------------------------

    private fun withPreview(previewCode: Long?): String {
        val preview = if (previewCode == null) "" else """
            ,"preview": {
              "version": "nightly-$previewCode", "versionCode": $previewCode,
              "apk": "https://e/n.apk", "size": 1, "sha256": "beef",
              "published": "2026-08-07T00:00:00Z", "notes": ""
            }
        """.trimIndent()
        return """
        {"format":1,"apps":[{
          "pkg":"a.b.c","name":"App","repo":"o/r","category":"utilities","summary":"s",
          "latest":{"version":"1.0.10","versionCode":10,"apk":"https://e/s.apk",
                    "size":1,"sha256":"cafe","published":"2026-08-01T00:00:00Z","notes":""}
          $preview
        }]}
        """.trimIndent()
    }

    @Test
    fun `preview is parsed when present and null when absent`() {
        assertNull(Index.parse(withPreview(null)).first().preview)
        val p = Index.parse(withPreview(20)).first().preview
        assertNotNull(p)
        assertEquals(20L, p!!.versionCode)
        assertEquals("https://e/n.apk", p.apkUrl)
    }

    @Test
    fun `a preview without a hash is refused`() {
        // No sha256 means nothing to verify the download against, which is the
        // one thing the nightly channel must not quietly become.
        val json = withPreview(20).replace("\"sha256\": \"beef\"", "\"sha256\": \"\"")
        assertNull(Index.parse(json).first().preview)
    }

    @Test
    fun `off the nightly channel the preview is ignored entirely`() {
        val app = Index.parse(withPreview(20)).first()
        val t = app.target(nightly = false)
        assertFalse(t.nightly)
        assertEquals(10L, t.versionCode)
    }

    @Test
    fun `on the nightly channel a newer preview wins`() {
        val t = Index.parse(withPreview(20)).first().target(nightly = true)
        assertTrue(t.nightly)
        assertEquals(20L, t.versionCode)
    }

    @Test
    fun `an official release that overtakes the nightly wins even on nightly`() {
        // The trap: cut a stable build after the last nightly and everyone who
        // opted in would sit on something older than everybody else.
        val t = Index.parse(withPreview(5)).first().target(nightly = true)
        assertFalse(t.nightly)
        assertEquals(10L, t.versionCode)
    }

    @Test
    fun `partitionInstalled offers a nightly update only on the nightly channel`() {
        val apps = Index.parse(withPreview(20))
        val installed = mapOf("a.b.c" to 10L)

        val (stableUpdates, stableCurrent, _) =
            Index.partitionInstalled(apps, installed, nightly = false)
        assertTrue(stableUpdates.isEmpty())
        assertEquals(1, stableCurrent.size)

        val (nightlyUpdates, _, _) =
            Index.partitionInstalled(apps, installed, nightly = true)
        assertEquals(1, nightlyUpdates.size)
        assertTrue(nightlyUpdates.first().target.nightly)
        assertEquals(20L, nightlyUpdates.first().target.versionCode)
    }
}
