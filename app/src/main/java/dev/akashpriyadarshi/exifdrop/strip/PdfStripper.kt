package dev.akashpriyadarshi.exifdrop.strip

import java.io.InputStream
import java.io.OutputStream

/**
 * PDF metadata strip, no library. Removes the document information dictionary (/Info —
 * Author/Producer/Creator/Title/Dates live there) and the catalog XMP metadata stream.
 *
 * Both edits overwrite in place with same-length neutral bytes, so xref offsets never
 * shift and the file stays valid without re-writing a single content stream — lossless.
 *
 * Kept: /ID (a content hash, not identity). Not touched: EXIF inside embedded images —
 * stripping that would force a re-encode. ponytail: in-place neutralization; a full
 * object-rewrite with xref regeneration is the upgrade path if deeper scrubs are wanted.
 */
object PdfStripper {

    private val INFO_REF = Regex("""/Info\s+(\d+)\s+\d+\s+R""")
    private val INFO_DIRECT = Regex("""/Info\s*<<""")
    private val META_REF = Regex("""/Metadata\s+(\d+)\s+\d+\s+R""")

    @Throws(IllegalArgumentException::class)
    fun strip(input: InputStream, out: OutputStream) {
        val s = String(input.readBytes(), Charsets.ISO_8859_1) // Latin-1: 1 char == 1 byte
        check(s.startsWith("%PDF-")) { "Not a PDF" }
        val chars = s.toCharArray()

        INFO_REF.find(s)?.let { m -> blankObject(chars, m.groupValues[1].toInt()) }
            ?: INFO_DIRECT.find(s)?.let { m ->
                // Match is "/Info <<" — dict opens at range.last - 1 (the << right before the
                // second <). Starting from range.last would land on the NEXT dict in the object.
                val open = s.indexOf("<<", m.range.last - 1)
                if (open >= 0) blankDict(chars, open, findDictEnd(chars, open))
            }

        META_REF.find(s)?.let { m -> blankStream(chars, m.groupValues[1].toInt()) }

        out.write(String(chars).toByteArray(Charsets.ISO_8859_1))
        out.flush()
    }

    /** Find `N 0 obj`, then blank its leading dict. */
    private fun blankObject(chars: CharArray, n: Int) {
        val start = Regex("""(?<!\d)$n\s+0\s+obj""").find(String(chars))?.range?.first ?: return
        val open = String(chars).indexOf("<<", start)
        if (open < 0) return
        blankDict(chars, open, findDictEnd(chars, open))
    }

    /** Find `N 0 obj ... stream ... endstream`, blank the stream payload with spaces. */
    private fun blankStream(chars: CharArray, n: Int) {
        val str = String(chars)
        val start = Regex("""(?<!\d)$n\s+0\s+obj""").find(str)?.range?.first ?: return
        val end = str.indexOf("endstream", start)
        if (end < 0) return
        var contentStart = str.indexOf("stream", start)
        if (contentStart < 0 || contentStart > end) return
        // Neutralize /Filter and /Length in the stream dict so a strict viewer doesn't
        // misinterpret the now-space payload as flate or expect the old byte count.
        val dictEnd = str.indexOf("stream", start) - 3 // "<<"...">> stream"
        if (dictEnd > start) {
            for (key in arrayOf("/Length", "/Filter")) {
                val k = str.indexOf(key, start)
                if (k in start until dictEnd) {
                    var vEnd = k + key.length
                    while (vEnd < dictEnd && !chars[vEnd].isWhitespace()) vEnd++
                    for (i in k until vEnd) chars[i] = ' '
                }
            }
        }
        // Advance past "stream" + EOL to the payload's first byte.
        contentStart += "stream".length
        while (contentStart < end && (chars[contentStart] == '\r' || chars[contentStart] == '\n')) contentStart++
        for (i in contentStart until end) chars[i] = ' '
    }

    /** Overwrite `<<…>>` (inclusive) with `<<` + spaces + `>>`, keeping length. */
    private fun blankDict(chars: CharArray, open: Int, close: Int) {
        if (close - open < 3) return
        chars[open] = '<'
        chars[open + 1] = '<'
        chars[close - 1] = '>'
        chars[close] = '>'
        for (i in open + 2 until close - 1) chars[i] = ' '
    }

    /** Index of the `>>` matching the `<<` at [open], skipping PDF string literals. */
    private fun findDictEnd(c: CharArray, open: Int): Int {
        var depth = 0
        var i = open
        while (i < c.size) {
            when {
                c[i] == '\\' -> i++
                c[i] == '(' -> i = skipLiteral(c, i)
                c[i] == '<' && i + 1 < c.size && c[i + 1] == '<' -> { depth++; i += 2 }
                c[i] == '>' && i + 1 < c.size && c[i + 1] == '>' -> {
                    depth--; i += 2
                    if (depth == 0) return i - 1
                }
                else -> i++
            }
        }
        return -1 // no closing >> : treat as no valid dict, don't wipe to EOF
    }

    /** Skip a PDF literal string `(…)`, honoring `\(` `\)` `\\`. Returns char after `)`. */
    private fun skipLiteral(c: CharArray, open: Int): Int {
        var i = open + 1
        while (i < c.size) {
            when (c[i]) {
                '\\' -> i += 2
                ')' -> return i + 1
                else -> i++
            }
        }
        return i
    }
}