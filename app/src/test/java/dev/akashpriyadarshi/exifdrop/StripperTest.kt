package dev.akashpriyadarshi.exifdrop

import dev.akashpriyadarshi.exifdrop.strip.PdfStripper
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
        assertTrue(s.contains("/Length 9")) // length token preserved
    }
}