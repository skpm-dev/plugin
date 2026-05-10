package dev.skpm.commands

import dev.skpm.installer.Installer
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin

class SKPMCommand(private val plugin: JavaPlugin) : CommandExecutor {

    private val installer = Installer(plugin)

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage(usage())
            return true
        }

        when (args[0].lowercase()) {
            "install" -> {
                if (args.size < 2) {
                    sender.sendMessage("Usage: /skpm install <package>")
                    return true
                }
                handleInstall(sender, args[1])
            }
            "remove" -> {
                if (args.size < 2) {
                    sender.sendMessage("Usage: /skpm remove <package>")
                    return true
                }
                handleRemove(sender, args[1])
            }
            "update" -> handleUpdate(sender, if (args.size >= 2) args[1] else null)
            "search" -> {
                if (args.size < 2) {
                    sender.sendMessage("Usage: /skpm search <query>")
                    return true
                }
                handleSearch(sender, args.drop(1).joinToString(" "))
            }
            "list" -> handleList(sender)
            else -> sender.sendMessage("Unknown subcommand. ${usage()}")
        }

        return true
    }

    private fun handleInstall(sender: CommandSender, packageName: String) {
        sender.sendMessage("[SKPM] Installing $packageName...")

        installer.install(
            packageName,
            onComplete = { msg -> sender.sendMessage("[SKPM] $msg") },
            onError = { msg -> sender.sendMessage("[SKPM] Error: $msg") }
        )
    }

    private fun handleRemove(sender: CommandSender, packageName: String) {
        sender.sendMessage("[SKPM] Removing $packageName...")

        installer.remove(
            packageName,
            onComplete = { msg -> sender.sendMessage("[SKPM] $msg") },
            onError = { msg -> sender.sendMessage("[SKPM] Error: $msg") }
        )
    }

    private fun handleUpdate(sender: CommandSender, packageName: String?) {
        if (packageName != null) {
            sender.sendMessage("[SKPM] Checking for updates to $packageName...")
            installer.update(
                packageName,
                onComplete = { msg -> sender.sendMessage("[SKPM] $msg") },
                onError = { msg -> sender.sendMessage("[SKPM] Error: $msg") }
            )
        } else {
            sender.sendMessage("[SKPM] Checking for updates...")
            installer.updateAll(
                onComplete = { msg -> sender.sendMessage("[SKPM] $msg") },
                onError = { msg -> sender.sendMessage("[SKPM] Error: $msg") }
            )
        }
    }

    private fun handleSearch(sender: CommandSender, query: String) {
        sender.sendMessage("[SKPM] Searching for '$query'...")
        installer.search(
            query,
            onComplete = { results ->
                if (results.isEmpty()) {
                    sender.sendMessage("[SKPM] No packages found for '$query'.")
                } else {
                    sender.sendMessage("[SKPM] Results for '$query':")
                    results.forEach { pkg ->
                        sender.sendMessage("  ${pkg.name}@${pkg.latest} — ${pkg.description}")
                    }
                }
            },
            onError = { msg -> sender.sendMessage("[SKPM] Error: $msg") }
        )
    }

    private fun handleList(sender: CommandSender) {
        val installed = installer.listInstalled()

        if (installed.isEmpty()) {
            sender.sendMessage("[SKPM] No packages installed.")
            return
        }

        sender.sendMessage("[SKPM] Installed packages (${installed.size}):")
        installed.forEach { entry -> sender.sendMessage("  - ${entry.name}@${entry.version}") }
    }

    private fun usage() = "Usage: /skpm <install|remove|update|search|list> [package]"
}
