package dev.skpm.installer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LockFileTest {

    @TempDir
    lateinit var tempDir: Path

    private fun lock() = LockFile(tempDir.resolve("skript.lock").toFile())

    @Test
    fun `missing file returns empty list`() {
        assertEquals(emptyList(), lock().read())
    }

    @Test
    fun `add and read entry`() {
        val lock = lock()
        val entry = LockEntry("economy", "1.0.0", mapOf("economy.sk" to "sha256:abc"))
        lock.add(entry)
        assertEquals(listOf(entry), lock.read())
    }

    @Test
    fun `add replaces existing entry with same name`() {
        val lock = lock()
        lock.add(LockEntry("economy", "1.0.0", mapOf("economy.sk" to "sha256:old")))
        val updated = LockEntry("economy", "1.0.1", mapOf("economy.sk" to "sha256:new"))
        lock.add(updated)
        assertEquals(listOf(updated), lock.read())
    }

    @Test
    fun `entries are sorted alphabetically`() {
        val lock = lock()
        lock.add(LockEntry("zoo", "1.0.0", emptyMap()))
        lock.add(LockEntry("alpha", "1.0.0", emptyMap()))
        lock.add(LockEntry("middle", "1.0.0", emptyMap()))
        assertEquals(listOf("alpha", "middle", "zoo"), lock.read().map { it.name })
    }

    @Test
    fun `remove deletes entry`() {
        val lock = lock()
        lock.add(LockEntry("economy", "1.0.0", emptyMap()))
        lock.remove("economy")
        assertEquals(emptyList(), lock.read())
    }

    @Test
    fun `remove non-existent entry is safe`() {
        lock().remove("nonexistent")
    }

    @Test
    fun `has returns true for installed package`() {
        val lock = lock()
        lock.add(LockEntry("economy", "1.0.0", emptyMap()))
        assertTrue(lock.has("economy"))
    }

    @Test
    fun `has returns false for absent package`() {
        assertFalse(lock().has("economy"))
    }

    @Test
    fun `corrupt file returns empty list`() {
        val file = tempDir.resolve("skript.lock").toFile()
        file.writeText("not valid json {{{{")
        assertEquals(emptyList(), LockFile(file).read())
    }

    @Test
    fun `multiple packages survive roundtrip`() {
        val lock = lock()
        val entries = listOf(
            LockEntry("alpha", "1.0.0", mapOf("alpha.sk" to "sha256:aaa")),
            LockEntry("beta", "2.0.0", mapOf("beta.sk" to "sha256:bbb")),
        )
        entries.forEach { lock.add(it) }
        assertEquals(entries, lock.read())
    }
}
