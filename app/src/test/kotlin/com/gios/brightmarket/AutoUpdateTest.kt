package com.gios.brightmarket

import com.gios.brightmarket.data.App
import com.gios.brightmarket.data.Installed
import com.gios.brightmarket.data.Preview
import com.gios.brightmarket.data.target
import com.gios.brightmarket.update.AutoUpdate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class AutoUpdateTest {

    private val self = "com.gios.brightmarket"
    private val now = Instant.parse("2026-10-02T08:00:00Z").toEpochMilli()

    private fun app(pkg: String, published: String = "2026-09-30T00:00:00Z", hold: Boolean = false,
                    preview: Preview? = null) = App(
        pkg = pkg, name = pkg.substringAfterLast('.'), repo = "o/$pkg", category = "tools",
        summary = "", version = "2.0", versionCode = 2, apkUrl = "https://x/$pkg.apk", size = 1,
        sha256 = "00", publishedAt = published, notes = "", downloads = 0, firstSeen = "",
        hold = hold, preview = preview,
    )

    private fun upd(a: App, nightly: Boolean = false) =
        Installed(app = a, installedVersionCode = 1, isSelf = a.pkg == self, target = a.target(nightly))

    private fun reasons(list: List<Installed>, owner: (String) -> String? = { self }) =
        AutoUpdate.plan(list, now, self, owner).associate { it.update.app.pkg to it.reason }

    @Test fun `an owned official release a day old installs`() {
        assertEquals(mapOf("a.ok" to "install"), reasons(listOf(upd(app("a.ok")))))
    }

    @Test fun `each guard leaves its app alone, with its reason`() {
        val pv = Preview("3.0", 3, "https://x/n.apk", 1, "11", "2026-09-29T00:00:00Z", "")
        val r = reasons(
            listOf(
                upd(app("a.held", hold = true)),
                upd(app("a.fresh", published = "2026-10-02T01:00:00Z")),
                upd(app("a.nodate", published = "")),
                upd(app("a.nightly", preview = pv), nightly = true),
                upd(app("a.theirs")),
            ),
            owner = { if (it == "a.theirs") "dev.imranr.obtainium" else self },
        )
        assertEquals("held in the catalogue", r["a.held"])
        assertEquals("released under a day ago", r["a.fresh"])
        assertEquals("no release date", r["a.nodate"])
        assertEquals("nightly channel", r["a.nightly"])
        assertEquals(AutoUpdate.NEEDS_TAP, r["a.theirs"])
    }

    @Test fun `a soak of exactly one day is enough`() {
        val r = reasons(listOf(upd(app("a.edge", published = "2026-10-01T08:00:00Z"))))
        assertEquals("install", r["a.edge"])
    }

    @Test fun `an app installed by adb has no owner and needs a tap`() {
        assertEquals(AutoUpdate.NEEDS_TAP, reasons(listOf(upd(app("a.adb"))), owner = { null })["a.adb"])
    }

    @Test fun `BrightMarket updates itself whoever installed it, and goes last`() {
        val plan = AutoUpdate.plan(
            listOf(upd(app(self)), upd(app("a.one")), upd(app("a.two"))),
            now, self, owner = { if (it == self) "com.android.shell" else self },
        )
        assertEquals(listOf("a.one", "a.two", self), plan.map { it.update.app.pkg })
        assertEquals(listOf(true, true, true), plan.map { it.install })
    }

    @Test fun `the summary line reads in one breath`() {
        val s = AutoUpdate.Summary(0, updated = listOf("Chats", "Roll"), needsTap = listOf("Spotify"))
        assertEquals("Last check: updated Chats, Roll · 1 needs a tap in Updates.", s.line())
        assertEquals("Last check: nothing installed.", AutoUpdate.Summary(0).line())
    }
}
