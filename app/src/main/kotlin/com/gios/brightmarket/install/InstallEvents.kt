package com.gios.brightmarket.install

import android.content.pm.PackageInstaller
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

/**
 * The bridge between a committed install session and the coroutine that started
 * it.
 *
 * ## Why this has to exist
 *
 * `PackageInstaller.Session.commit()` returns the moment the session is handed
 * over, which is long before anything is installed: on the dialog path the very
 * next thing that happens is a broadcast asking us to show the system's
 * confirmation UI, and the actual result arrives in a second broadcast after the
 * person taps INSTALL. So [Installer.install] returning success means "the
 * session was accepted", not "the app is on the phone".
 *
 * For one install that distinction is invisible. For a batch it is the whole
 * bug: "update all" looped over its list and committed every session back to
 * back in a few hundred milliseconds, each one launching a confirmation activity
 * over the last. One dialog survives, so one app updates, and the button reads
 * as broken.
 *
 * [expect] is registered *before* the commit, so there is no window in which the
 * result can arrive with nobody listening — which is why this is a map of
 * deferreds rather than a shared flow a collector has to get subscribed to in
 * time.
 */
object InstallEvents {

    data class Result(val pkg: String, val status: Int, val message: String) {
        val success: Boolean get() = status == PackageInstaller.STATUS_SUCCESS
    }

    private val waiters = ConcurrentHashMap<String, CompletableDeferred<Result>>()

    /**
     * Claim the next terminal result for [pkg]. Call before committing.
     *
     * A second call for a package already being waited on replaces the first and
     * completes it as aborted, so nothing is left hanging forever.
     */
    fun expect(pkg: String): CompletableDeferred<Result> {
        val fresh = CompletableDeferred<Result>()
        waiters.put(pkg, fresh)?.complete(
            Result(pkg, PackageInstaller.STATUS_FAILURE_ABORTED, "superseded")
        )
        return fresh
    }

    /** Stop waiting — the install never got as far as a session. */
    fun cancel(pkg: String) {
        waiters.remove(pkg)?.complete(
            Result(pkg, PackageInstaller.STATUS_FAILURE_ABORTED, "not committed")
        )
    }

    /**
     * A terminal result arrived. STATUS_PENDING_USER_ACTION must never reach
     * here: it is the system asking for its dialog, and treating it as an answer
     * is how a batch would race itself again.
     */
    fun publish(pkg: String, status: Int, message: String) {
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) return
        waiters.remove(pkg)?.complete(Result(pkg, status, message))
    }
}
