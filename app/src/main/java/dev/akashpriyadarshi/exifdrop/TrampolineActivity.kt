package dev.akashpriyadarshi.exifdrop

import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import dev.akashpriyadarshi.exifdrop.strip.ImageStripper
import dev.akashpriyadarshi.exifdrop.strip.PdfStripper
import dev.akashpriyadarshi.exifdrop.strip.Renamer
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Zero-UI share trampoline. Target of the share sheet; never rendered.
 *
 * Read → strip (in memory) → write `share_<hash>.<ext>` to cache → hand the clean file's
 * `content://` URI to the real destination chooser → finish. Two taps total: first here,
 * second in the destination sheet.
 */
class TrampolineActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        purgeCache()
        val cleaned = ArrayList<Clean>()
        try {
            val clip = intent.clipData
            if (clip != null) {
                for (i in 0 until clip.itemCount) {
                    val uri = clip.getItemAt(i)?.uri ?: continue
                    cleaned.addIfClean(uri)
                }
            } else if (intent.hasExtra(Intent.EXTRA_STREAM)) {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?.let { cleaned.addIfClean(it) }
            }
        } catch (e: Throwable) {
            // Nothing to hand off; user's share dies here rather than leak metadata.
        }
        if (cleaned.isEmpty()) {
            finish()
            return
        }
        startActivity(destination(cleaned))
        finish()
    }

    /** Strip + cache one source; on any per-file failure, skip it, keep the batch alive. */
    private fun ArrayList<Clean>.addIfClean(src: Uri) {
        try {
            prepare(src)?.let { add(it) }
        } catch (e: Throwable) {
            // corrupt/hostile single file: drop it, keep the rest
        }
    }

    /** One stripped file ready to hand off. */
    private data class Clean(val uri: Uri, val mime: String)

    /** Strip one source, cache the clean file, return its URI + the ORIGINAL source mime
     *  (FileProvider would re-guess from the neutral extension, losing .JPG → image/jpeg). */
    private fun prepare(src: Uri): Clean? {
        val mime = contentResolver.getType(src) ?: return null
        return try {
            // Cap the in-RAM buffer. Reality: phone photos top out ~20MB; WebP stripper
            // already buffers the whole file. A pathological 1GB image would OOM before
            // ANR — fail it fast instead. ponytail: buffer-in-RAM; swap to stream-rewrite
            // when >64MB sources actually matter.
            val size = try {
                contentResolver.openAssetFileDescriptor(src, "r")?.use { it.length }
            } catch (e: IOException) { null } // provider doesn't expose length: skip the cap
            if (size != null && size > 64L * 1024 * 1024) return null
            val bytes = contentResolver.openInputStream(src)?.use { input ->
                val buf = ByteArrayOutputStream()
                when (mime) {
                    "application/pdf" -> PdfStripper.strip(input, buf)
                    "image/webp" -> ImageStripper.strip(input, buf, mime,
                        stripGps = Prefs.stripGps(this), keepOrientation = Prefs.keepOrientation(this))
                    else -> if (mime.startsWith("image/")) {
                        ImageStripper.strip(input, buf, mime,
                            stripGps = Prefs.stripGps(this), keepOrientation = Prefs.keepOrientation(this))
                    } else return null
                }
                buf.toByteArray()
            } ?: return null
            if (bytes.isEmpty()) return null

            val dir = java.io.File(cacheDir, "cleaned").apply { mkdirs() }
            val name = Renamer.name(queryName(src), bytes, Prefs.filenamePattern(this))
            val out = java.io.File(dir, name)
            if (!out.exists()) out.writeBytes(bytes)

            Clean(FileProvider.getUriForFile(this, "$packageName.fileprovider", out), mime)
        } catch (e: IOException) {
            null
        }
    }

    /** Delete cleaned files older than the configured cache age. Call on launch. */
    private fun purgeCache() {
        val dir = java.io.File(cacheDir, "cleaned")
        if (!dir.isDirectory) return
        val cutoff = System.currentTimeMillis() - Prefs.cacheAgeDays(this) * 24L * 60 * 60 * 1000
        dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
    }

    private fun queryName(uri: Uri): String? =
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        }

    private fun destination(cleaned: List<Clean>): Intent {
        val mimes = cleaned.map { it.mime }
        val shareType = if (mimes.all { it.startsWith("image/") }) "image/*" else "application/pdf"
        val uris = cleaned.map { it.uri }
        val send = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
            @Suppress("DEPRECATION") type = shareType
            // Single → Uri; multiple → ArrayList<Parcelable> (java.util.ArrayList isn't Parcelable itself).
            if (uris.size == 1) {
                putExtra(Intent.EXTRA_STREAM, uris[0])
            } else {
                putExtra(Intent.EXTRA_STREAM, ArrayList<Parcelable>(uris))
            }
            // clipData is how ActivityManagerService derives URI grants for chooser-based
            // shares: every URI must be in it or recipients only get read on the first.
            clipData = ClipData.newUri(contentResolver, "share", uris[0])
            for (u in uris.drop(1)) clipData?.addItem(ClipData.Item(u))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, null).apply {
            putExtra(
                Intent.EXTRA_EXCLUDE_COMPONENTS,
                arrayOf(ComponentName(packageName, TrampolineActivity::class.java.name))
            )
        }
    }
}