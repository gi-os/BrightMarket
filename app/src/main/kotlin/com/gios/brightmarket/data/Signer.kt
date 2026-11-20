package com.gios.brightmarket.data

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.SigningInfo
import java.security.MessageDigest

/**
 * Catalogue signer pins, as SHA-256 fingerprints of the signing certificates.
 *
 * ## Why this is needed at all
 *
 * A package name is not an identity. Anyone may build an app under any applicationId, and forks of
 * the same project routinely keep the one they inherited — `app.lightphonekeyboard` is currently
 * shipped by three unrelated projects, each signed with its own key. Everything here used to match
 * on the package name alone, which made all three the same app:
 *
 *  - the counter reported strangers' versions on our row, and
 *  - the update check offered our APK to people running somebody else's build, where the install
 *    can only ever fail. Android identifies an app by (packageName, certificate), so a replacement
 *    signed by a different key is refused — not with a message anybody can act on, just a failure.
 *
 * The index pins the current release certificate and, for an intentional rotation, the original
 * certificate. Recognizing an old install is separate from authorizing a new APK.
 *
 * ## What an unknown answer means
 *
 * For installed apps, an unreadable certificate is not evidence that an app is foreign.
 * For downloads with a pin, failure to verify the signer or required lineage blocks the install.
 */
object Signer {

    /** Verified signing history of the installed package, or null if it cannot be read. */
    fun of(ctx: Context, pkg: String): List<String>? = runCatching {
        val info = ctx.packageManager.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
        verifiedHistory(info.signingInfo)
    }.getOrNull()

    /** Oldest to current, only for a single signer as reported by Android's verifier. */
    fun verifiedHistory(signing: SigningInfo?): List<String>? {
        if (signing == null) return null
        if (signing.hasMultipleSigners()) return emptyList()
        return signing.signingCertificateHistory?.map { sha256(it.toByteArray()) }
    }

    data class Pin(val current: String?, val root: String? = null) {
        val requiresVerification: Boolean get() = !current.isNullOrBlank() || root != null

        /** Only the installed history is known here; branch compatibility is checked on download. */
        fun foreign(installed: List<String>?): Boolean {
            if (current.isNullOrBlank() || installed == null) return false
            return !current.equals(installed.lastOrNull(), ignoreCase = true) &&
                (root == null || !root.equals(installed.firstOrNull(), ignoreCase = true))
        }

        /** [history] must come from Android's APK signature verifier, not the index. */
        fun accepts(history: List<String>?, installed: List<String>? = null): Boolean {
            if (current.isNullOrBlank() || history.isNullOrEmpty()) return false
            if (!current.equals(history.last(), ignoreCase = true)) return false
            if (root == null) return true
            if (history.size < 2 || !root.equals(history.first(), ignoreCase = true)) return false
            // An APK on a sibling branch may share the original cert but cannot update this copy.
            return installed == null || (installed.isNotEmpty() && installed.size <= history.size &&
                installed.indices.all { installed[it].equals(history[it], ignoreCase = true) })
        }
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
