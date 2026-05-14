package dev.skpm.installer

import dev.skpm.registry.Package
import dev.skpm.registry.PackageSummary
import dev.skpm.registry.RegistryClient
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class Installer(private val plugin: JavaPlugin) {

    private val registry = RegistryClient()
    private val scriptsDir = File(plugin.dataFolder.parentFile, "Skript/scripts/skpm")
    private val lock = LockFile(File(plugin.dataFolder, "skript.lock"))
    private val inProgress: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun install(packageName: String, onComplete: (String) -> Unit, onError: (String) -> Unit) {
        if (packageName.startsWith("spigotmc:")) {
            installFromSpigot(packageName.removePrefix("spigotmc:"), onComplete, onError)
            return
        }

        val safePackageName = try {
            requireSafeSegment(packageName, "package name")
        } catch (e: IllegalArgumentException) {
            onError(e.message ?: "Invalid package name")
            return
        }

        if (lock.has(safePackageName)) {
            val installedVersion = lock.read().find { it.name == safePackageName }?.version ?: "unknown"
            onComplete("$safePackageName@$installedVersion is already installed. Use /skpm update $safePackageName to check for a newer version.")
            return
        }

        if (!inProgress.add(safePackageName)) {
            onError("An operation on '$safePackageName' is already in progress")
            return
        }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                val pkg = registry.fetchPackage(safePackageName)
                    ?: return@Runnable onError("Package '$safePackageName' not found in registry")

                val versionEntry = pkg.versions!![pkg.latest]
                    ?: return@Runnable onError("Could not find version ${pkg.latest} for $safePackageName")

                if (versionEntry.files.isNullOrEmpty()) {
                    return@Runnable onError("Package '$safePackageName' has no files listed")
                }

                if (!versionEntry.dependencies.isNullOrEmpty()) {
                    val installedNames = lock.read().map { it.name }.toSet()
                    val missing = versionEntry.dependencies.keys.filter { it !in installedNames }
                    if (missing.isNotEmpty()) {
                        val list = missing.joinToString("\n") { "  /skpm install $it" }
                        return@Runnable onError(
                            "$safePackageName requires the following package${if (missing.size != 1) "s" else ""} to be installed first:\n$list"
                        )
                    }
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
                    if (expected.isNullOrEmpty()) {
                        plugin.logger.warning("No checksum for $name in registry — integrity check skipped")
                    } else {
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
                lock.add(LockEntry(safePackageName, pkg.latest!!, integrityMap, pkg.description))

                plugin.server.scheduler.runTask(plugin, Runnable {
                    reloadFiles(safePackageName, fileNames)
                    inProgress.remove(safePackageName)
                    onComplete("Installed ${pkg.name}@${pkg.latest}")
                })
            } catch (e: Exception) {
                inProgress.remove(safePackageName)
                plugin.logger.severe("SKPM install error: ${e::class.simpleName}: ${e.message}")
                e.printStackTrace()
                onError("Failed to install $packageName: ${e.message ?: e::class.simpleName}")
            }
        })
    }

    fun remove(packageName: String, onComplete: (String) -> Unit, onError: (String) -> Unit) {
        val safePackageName = if (packageName.startsWith("spigotmc:")) {
            spigotLockName(packageName.removePrefix("spigotmc:"))
        } else {
            try {
                requireSafeSegment(packageName, "package name")
            } catch (e: IllegalArgumentException) {
                onError(e.message ?: "Invalid package name")
                return
            }
        }
        val packageDir = File(scriptsDir, safePackageName)

        if (!packageDir.exists()) {
            onError("Package '$packageName' is not installed")
            return
        }

        val fileNames = lock.read().find { it.name == safePackageName }?.files?.keys?.toList() ?: emptyList()

        plugin.server.scheduler.runTask(plugin, Runnable {
            for (name in fileNames) {
                plugin.server.dispatchCommand(
                    plugin.server.consoleSender,
                    "skript disable skpm/$safePackageName/$name"
                )
            }
            packageDir.deleteRecursively()
            lock.remove(safePackageName)
            onComplete("Removed $packageName")
        })
    }

    fun listInstalled(): List<LockEntry> = lock.read()

    fun search(query: String, onComplete: (List<PackageSummary>) -> Unit, onError: (String) -> Unit) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                val results = registry.searchPackages(query)
                plugin.server.scheduler.runTask(plugin, Runnable { onComplete(results) })
            } catch (e: Exception) {
                plugin.logger.severe("SKPM search error: ${e.message}")
                onError("Search failed: ${e.message ?: e::class.simpleName}")
            }
        })
    }

    fun info(packageName: String, onComplete: (Package) -> Unit, onError: (String) -> Unit) {
        val safePackageName = try {
            requireSafeSegment(packageName, "package name")
        } catch (e: IllegalArgumentException) {
            onError(e.message ?: "Invalid package name")
            return
        }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                val pkg = registry.fetchPackage(safePackageName)
                    ?: return@Runnable onError("Package '$safePackageName' not found in registry")
                plugin.server.scheduler.runTask(plugin, Runnable { onComplete(pkg) })
            } catch (e: Exception) {
                plugin.logger.severe("SKPM info error: ${e.message}")
                onError("Failed to fetch info: ${e.message ?: e::class.simpleName}")
            }
        })
    }

    fun update(packageName: String, onComplete: (String) -> Unit, onError: (String) -> Unit) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                val safePackageName = try {
                    requireSafeSegment(packageName, "package name")
                } catch (e: IllegalArgumentException) {
                    return@Runnable onError(e.message ?: "Invalid package name")
                }

                val entry = lock.read().find { it.name == safePackageName }
                    ?: return@Runnable onError("Package '$safePackageName' is not installed")

                val pkg = registry.fetchPackage(safePackageName)
                    ?: return@Runnable onError("Package '$safePackageName' not found in registry")

                if (pkg.latest == entry.version) {
                    return@Runnable onComplete("$safePackageName is already up to date (${entry.version})")
                }

                val oldVersion = entry.version
                lock.remove(safePackageName)
                install(
                    safePackageName,
                    onComplete = { onComplete("Updated $safePackageName $oldVersion → ${pkg.latest}") },
                    onError = onError
                )
            } catch (e: Exception) {
                plugin.logger.severe("SKPM update error: ${e.message}")
                onError("Failed to update $packageName: ${e.message ?: e::class.simpleName}")
            }
        })
    }

    fun updateAll(onComplete: (String) -> Unit, onError: (String) -> Unit) {
        val installed = lock.read()
        if (installed.isEmpty()) {
            onComplete("No packages installed.")
            return
        }

        val total = installed.size
        val updated = AtomicInteger(0)
        val upToDate = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val remaining = AtomicInteger(total)

        fun finish() {
            if (remaining.decrementAndGet() == 0) {
                val parts = mutableListOf<String>()
                val u = updated.get(); val s = upToDate.get(); val f = failed.get()
                if (u > 0) parts.add("$u updated")
                if (s > 0) parts.add("$s already up to date")
                if (f > 0) parts.add("$f failed")
                onComplete("Checked $total package${if (total != 1) "s" else ""}: ${parts.joinToString(", ")}.")
            }
        }

        installed.forEach { entry ->
            update(
                entry.name,
                onComplete = { msg ->
                    if (msg.contains("already up to date")) upToDate.incrementAndGet() else updated.incrementAndGet()
                    finish()
                },
                onError = { failed.incrementAndGet(); finish() }
            )
        }
    }

    private fun spigotLockName(query: String): String {
        val numeric = query.toIntOrNull()
        if (numeric != null) return "spigotmc-$numeric"
        val slug = query.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return "spigotmc-$slug"
    }

    private fun installFromSpigot(query: String, onComplete: (String) -> Unit, onError: (String) -> Unit) {
        val lockName = spigotLockName(query)
        if (!inProgress.add(lockName)) {
            onError("An operation on '$query' is already in progress")
            return
        }
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                val client = SpigotMCClient()

                val resource: SpigotResource = run {
                    val numericId = query.toIntOrNull()
                    if (numericId != null) {
                        client.findById(numericId)
                            ?: return@Runnable onError("Resource #$numericId not found, or is not a free Skript resource")
                    } else {
                        val matches = client.searchResources(query)
                        when {
                            matches.isEmpty() -> return@Runnable onError("No free Skript resources found for '$query' on SpigotMC")
                            matches.size == 1 -> matches[0]
                            else -> {
                                val list = matches.joinToString("\n") { "  #${it.id} — ${it.name} by ${it.author} (${it.version})" }
                                return@Runnable onError(
                                    "Multiple Skript resources match '$query'. Use the resource ID to be specific:\n$list\n" +
                                    "Example: /skpm install spigotmc:${matches[0].id}"
                                )
                            }
                        }
                    }
                }

                val files = client.download(resource.id)
                if (files.isEmpty())
                    return@Runnable onError("No .sk files found in SpigotMC resource '${resource.name}'")

                val packageDir = File(scriptsDir, lockName)
                packageDir.mkdirs()

                val fileMap = mutableMapOf<String, String>()
                for ((rawName, bytes) in files) {
                    val safeName = requireSafeSegment(
                        rawName.replace(Regex("[^a-zA-Z0-9._-]"), "-"),
                        "file name"
                    )
                    val dest = File(packageDir, safeName).canonicalFile
                    if (!dest.startsWith(packageDir.canonicalFile))
                        throw SecurityException("File '$safeName' escapes package directory")
                    dest.writeBytes(bytes)
                    fileMap[safeName] = sha256Hex(bytes)
                    plugin.logger.info("Downloaded $safeName from SpigotMC")
                }

                val fileNames = fileMap.keys.toList()
                lock.add(LockEntry(lockName, resource.version, fileMap, "${resource.name} (SpigotMC #${resource.id})"))

                plugin.server.scheduler.runTask(plugin, Runnable {
                    reloadFiles(lockName, fileNames)
                    inProgress.remove(lockName)
                    onComplete("Installed ${resource.name} (${resource.version}) from SpigotMC")
                })
            } catch (e: Exception) {
                inProgress.remove(lockName)
                plugin.logger.severe("SKPM SpigotMC install error: ${e.message}")
                onError("Failed to install from SpigotMC: ${e.message ?: e::class.simpleName}")
            }
        })
    }

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
