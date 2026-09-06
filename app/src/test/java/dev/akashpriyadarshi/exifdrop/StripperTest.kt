package dev.akashpriyadarshi.exifdrop

import dev.akashpriyadarshi.exifdrop.strip.PdfStripper
import dev.akashpriyadarshi.exifdrop.strip.Renamer
import dev.akashpriyadarshi.exifdrop.strip.WebpStripper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure stream tests for the two hand-rolled strippers. exifinterface's image path is its own contract. */
class StripperTest {

    // -- WebP ---------------------------------------------------------------
    // RIFF header + WEBP, then: XMP chunk (size 5 + pad), ICCP (3), VP8 (3).
    private val webp = "RIFF".toByteArray(Charsets.ISO_8859_1) +
        byteArrayOf(0, 0, 0, 0) +
        "WEBP".toByteArray(Charsets.ISO_8859_1) +
        "XMP ".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(5, 0, 0, 0) +
        "abcde".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0) +
        "ICCP".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(3, 0, 0, 0) +
        "icc".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0) +
        "VP8 ".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(3, 0, 0, 0) +
        "fra".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0)

    @Test
    fun webp_dropsXmp_keepsIccAndPixels() {
        val out = java.io.ByteArrayOutputStream()
        WebpStripper.strip(webp.inputStream(), out)
        val s = out.toByteArray().toString(Charsets.ISO_8859_1)
        assertFalse(s.contains("XMP "))
        assertTrue(s.contains("ICCP"))
        assertTrue(s.contains("VP8 "))
        assertTrue(s.startsWith("RIFF") && s.contains("WEBP"))
    }

    @Test
    fun webp_hostileSizeField_bailsWithoutCrash() {
        // Chunk size 0xFFFFFFFF (negative as Int): must not force a read past the buffer.
        val hostile = "RIFF".toByteArray(Charsets.ISO_8859_1) +
            byteArrayOf(0, 0, 0, 0) +
            "WEBP".toByteArray(Charsets.ISO_8859_1) +
            "XMP ".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(-1, -1, -1, -1) // 4 294 967 295
        val out = java.io.ByteArrayOutputStream()
        WebpStripper.strip(hostile.inputStream(), out)
        // Should have produced a valid empty WebP (only RIFF+WEBP headers), no exception.
        val s = out.toByteArray().toString(Charsets.ISO_8859_1)
        assertTrue(s.startsWith("RIFF") && s.contains("WEBP"))
    }

    @Test
    fun webp_zeroSizeVp8x_doesNotCrash() {
        // size-0 VP8X: the strip branch must not read raw[payload+1] of length -1.
        val zero = "RIFF".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0, 0, 0, 0) +
            "WEBP".toByteArray(Charsets.ISO_8859_1) +
            "VP8X".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0, 0, 0, 0)
        val out = java.io.ByteArrayOutputStream()
        WebpStripper.strip(zero.inputStream(), out)
        val s = out.toByteArray().toString(Charsets.ISO_8859_1)
        assertTrue(s.startsWith("RIFF") && s.contains("WEBP"))
    }

    @Test
    fun webp_doesNotMatchNonWebp_throws() {
        val bad = "not a webp at all".toByteArray()
        org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            WebpStripper.strip(bad.inputStream(), java.io.ByteArrayOutputStream())
        }
    }

    // -- PDF -----------------------------------------------------------------
    private val pdf =
        "%PDF-1.4\n" +
            "1 0 obj\n<< /Type /Catalog /Pages 2 0 R /Metadata 4 0 R >>\nendobj\n" +
            "2 0 obj\n<< /Type /Pages /Kids [] /Count 0 >>\nendobj\n" +
            "4 0 obj\n<< /Type /Metadata /Subtype /XML /Length 9 >>\nstream\n" +
            "<metadata>evil</metadata>\nendstream\nendobj\n" +
            "3 0 obj\n<< /Title (My Title) /Author (Attacker) /Subject (Secret) >>\nendobj\n" +
            "trailer\n<< /Root 1 0 R /Info 3 0 R >>\n" +
            "startxref\n0\n%%EOF\n"

    @Test
    fun pdf_unterminatedLiteral_doesNotWipeTail() {
        // /Info with an unclosed ( : findDictEnd must not scan to EOF and blank the whole rest.
        val pdf = "%PDF-1.4\n" +
            "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n" +
            "2 0 obj\n<< /Type /Pages /Kids [] /Count 0 >>\nendobj\n" +
            "3 0 obj\n<< /Title (unterminated /Author (Still Here) >>\nendobj\n" +
            "trailer\n<< /Root 1 0 R /Info 3 0 R >>\n" +
            "startxref\n0\n%%EOF\n"
        val out = java.io.ByteArrayOutputStream()
        PdfStripper.strip(pdf.toByteArray(Charsets.ISO_8859_1).inputStream(), out)
        val s = out.toByteArray().toString(Charsets.ISO_8859_1)
        // Object 2's content stream must survive — not blanked all the way to EOF.
        assertTrue(s.contains("/Type /Catalog"))
        assertTrue(s.contains("/Kids []"))
        // /Info partially scrubbed at worst; nothing past the dict is destroyed.
        assertFalse(s.contains("/Title (unterminated"))
    }

    @Test
    fun pdf_objStmRef_throwsDirtyHandoff() {
        // /Info 3 0 R in the trailer, but object 3 lives compressed inside an ObjStm — no
        // literal "3 0 obj" exists, so the blankers would silently no-op and DIRTY metadata
        // would ship. Must refuse, loud.
        val pdf = "%PDF-1.4\n" +
            "1 0 obj\n<< /Type /ObjStm /N 3 /First 30 >>\nstream\n3 0 << /Title (X) >>\nendstream\nendobj\n" +
            "trailer\n<< /Root 1 0 R /Info 3 0 R >>\n" +
            "startxref\n0\n%%EOF\n"
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            PdfStripper.strip(pdf.toByteArray(Charsets.ISO_8859_1).inputStream(), java.io.ByteArrayOutputStream())
        }
    }

    @Test
    fun pdf_scrubsInlineInfoDict() {
        // /Info written INLINE in the trailer (<< after the name, not a ref) — the
        // INFO_DIRECT branch. The dict's own keys must be scrubbed, not the NEXT dict.
        val pdf = "%PDF-1.4\n" +
            "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n" +
            "2 0 obj\n<< /Type /Pages /Kids [] /Count 0 >>\nendobj\n" +
            "trailer\n<< /Root 1 0 R /Info << /Title (Inline Title) /Author (Inline Author) >> >>\n" +
            "startxref\n0\n%%EOF\n"
        val out = java.io.ByteArrayOutputStream()
        PdfStripper.strip(pdf.toByteArray(Charsets.ISO_8859_1).inputStream(), out)
        val s = out.toByteArray().toString(Charsets.ISO_8859_1)
        assertFalse(s.contains("/Title (Inline Title)"))
        assertFalse(s.contains("/Author (Inline Author)"))
        // The OTHER dicts (catalog, pages) survive untouched.
        assertTrue(s.contains("/Type /Catalog"))
        assertTrue(s.contains("/Type /Pages"))
    }

    @Test
    fun pdf_scrubsInfoDict() {
        val out = java.io.ByteArrayOutputStream()
        PdfStripper.strip(pdf.toByteArray(Charsets.ISO_8859_1).inputStream(), out)
        val s = out.toByteArray().toString(Charsets.ISO_8859_1)
        assertTrue(s.contains("/Info"))          // xref intact
        assertFalse(s.contains("/Title (My Title)"))
        assertFalse(s.contains("/Author (Attacker)"))
        assertFalse(s.contains("(Secret)"))
    }

    @Test
    fun pdf_scrubsXmpStream() {
        val out = java.io.ByteArrayOutputStream()
        PdfStripper.strip(pdf.toByteArray(Charsets.ISO_8859_1).inputStream(), out)
        val s = out.toByteArray().toString(Charsets.ISO_8859_1)
        assertFalse(s.contains("evil"))
        // /Length and /Filter are neutralized too, so the residue can't be misread as flate.
        assertFalse(s.contains("/Length"))
        assertFalse(s.contains("/Filter"))
    }

    // -- Renamer (traversal guard) -------------------------------------------
    @Test
    fun renamer_traversalPath_becomesSafeStem() {
        // A hostile provider can send DISPLAY_NAME with ../.. to escape cache/cleaned/.
        val safe = Renamer.name("../../../shared_prefs/app.xml", byteArrayOf(1, 2, 3), "original")
        // The output is one bare filename — no separators or parent traversal survives.
        assertFalse(safe.contains("/"))
        assertFalse(safe.contains(".."))
        assertTrue(safe.endsWith(".xml"))
    }

    @Test
    fun renamer_neutral_stillHashes() {
        val a = Renamer.name("IMG_20250115_140233.jpg", byteArrayOf(1, 2, 3), "neutral")
        assertTrue(a.startsWith("share_"))
        assertTrue(a.endsWith(".jpg"))
        // Equal content → equal name (cache hit).
        assertEquals(a, Renamer.name("whatever.jpg", byteArrayOf(1, 2, 3), "neutral"))
    }

    @Test
    fun renamer_dotName_cannotBeParent() {
        // "..", ".", stores become empty → fall back to hash (never a parent handle).
        for (hostile in listOf("...jpg", "..jpg", ".jpg")) {
            val out = Renamer.name(hostile, byteArrayOf(1, 2, 3), "original")
            assertTrue(out.startsWith("share_") || out.endsWith(".jpg"))
            assertFalse(out == "..jpg" || out == ".jpg" || out == "...jpg")
        }
    }
}