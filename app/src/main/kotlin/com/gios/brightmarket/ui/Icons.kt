package com.gios.brightmarket.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Icons, drawn rather than shipped.
 *
 * The SDK's own set is ~105 `ic_*_white.xml` vectors, and the handful used here
 * are simple enough to draw directly — which avoids copying assets out of
 * another project and keeps the APK from carrying a drawable set it barely
 * touches. They follow the same rules as the SDK's: single colour, stroked not
 * filled, no rounded decoration, sized in grid units by the caller.
 *
 * Everything is expressed as a fraction of the canvas, so one definition works
 * at bar size and at any other size a screen asks for.
 */

private fun DrawScope.strokePx() = size.minDimension * 0.085f

@Composable
fun IconBrowse(tint: Color, units: Float = Grid.ICON) {
    Canvas(Modifier.size(gridUnits(units))) {
        val w = strokePx()
        val cell = size.minDimension / 2.6f
        val gap = size.minDimension - cell * 2
        // Four cells: a catalogue, not a list.
        listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f).forEach { (cx, cy) ->
            drawRect(
                color = tint,
                topLeft = Offset(cx * (cell + gap), cy * (cell + gap)),
                size = Size(cell, cell),
                style = Stroke(width = w),
            )
        }
    }
}

@Composable
fun IconUpdates(tint: Color, units: Float = Grid.ICON) {
    Canvas(Modifier.size(gridUnits(units))) {
        val w = strokePx()
        val cx = size.width / 2
        // Arrow down into a tray: an update arriving, not a generic download.
        drawLine(tint, Offset(cx, size.height * 0.08f), Offset(cx, size.height * 0.60f), w, StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.28f, size.height * 0.40f), Offset(cx, size.height * 0.62f), w, StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.72f, size.height * 0.40f), Offset(cx, size.height * 0.62f), w, StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.12f, size.height * 0.86f), Offset(size.width * 0.88f, size.height * 0.86f), w, StrokeCap.Round)
    }
}

@Composable
fun IconSettings(tint: Color, units: Float = Grid.ICON) {
    Canvas(Modifier.size(gridUnits(units))) {
        val w = strokePx()
        // Sliders rather than a cog: a cog at this size turns into a blob.
        listOf(0.24f, 0.5f, 0.76f).forEachIndexed { i, y ->
            drawLine(
                tint,
                Offset(size.width * 0.10f, size.height * y),
                Offset(size.width * 0.90f, size.height * y),
                w, StrokeCap.Round,
            )
            val knobX = size.width * (if (i % 2 == 0) 0.68f else 0.34f)
            drawCircle(tint, radius = w * 1.5f, center = Offset(knobX, size.height * y))
        }
    }
}

@Composable
fun IconScan(tint: Color, units: Float = Grid.ICON) {
    Canvas(Modifier.size(gridUnits(units))) {
        val w = strokePx()
        val arm = size.minDimension * 0.30f
        val i = size.minDimension * 0.08f
        val far = size.minDimension - i
        // Four viewfinder corners — the universal "point the camera here".
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(tint, Offset(x, y), Offset(x + dx * arm, y), w, StrokeCap.Round)
            drawLine(tint, Offset(x, y), Offset(x, y + dy * arm), w, StrokeCap.Round)
        }
        corner(i, i, 1f, 1f)
        corner(far, i, -1f, 1f)
        corner(i, far, 1f, -1f)
        corner(far, far, -1f, -1f)
    }
}

@Composable
fun IconRefresh(tint: Color, units: Float = Grid.ICON) {
    Canvas(Modifier.size(gridUnits(units))) {
        val w = strokePx()
        val inset = size.minDimension * 0.16f
        // An open circle plus an arrowhead: a full ring reads as a spinner,
        // which would imply it is already working.
        drawArc(
            color = tint,
            startAngle = 40f,
            sweepAngle = 280f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2, size.height - inset * 2),
            style = Stroke(width = w, cap = StrokeCap.Round),
        )
        val tipX = size.width * 0.80f
        val tipY = size.height * 0.30f
        drawLine(tint, Offset(tipX, tipY), Offset(tipX - w * 2.2f, tipY - w * 1.4f), w, StrokeCap.Round)
        drawLine(tint, Offset(tipX, tipY), Offset(tipX + w * 0.4f, tipY - w * 2.6f), w, StrokeCap.Round)
    }
}

@Composable
fun IconBack(tint: Color, units: Float = Grid.ICON) {
    Canvas(Modifier.size(gridUnits(units))) {
        val w = strokePx()
        drawLine(tint, Offset(size.width * 0.62f, size.height * 0.16f),
            Offset(size.width * 0.32f, size.height * 0.5f), w, StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.32f, size.height * 0.5f),
            Offset(size.width * 0.62f, size.height * 0.84f), w, StrokeCap.Round)
    }
}
