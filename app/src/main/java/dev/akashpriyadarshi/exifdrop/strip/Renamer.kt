package dev.akashpriyadarshi.exifdrop.strip

import java.security.MessageDigest

/** Neutral outgoing filename: `share_<sha1-prefix>.<ext>` — no original name leaks. */
object Renamer {

    /** [bytes] are the file's (scanned) content so equal files get equal names and cache hits. */
    fun name(sourceName: String?, bytes: ByteArray): String {
        val ext = sourceName?.substringAfterLast('.', "")
            ?.takeIf { it.isNotBlank() && it.length <= 5 && it.all(Char::isLetterOrDigit) }
            .orEmpty()
        val hash = MessageDigest.getInstance("SHA-1").digest(bytes)
            .take(8) // 16 hex chars: collisions impossible for one share.
            .joinToString("") { "%02x".format(it) }
        return if (ext.isEmpty()) "share_$hash" else "share_$hash.$ext"
    }
}