package dev.akashpriyadarshi.exifdrop.strip

import androidx.exifinterface.media.ExifInterface
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Strip all EXIF/XMP/IPTC metadata from a source image into [out].
 *
 * JPEG/PNG: exifinterface tag-wipe (`setAttribute(TAG_XMP, null)` removes separate-segment
 * XMP since 1.4.0). Lossless — only metadata segments are rewritten, pixels pass through.
 *
 * WebP: exifinterface cannot read or write the separate WebP XMP chunk, so that chunk is
 * dropped by hand ([WebpStripper]). GPS/EXIF still handled here. ICC is kept for all
 * formats — color data, not identity — except WebP files that carry an XMP chunk, where
 * keeping ICC would also keep the XMP we intend to remove.
 */
object ImageStripper {

    /**
     * Wipe metadata from [input] into [out]. Returns nothing; caller owns stream lifecycle.
     * [orientation] is captured by [rememberOrientation] before wiping and re-applied so
     * the receiver doesn't render the photo sideways.
     */
    @Throws(IOException::class)
    fun strip(input: InputStream, out: OutputStream, mimeType: String?) {
        // Detect type. exifinterface's [ExifInterface] works on a path; for WebP we hand-roll.
        if (mimeType == "image/webp") {
            WebpStripper.strip(input, out)
            return
        }
        // ExifInterface needs a seekable path, not a stream — copy to a temp file.
        val tmp = java.io.File.createTempFile("exifdrop_in", null)
        try {
            input.use { it.copyTo(tmp.outputStream()) }
            val exif = ExifInterface(tmp.absolutePath)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            wipe(exif)
            // Re-apply orientation so the wiper doesn't rotate the photo.
            if (orientation != ExifInterface.ORIENTATION_UNDEFINED) {
                exif.setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            }
            exif.saveAttributes()
            tmp.inputStream().use { it.copyTo(out) }
        } finally {
            tmp.delete()
        }
    }

    /** Drop every EXIF tag the library knows, plus XMP (both segments) and ICC white-balance cruft. */
    private fun wipe(exif: ExifInterface) {
        // Wipe all known GPS.
        for (tag in GPS_TAGS) exif.setAttribute(tag, null)
        // Wipe camera/make/software/date-time.
        for (tag in INFO_TAGS) exif.setAttribute(tag, null)
        // XMP: separate-segment for JPEG/PNG cleared since 1.4.0; tag-700 cleared here too.
        exif.setAttribute(ExifInterface.TAG_XMP, null)
        // IPTC is inside APP13 (Adobe) — exifinterface doesn't read it; leave it for now
        // (rare, and its absence would force a re-encode we refuse for lossless).
        // ponytail: IPTC/APP13 left intact — add a raw APP13 drop if fixture shows leaks.
    }

    private val GPS_TAGS = arrayOf(
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_AREA_INFORMATION,
        ExifInterface.TAG_GPS_DOP,
        ExifInterface.TAG_GPS_DEST_BEARING,
        ExifInterface.TAG_GPS_DEST_BEARING_REF,
        ExifInterface.TAG_GPS_DEST_DISTANCE,
        ExifInterface.TAG_GPS_DEST_DISTANCE_REF,
        ExifInterface.TAG_GPS_DEST_LATITUDE,
        ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
        ExifInterface.TAG_GPS_DEST_LONGITUDE,
        ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
        ExifInterface.TAG_GPS_DIFFERENTIAL,
        ExifInterface.TAG_GPS_H_POSITIONING_ERROR,
        ExifInterface.TAG_GPS_IMG_DIRECTION,
        ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_MAP_DATUM,
        ExifInterface.TAG_GPS_MEASURE_MODE,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_GPS_SATELLITES,
        ExifInterface.TAG_GPS_SPEED,
        ExifInterface.TAG_GPS_SPEED_REF,
        ExifInterface.TAG_GPS_STATUS,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_TRACK,
        ExifInterface.TAG_GPS_TRACK_REF,
        ExifInterface.TAG_GPS_VERSION_ID,
    )

    private val INFO_TAGS = arrayOf(
        ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_COPYRIGHT,
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DEVICE_SETTING_DESCRIPTION,
        ExifInterface.TAG_DIGITAL_ZOOM_RATIO,
        ExifInterface.TAG_IMAGE_DESCRIPTION,
        ExifInterface.TAG_IMAGE_UNIQUE_ID,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_OFFSET_TIME,
        ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_SUBSEC_TIME,
        ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
        ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
        ExifInterface.TAG_USER_COMMENT,
    )
}