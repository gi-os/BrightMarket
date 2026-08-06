package com.gios.brightmarket.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Downloads and installs an APK.
 *
 * v1 ships only this path: PackageInstaller with the system's own confirmation
 * dialog. It always works, on any device, with no setup. The silent path (an
 * embedded ADB client pairing the phone to its own adbd over loopback, which
 * gets shell uid and therefore INSTALL_PACKAGES) is a later version, and this
 * stays as the fallback -- an ADB maintainer has proposed binding adbd to wlan0
 * only, which would end that technique entirely.
 */
object Installer {

    const val ACTION_INSTALL_STATUS = "com.gios.brightmarket.INSTALL_STATUS"

    sealed interface Progress {
        data class Downloading(val bytes: Long, val total: Long) : Progress
        data object Verifying : Progress
        data object AwaitingConfirmation : Progress
        data class Failed(val reason: String) : Progress
    }

    /** True when [versionCode] is newer than what's on the device. */
    fun updateAvailable(ctx: Context, pkg: String, versionCode: Long): Boolean {
        val installed = installedVersionCode(ctx, pkg) ?: return false
        return versionCode > installed
    }

    fun installedVersionCode(ctx: Context, pkg: String): Long? = try {
        ctx.packageManager.getPackageInfo(pkg, 0).longVersionCode
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    suspend fun install(
        ctx: Context,
        apkUrl: String,
        expectedSha256: String,
        pkg: String,
        onProgress: (Progress) -> Unit = {},
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val apk = File(ctx.cacheDir, "$pkg.apk")
            download(apkUrl, apk, onProgress)

            onProgress(Progress.Verifying)
            val actual = sha256(apk)
            if (!actual.equals(expectedSha256, ignoreCase = true)) {
                apk.delete()
                // The index is a static file on GitHub Pages; the hash is the
                // only thing making it trustworthy. A mismatch is never worth
                // "probably fine".
                error("Download didn't match the expected checksum, so it wasn't installed.")
            }

            onProgress(Progress.AwaitingConfirmation)
            commitSession(ctx, apk, pkg)
            apk.delete()
        }.onFailure { onProgress(Progress.Failed(it.message ?: "install failed")) }
    }

    private fun download(url: String, dest: File, onProgress: (Progress) -> Unit) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode !in 200..299) {
                error("Download failed (HTTP ${conn.responseCode})")
            }
            val total = conn.contentLengthLong
            var written = 0L
            conn.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        written += n
                        onProgress(Progress.Downloading(written, total))
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun commitSession(ctx: Context, apk: File, pkg: String) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply { setAppPackageName(pkg) }

        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(ACTION_INSTALL_STATUS).setPackage(ctx.packageName)
            val pending = PendingIntent.getBroadcast(
                ctx,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pending.intentSender)
        }
    }
}
