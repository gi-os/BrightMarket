package com.gios.brightmarket.hw

/*
 * The one part of `hw/` that did not move to light-common.
 *
 * light-common 1.2.0's wheel arms on two notches and then passes everything through, which is
 * the right behavior for scrolling a list. It is not the right behavior here: walking the
 * tvOS focus turns one inverted notch — a few percent of them are, because the sensor reads a
 * moving surface rather than a detent — into a whole row going the wrong way. v1.17 fixed that
 * with [NotchGuard], which also holds a turn's direction and only commits a reversal on the
 * third notch against it, and which has eight tests over a synthetic clock.
 *
 * Deleting this file in favour of the library would put that bug back. The guard belongs
 * upstream; until light-common carries it, this stays. Everything else in `hw/` — LightKeys,
 * and the report module beside it — now comes from the library.
 */

import android.webkit.WebView
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.abs
import kotlin.math.sign

/**
 * Wheel notches on their way from the activity to whatever is on screen.
 *
 * One notch per event, positive for up. The activity is the only thing that can see the
 * key — a `dispatchKeyEvent` override is what lets it win against a focused WebView — but
 * only the current screen knows what scrolling means, so the two are joined by a flow
 * rather than by the activity reaching into the UI.
 *
 * A [SharedFlow] with no replay, deliberately: a notch that arrives while nothing is
 * listening is gone, which is what you want. Buffered generously because the sensor emits
 * bursts far faster than a frame.
 */
class WheelBus {
    private val _notches = MutableSharedFlow<Int>(extraBufferCapacity = 64)
    val notches: SharedFlow<Int> = _notches.asSharedFlow()

    fun send(notches: Int) {
        _notches.tryEmit(notches)
    }
}

val LocalWheelBus = staticCompositionLocalOf<WheelBus?> { null }

/**
 * Distance per notch. About six notches to a screenful on the LPIII panel — enough that a
 * flick of the wheel moves you somewhere, short enough that you can land on a paragraph.
 */
private val NOTCH = 64.dp

/**
 * Which way a notch moves the page.
 *
 * `1` means turning the wheel up moves you *down* the document — the wheel drags the page
 * the way a finger flick does, rather than moving a viewport over it. Flip to `-1` for the
 * mouse-wheel convention.
 */
private const val DIRECTION = 1

/**
 * Fraction of the remaining distance applied per frame.
 *
 * This is the whole reason scrolling feels like scrolling rather than like a slide
 * projector. The sensor fires a notch every ~35 ms, which is faster than a frame, so
 * applying each one on arrival produces a stack of instant jumps — nothing to follow with
 * your eye. Instead every notch adds to a debt, and each frame pays off a share of it, so
 * one notch glides and a fast spin becomes a single continuous sweep that keeps moving
 * slightly after your thumb stops.
 *
 * 0.28 settles ~90% inside seven frames: quick enough to feel direct, slow enough to read.
 */
private const val SMOOTHING = 0.28f

/**
 * Notches needed to start scrolling, and how long a turn stays live.
 *
 * The wheel sits under a thumb and catches stray brushes, and one stray notch used to be a
 * scroll. So the first notch after a pause buys nothing on its own: it is remembered, and
 * only a second notch releases both. Once turning, everything applies immediately until
 * [IDLE_MS] passes with the wheel still, at which point the guard comes back.
 *
 * 1.5 s is deliberately long. It has to cover deliberate-but-slow turning, and the cost of
 * it being too long is nil — you are turning the wheel, so the next notch re-arms it.
 */
private const val ARM_NOTCHES = 2
private const val IDLE_MS = 1_500L

/**
 * Point the wheel at a Compose scroller. Works for both `ScrollState` and `LazyListState`.
 */
@Composable
fun WheelScroll(state: ScrollableState, active: Boolean = true) {
    val step = with(LocalDensity.current) { NOTCH.toPx() }
    val debt = remember { Debt() }
    val wake = remember { Channel<Unit>(Channel.CONFLATED) }

    ArmedNotches(active) { notches ->
        debt.px += notches * step * DIRECTION
        wake.trySend(Unit)
    }

    LaunchedEffect(state, wake) {
        while (true) {
            // Suspends while the wheel is still, so an idle screen costs nothing.
            wake.receive()
            // One scroll session for the whole glide. A finger on the screen takes priority
            // and cancels this, which is the right outcome.
            state.scroll {
                while (abs(debt.px) > 0.5f) {
                    withFrameNanos { }
                    val wanted = (debt.px * SMOOTHING).let {
                        // Never stall a notch out in sub-pixel increments.
                        if (abs(it) < 1f) debt.px else it
                    }
                    debt.px -= wanted
                    val consumed = scrollBy(wanted)
                    // At the top or bottom the rest of the debt is unpayable, and keeping
                    // it would mean the next turn back spends its first notches on nothing.
                    if (abs(consumed) < abs(wanted) - 0.5f) debt.px = 0f
                }
            }
        }
    }
}

