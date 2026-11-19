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

    /** SHA-256 of [pkg]'s signing certificate, lowercase hex, or null if it cannot be read. */
    fun of(ctx: Context, pkg: String): String? = runCatching {
        val info = ctx.packageManager.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
        info.signingInfo?.apkContentsSigners?.firstOrNull()?.let { sha256(it.toByteArray()) }
    }.getOrNull()

    /** Oldest to current, only for a single signer as reported by Android's verifier. */
    fun verifiedHistory(signing: SigningInfo?): List<String>? {
        if (signing == null || signing.hasMultipleSigners()) return null
        return signing.signingCertificateHistory?.map { sha256(it.toByteArray()) }
    }

    data class Pin(val current: String?, val root: String? = null) {
        val requiresVerification: Boolean get() = !current.isNullOrBlank() || root != null

        /** The pre-rotation key still identifies old installs but cannot authorize downloads. */
        fun foreign(installed: String?): Boolean {
            if (current.isNullOrBlank() || installed.isNullOrBlank()) return false
            return !current.equals(installed, ignoreCase = true) &&
                !root.equals(installed, ignoreCase = true)
        }

        /** [history] must come from Android's APK signature verifier, not the index. */
        fun accepts(history: List<String>?): Boolean {
            if (current.isNullOrBlank() || history.isNullOrEmpty()) return false
            if (!current.equals(history.last(), ignoreCase = true)) return false
            return root == null ||
                (history.size > 1 && root.equals(history.first(), ignoreCase = true))
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
