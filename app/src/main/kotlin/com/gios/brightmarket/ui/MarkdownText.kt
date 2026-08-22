package com.gios.brightmarket.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
// withStyle is a top-level extension on the builder, not a member of it.
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.TextStyle
import com.gios.brightmarket.data.Markdown

/**
 * Renders the small subset of markdown that app descriptions and release notes
 * actually use. Parsing lives in [Markdown] so it can be tested off-device;
 * this only maps spans to styles.
 */
@Composable
fun MarkdownText(
    source: String,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: androidx.compose.ui.graphics.Color = Light.Content,
) {
    val lines = Markdown.parse(source)
    Column {
        lines.forEach { line ->
            if (line.spans.size == 1 && line.spans[0].text.isBlank() && line.heading == 0) {
                // A blank line is a paragraph break, not an empty row of text.
                Spacer(Modifier.height(gridUnits(0.4f)))
                return@forEach
            }
            val lineStyle = when (line.heading) {
                1 -> MaterialTheme.typography.titleLarge
                2 -> MaterialTheme.typography.titleMedium
                3 -> MaterialTheme.typography.bodyLarge
                else -> style
            }
            Row {
                if (line.bullet) {
                    Text("·", style = lineStyle, color = color)
                    Spacer(Modifier.width(gridUnits(0.3f)))
                }
                Text(annotate(line.spans), style = lineStyle, color = color)
            }
        }
    }
}

private fun annotate(spans: List<Markdown.Span>): AnnotatedString = buildAnnotatedString {
    spans.forEach { span ->
        val s = when (span.style) {
            Markdown.Style.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
            Markdown.Style.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
            Markdown.Style.CODE -> SpanStyle(fontFamily = FontFamily.Monospace)
            // Underlined rather than colored: there is no accent in the
            // palette, and a link that is merely a different gray is invisible.
            Markdown.Style.LINK -> SpanStyle(textDecoration = TextDecoration.Underline)
            Markdown.Style.PLAIN -> SpanStyle()
        }
        withStyle(s) { append(span.text) }
    }
}