/** The same, for the reader's WebView, which Compose knows nothing about. */
@Composable
fun WheelScroll(web: WebView?, active: Boolean = true) {
    val step = with(LocalDensity.current) { NOTCH.toPx() }
    val debt = remember { Debt() }
    val wake = remember { Channel<Unit>(Channel.CONFLATED) }

    ArmedNotches(active && web != null) { notches ->
        debt.px += notches * step * DIRECTION
        wake.trySend(Unit)
    }

    LaunchedEffect(web, wake) {
        val target = web ?: return@LaunchedEffect
        while (true) {
            wake.receive()
            while (abs(debt.px) > 0.5f) {
                withFrameNanos { }
                val wanted = (debt.px * SMOOTHING).let {
                    if (abs(it) < 1f) debt.px else it
                }
                debt.px -= wanted
                if (!target.wheelScrollBy(wanted.toInt())) debt.px = 0f
            }
        }
    }
}

/**
 * Point the wheel at the television instead of at a list: one notch, one step.
 *
 * Used on the remote, where turning the wheel walks the tvOS focus up and down. The sensor
 * fires every 35–60 ms, which is far quicker than anyone can read a moving highlight, so
 * notches are banked and paid out no faster than [minIntervalMs]. Without that a flick of the
 * thumb sends a dozen D-pad presses and the selection ends up somewhere nobody chose.
 *
 * The bank is clamped for the same reason: after a hard spin the tail of unpaid notches would
 * otherwise keep walking the focus for a second after the thumb stopped.
 */
@Composable
fun WheelSteps(
    active: Boolean = true,
    notchesPerStep: Int = NOTCHES_PER_STEP,
    minIntervalMs: Long = 110,
    onStep: suspend (Int) -> Unit,
) {
    val handler by rememberUpdatedState(onStep)
    val bank = remember { Debt() }
    val wake = remember { Channel<Unit>(Channel.CONFLATED) }

    ArmedNotches(active) { notches ->
        bank.px = (bank.px + notches).coerceIn(-MAX_BANKED_NOTCHES, MAX_BANKED_NOTCHES)
        wake.trySend(Unit)
    }

    LaunchedEffect(wake, notchesPerStep, minIntervalMs) {
        while (true) {
            wake.receive()
            while (abs(bank.px) >= notchesPerStep) {
                val direction = bank.px.sign
                bank.px -= direction * notchesPerStep
                // Awaited, not fired and forgotten. A step is a request the television has to
                // answer, and paying the bank out on a fixed timer regardless meant a spin whose
                // round trip ran slower than the timer built a queue of presses that kept walking
                // the focus after the thumb stopped — the clamp on the bank only limits notches
                // nobody has spent yet, not steps already in the air.
                val started = System.nanoTime()
                handler(direction.toInt())
                // A floor on the rate, not an addition to it: a round trip that already took
                // longer than the interval has paced itself.
                val elapsed = (System.nanoTime() - started) / 1_000_000
                if (elapsed < minIntervalMs) delay(minIntervalMs - elapsed)
            }
            // The remainder is kept, not discarded. Zeroing it would mean a wheel turned one
            // notch at a time never moved anything at all, since no single notch reaches the
            // threshold on its own.
        }
    }
}

/**
 * Notches the wheel has to travel to move the focus one row.
 *
 * One-to-one was the obvious first guess and it overshot by exactly double: the sensor is
 * optical, so a "notch" is a sampling artefact rather than a detent you can feel, and what
 * reads as one flick of the thumb is two of them.
 */
private const val NOTCHES_PER_STEP = 2

/**
 * Notches worth banking mid-spin — two rows' worth. Beyond this the focus keeps travelling
 * after the thumb has stopped, which feels like the remote arguing with you.
 *
 * This was four rows, and four rows is too many when one step is a network round trip: the
 * coast outlived the gesture by most of a second, and every row of it looked like the remote
 * moving on its own.
 */
