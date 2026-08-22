package com.gios.brightmarket.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast
import com.gios.brightmarket.data.InstalledVersions

/**
 * Receives the PackageInstaller session result.
 *
 * STATUS_PENDING_USER_ACTION is the normal, expected first response on the
 * dialog path -- it is the system asking us to show its confirmation UI, not a
 * failure. Treating it as one is the classic way an install appears to do
 * nothing at all.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = if (android.os.Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            }
            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            confirm?.let(context::startActivity)
            return
        }

        // Which release actually landed, so the next update check can compare
        // two strings from the same source instead of guessing across schemes.
        // Only on success: a cancelled install must not claim the new version,
        // because that record's job is to say "up to date".
        val pkg = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME).orEmpty()
        if (status == PackageInstaller.STATUS_SUCCESS) {
            InstalledVersions.confirm(context, pkg)
        } else {
            InstalledVersions.clearPending(context, pkg)
        }

        val message = when (status) {
            PackageInstaller.STATUS_SUCCESS -> "Installed"
            PackageInstaller.STATUS_FAILURE_ABORTED -> "Install cancelled"
            PackageInstaller.STATUS_FAILURE_CONFLICT ->
                // Practically always a signing-certificate mismatch: the same
                // package signed by a different key can't upgrade in place.
                "Already installed with a different signature — uninstall it first"
            PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough storage"
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "Not compatible with this device"
            else -> intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Install failed"
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
