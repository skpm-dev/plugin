package dev.skpm.commands

import dev.skpm.installer.Installer
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin

class SKPMCommand(private val plugin: JavaPlugin) : CommandExecutor {

    private val installer = Installer(plugin)

    // Color prefixes
    private val OK   = "§a[SKPM] §r"
    private val ERR  = "§c[SKPM] §r"
    private val INFO = "§e[SKPM] §r"
    private val DIM  = "§7"

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage(usage())
            return true
        }

        when (args[0].lowercase()) {
            "install" -> {
                if (!sender.hasPermission("skpm.install")) {
                    sender.sendMessage("${ERR}You don't have permission to install packages.")
                    return true
                }
                if (args.size < 2) {
                    sender.sendMessage("${INFO}Usage: /skpm install <package>")
                    return true
                }
                handleInstall(sender, args[1])
            }
            "remove" -> {
                if (!sender.hasPermission("skpm.remove")) {
                    sender.sendMessage("${ERR}You don't have permission to remove packages.")
                    return true
                }
                if (args.size < 2) {
                    sender.sendMessage("${INFO}Usage: /skpm remove <package> --confirm")
                    return true
                }
                val confirmed = args.drop(1).contains("--confirm")
                val packageName = args.drop(1).firstOrNull { it != "--confirm" } ?: ""
                handleRemove(sender, packageName, confirmed)
            }
            "update" -> {
                if (!sender.hasPermission("skpm.update")) {
                    sender.sendMessage("${ERR}You don't have permission to update packages.")
                    return true
                }
                handleUpdate(sender, if (args.size >= 2) args[1] else null)
            }
            "search" -> {
                if (!sender.hasPermission("skpm.search")) {
                    sender.sendMessage("${ERR}You don't have permission to search packages.")
                    return true
                }
                if (args.size < 2) {
                    sender.sendMessage("${INFO}Usage: /skpm search <query>")
                    return true
                }
                handleSearch(sender, args.drop(1).joinToString(" "))
            }
            "list" -> {
                if (!sender.hasPermission("skpm.list")) {
                    sender.sendMessage("${ERR}You don't have permission to list packages.")
                    return true
                }
                handleList(sender)
            }
            "info" -> {
                if (!sender.hasPermission("skpm.info")) {
                    sender.sendMessage("${ERR}You don't have permission to view package info.")
                    return true
                }
                if (args.size < 2) {
                    sender.sendMessage("${INFO}Usage: /skpm info <package>")
                    return true
                }
                handleInfo(sender, args[1])
            }
            else -> sender.sendMessage("${ERR}Unknown subcommand. ${usage()}")
        }

        return true
    }

    private fun handleInstall(sender: CommandSender, packageName: String) {
        sender.sendMessage("${INFO}Installing $packageName...")
        installer.install(
            packageName,
            onComplete = { msg -> sender.sendMessage("${OK}$msg") },
            onError    = { msg -> sender.sendMessage("${ERR}$msg") }
        )
    }

    private fun handleRemove(sender: CommandSender, packageName: String, confirmed: Boolean) {
        if (!confirmed) {
            val entry = installer.listInstalled().find { it.name == packageName }
            val atVersion = if (entry != null) "@${entry.version}" else ""
            sender.sendMessage("${INFO}This will remove $packageName$atVersion and disable its scripts.")
            sender.sendMessage("${INFO}Run §r/skpm remove $packageName --confirm §eto proceed.")
            return
        }
        sender.sendMessage("${INFO}Removing $packageName...")
        installer.remove(
            packageName,
            onComplete = { msg -> sender.sendMessage("${OK}$msg") },
            onError    = { msg -> sender.sendMessage("${ERR}$msg") }
        )
    }

    private fun handleUpdate(sender: CommandSender, packageName: String?) {
        if (packageName != null) {
            sender.sendMessage("${INFO}Checking for updates to $packageName...")
            installer.update(
                packageName,
                onComplete = { msg -> sender.sendMessage("${OK}$msg") },
                onError    = { msg -> sender.sendMessage("${ERR}$msg") }
            )
        } else {
            sender.sendMessage("${INFO}Checking for updates...")
            installer.updateAll(
                onComplete = { msg -> sender.sendMessage("${OK}$msg") },
                onError    = { msg -> sender.sendMessage("${ERR}$msg") }
            )
        }
    }

    private fun handleSearch(sender: CommandSender, query: String) {
        sender.sendMessage("${INFO}Searching for '$query'...")
        installer.search(
            query,
            onComplete = { results ->
                if (results.isEmpty()) {
                    sender.sendMessage("${INFO}No packages found for '$query'.")
                } else {
                    sender.sendMessage("${OK}Results for '$query':")
                    results.forEach { pkg ->
                        sender.sendMessage("  §r${pkg.name}@${pkg.latest} ${DIM}— ${pkg.description}")
                    }
                }
            },
            onError = { msg -> sender.sendMessage("${ERR}$msg") }
        )
    }

    private fun handleList(sender: CommandSender) {
        val installed = installer.listInstalled()
        if (installed.isEmpty()) {
            sender.sendMessage("${INFO}No packages installed.")
            return
        }
        sender.sendMessage("${OK}Installed packages (${installed.size}):")
        installed.forEach { entry ->
            val desc = if (entry.description != null) " ${DIM}— ${entry.description}" else ""
            sender.sendMessage("  §r${entry.name}@${entry.version}$desc")
        }
    }

    private fun handleInfo(sender: CommandSender, packageName: String) {
        sender.sendMessage("${INFO}Fetching info for $packageName...")
        installer.info(
            packageName,
            onComplete = { pkg ->
                val v = pkg.versions?.get(pkg.latest)
                sender.sendMessage("${OK}${pkg.name}@${pkg.latest}")
                if (!pkg.description.isNullOrEmpty()) sender.sendMessage("  ${DIM}${pkg.description}")
                if (!pkg.author.isNullOrEmpty())      sender.sendMessage("  ${DIM}by ${pkg.author}")
                if (!v?.skript.isNullOrEmpty())       sender.sendMessage("  ${DIM}Skript: ${v!!.skript}")
                if (!v?.minecraft.isNullOrEmpty())    sender.sendMessage("  ${DIM}Minecraft: ${v!!.minecraft}")
                if (!v?.addons.isNullOrEmpty()) {
                    sender.sendMessage("  ${DIM}Addons:")
                    v!!.addons!!.forEach { (name, ver) -> sender.sendMessage("    ${DIM}$name $ver") }
                }
                val lock = installer.listInstalled().find { it.name == pkg.name }
                if (lock != null) {
                    if (lock.version == pkg.latest) {
                        sender.sendMessage("  §aInstalled: ${lock.version} (up to date)")
                    } else {
                        sender.sendMessage("  §eInstalled: ${lock.version} ${DIM}(latest: ${pkg.latest})")
                    }
                }
            },
            onError = { msg -> sender.sendMessage("${ERR}$msg") }
        )
    }

    private fun usage() = "${INFO}Usage: /skpm <install|remove|update|search|list|info> [package]"
}