private const val MAX_BANKED_NOTCHES = 4f

/**
 * Notches against the turn needed to call it a reversal.
 *
 * The wheel is an optical sensor reading a moving surface, not a detented encoder, so a steady
 * turn is not a clean run of same-sign notches: a few percent come back with the opposite sign,
 * most often at the start and the end of a spin where the surface is barely moving. A scroller
 * absorbs those — the debt is signed and they mostly cancel — but a control where one notch is
 * one discrete action does not, and a single stray notch there is a whole row of tvOS focus
 * travelling the wrong way. That is what "it jumps around" is.
 *
 * So while a turn is in progress notches against it are spent proving themselves and do nothing.
 * Turning back for real produces a run of them and the third one through commits the reversal.
 *
 * Three, not two. Strays are not evenly distributed — they cluster where the surface is barely
 * moving, which means two of them in a row is a normal thing to see, and at two this guard would
 * hand one of them through *and* flip the direction it is protecting, so the next real notch
 * would be swallowed instead. Three costs 120 ms on a deliberate change of direction, against the
 * two notches it already takes to start a turn at all.
 */
private const val REVERSE_NOTCHES = 3

/** Notches, minus the stray ones. The rules are in [NotchGuard]. */
@Composable
private fun ArmedNotches(active: Boolean, onNotch: (Int) -> Unit) {
    val handler by rememberUpdatedState(onNotch)
    val bus = LocalWheelBus.current ?: return
    LaunchedEffect(bus, active) {
        if (!active) return@LaunchedEffect
        // Guard state lives in the effect rather than in composition state: it is a property of
        // the turn in progress, and a recomposition mid-turn should not disarm the wheel.
        val guard = NotchGuard()
        bus.notches.collect { notches ->
            val emit = guard.accept(notches, System.nanoTime() / 1_000_000)
            if (emit != 0) handler(emit)
        }
    }
}

/**
 * Which notches survive the guard, as plain arithmetic over (notch, clock).
 *
 * Pulled out of the collector so it can be tested. The whole of this file is otherwise a
 * Composable and a `SharedFlow`, neither of which a JVM unit test can drive — and the two rules
 * in here are exactly the kind that read as obviously right and then behave differently in the
 * hand. There is no Android toolchain in the sandbox this is written in, so a test that runs
 * without one is worth more than usual.
 */
internal class NotchGuard {
    private var armed = false
    private var held = 0
    private var count = 0
    private var last = 0L

    /** Which way the turn in progress is going, and how many notches have come against it. */
    private var turn = 0
    private var against = 0

    /** @return the notches to act on, or 0 to swallow this one. */
    fun accept(notches: Int, nowMs: Long): Int {
        if (nowMs - last > IDLE_MS) {
            armed = false
            held = 0
            count = 0
            turn = 0
            against = 0
        }
        last = nowMs

        if (armed) {
            if (turn != 0 && notches.sign != 0 && notches.sign != turn) {
                against++
                // Too few of them to be a change of mind, so it was the sensor.
                if (against < REVERSE_NOTCHES) return 0
                turn = notches.sign
                against = 0
            } else {
                against = 0
                if (notches.sign != 0) turn = notches.sign
            }
            return notches
        }

        held += notches
        count++
        if (count < ARM_NOTCHES) return 0
        armed = true
        // Release what the guard was holding, so nothing deliberate is lost.
        val release = if (held != 0) held else notches.sign
        turn = release.sign
        against = 0
        held = 0
        return release
    }
}

/**
 * Scrolling the document, bounded at both ends. Returns false at an edge, so the caller can
 * drop the rest of the debt instead of pushing against it.
 *
 * `canScrollVertically` is the public way to ask — `computeVerticalScrollRange` is protected
 * on View, and the content height is only available in CSS pixels that would have to be
 * scaled back by hand.
 */
private fun WebView.wheelScrollBy(px: Int): Boolean {
    if (px == 0) return true
    if (!canScrollVertically(if (px > 0) 1 else -1)) return false
    scrollBy(0, px)
    return true
}

/**
 * Distance still owed to the scroller.
 *
 * Deliberately not Compose state: nothing in composition reads it, and making it observable
 * would restart the glide on every recomposition it caused.
 */
private class Debt {
    @Volatile
    var px: Float = 0f
}
