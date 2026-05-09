package dev.skpm

import dev.skpm.commands.SKPMCommand
import org.bukkit.plugin.java.JavaPlugin

class SKPMPlugin : JavaPlugin() {

    override fun onEnable() {
        logger.info("SKPM enabled — ready to install packages")
        getCommand("skpm")?.setExecutor(SKPMCommand(this))
    }

    override fun onDisable() {
        logger.info("SKPM disabled")
    }
}
