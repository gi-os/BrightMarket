package com.gios.brightmarket.data

import android.content.Context

/**
 * Focus mode: the phone stops being somewhere you browse.
 *
 * The problem isn't installing apps — that already works. It's that a store on
 * your phone is a scrollable feed, and a scrollable feed on a Light Phone
 * defeats the point of owning one. So discovery moves to the desktop: you
 * browse the catalog on a real screen and send the phone one specific
 * decision, as a QR code.
 *
 * What stays on the phone is deliberate: the apps you already have, and their
 * updates. That's maintenance, not discovery — you can patch a security fix
 * without finding a laptop, but you can never *encounter* an app you don't
 * already own.
 *
 * ## Why there is no unlock key
 *
 * An earlier design made disabling require a key held by the desktop that set
 * it up. That was dropped once focus mode became switchable in Settings behind
 * a confirmation: a lock with a documented bypass isn't a lock, it's a lie
 * about how much protection you have. This is a commitment device, not a
 * security boundary. The friction is a deliberate pause — a mode you chose, a
 * confirmation to leave — and pretending otherwise would invite someone to
 * depend on a guarantee that was never there.
 */
object Focus {

    private const val PREFS = "focus"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_ONBOARDED = "onboarded"
    private const val KEY_NIGHTLY = "nightly"

    /** `brightmarket://` links, carried by QR codes from the desktop site. */
    sealed interface Link {
        /** Open one app's detail page — the only route to an app in focus mode. */
        data class OpenApp(val pkg: String) : Link
        data class SetFocus(val on: Boolean) : Link
    }

    fun enabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ENABLED, false)

    /**
     * Whether to be offered prerelease builds. Off by default and it should
     * stay that way: a nightly is whatever was pushed, and the person who has
     * not gone looking for that has not agreed to run it.
     *
     * Lives here rather than in its own file because it is the same shape of
     * thing — a preference about how the app behaves, stored beside the other
     * one — and a second SharedPreferences file for one boolean is filing, not
     * design.
     */
    fun nightly(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_NIGHTLY, false)

    fun setNightly(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_NIGHTLY, on).apply()
    }

    /**
     * True once the user has actually chosen a mode. Distinct from [enabled]:
     * "hasn't chosen yet" and "chose browsing" are different states, and
     * conflating them would silently skip the choice for anyone who picks the
     * default.
     */
    fun onboarded(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ONBOARDED, false)

    fun choose(ctx: Context, focus: Boolean) {
        prefs(ctx).edit()
            .putBoolean(KEY_ENABLED, focus)
            .putBoolean(KEY_ONBOARDED, true)
            .apply()
    }

    fun setEnabled(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, on).apply()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Parse a scanned QR payload.
     *
     * Returns null for anything unrecognised rather than throwing. A QR scanner
     * points at the world and decodes all sorts of things; a stray barcode on a
     * cereal box has to be a no-op, not a crash.
     *
     * BrightMarket does not open the camera itself. The phone already has a QR
     * scanner — Roll — and adding CameraX plus a decoder here would duplicate it
     * for one screen, along with a camera permission this app has no other
     * reason to hold. Scanning with Roll fires the intent instead.
     */
    fun parseLink(raw: String): Link? {
        // Parsed by hand rather than with android.net.Uri. Uri is an Android
        // stub under plain JVM unit tests, so using it would make this logic
        // untestable without pulling in Robolectric -- for what is, in the end,
        // splitting a short string on '/' and '?'.
        val text = raw.trim()
        val scheme = "brightmarket://"
        if (!text.regionMatches(0, scheme, 0, scheme.length, ignoreCase = true)) return null

        val rest = text.substring(scheme.length).substringBefore('?').trim('/')
        if (rest.isEmpty()) return null

        val parts = rest.split('/')
        val host = parts.firstOrNull()?.lowercase() ?: return null
        val first = parts.getOrNull(1)?.lowercase()

        return when (host) {
            // brightmarket://app/com.gios.lighttip
            "app" -> parts.getOrNull(1)
                // A package name always contains a dot. This rejects the obvious
                // malformed cases without pretending to fully validate: the
                // index lookup is the real check, and an unknown package simply
                // isn't found.
                ?.takeIf { it.isNotBlank() && it.contains('.') }
                ?.let { Link.OpenApp(it) }

            // brightmarket://focus/on | brightmarket://focus/off
            "focus" -> when (first) {
                "on" -> Link.SetFocus(true)
                "off" -> Link.SetFocus(false)
                else -> null
            }

            else -> null
        }
    }
}
