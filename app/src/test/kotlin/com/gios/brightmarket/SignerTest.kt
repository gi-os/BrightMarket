package com.gios.brightmarket

import com.gios.brightmarket.data.Index
import com.gios.brightmarket.data.Signer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Matching an installed app against the certificate the index pinned.
 *
 * The case this exists for is real and live: `app.lightphonekeyboard` is shipped by three unrelated
 * projects — gi-os/BrightKeyboard, KEZO555/Type and the upstream it all forks from — each signed
 * with its own key. Matching on the package name alone counted all three as one app and offered our
 * APK to people running the other two, where Android can only refuse the install.
 */
class SignerTest {

    private val ours = "7844d8cc52fc12b5a2b5879b028605d90391a0a06cc4d87ef92a59350226d203"
    private val theirs = "355710cb7184d872ec40b9d060b7687a52b675f0000000000000000000000000"

    @Test fun `a different certificate is foreign`() {
        assertTrue(Signer.foreign(ours, theirs))
    }

    @Test fun `the same certificate is not, whatever the case`() {
        assertFalse(Signer.foreign(ours, ours))
        assertFalse(Signer.foreign(ours.uppercase(), ours))
    }

    /**
     * Not knowing is not evidence. A phone that will not report a certificate, or an index entry
     * without one, has to behave exactly as it did before this existed — otherwise the first
     * platform quirk stops updates for everybody.
     */
    @Test fun `an unknown certificate on either side is never foreign`() {
        assertFalse(Signer.foreign(null, theirs))
        assertFalse(Signer.foreign("", theirs))
        assertFalse(Signer.foreign(ours, null))
        assertFalse(Signer.foreign(ours, ""))
        assertFalse(Signer.foreign(null, null))
    }

    private val sample = """
    {"format":1,"apps":[
      {"pkg":"app.lightphonekeyboard","name":"BrightKeyboard","repo":"gi-os/BrightKeyboard",
       "category":"hardware","summary":"Keyboard.","signer":"$ours",
       "latest":{"version":"1.4.36","versionCode":36,
                 "apk":"https://example/BrightKeyboard-v1.4.36.apk","size":24033681,
                 "sha256":"abc","published":"2026-09-17T21:38:58Z","notes":""},
       "downloads":0,"firstSeen":"2026-08-06"}
    ]}
    """.trimIndent()

    @Test fun `the signer is read out of the index`() {
        assertEquals(ours, Index.parse(sample).single().signer)
    }

    /** Upstream's build is at versionCode 13 against our 36, so without this it is offered ours. */
    @Test fun `an older build signed by somebody else is not offered an update`() {
        val apps = Index.parse(sample)
        val (updates, current, _) = Index.partitionInstalled(
            apps,
            mapOf("app.lightphonekeyboard" to 13L),
            signerOf = { theirs },
        )
        assertEquals(0, updates.size)
        assertEquals(1, current.size)
        assertTrue(current.single().foreign)
    }

    @Test fun `our own older build still is`() {
        val apps = Index.parse(sample)
        val (updates, _, _) = Index.partitionInstalled(
            apps,
            mapOf("app.lightphonekeyboard" to 13L),
            signerOf = { ours },
        )
        assertEquals(1, updates.size)
        assertFalse(updates.single().foreign)
    }

    @Test fun `a phone that reports no certificate behaves as before`() {
        val apps = Index.parse(sample)
        val (updates, _, _) = Index.partitionInstalled(
            apps,
            mapOf("app.lightphonekeyboard" to 13L),
            signerOf = { null },
        )
        assertEquals(1, updates.size)
    }
}
