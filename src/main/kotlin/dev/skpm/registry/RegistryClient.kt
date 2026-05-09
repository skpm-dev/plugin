package dev.skpm.registry

import com.google.gson.Gson
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

data class FileEntry(
    val name: String?,
    val url: String?
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

    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val gson = Gson()
    private val registryUrl = "http://localhost:8080"

    fun fetchPackage(name: String): Package? {
        val response = get("$registryUrl/packages/$name")

        if (response.statusCode() == 404) return null

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

    fun downloadFile(url: String): String {
        val response = get(url)

        if (response.statusCode() != 200) {
            throw RuntimeException("Failed to download file from $url — got ${response.statusCode()}")
        }

        return response.body()
    }

    private fun get(url: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build()

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }
}
