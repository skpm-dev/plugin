package dev.skpm.installer

import com.google.gson.GsonBuilder
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant

data class LockEntry(
    val name: String,
    val version: String,
    val files: Map<String, String>,  // filename → "sha256:<hex>"
    val description: String? = null
)

private data class LockData(
    val schemaVersion: Int = 1,
    val generatedAt: String = "",
    val packages: MutableList<LockEntry> = mutableListOf()
)

class LockFile(private val file: File) {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun read(): List<LockEntry> = readData().packages

    fun add(entry: LockEntry) {
        val data = readData()
        data.packages.removeIf { it.name == entry.name }
        data.packages.add(entry)
        data.packages.sortBy { it.name }
        writeAtomic(data)
    }

    fun remove(name: String) {
        val data = readData()
        if (data.packages.removeIf { it.name == name }) {
            writeAtomic(data)
        }
    }

    fun has(name: String): Boolean = readData().packages.any { it.name == name }

    private fun readData(): LockData {
        if (!file.exists()) return LockData()
        return try {
            gson.fromJson(file.readText(), LockData::class.java) ?: LockData()
        } catch (_: Exception) {
            LockData()
        }
    }

    private fun writeAtomic(data: LockData) {
        val snapshot = data.copy(generatedAt = Instant.now().toString())
        file.parentFile?.mkdirs()
        val tmp = File(file.parent, "${file.name}.tmp")
        tmp.writeText(gson.toJson(snapshot))
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}
