package org.rsmod.content.quest.area.ardougne.sheepherder

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onCommand
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.CLOTHING_PRICE
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.COINS
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * `::sheepherder hint` names the next step, and `::sheepherder status` prints the four-colour
 * checklist; both are open to anyone.
 *
 * Testing shortcuts for administrators:
 * - `::sheepherder kit` adds the 100 coins Doctor Orbon charges.
 * - `::sheepherder goto <place>` jumps to a quest location (see [PLACES]).
 */
class SheepHerderCommands
@Inject
constructor(
    private val sheep: SheepHerderQuest,
    private val launcher: ProtectedAccessLauncher,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onCommand("sheepherder") {
            desc = "Sheep Herder helpers (ex: ::sheepherder hint | status | kit | goto gate)"
            invalidArgs = USAGE
            cheat {
                when (args.firstOrNull()?.lowercase()) {
                    "hint" -> player.mes("Hint: ${sheep.hint(player, null)}")
                    "status" -> status(player)
                    "kit" -> if (isAdmin(player)) kit(player)
                    "goto" -> {
                        if (!isAdmin(player)) return@cheat
                        val dest = PLACES[args.getOrNull(1)?.lowercase()]
                        if (dest == null) {
                            player.mes("Places: ${PLACES.keys.joinToString()}")
                            return@cheat
                        }
                        launcher.launch(player) { telejump(dest, TeleportType.Exempt) }
                    }
                    else -> player.mes(USAGE)
                }
            }
        }
    }

    private fun status(player: Player) {
        if (sheep.stage(player) == 0) {
            player.mes("You haven't started Sheep Herder.")
            return
        }
        for (colour in SheepColour.entries) {
            player.mes(colour.journalLine(sheep.state(player, colour), colour.bones in player.inv))
        }
    }

    private fun isAdmin(player: Player): Boolean {
        if (player.modLevel.isAtLeast(Rights.ADMINISTRATOR)) {
            return true
        }
        player.mes("Only administrators can use that.")
        return false
    }

    private fun kit(player: Player) {
        if (player.inv.freeSpace() == 0 && COINS !in player.inv) {
            player.mes("You need a free inventory slot for the coins.")
            return
        }
        player.invAdd(player.inv, COINS, CLOTHING_PRICE)
        player.mes("Added $CLOTHING_PRICE coins for Doctor Orbon's protective clothing.")
    }

    private companion object {
        const val USAGE = "Use as ::sheepherder hint, status, kit or goto <place>"

        val PLACES =
            linkedMapOf(
                "halgrive" to CoordGrid(2616, 3298, 0),
                "orbon" to CoordGrid(2614, 3305, 0),
                "brumty" to CoordGrid(2592, 3358, 0),
                "gate" to CoordGrid(2593, 3361, 0),
                "pen" to CoordGrid(2598, 3358, 0),
                "red" to CoordGrid(2609, 3346, 0),
                "green" to CoordGrid(2620, 3366, 0),
                "blue" to CoordGrid(2562, 3389, 0),
                "yellow" to CoordGrid(2611, 3388, 0),
            )
    }
}
