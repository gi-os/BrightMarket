package com.gios.brightmarket.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * LightOS's design language, ported. See the light-sdk `sdk/ui` module (MIT).
 *
 * Three colours, no accent, no dividers. Anything that looks like Material --
 * ripples, filled text fields, elevation -- appears nowhere in LightOS and is
 * deliberately absent here.
 */
object Light {
    val Background = Color(0xFF000000)
    val Content = Color(0xFFFFFFFF)
    val ContentSecondary = Color(0xFFBBBBBB)
}

/**
 * LightGrid is 27 units wide and 31 tall. Every bar height, inset and icon size
 * is expressed in grid units against the screen width, never in fixed dp, which
 * is what keeps a layout identical across the LP3 panel and an emulator.
 */
@Composable
fun gridUnits(units: Float): Dp {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    return (screenWidth / 27f * units).dp
}

/**
 * The SDK scales type against a 600dp-tall design baseline. Named sizes, not
 * arbitrary sp: title 115, subtitle 52, heading 38, subheading 30, copy 30,
 * button 30, paragraph 24.5, detail 20, fine 25, superfine 16.
 */
@Composable
fun designSp(designPx: Float): TextUnit {
    val screenHeight = LocalConfiguration.current.screenHeightDp
    return (designPx * screenHeight / 600f).sp
}

@Composable
fun lightTypography(): Typography {
    val base = Typography()
    return base.copy(
        headlineLarge = TextStyle(fontSize = designSp(115f), fontWeight = FontWeight.Normal),
        headlineMedium = TextStyle(fontSize = designSp(52f), fontWeight = FontWeight.Normal),
        titleLarge = TextStyle(fontSize = designSp(38f), fontWeight = FontWeight.Normal),
        titleMedium = TextStyle(fontSize = designSp(30f), letterSpacing = 0.03.em),
        bodyLarge = TextStyle(fontSize = designSp(30f)),
        bodyMedium = TextStyle(fontSize = designSp(24.5f)),
        bodySmall = TextStyle(fontSize = designSp(20f)),
        labelLarge = TextStyle(fontSize = designSp(30f), letterSpacing = 0.15.em),
        labelSmall = TextStyle(fontSize = designSp(16f)),
    )
}

private val Double.em get() = androidx.compose.ui.unit.TextUnit(
    this.toFloat(), androidx.compose.ui.unit.TextUnitType.Em
)

/**
 * The SDK's clickable: no ripple, no indication of any kind. LightOS gives
 * feedback through a 45ms vibration on finger-DOWN rather than on click, so a
 * press registers before the gesture completes.
 */
fun Modifier.lightClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    clickable(
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
        content = content,
    )
}
