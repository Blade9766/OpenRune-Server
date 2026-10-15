package org.rsmod.content.skills.construction.scripts

import dev.or2.central.account.Rights
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.content.skills.construction.house.freeBuild
import org.rsmod.game.cheat.Cheat
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FreeBuildScript : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("freebuild") {
            desc = "Toggle building in your house without levels, materials or coins"
            requiredRights = Rights.ADMINISTRATOR
            invalidArgs = "Usage: ::freebuild [on|off]"
            cheat(::toggle)
        }
    }

    private fun toggle(cheat: Cheat) {
        val player = cheat.player
        val enabled =
            when (cheat.args.getOrNull(0)?.lowercase()) {
                null -> !player.freeBuild
                "on", "true", "1" -> true
                "off", "false", "0" -> false
                else -> {
                    player.mes("Usage: ::freebuild [on|off]")
                    return
                }
            }
        player.freeBuild = enabled
        player.mes("Free build is now ${if (enabled) "on" else "off"}.")
    }
}
