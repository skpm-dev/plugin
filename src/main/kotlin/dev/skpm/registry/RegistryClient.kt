package dev.skpm.registry

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class PackageSummary(
    val name: String?,
    val description: String?,
    val author: String?,
    val latest: String?
)

data class FileEntry(
    val name: String?,
    val url: String?,
    val sha256: String?
)

data class VersionEntry(
    val skript: String?,
    val minecraft: String?,
    val addons: Map<String, String>?,
    val files: List<FileEntry>?
)

data class Package(
    val name: String?,
    val description: String?,
    val author: String?,
    val latest: String?,
    val versions: Map<String, VersionEntry>?
)

class RegistryClient {

    companion object {
        private const val BASE_URL = "https://registry.skpm.org"
    }

    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private val gson = Gson()

    fun fetchPackage(name: String): Package? {
        val response = get("$BASE_URL/packages/$name")

        if (response.statusCode() == 404) return null

        if (response.statusCode() == 410) {
            val reason = parseErrorMessage(response.body()) ?: "this package has been removed from the registry"
            throw RuntimeException(reason)
        }

        if (response.statusCode() != 200) {
            throw RuntimeException("Registry returned ${response.statusCode()} for package '$name'")
        }

        val pkg = gson.fromJson(response.body(), Package::class.java)
            ?: throw RuntimeException("Registry returned empty response for '$name'")

        if (pkg.latest == null || pkg.versions == null) {
            throw RuntimeException("Package '$name' has missing fields — registry response may be malformed")
        }

        return pkg
    }

    fun searchPackages(query: String): List<PackageSummary> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val response = get("$BASE_URL/search?q=$encoded")

        if (response.statusCode() != 200) {
            throw RuntimeException("Registry returned ${response.statusCode()} for search '$query'")
        }

        val type = object : TypeToken<List<PackageSummary>>() {}.type
        return gson.fromJson(response.body(), type) ?: emptyList()
    }

    fun downloadFile(url: String): String {
        val response = get(url)

        if (response.statusCode() != 200) {
            throw RuntimeException("Failed to download file from $url — got ${response.statusCode()}")
        }

        return response.body()
    }

    private fun parseErrorMessage(body: String): String? = try {
        gson.fromJson(body, com.google.gson.JsonObject::class.java)
            ?.get("error")?.asString
    } catch (_: Exception) { null }

    private fun get(url: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build()

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }
}
