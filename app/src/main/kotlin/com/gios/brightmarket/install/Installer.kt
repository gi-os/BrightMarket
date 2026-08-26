package com.gios.brightmarket.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import com.gios.brightmarket.data.InstalledVersions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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

    /**
     * Read the applicationId and versionCode out of a downloaded APK.
     *
     * This is how a scanned repo learns what it actually is. A tracked repo
     * gives us only "owner/name" — nothing in a GitHub release says which
     * package it installs — so without this the app can never tell whether a
     * tracked app is installed, and never offers an update for it.
     *
     * getPackageArchiveInfo is the platform's own parser, so no aapt and no
     * manifest-guessing: the numbers are the ones PackageManager will use.
     */
    fun readApk(ctx: Context, file: File): Pair<String, Long>? =
        runCatching {
            val info = ctx.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
                ?: return null
            info.packageName to info.longVersionCode
        }.getOrNull()

    /** True when [versionCode] is newer than what's on the device. */
    fun updateAvailable(ctx: Context, pkg: String, versionCode: Long): Boolean {
        val installed = installedVersionCode(ctx, pkg) ?: return false
        return versionCode > installed
    }

    /**
     * The installed versionCode, or null if the app isn't on the phone.
     *
     * Null is also what comes back when the package is merely *invisible* to
     * us, which is not the same thing and is exactly how this failed silently:
     * without QUERY_ALL_PACKAGES in the manifest, API 30+ throws
     * NameNotFoundException for every package regardless of whether it is
     * installed, and this catch turned all of it into "not installed".
     */
    fun installedVersionCode(ctx: Context, pkg: String): Long? = try {
        ctx.packageManager.getPackageInfo(pkg, 0).longVersionCode
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /**
     * The installed versionName — the human string, "1.4.2".
     *
     * The counterpart to [installedVersionCode], and the one that can actually
     * be compared against a release tag. See [com.gios.brightmarket.data.Version]
     * for why comparing codes alone was missing updates.
     */
    fun installedVersionName(ctx: Context, pkg: String): String? = try {
        ctx.packageManager.getPackageInfo(pkg, 0).versionName
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /**
     * @param expectedSha256 the hash the index published, or null for a tracked
     *   repo that BrightMarket doesn't index. Null means the download CANNOT be
     *   verified — nothing generated a hash for it — so the check is skipped
     *   rather than faked. That difference is surfaced in the UI; quietly
     *   accepting an unverified download would make the indexed guarantee
     *   meaningless.
     */
    suspend fun install(
        ctx: Context,
        apkUrl: String,
        expectedSha256: String?,
        pkg: String,
        /**
         * The release string being installed, recorded once the install lands so
         * later checks can compare like with like. See [InstalledVersions].
         */
        releaseVersion: String? = null,
        onProgress: (Progress) -> Unit = {},
        /** Called with (applicationId, versionCode) once the APK is on disk. */
        onIdentified: (String, Long) -> Unit = { _, _ -> },
        /**
         * Don't return until PackageInstaller reports what happened.
         *
         * Off by default, because a single install has nothing to wait for: the
         * broadcast receiver shows the result and refreshes the row. Batches need
         * it. `commit()` returns as soon as the session is handed over, so
         * committing the next app immediately launches a second confirmation
         * activity over the first one -- and the person only ever gets to answer
         * the last dialog standing. See [InstallEvents].
         */
        awaitResult: Boolean = false,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        // Named per install, not per package. `pkg` is empty for a tracked repo
        // whose applicationId isn't known yet, which made the file ".apk" --
        // one shared name for every unlisted download, so two of them running
        // near each other wrote over one another.
        val apk = File(ctx.cacheDir, "dl-" + (pkg.ifBlank { apkUrl }).hashCode().toUInt().toString(16) + ".apk")
        runCatching {
            download(apkUrl, apk, onProgress)

            if (expectedSha256 != null) {
            onProgress(Progress.Verifying)
            val actual = sha256(apk)
            if (!actual.equals(expectedSha256, ignoreCase = true)) {
                apk.delete()
                // The index is a static file on GitHub Pages; the hash is the
                // only thing making it trustworthy. A mismatch is never worth
                // "probably fine".
                error("Download didn't match the expected checksum, so it wasn't installed.")
            }
            }

            // Learn what this APK actually is before handing it to the
            // installer -- for a tracked repo this is the only chance to find
            // out, and it is what makes future update checks possible.
            // Read once: this parses the whole archive, and these are 60MB files.
            val identity = readApk(ctx, apk)
            identity?.let { (id, code) -> onIdentified(id, code) }

            // An APK the package manager cannot parse is not going to install,
            // and saying so here is far more use than the installer's own
            // "There was a problem parsing the package".
            if (identity == null) {
                error("That download isn't a readable Android package. It may have arrived incomplete — try again.")
            }

            // `pkg` non-blank means the caller already believes this download
            // is a specific app -- a tracked repo whose applicationId was
            // learned on an earlier install. If the APK actually on disk is a
            // different one, committing anyway asks PackageInstaller to open a
            // session for one app and hand it another; Android notices and
            // refuses with INSTALL_FAILED_INVALID_APK, a string that names
            // nothing and looks exactly like a corrupt file. It happened for
            // real: a repo can publish more than one installable APK in a
            // single release (Obtainium ships a "-fdroid-" flavored build
            // alongside the regular one, under a different applicationId
            // entirely), and asset order is not guaranteed stable release to
            // release. Catching the mismatch here turns that into a message
            // that says what actually happened.
            if (pkg.isNotBlank() && identity.first != pkg) {
                error(
                    "This release is a different app (${identity.first}) than the one being " +
                        "tracked ($pkg). Forget it and add it again to switch to the new one."
                )
            }

            onProgress(Progress.AwaitingConfirmation)
            val target = pkg.ifBlank { identity.first }
            releaseVersion?.let { InstalledVersions.markPending(ctx, target, it) }
            // Registered BEFORE the commit. The result broadcast can arrive on
            // the very next tick, and a listener attached afterwards would miss
            // it and wait out the whole timeout for an install that already
            // finished.
            val waiter = if (awaitResult) InstallEvents.expect(target) else null
            try {
                commitSession(ctx, apk, target)
            } catch (e: Throwable) {
                if (waiter != null) InstallEvents.cancel(target)
                throw e
            }
            if (waiter != null) {
                val outcome = try {
                    withTimeout(CONFIRM_TIMEOUT_MS) { waiter.await() }
                } catch (e: TimeoutCancellationException) {
                    // A dialog nobody answered. Give up on this one rather than
                    // stalling the rest of the batch behind it forever.
                    InstallEvents.cancel(target)
                    null
                }
                if (outcome != null && !outcome.success) {
                    apk.delete()
                    error(outcome.message)
                }
            }
            apk.delete()
            // delete() returns Boolean, which would make this Result<Boolean>.
            // The contract is Result<Unit>, and whether the temp copy was still
            // on disk is not something a caller should branch on.
            Unit
        }.onFailure {
            // A partial file left in the cache is how a retry inherits the
            // failure it was meant to escape.
            apk.delete()
            onProgress(Progress.Failed(it.message ?: "install failed"))
        }
    }

    /**
     * How long to hold a batch open on one confirmation dialog.
     *
     * Long enough that reading the dialog, or glancing away mid-batch, doesn't
     * drop the rest of the queue; short enough that a phone left on the counter
     * is not stuck in "updating" until it is picked up.
     */
    private const val CONFIRM_TIMEOUT_MS = 5 * 60 * 1000L

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

            // A dropped connection ends the read loop without throwing, so a
            // truncated file looked like a finished download. For an indexed app
            // the sha256 catches it a moment later; for an unlisted one there is
            // no hash, so a short file went straight to the installer, failed
            // there for reasons that named nothing, and worked on the retry that
            // happened to complete. Which is precisely how this gets reported:
            // "it failed, then it didn't".
            if (total > 0 && written != total) {
                error("Download stopped early — got ${written / 1_000_000}MB of ${total / 1_000_000}MB.")
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
