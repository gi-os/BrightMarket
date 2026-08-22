package com.gios.brightmarket.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Handing an app's ADB setup to BrightControl.
 *
 * Some Light Phone apps need a grant LightOS has no screen for -- `WRITE_SECURE_SETTINGS`, an
 * app op, a notification listener. The README says "run this from a computer", which on a phone
 * bought specifically to be used away from a computer is a poor answer. BrightControl already
 * holds an ADB connection to the phone's own daemon, so it can run them here.
 *
 * This side only carries the request. It deliberately does no checking of its own: BrightControl
 * parses every line, rebuilds each command pinned to the requesting package, refuses anything
 * that names a different one, and shows the user the result before running it. Validating here
 * as well would only invite the assumption that the far side can relax, and the far side is the
 * one holding the shell.
 */
object AdbSetup {

    const val CONTROL_PKG = "com.gios.lightcontrol"

    private const val ACTION = "com.gios.lightcontrol.action.RUN_GRANTS"
    private const val EXTRA_PACKAGE = "com.gios.lightcontrol.extra.PACKAGE"
    private const val EXTRA_LABEL = "com.gios.lightcontrol.extra.LABEL"
    private const val EXTRA_COMMANDS = "com.gios.lightcontrol.extra.COMMANDS"

    /** Whether BrightControl is on the phone at all. */
    fun controlInstalled(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo(CONTROL_PKG, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /**
     * Open BrightControl on this app's setup.
     *
     * Explicit package rather than a bare action: the receiver is one known app, and an implicit
     * intent for something that runs shell commands is an invitation for anything else to answer
     * it. Returns false when it could not be opened, so the caller can say so rather than leaving
     * a button that does nothing.
     */
    fun open(ctx: Context, app: App): Boolean {
        if (app.adbSetup.isEmpty()) return false
        val intent = Intent(ACTION).apply {
            setPackage(CONTROL_PKG)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_PACKAGE, app.pkg)
            putExtra(EXTRA_LABEL, app.name)
            putStringArrayListExtra(EXTRA_COMMANDS, ArrayList(app.adbSetup))
        }
        return runCatching { ctx.startActivity(intent); true }.getOrDefault(false)
    }
}
