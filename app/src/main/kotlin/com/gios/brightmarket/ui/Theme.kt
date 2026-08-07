package com.gios.brightmarket.ui

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * LightOS's design language, ported from light-sdk's `sdk/ui` (MIT).
 *
 * Three colours. No accent, no dividers, no elevation. Anything that looks like
 * Material — ripples, filled text fields, tonal surfaces, progress spinners —
 * appears nowhere in LightOS and is deliberately absent here.
 */
object Light {
    val Background = Color(0xFF000000)
    val Content = Color(0xFFFFFFFF)
    val ContentSecondary = Color(0xFFBBBBBB)
}

/**
 * LightGrid is 27 units wide and 31 tall. Every bar height, inset and icon size
 * is expressed in grid units against the screen width — never fixed dp — which
 * is what keeps a layout identical across the LP3 panel and an emulator.
 *
 * The SDK's own constants: top bar 3 units, bottom bar 4, horizontal inset 1,
 * bar icons 2.
 */
@Composable
fun gridUnits(units: Float): Dp {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    return (screenWidth / 27f * units).dp
}

object Grid {
    const val TOP_BAR = 3f
    const val BOTTOM_BAR = 4f
    const val INSET = 1f
    const val ICON = 2f
}

/**
 * The SDK scales type against a 600dp-tall design baseline: a size is given in
 * design pixels and multiplied by `screenHeightDp / 600`.
 */
@Composable
fun designSp(designPx: Float): TextUnit {
    val screenHeight = LocalConfiguration.current.screenHeightDp
    return (designPx * screenHeight / 600f).sp
}

private val Double.em get() = TextUnit(this.toFloat(), TextUnitType.Em)

/**
 * The SDK's named scale, mapped onto Material's slots so plain `Text` picks the
 * right one. Design pixels are the LP3 values from light-sdk:
 *
 *   title 115 · subtitle 52 · heading 38 · subheading 30 (3% tracking)
 *   copy 30 · button 30 (15% tracking) · paragraph 24.5 · detail 20
 *   fine 25 · superfine 16
 *
 * Usage rules from the SDK, which the screens follow: top-bar titles are `fine`,
 * bar labels are `button`, list rows are `copy` over `detail`.
 */
@Composable
fun lightTypography(): Typography = Typography().copy(
    displayLarge = TextStyle(fontSize = designSp(115f)),                 // title
    headlineLarge = TextStyle(fontSize = designSp(52f)),                 // subtitle
    titleLarge = TextStyle(fontSize = designSp(38f)),                    // heading
    titleMedium = TextStyle(fontSize = designSp(30f), letterSpacing = 0.03.em), // subheading
    bodyLarge = TextStyle(fontSize = designSp(30f)),                     // copy
    labelLarge = TextStyle(fontSize = designSp(30f), letterSpacing = 0.15.em),  // button
    bodyMedium = TextStyle(fontSize = designSp(24.5f)),                  // paragraph
    bodySmall = TextStyle(fontSize = designSp(20f)),                     // detail
    labelMedium = TextStyle(fontSize = designSp(25f)),                   // fine
    labelSmall = TextStyle(fontSize = designSp(16f)),                    // superfine
)

/**
 * The SDK's clickable: no ripple and no indication of any kind. Feedback is a
 * 45ms vibration on finger-DOWN rather than on click, so a press registers
 * before the gesture completes — which is the whole reason it feels responsive
 * on a screen with no animation.
 */
fun Modifier.lightClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(VibratorManager::class.java))?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    }

    this
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onPress = {
                    vibrator?.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
                },
                // The tap itself is handled by clickable() below so semantics
                // and accessibility still work; this layer only adds the haptic.
                onTap = { },
            )
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

@Composable
fun BrightMarketTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Light.Background,
            surface = Light.Background,
            onBackground = Light.Content,
            onSurface = Light.Content,
            primary = Light.Content,
            onPrimary = Light.Background,
        ),
        typography = lightTypography(),
    ) {
        // Material3's Text falls back to LocalContentColor, and that local
        // defaults to BLACK -- it is normally supplied by a Surface, which this
        // app deliberately doesn't use (Surface brings elevation and tonal
        // overlays that appear nowhere in LightOS). Without this, every Text
        // that doesn't name a colour renders black on black and is invisible.
        // The colorScheme above does NOT cover it: onBackground is only read by
        // components that consult it, not by a bare Text.
        CompositionLocalProvider(LocalContentColor provides Light.Content) {
            content()
        }
    }
}
