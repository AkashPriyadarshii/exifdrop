package dev.akashpriyadarshi.exifdrop

import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
        val outUris = ArrayList<Uri>()
        try {
            val clip = intent.clipData
            if (clip != null) {
                for (i in 0 until clip.itemCount) {
                    prepare(clip.getItemAt(i).uri)?.let(outUris::add)
                }
            } else if (intent.hasExtra(Intent.EXTRA_STREAM)) {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?.let { prepare(it) }?.let { outUris.add(it) }
            }
        } catch (e: Throwable) {
            // Nothing to hand off; user's share dies here rather than leak metadata.
        }
        if (outUris.isEmpty()) {
            finish()
            return
        }
        startActivity(destination(outUris))
        finish()
    }

    /** Strip one source, cache the clean file, return its `content://` URI (or null). */
    private fun prepare(src: Uri): Uri? {
        val mime = contentResolver.getType(src) ?: return null
        return try {
            val bytes = contentResolver.openInputStream(src)?.use { input ->
                val buf = ByteArrayOutputStream()
                when (mime) {
                    "application/pdf" -> PdfStripper.strip(input, buf)
                    "image/webp" -> ImageStripper.strip(input, buf, mime)
                    else -> if (mime.startsWith("image/")) {
                        ImageStripper.strip(input, buf, mime)
                    } else return null
                }
                buf.toByteArray()
            } ?: return null
            if (bytes.isEmpty()) return null

            val dir = java.io.File(cacheDir, "cleaned").apply { mkdirs() }
            val name = Renamer.name(queryName(src), bytes)
            val out = java.io.File(dir, name)
            if (!out.exists()) out.writeBytes(bytes)

            FileProvider.getUriForFile(this, "$packageName.fileprovider", out)
        } catch (e: IOException) {
            null
        }
    }

    private fun queryName(uri: Uri): String? =
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        }

    private fun destination(uris: List<Uri>): Intent {
        val mimes = uris.mapNotNull { contentResolver.getType(it) }
        val type = if (mimes.all { it.startsWith("image/") }) "image/*" else "application/pdf"
        val send =
            if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                }
            }
        @Suppress("DEPRECATION")
        send.type = type
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, null).apply {
            putExtra(
                Intent.EXTRA_EXCLUDE_COMPONENTS,
                arrayOf(ComponentName(packageName, TrampolineActivity::class.java.name))
            )
        }
    }
}