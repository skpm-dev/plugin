package dev.skpm.installer

import dev.skpm.registry.Package
import dev.skpm.registry.RegistryClient
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.security.MessageDigest

private val SAFE_SEGMENT = Regex("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$")

private fun sha256Hex(content: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
    return "sha256:" + digest.joinToString("") { "%02x".format(it) }
}

private fun requireSafeSegment(value: String, label: String): String {
    if (!SAFE_SEGMENT.matches(value) || value.contains(".."))
        throw IllegalArgumentException("Unsafe $label rejected: '$value'")
    return value
}

class Installer(private val plugin: JavaPlugin) {

    private val registry = RegistryClient()
    private val scriptsDir = File(plugin.dataFolder.parentFile, "Skript/scripts/skpm")
    private val lock = LockFile(File(plugin.dataFolder, "skript.lock"))

    fun install(packageName: String, onComplete: (String) -> Unit, onError: (String) -> Unit) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                val safePackageName = try {
                    requireSafeSegment(packageName, "package name")
                } catch (e: IllegalArgumentException) {
                    return@Runnable onError(e.message ?: "Invalid package name")
                }

                val pkg = registry.fetchPackage(safePackageName)
                    ?: return@Runnable onError("Package '$safePackageName' not found in registry")

                val versionEntry = pkg.versions!![pkg.latest]
                    ?: return@Runnable onError("Could not find version ${pkg.latest} for $safePackageName")

                if (versionEntry.files.isNullOrEmpty()) {
                    return@Runnable onError("Package '$safePackageName' has no files listed")
                }

                val packageDir = File(scriptsDir, safePackageName)
                packageDir.mkdirs()

                for (file in versionEntry.files) {
                    val url = file.url ?: throw RuntimeException("File entry for ${file.name} has no URL")
                    val rawName = file.name ?: throw RuntimeException("File entry has no name")
                    val name = requireSafeSegment(rawName, "file name")
                    val dest = File(packageDir, name).canonicalFile
                    if (!dest.startsWith(packageDir.canonicalFile))
                        throw SecurityException("File '$name' escapes package directory")
                    val content = registry.downloadFile(url)
                    val expected = file.sha256
                    if (expected != null) {
                        val actual = sha256Hex(content)
                        if (!actual.equals(expected, ignoreCase = true))
                            throw SecurityException("Checksum mismatch for $name: expected $expected, got $actual")
                    }
                    dest.writeText(content)
                    plugin.logger.info("Downloaded $name")
                }

                val fileNames = versionEntry.files.mapNotNull { it.name }
                val integrityMap = versionEntry.files
                    .filter { it.name != null }
                    .associate { it.name!! to (it.sha256 ?: "") }
                lock.add(LockEntry(safePackageName, pkg.latest!!, integrityMap))

                plugin.server.scheduler.runTask(plugin, Runnable {
                    reloadFiles(safePackageName, fileNames)
                    onComplete("Installed ${pkg.name}@${pkg.latest}")
                })
            } catch (e: Exception) {
                plugin.logger.severe("SKPM install error: ${e::class.simpleName}: ${e.message}")
                e.printStackTrace()
                onError("Failed to install $packageName: ${e.message ?: e::class.simpleName}")
            }
        })
    }

    fun remove(packageName: String, onComplete: (String) -> Unit, onError: (String) -> Unit) {
        val safePackageName = try {
            requireSafeSegment(packageName, "package name")
        } catch (e: IllegalArgumentException) {
            onError(e.message ?: "Invalid package name")
            return
        }
        val packageDir = File(scriptsDir, safePackageName)

        if (!packageDir.exists()) {
            onError("Package '$packageName' is not installed")
            return
        }

        packageDir.deleteRecursively()
        lock.remove(safePackageName)

        plugin.server.scheduler.runTask(plugin, Runnable {
            reloadFiles(safePackageName, emptyList())
            onComplete("Removed $safePackageName")
        })
    }

    fun listInstalled(): List<String> = lock.read().map { it.name }

    private fun reloadFiles(packageName: String, fileNames: List<String>) {
        if (fileNames.isEmpty()) {
            plugin.server.dispatchCommand(
                plugin.server.consoleSender,
                "skript reload skpm/$packageName"
            )
        } else {
            for (name in fileNames) {
                plugin.server.dispatchCommand(
                    plugin.server.consoleSender,
                    "skript reload skpm/$packageName/$name"
                )
            }
        }
    }
}
