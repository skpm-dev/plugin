package dev.skpm.installer

import java.security.MessageDigest

internal val SAFE_SEGMENT = Regex("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$")

internal fun requireSafeSegment(value: String, label: String): String {
    if (!SAFE_SEGMENT.matches(value) || value.contains(".."))
        throw IllegalArgumentException("Unsafe $label rejected: '$value'")
    return value
}

internal fun sha256Hex(content: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
    return "sha256:" + digest.joinToString("") { "%02x".format(it) }
}
