package org.rsmod.content.events.halloween

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.cheat.Cheat
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class HalloweenWorldScript
@Inject
constructor(private val season: HalloweenSeason, private val world: HalloweenWorld) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> { world.tick() }
        for (colour in PUMPKIN_COLOURS) {
            onOpLoc1("loc.hw24_pumpkin_plant_$colour") {
                mes("These pumpkins are here to get everyone in the Halloween spirit.")
            }
        }
        onCommand("halloween") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Force the Halloween event on or off, or back to the October calendar"
            invalidArgs = "Usage: ::halloween on|off|auto"
            cheat { setSeason() }
        }
    }

    private fun Cheat.setSeason() {
        season.override =
            when (args.getOrNull(0)?.lowercase()) {
                "on" -> true
                "off" -> false
                "auto" -> null
                else -> {
                    player.mes("Usage: ::halloween on|off|auto")
                    return
                }
            }
        val state = if (season.isActive) "active" else "inactive"
        player.mes("The Halloween event is now $state.")
    }

    private companion object {
        val PUMPKIN_COLOURS =
            listOf("orange", "yellow", "red", "white", "darkgreen", "beige", "powdergrey")
    }
}
