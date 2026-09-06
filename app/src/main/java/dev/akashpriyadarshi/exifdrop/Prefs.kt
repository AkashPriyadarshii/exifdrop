package dev.akashpriyadarshi.exifdrop

import android.content.Context

/** Persisted user settings. */
object Prefs {
    private const val FILE = "exifdrop_settings"

    fun filenamePattern(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("filename_pattern", "neutral")!!

    fun setFilenamePattern(ctx: Context, v: String) = put(ctx, "filename_pattern", v)

    /** Strip GPS = true means GPS tags are removed in output. */
    fun stripGps(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("strip_gps", true)

    fun setStripGps(ctx: Context, v: Boolean) = put(ctx, "strip_gps", v)

    fun keepOrientation(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("keep_orientation", true)

    fun setKeepOrientation(ctx: Context, v: Boolean) = put(ctx, "keep_orientation", v)

    fun cacheAgeDays(ctx: Context): Int =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt("cache_age_days", 1)

    fun setCacheAgeDays(ctx: Context, v: Int) = put(ctx, "cache_age_days", v)

    private fun put(ctx: Context, k: String, v: Any) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().apply {
            when (v) {
                is String -> putString(k, v)
                is Boolean -> putBoolean(k, v)
                is Int -> putInt(k, v)
            }
        }.apply()
}
