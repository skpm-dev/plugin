package dev.skpm.commands

import dev.skpm.installer.Installer
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin

class SKPMCommand(plugin: JavaPlugin) : CommandExecutor {

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

    private fun handleList(sender: CommandSender) {
        val installed = installer.listInstalled()

        if (installed.isEmpty()) {
            sender.sendMessage("[SKPM] No packages installed.")
            return
        }

        sender.sendMessage("[SKPM] Installed packages:")
        installed.forEach { name -> sender.sendMessage("  - $name") }
    }

    private fun usage() = "Usage: /skpm <install|remove|list> [package]"
}
