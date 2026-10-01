package org.rsmod.content.quest.area.mortton.myreque

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onCommand
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.BLESSED_SICKLE
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.COINS
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.HAMMER
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.NAILS
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.NAILS_NEEDED
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.PLANK
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.PLANKS_NEEDED
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.POUCH
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.WEAPONS
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Testing shortcuts for In Search of the Myreque.
 *
 * - `::myreque kit` fills the pack with the delivery weapons and every travel supply.
 * - `::myreque hint` describes the current obstacle, and nothing beyond it.
 * - `::myreque goto <place>` jumps to a quest location (see [PLACES]).
 *
 * Levels and the Nature Spirit prerequisite are covered by `::setlevel agility 25` and the
 * `assume-completed` quest requirement mode.
 */
class MyrequeCommands
@Inject
constructor(
    private val myq: InSearchOfTheMyrequeQuest,
    private val launcher: ProtectedAccessLauncher,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onCommand("myreque") {
            desc = "In Search of the Myreque helpers (ex: ::myreque kit | hint | goto hollows)"
            invalidArgs = USAGE
            requiredRights = Rights.ADMINISTRATOR
            cheat {
                when (args.firstOrNull()?.lowercase()) {
                    "kit" -> {
                        val slotsNeeded = KIT.sumOf { (obj, count) -> if (!isStackable(obj)) count else if (obj in player.inv) 0 else 1 }
                        if (player.inv.freeSpace() < slotsNeeded) {
                            player.mes("You need $slotsNeeded free inventory slots for the kit.")
                            return@cheat
                        }
                        for ((obj, count) in KIT) {
                            player.invAdd(player.inv, obj, count)
                        }
                        player.mes("Added the Myreque delivery weapons and travel supplies to your pack.")
                    }
                    "hint" -> player.mes("Hint: ${myq.hint(player)}")
                    "goto" -> {
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

    private fun isStackable(obj: String): Boolean =
        ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))?.stackable == true

    private companion object {
        const val USAGE = "Use as ::myreque kit, ::myreque hint or ::myreque goto <place>"

        val KIT: List<Pair<String, Int>> =
            WEAPONS.map { (obj, count) -> obj to count } +
                listOf(
                    PLANK to PLANKS_NEEDED,
                    NAILS to NAILS_NEEDED,
                    HAMMER to 1,
                    COINS to 100,
                    POUCH to 10,
                    BLESSED_SICKLE to 1,
                )

        val PLACES =
            linkedMapOf(
                "canifis" to CoordGrid(3500, 3473, 0),
                "mortton" to MyrequeCoords.MORTTON_LANDING,
                "hollows" to MyrequeCoords.HOLLOWS_LANDING,
                "guard" to MyrequeCoords.BRIDGE_NORTH_END,
                "tunnels" to MyrequeCoords.TUNNEL_DOORS_INSIDE,
                "hideout" to MyrequeCoords.HIDEOUT_ARRIVAL,
            )
    }
}
