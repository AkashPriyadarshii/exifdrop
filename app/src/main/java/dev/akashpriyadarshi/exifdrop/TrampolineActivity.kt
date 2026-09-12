package dev.akashpriyadarshi.exifdrop

import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import dev.akashpriyadarshi.exifdrop.strip.ImageStripper
import dev.akashpriyadarshi.exifdrop.strip.PdfStripper
import dev.akashpriyadarshi.exifdrop.strip.Renamer
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Zero-UI share trampoline. Target of the share sheet; never rendered.
 *
 * Read → strip (in memory) → write `share_<hash>.<ext>` to cache → hand the clean file's
 * `content://` URI to the real destination chooser → finish. Two taps total: first here,
 * second in the destination sheet.
 */
class TrampolineActivity : ComponentActivity() {

    private var _executor: java.util.concurrent.ExecutorService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Kill the transition so the cover replaces the share sheet instantly.
        overridePendingTransition(0, 0)
        // Full-screen cover while we strip, so the pick never flashes the app behind.
        window.setBackgroundDrawableResource(R.drawable.bg_trampoline)
        window.statusBarColor = ContextCompat.getColor(this, R.color.exifdrop_bg)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.exifdrop_bg)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(ContextCompat.getColor(this@TrampolineActivity, R.color.exifdrop_bg))
        }
        val spinner = ProgressBar(this).apply {
            id = android.R.id.progress
            indeterminateTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this@TrampolineActivity, R.color.trampoline_accent)
            )
        }
        val label = TextView(this).apply {
            text = "Stripping metadata…"
            setTextColor(ContextCompat.getColor(this@TrampolineActivity, R.color.trampoline_fg))
            textSize = 16f
        }
        root.apply {
            addView(spinner, LinearLayout.LayoutParams(64.dp(), 64.dp()))
            addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 16.dp() })
        }
        setContentView(root)

        val sources = collectSources()
        if (sources.isEmpty()) {
            Toast.makeText(this, "Nothing to strip from this share.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Strip on a background thread so the cover never janks; then hand off.
        val exec = Executors.newSingleThreadExecutor()
        _executor = exec
        exec.execute {
            val cleaned = ArrayList<Clean>()
            var skipped = 0
            for (src in sources) {
                try {
                    prepare(src)?.let { cleaned.add(it) } ?: skipped++
                } catch (e: Throwable) {
                    skipped++ // corrupt/hostile single file: drop it, keep the rest
                }
            }
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread // user quit mid-strip
                if (cleaned.isEmpty()) {
                    Toast.makeText(this@TrampolineActivity,
                        if (sources.size > 1) "None of those files could be stripped." else "Couldn't strip that file.",
                        Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    if (skipped > 0) {
                        Toast.makeText(this@TrampolineActivity,
                            "Skipped $skipped file${if (skipped > 1) "s" else ""} (corrupt or too large).",
                            Toast.LENGTH_SHORT).show()
                    }
                    startActivity(destination(cleaned))
                    finish()
                }
            }
        }
    }

    override fun onBackPressed() {
        // Back mid-strip would destroy the activity under the still-running executor and race
        // runOnUiThread's startActivity. Block it for the short strip window; the chooser that
        // follows has its own back handling.
    }

    override fun onDestroy() {
        super.onDestroy()
        _executor?.shutdownNow()
    }

    /** A second share while the first is still stripping re-launches this singleInstance. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    /** Pull source URIs from the share intent: clipData (multi/single) or EXTRA_STREAM (single SEND). */
    private fun collectSources(): List<Uri> {
        val out = ArrayList<Uri>()
        try {
            val clip = intent.clipData
            if (clip != null) {
                for (i in 0 until clip.itemCount) {
                    val uri = clip.getItemAt(i)?.uri ?: continue
                    if (!isOwnProvider(uri)) out.add(uri)
                }
            } else if (intent.action == Intent.ACTION_SEND && intent.hasExtra(Intent.EXTRA_STREAM)) {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.let { if (!isOwnProvider(it)) out.add(it) }
            }
            // Dedupe: SEND_MULTIPLE with the same file twice used to strip+cache+hand it twice.
            return ArrayList(LinkedHashSet(out))
        } catch (e: Throwable) {
            // Nothing to hand off; the share dies here rather than leak metadata.
        }
        return out
    }

    /** One stripped file ready to hand off. */
    private data class Clean(val uri: Uri, val mime: String)

    /** Strip one source, cache the clean file, return its URI + the ORIGINAL source mime
     *  (FileProvider would re-guess from the neutral extension, losing .JPG → image/jpeg). */
    private fun prepare(src: Uri): Clean? {
        val mime = contentResolver.getType(src) ?: return null
        // Fail closed: only exact types we provably strip. SVG/GIF/BMP/AVIF/HEIC would either
        // pass through dirty or misbehave in exifinterface — refuse loudly, not silently.
        if (mime !in setOf("image/jpeg", "image/png", "image/webp", "application/pdf")) return null
        return try {
            // Cap the in-RAM buffer. Reality: phone photos top out ~20MB; WebP stripper
            // already buffers the whole file. A pathological 1GB image would OOM before
            // ANR — fail it fast instead. ponytail: buffer-in-RAM; swap to stream-rewrite
            // when >64MB sources actually matter.
            val size = try {
                contentResolver.openAssetFileDescriptor(src, "r")?.use { it.length }
            } catch (e: IOException) { null } // provider doesn't expose length: skip the cap
            if (size != null && size > 0 && size > 64L * 1024 * 1024) return null
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

    /** True when the URI comes from our own provider — never re-process it. */
    private fun isOwnProvider(uri: Uri): Boolean = uri.authority == "$packageName.fileprovider"

    private fun queryName(uri: Uri): String? =
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        }

    private fun destination(cleaned: List<Clean>): Intent {
        val mimes = cleaned.map { it.mime }
        // Honest type: all-images and all-pdf keep their narrow type so narrow receivers appear.
        // A mixed batch (photo + PDF) must NOT claim application/pdf — the image URI would fail
        // to open. Fall back to */* so any broad receiver can take the whole batch.
        val shareType = when {
            mimes.all { it.startsWith("image/") } -> "image/*"
            mimes.all { it == "application/pdf" } -> "application/pdf"
            else -> "*/*"
        }
        val uris = cleaned.map { it.uri }
        val send = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
            @Suppress("DEPRECATION") type = shareType
            // Single → Uri; multiple → Parcelable-typed ArrayList. putExtra(String, Serializable)
            // would mark the list Serializable and then Java-serialize each Uri at IPC → crash.
            if (uris.size == 1) {
                putExtra(Intent.EXTRA_STREAM, uris[0])
            } else {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
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