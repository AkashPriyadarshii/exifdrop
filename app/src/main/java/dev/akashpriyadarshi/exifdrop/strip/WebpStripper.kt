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
     * ponytail: whole file buffered in RAM; switch to streaming if >64MB WebPs appear.
     */
    fun strip(input: InputStream, out: OutputStream) {
        val raw = input.readBytes()
        check(raw.size >= 12 && ascii(raw, 0) == "RIFF" && ascii(raw, 8) == "WEBP") { "Not WebP" }

        val body = ByteArrayOutputStream(raw.size)
        var pos = 12
        while (pos + 8 <= raw.size) {
            val code = ascii(raw, pos)
            val size = le32(raw, pos + 4)
            if (size < 0 || size > MAX_CHUNK) break // hostile size field: bail, keep what's valid
            val payload = pos + 8
            if (payload + size > raw.size) break // truncated tail: keep valid part
            when (code) {
                "XMP ", "EXIF" -> { /* drop; size==0 still advances pos below */ }
                "VP8X" -> {
                    body.write(codeBytes(code))
                    body.write(le32(size))
                    if (size > 0) {
                        body.write(raw[payload].toInt() and CLEAR)
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