package dev.skpm.installer

import java.security.MessageDigest

internal val SAFE_SEGMENT = Regex("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$")

internal fun requireSafeSegment(value: String, label: String): String {
    if (!SAFE_SEGMENT.matches(value) || value.contains(".."))
        throw IllegalArgumentException("Unsafe $label rejected: '$value'")
    return value
}

internal fun sha256Hex(content: String): String = sha256Hex(content.toByteArray(Charsets.UTF_8))

internal fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return "sha256:" + digest.joinToString("") { "%02x".format(it) }
}

// Parses "1.2.3", "2.3" (padded to 2.3.0), "v1.0.0" into (major, minor, patch).
// Pre-release and build-metadata suffixes are ignored.
internal fun parseVersionTriple(v: String): Triple<Int, Int, Int>? {
    val clean = v.trimStart('v').substringBefore('-').substringBefore('+')
    val parts = clean.split('.')
    return try {
        Triple(
            parts.getOrNull(0)?.toIntOrNull() ?: return null,
            parts.getOrNull(1)?.toIntOrNull() ?: 0,
            parts.getOrNull(2)?.toIntOrNull() ?: 0
        )
    } catch (_: Exception) { null }
}

private fun cmpTriple(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>): Int =
    compareValuesBy(a, b, { it.first }, { it.second }, { it.third })

// Returns true if [version] satisfies the Masterminds-style [constraint].
// Supported operators: >=, <=, >, <, =, ^, ~ and exact version.
// Multiple space-separated tokens in a clause are ANDed; || separates OR clauses.
// Unparseable version strings pass (fail-open) to avoid blocking installs on
// non-semver Bukkit plugin versions.
internal fun satisfiesConstraint(version: String, constraint: String): Boolean {
    val v = parseVersionTriple(version) ?: return true
    return constraint.split("||").any { clause ->
        clause.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.all { token ->
            satisfiesSingleToken(v, token.trim())
        }
    }
}

private fun satisfiesSingleToken(v: Triple<Int, Int, Int>, token: String): Boolean {
    val (op, vStr) = when {
        token.startsWith(">=") -> ">=" to token.drop(2)
        token.startsWith("<=") -> "<=" to token.drop(2)
        token.startsWith(">")  -> ">"  to token.drop(1)
        token.startsWith("<")  -> "<"  to token.drop(1)
        token.startsWith("^")  -> "^"  to token.drop(1)
        token.startsWith("~")  -> "~"  to token.drop(1)
        token.startsWith("=")  -> "="  to token.drop(1)
        else                   -> "="  to token
    }
    val c = parseVersionTriple(vStr) ?: return true
    val cmp = cmpTriple(v, c)
    return when (op) {
        ">=" -> cmp >= 0
        "<=" -> cmp <= 0
        ">"  -> cmp > 0
        "<"  -> cmp < 0
        // ^ = same major, at least the specified minor/patch
        "^"  -> v.first == c.first && cmp >= 0
        // ~ = same major+minor, at least the specified patch
        "~"  -> v.first == c.first && v.second == c.second && cmp >= 0
        else -> cmp == 0
    }
}
