package com.gios.brightmarket.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast
import com.gios.brightmarket.data.InstalledVersions
import com.gios.brightmarket.update.AutoUpdate

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
        val unattended = intent.getBooleanExtra(Installer.EXTRA_UNATTENDED, false)
        val target = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: intent.getStringExtra(Installer.EXTRA_TARGET).orEmpty()

        // Nobody is holding the phone. Android wants a tap for this one -- it is not
        // ours to update silently -- and opening the dialog from a background job would
        // put an install prompt on the screen at 3 a.m. Drop the session; the app stays
        // in Updates for the next time somebody opens BrightMarket.
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION && unattended) {
            val session = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
            // Why Android wanted a tap, for the log: the one thing worth knowing when a
            // phone does this to an app BrightMarket thought it owned.
            android.util.Log.i(
                AutoUpdate.TAG,
                "system wants a tap for $target: " + (intent.extras?.keySet()?.joinToString { k ->
                    "$k=${if (k == Intent.EXTRA_INTENT) "…" else intent.extras?.get(k)}"
                } ?: ""),
            )
            if (session >= 0) {
                runCatching { context.packageManager.packageInstaller.abandonSession(session) }
            }
            InstalledVersions.clearPending(context, target)
            InstallEvents.publishNeedsTap(target, AutoUpdate.NEEDS_TAP)
            return
        }

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
        val pkg = target
        if (status == PackageInstaller.STATUS_SUCCESS) {
            InstalledVersions.confirm(context, pkg)
        } else {
            InstalledVersions.clearPending(context, pkg)
        }

        // Whatever started this session may be waiting on the answer -- an
        // "update all" run holds its place in the queue until each install
        // reaches a terminal state, because committing the next one first is
        // what buried the confirmation dialogs.
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
        InstallEvents.publish(pkg, status, message)
        // No toast for a background install: there is nobody to read it, and on a
        // screen that is on it is a message about something the person didn't do.
        if (!unattended) Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
