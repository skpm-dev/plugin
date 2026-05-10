package dev.skpm.installer

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.zip.ZipInputStream

internal data class SpigotResource(val id: Int, val name: String, val version: String, val author: String)

internal class SpigotMCClient {

    private val http = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private val gson = Gson()

    /** Returns all free Skript resources matching [query] by name. */
    fun searchResources(query: String): List<SpigotResource> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val resp = getString("https://api.spiget.org/v2/search/resources/$encoded?field=name&size=20&sort=-downloads")
        if (resp.statusCode() != 200) return emptyList()

        val results = gson.fromJson(resp.body(), JsonArray::class.java) ?: return emptyList()
        return results
            .map { it.asJsonObject }
            .filter { obj ->
                obj.getAsJsonObject("category")?.get("id")?.asInt == SKRIPT_CATEGORY &&
                    obj.get("premium")?.asBoolean != true
            }
            .map { parseResource(it) }
    }

    /** Looks up a single free Skript resource by numeric ID. Returns null if not found, not Skript category, or premium. */
    fun findById(id: Int): SpigotResource? {
        val resp = getString("https://api.spiget.org/v2/resources/$id")
        if (resp.statusCode() != 200) return null

        val obj = gson.fromJson(resp.body(), JsonObject::class.java) ?: return null
        if (obj.getAsJsonObject("category")?.get("id")?.asInt != SKRIPT_CATEGORY) return null
        if (obj.get("premium")?.asBoolean == true) return null

        return parseResource(obj)
    }

    fun download(resourceId: Int): List<Pair<String, ByteArray>> {
        val resp = getBytes("https://api.spiget.org/v2/resources/$resourceId/download")
        if (resp.statusCode() != 200)
            throw RuntimeException("SpigotMC download failed (HTTP ${resp.statusCode()})")

        val bytes = resp.body()
        val contentType = resp.headers().firstValue("content-type").orElse("")

        return if (contentType.contains("zip") || isZipMagic(bytes)) {
            extractSkFiles(bytes)
        } else {
            listOf("script.sk" to bytes)
        }
    }

    private fun parseResource(obj: JsonObject): SpigotResource {
        val authorName = try {
            obj.getAsJsonObject("author")?.get("name")?.asString ?: "unknown"
        } catch (_: Exception) { "unknown" }
        return SpigotResource(
            id = obj.get("id").asInt,
            name = obj.get("name")?.asString ?: "unknown",
            version = obj.get("tag")?.asString ?: "unknown",
            author = authorName
        )
    }

    private fun isZipMagic(bytes: ByteArray) =
        bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

    private fun extractSkFiles(zip: ByteArray): List<Pair<String, ByteArray>> {
        val files = mutableListOf<Pair<String, ByteArray>>()
        ZipInputStream(zip.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".sk")) {
                    files.add(entry.name.substringAfterLast('/') to zis.readBytes())
                }
                entry = zis.nextEntry
            }
        }
        return files
    }

    private fun getString(url: String): HttpResponse<String> {
        val req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", "SKPM/1.0")
            .GET().build()
        return http.send(req, HttpResponse.BodyHandlers.ofString())
    }

    private fun getBytes(url: String): HttpResponse<ByteArray> {
        val req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", "SKPM/1.0")
            .GET().build()
        return http.send(req, HttpResponse.BodyHandlers.ofByteArray())
    }

    companion object {
        private const val SKRIPT_CATEGORY = 25
    }
}
