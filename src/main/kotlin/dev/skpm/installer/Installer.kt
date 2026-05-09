package dev.skpm.installer

import dev.skpm.registry.Package
import dev.skpm.registry.RegistryClient
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class Installer(private val plugin: JavaPlugin) {

    private val registry = RegistryClient()
    private val scriptsDir = File(plugin.dataFolder.parentFile, "Skript/scripts/skpm")
    private val lockFile = File(plugin.dataFolder, "installed.json")

    fun install(packageName: String, onComplete: (String) -> Unit, onError: (String) -> Unit) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                val pkg = registry.fetchPackage(packageName)
                    ?: return@Runnable onError("Package '$packageName' not found in registry")

                val versionEntry = pkg.versions!![pkg.latest]
                    ?: return@Runnable onError("Could not find version ${pkg.latest} for $packageName")

                if (versionEntry.files.isNullOrEmpty()) {
                    return@Runnable onError("Package '$packageName' has no files listed")
                }

                val packageDir = File(scriptsDir, packageName)
                packageDir.mkdirs()

                for (file in versionEntry.files) {
                    val url = file.url ?: throw RuntimeException("File entry for ${file.name} has no URL")
                    val name = file.name ?: throw RuntimeException("File entry has no name")
                    val content = registry.downloadFile(url)
                    File(packageDir, name).writeText(content)
                    plugin.logger.info("Downloaded $name")
                }

                plugin.server.scheduler.runTask(plugin, Runnable {
                    reloadSkript(packageName)
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
        val packageDir = File(scriptsDir, packageName)

        if (!packageDir.exists()) {
            onError("Package '$packageName' is not installed")
            return
        }

        packageDir.deleteRecursively()

        plugin.server.scheduler.runTask(plugin, Runnable {
            reloadSkript(packageName)
            onComplete("Removed $packageName")
        })
    }

    fun listInstalled(): List<String> {
        if (!scriptsDir.exists()) return emptyList()
        return scriptsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?: emptyList()
    }

    private fun reloadSkript(packageName: String) {
        plugin.server.dispatchCommand(
            plugin.server.consoleSender,
            "skript reload skpm/$packageName"
        )
    }
}
