package dev.akashpriyadarshi.exifdrop.strip

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Hand-rolled WebP metadata strip. exifinterface cannot read or write the separate WebP
 * XMP/EXIF chunks, so they are dropped here chunk-wise and the VP8X feature flags cleared.
 * Everything else (VP8, VP8L, ANIM, ANMF, ICCP, alpha) passes through byte-exact — the
 * pixels are never decoded, so the strip is lossless.
 */
object WebpStripper {

    // Guard: a hostile or corrupt size field must not drive an allocation or read.
    private const val MAX_CHUNK = 1 shl 28 // 256MB — no legitimate WebP chunk is bigger.

    // VP8X flags byte, from high bit: res res ICC alpha EXIF XMP anim res.
    private const val FLAG_EXIF = 0x08
    private const val FLAG_XMP = 0x04
    private const val CLEAR = (FLAG_EXIF or FLAG_XMP).inv()

    /**
     * Re-emits the WebP from [input] into [out] with XMP and EXIF chunks removed.
     * Preserves orientation in a clean, minimal EXIF chunk if [keepOrientation] is true.
     * ponytail: whole file buffered in RAM; switch to streaming if >64MB WebPs appear.
     */
    fun strip(input: InputStream, out: OutputStream, keepOrientation: Boolean = true) {
        val raw = input.readBytes()
        check(raw.size >= 12 && ascii(raw, 0) == "RIFF" && ascii(raw, 8) == "WEBP") { "Not WebP" }

        val orientation = if (keepOrientation) findOrientation(raw) else null
        val cleanExif = if (orientation != null && orientation != 1) makeOrientationExif(orientation) else null
        val hasCleanExif = cleanExif != null
        val vp8xClearMask = if (hasCleanExif) FLAG_XMP.inv() else (FLAG_EXIF or FLAG_XMP).inv()

        val body = ByteArrayOutputStream(raw.size)
        var pos = 12
        while (pos + 8 <= raw.size) {
            val code = ascii(raw, pos)
            val size = le32(raw, pos + 4)
            if (size < 0 || size > MAX_CHUNK) break // hostile size field: bail, keep what's valid
            val payload = pos + 8
            if (payload + size > raw.size) break // truncated tail: keep valid part
            when (code) {
                "XMP " -> { /* drop */ }
                "EXIF" -> {
                    if (cleanExif != null) {
                        body.write(codeBytes("EXIF"))
                        body.write(le32(cleanExif.size))
                        body.write(cleanExif)
                        if (cleanExif.size and 1 == 1) body.write(0)
                    }
                }
                "VP8X" -> {
                    body.write(codeBytes(code))
                    body.write(le32(size))
                    if (size > 0) {
                        val flags = (raw[payload].toInt() and vp8xClearMask) or (if (hasCleanExif) FLAG_EXIF else 0)
                        body.write(flags)
                        body.write(raw, payload + 1, size - 1)
                        if (size and 1 == 1) body.write(0)
                    }
                }
                else -> {
                    body.write(codeBytes(code))
                    body.write(le32(size))
                    body.write(raw, payload, size)
                    if (size and 1 == 1) body.write(0)
                }
            }
            pos = payload + size + (size and 1)
        }

        val buf = body.toByteArray()
        out.write("RIFF".toByteArray(Charsets.ISO_8859_1))
        out.write(le32(4 + buf.size))
        out.write("WEBP".toByteArray(Charsets.ISO_8859_1))
        out.write(buf)
        out.flush()
    }

    private fun findOrientation(raw: ByteArray): Int? {
        var pos = 12
        while (pos + 8 <= raw.size) {
            val code = ascii(raw, pos)
            val size = le32(raw, pos + 4)
            if (size < 0 || size > MAX_CHUNK) break
            val payload = pos + 8
            if (payload + size > raw.size) break
            if (code == "EXIF") {
                return extractOrientation(raw, payload, size)
            }
            pos = payload + size + (size and 1)
        }
        return null
    }

    private fun extractOrientation(raw: ByteArray, payload: Int, size: Int): Int? {
        if (size < 14) return null
        var off = payload
        val end = payload + size
        // Optional "Exif\0\0" header prefix
        if (off + 6 <= end && ascii(raw, off) == "Exif" && raw[off + 4] == 0.toByte() && raw[off + 5] == 0.toByte()) {
            off += 6
        }
        if (off + 8 > end) return null
        val isLe = raw[off] == 0x49.toByte() && raw[off + 1] == 0x49.toByte()
        val isBe = raw[off] == 0x4D.toByte() && raw[off + 1] == 0x4D.toByte()
        if (!isLe && !isBe) return null

        fun u16(p: Int): Int = if (isLe) {
            (raw[p].toInt() and 0xFF) or ((raw[p + 1].toInt() and 0xFF) shl 8)
        } else {
            ((raw[p].toInt() and 0xFF) shl 8) or (raw[p + 1].toInt() and 0xFF)
        }

        fun u32(p: Int): Int = if (isLe) {
            (raw[p].toInt() and 0xFF) or ((raw[p + 1].toInt() and 0xFF) shl 8) or
                ((raw[p + 2].toInt() and 0xFF) shl 16) or ((raw[p + 3].toInt() and 0xFF) shl 24)
        } else {
            ((raw[p].toInt() and 0xFF) shl 24) or ((raw[p + 1].toInt() and 0xFF) shl 16) or
                ((raw[p + 2].toInt() and 0xFF) shl 8) or (raw[p + 3].toInt() and 0xFF)
        }

        val ifd0Off = u32(off + 4)
        var ifd = off + ifd0Off
        if (ifd < off || ifd + 2 > end) return null
        val numEntries = u16(ifd)
        ifd += 2
        for (i in 0 until numEntries) {
            if (ifd + 12 > end) break
            val tag = u16(ifd)
            if (tag == 0x0112) { // TAG_ORIENTATION
                val orient = u16(ifd + 8)
                if (orient in 1..8) return orient
            }
            ifd += 12
        }
        return null
    }

    private fun makeOrientationExif(orientation: Int): ByteArray {
        val o = orientation.coerceIn(1, 8)
        // Standard TIFF Little-Endian header + IFD0 with 1 entry (Orientation)
        return byteArrayOf(
            0x49, 0x49, 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00, // TIFF: 'II', 42, IFD0 offset 8
            0x01, 0x00, // 1 IFD entry
            0x12, 0x01, // Tag 0x0112 (Orientation)
            0x03, 0x00, // Type SHORT (3)
            0x01, 0x00, 0x00, 0x00, // Count 1
            (o and 0xFF).toByte(), ((o shr 8) and 0xFF).toByte(), 0x00, 0x00, // Value
            0x00, 0x00, 0x00, 0x00, // Next IFD offset 0
        )
    }

    private fun ascii(a: ByteArray, off: Int) = String(a, off, 4, Charsets.ISO_8859_1)
    private fun codeBytes(c: String) = c.toByteArray(Charsets.ISO_8859_1)

    private fun le32(a: ByteArray, off: Int): Int =
        (a[off].toInt() and 0xFF) or ((a[off + 1].toInt() and 0xFF) shl 8) or
            ((a[off + 2].toInt() and 0xFF) shl 16) or ((a[off + 3].toInt() and 0xFF) shl 24)

    private fun le32(v: Int) = byteArrayOf(
        (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte(),
    )
}