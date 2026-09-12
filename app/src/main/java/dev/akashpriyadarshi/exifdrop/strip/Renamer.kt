package dev.akashpriyadarshi.exifdrop.strip

import java.security.MessageDigest

/** Outgoing filename: `share_<sha1-prefix>.<ext>` (neutral) or `<original>.<ext>` (original name kept). */
object Renamer {

    /** [bytes] are the file's (scanned) content so equal files get equal names and cache hits. */
    fun name(sourceName: String?, bytes: ByteArray, pattern: String = "neutral"): String {
        val ext = sourceName?.substringAfterLast('.', "")
            ?.takeIf { it.isNotBlank() && it.length <= 5 && it.all(Char::isLetterOrDigit) }
            .orEmpty()
        // "original" keeps the source stem (privacy opt-out; metadata, not the name, is the leak).
        if (pattern == "original") {
            // DISPLAY_NAME is provider-controlled. Sanitize to a bare basename: a hostile
            // provider can send "../../../shared_prefs/app.xml" and we must never let that
            // escape cache/cleaned/. Accept alnum + _-. (and spaces), reject everything else.
            val stem = sourceName?.substringBeforeLast('.')?.take(80)
                ?.map { if (it in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 _-.") it else '_' }
                ?.joinToString("")
                // Drop any run of two+ dots: no "..", no leading "." — else File(dir, "..")
                // resolves to the PARENT of cache/cleaned/. A single interior dot is fine.
                ?.replace("..", "_")
                ?.trimStart('.')
            if (!stem.isNullOrBlank()) return if (ext.isEmpty()) stem else "$stem.$ext"
        }
        val hash = MessageDigest.getInstance("SHA-1").digest(bytes)
            .take(8) // 16 hex chars: collisions impossible for one share.
            .joinToString("") { "%02x".format(it) }
        return if (ext.isEmpty()) "share_$hash" else "share_$hash.$ext"
    }
}