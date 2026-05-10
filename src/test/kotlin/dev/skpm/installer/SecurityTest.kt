package dev.skpm.installer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SecurityTest {

    @Test
    fun `valid segments are accepted`() {
        listOf("economy", "my-script", "test123", "Script-v2", "a1", "hello.sk").forEach {
            assertEquals(it, requireSafeSegment(it, "test"))
        }
    }

    @Test
    fun `dot-dot traversal is rejected`() {
        assertThrows<IllegalArgumentException> {
            requireSafeSegment("../etc/passwd", "test")
        }
    }

    @Test
    fun `embedded dot-dot is rejected`() {
        assertThrows<IllegalArgumentException> {
            requireSafeSegment("foo..bar", "test")
        }
    }

    @Test
    fun `slash is rejected`() {
        assertThrows<IllegalArgumentException> {
            requireSafeSegment("foo/bar", "test")
        }
    }

    @Test
    fun `empty string is rejected`() {
        assertThrows<IllegalArgumentException> {
            requireSafeSegment("", "test")
        }
    }

    @Test
    fun `string starting with hyphen is rejected`() {
        assertThrows<IllegalArgumentException> {
            requireSafeSegment("-bad", "test")
        }
    }

    @Test
    fun `sha256Hex produces correct prefix`() {
        assertTrue(sha256Hex("hello").startsWith("sha256:"))
    }

    @Test
    fun `sha256Hex produces correct length`() {
        val result = sha256Hex("hello")
        assertEquals(7 + 64, result.length) // "sha256:" + 64 hex chars
    }

    @Test
    fun `sha256Hex known value`() {
        // SHA-256("") is well-known
        assertEquals(
            "sha256:e3b0c44298fc1c149afbf4c8996fb924" +
                    "27ae41e4649b934ca495991b7852b855",
            sha256Hex("")
        )
    }

    @Test
    fun `sha256Hex is deterministic`() {
        val content = "on spawn:\n  send \"hello\""
        assertEquals(sha256Hex(content), sha256Hex(content))
    }
}
