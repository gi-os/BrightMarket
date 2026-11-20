package com.gios.brightmarket

import com.gios.brightmarket.data.Index
import com.gios.brightmarket.data.Signer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    private val rotated = "42".repeat(32)
    private val intermediate = "67".repeat(32)
    private val rotation = Signer.Pin(rotated, root = ours)

    @Test fun `a different certificate is foreign`() {
        assertTrue(Signer.Pin(ours).foreign(listOf(theirs)))
    }

    @Test fun `the same certificate is not, whatever the case`() {
        assertFalse(Signer.Pin(ours).foreign(listOf(ours)))
        assertFalse(Signer.Pin(ours.uppercase()).foreign(listOf(ours)))
    }

    /**
     * Not knowing is not evidence. A phone that will not report a certificate, or an index entry
     * without one, has to behave exactly as it did before this existed — otherwise the first
     * platform quirk stops updates for everybody.
     */
    @Test fun `an unknown certificate on either side is never foreign`() {
        assertFalse(Signer.Pin(null).foreign(listOf(theirs)))
        assertFalse(Signer.Pin("").foreign(listOf(theirs)))
        assertFalse(Signer.Pin(ours).foreign(null))
        assertFalse(Signer.Pin(null).foreign(null))
    }

    @Test fun `a legacy pin only authorizes the pinned current signer`() {
        val pin = Signer.Pin(ours)
        assertTrue(pin.accepts(listOf(ours.uppercase())))
        assertFalse(pin.accepts(listOf(ours, rotated)))
        assertFalse(pin.accepts(listOf(theirs)))
        assertFalse(pin.accepts(null))
        assertFalse(Signer.Pin("").requiresVerification)
    }

    @Test fun `the pinned new signer descends from the original signer`() {
        assertTrue(rotation.accepts(listOf(ours.uppercase(), rotated.uppercase())))
        assertTrue(rotation.accepts(listOf(ours, intermediate, rotated)))
    }

    @Test fun `an update must extend rather than replace the installed lineage`() {
        val update = listOf(ours, intermediate, rotated)
        assertTrue(rotation.accepts(update, listOf(ours)))
        assertTrue(rotation.accepts(update, listOf(ours, intermediate)))
        assertTrue(rotation.accepts(update, update))
        assertFalse(rotation.accepts(update, listOf(ours, theirs)))
        assertFalse(rotation.accepts(update, listOf(ours, rotated, theirs)))
        assertFalse(rotation.accepts(update, emptyList()))
    }

    @Test fun `the new signer without the pinned root is refused`() {
        assertFalse(rotation.accepts(listOf(rotated)))
        assertFalse(rotation.accepts(listOf(theirs, rotated)))
        assertFalse(rotation.accepts(listOf(theirs, ours, rotated)))
    }

    @Test fun `the old signer is not trusted to publish or nominate another successor`() {
        assertFalse(rotation.accepts(listOf(ours)))
        assertFalse(rotation.accepts(listOf(ours, theirs)))
    }

    @Test fun `unreadable signatures and incomplete rotation pins fail closed`() {
        assertFalse(rotation.accepts(null))
        assertFalse(rotation.accepts(emptyList()))
        assertFalse(Signer.Pin(rotated, root = "").accepts(listOf(rotated)))
        val missingCurrent = Signer.Pin("", root = ours)
        assertTrue(missingCurrent.requiresVerification)
        assertFalse(missingCurrent.accepts(listOf(ours, rotated)))
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
        val app = Index.parse(sample).single()
        assertEquals(ours, app.signer)
        assertNull(app.signerRoot)
    }

    @Test fun `a rotation offers the update to the original signer but not a fork`() {
        val apps = Index.parse(sample.replace(
            "\"signer\":\"$ours\"",
            "\"signer\":\"$rotated\",\"signerRoot\":\"$ours\"",
        ))
        assertEquals(rotation, apps.single().signingPin)
        for (history in listOf(listOf(ours), listOf(ours, intermediate), listOf(ours, intermediate, rotated))) {
            val (updates, _, _) = Index.partitionInstalled(
                apps, mapOf("app.lightphonekeyboard" to 13L), signerOf = { history },
            )
            assertFalse(updates.single().foreign)
        }
        val (updates, current, _) = Index.partitionInstalled(
            apps, mapOf("app.lightphonekeyboard" to 13L), signerOf = { listOf(theirs) },
        )
        assertTrue(updates.isEmpty())
        assertTrue(current.single().foreign)
    }

    /** Upstream's build is at versionCode 13 against our 36, so without this it is offered ours. */
    @Test fun `an older build signed by somebody else is not offered an update`() {
        val apps = Index.parse(sample)
        val (updates, current, _) = Index.partitionInstalled(
            apps,
            mapOf("app.lightphonekeyboard" to 13L),
            signerOf = { listOf(theirs) },
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
            signerOf = { listOf(ours) },
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
