package dev.akashpriyadarshi.exifdrop

import android.app.Application
import java.io.File

/** FOSS/offline: no INTERNET, no analytics, no crash reporting. Only cache hygiene runs here. */
class ExifDropApp : Application() {

    override fun onCreate() {
        super.onCreate()
        java.util.concurrent.Executors.newSingleThreadExecutor().execute {
            purgeCache()
        }
    }

    /** Drop stale (> user's cache age) and oversized (>64MB) cleaned files on cold start. */
    private fun purgeCache() {
        val dir = File(cacheDir, "cleaned")
        val files = dir.listFiles() ?: return
        val now = System.currentTimeMillis()
        val maxAge = Prefs.cacheAgeDays(this) * 24L * 60 * 60 * 1000
        var total = 0L
        for (f in files) {
            if (now - f.lastModified() > maxAge || f.length() == 0L) {
                f.delete()
            } else {
                total += f.length()
            }
        }
        if (total > MAX_TOTAL_BYTES) {
            files.filter { it.exists() }
                .sortedBy { it.lastModified() } // oldest first, keep the freshest
                .forEach { f ->
                    total -= f.length()
                    f.delete()
                    if (total <= MAX_TOTAL_BYTES) return // down to budget, stop
                }
        }
    }

    private companion object {
        const val MAX_TOTAL_BYTES = 64L * 1024 * 1024
    }
}