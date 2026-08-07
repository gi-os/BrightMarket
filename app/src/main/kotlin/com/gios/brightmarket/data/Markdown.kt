package com.gios.brightmarket.data

/**
 * A very small markdown reader, for app descriptions and release notes.
 *
 * Both come from GitHub — release notes especially, which are markdown by
 * definition and today all contain at least `**Full Changelog**`. Rendered as
 * plain text those asterisks and backticks show up literally, which reads as a
 * bug.
 *
 * Deliberately not a full parser. It handles the inline marks that actually
 * appear (`**bold**`, `*italic*`, `` `code` ``, `[text](url)`), plus headings
 * and list bullets at the start of a line, and leaves everything else as
 * written. A complete implementation would be a library, and pulling one in for
 * two screens of text is a poor trade on a phone with this little storage.
 *
 * Kept free of Android and Compose types so it can be tested on the JVM.
 */
object Markdown {

    enum class Style { PLAIN, BOLD, ITALIC, CODE, LINK }

    /** One run of text with a single style, plus a target when it's a link. */
    data class Span(val text: String, val style: Style = Style.PLAIN, val href: String? = null)

    data class Line(
        val spans: List<Span>,
        /** 1-3 for a heading, 0 otherwise. */
        val heading: Int = 0,
        val bullet: Boolean = false,
    )

    fun parse(source: String): List<Line> =
        source.replace("\r\n", "\n").split("\n").map { raw ->
            var text = raw.trimEnd()
            var heading = 0
            var bullet = false

            // Headings: up to three levels, which is all a release note uses.
            val h = Regex("""^(#{1,3})\s+(.*)$""").find(text.trimStart())
            if (h != null) {
                heading = h.groupValues[1].length
                text = h.groupValues[2]
            } else {
                val b = Regex("""^\s*[-*+]\s+(.*)$""").find(text)
                if (b != null) {
                    bullet = true
                    text = b.groupValues[1]
                }
            }
            Line(inline(text), heading, bullet)
        }

    /**
     * Split one line into styled runs.
     *
     * Scans left to right and takes the first match of any mark, so nesting
     * isn't supported — `**bold with `code`**` renders as bold up to the
     * backtick. That is a real limitation and an acceptable one: it degrades to
     * slightly-wrong emphasis rather than to visible syntax, which is the
     * failure that matters.
     */
    fun inline(text: String): List<Span> {
        if (text.isEmpty()) return listOf(Span(""))
        val out = mutableListOf<Span>()
        var i = 0

        // Order matters: ** before *, or bold is read as two italics.
        val rules = listOf(
            Triple(Regex("""\*\*(.+?)\*\*"""), Style.BOLD, false),
            Triple(Regex("""__(.+?)__"""), Style.BOLD, false),
            Triple(Regex("""\*(.+?)\*"""), Style.ITALIC, false),
            Triple(Regex("""`([^`]+)`"""), Style.CODE, false),
            Triple(Regex("""\[([^\]]+)]\(([^)\s]+)\)"""), Style.LINK, true),
        )

        while (i < text.length) {
            var best: Triple<MatchResult, Style, Boolean>? = null
            for ((re, style, isLink) in rules) {
                val m = re.find(text, i) ?: continue
                if (best == null || m.range.first < best!!.first.range.first) {
                    best = Triple(m, style, isLink)
                }
            }
            if (best == null) {
                out += Span(text.substring(i))
                break
            }
            val (m, style, isLink) = best!!
            if (m.range.first > i) out += Span(text.substring(i, m.range.first))
            out += if (isLink) {
                Span(m.groupValues[1], Style.LINK, m.groupValues[2])
            } else {
                Span(m.groupValues[1], style)
            }
            i = m.range.last + 1
        }
        return out.ifEmpty { listOf(Span(text)) }
    }

    /** Everything with the marks removed, for places that can't style text. */
    fun plain(source: String): String =
        parse(source).joinToString("\n") { line ->
            line.spans.joinToString("") { it.text }
        }.trim()
}
