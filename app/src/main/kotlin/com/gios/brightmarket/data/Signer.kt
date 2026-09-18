package com.gios.brightmarket.data

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

/**
 * The signing certificate of an installed app, as a SHA-256 hex string.
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
 * The index already records the certificate the submission checks pinned, so the comparison costs
 * one package-manager call and settles it.
 *
 * ## What an unknown answer means
 *
 * Null, and every caller treats null as "carry on as before". A phone that will not tell us, an
 * index entry with no signer recorded, a package we cannot see — none of those are evidence that an
 * app is somebody else's, and refusing to update on a failed read would break updates for everyone
 * the first time a platform behaves unexpectedly. Only a certificate that is present on both sides
 * and *differs* is treated as foreign.
 */
object Signer {

    /** SHA-256 of [pkg]'s signing certificate, lowercase hex, or null if it cannot be read. */
    fun of(ctx: Context, pkg: String): String? = runCatching {
        val pm = ctx.packageManager
        val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            val signing = info.signingInfo ?: return null
            // A rotated key reports a history; the current certificate is the one an install is
            // checked against, and it is the last entry.
            if (signing.hasMultipleSigners()) signing.apkContentsSigners
            else signing.signingCertificateHistory?.takeIf { it.isNotEmpty() }?.let { arrayOf(it.last()) }
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
        }
        val first = signatures?.firstOrNull() ?: return null
        sha256(first.toByteArray())
    }.getOrNull()

    /**
     * Whether [installed] is a build by somebody other than whoever the index pinned.
     *
     * False whenever either side is unknown. See the note on this file: an unreadable certificate
     * is not evidence of anything.
     */
    fun foreign(indexed: String?, installed: String?): Boolean {
        if (indexed.isNullOrBlank() || installed.isNullOrBlank()) return false
        return !indexed.equals(installed, ignoreCase = true)
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
