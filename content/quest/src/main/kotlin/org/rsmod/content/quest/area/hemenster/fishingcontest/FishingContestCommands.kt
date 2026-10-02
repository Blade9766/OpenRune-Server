package org.rsmod.content.quest.area.hemenster.fishingcontest

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.baseFishingLvl
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.script.onCommand
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.COINS
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.FISHING_LEVEL
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.SPADE
import org.rsmod.game.entity.Player
import org.rsmod.game.stat.PlayerSkillXPTable
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * `::fishingcontest hint` (anyone) names the current obstacle and nothing beyond it.
 *
 * Testing shortcuts for administrators:
 * - `::fishingcontest kit` packs a spade, coins for a rod and two rounds, and food for the guard
 *   dogs, and raises Fishing to the level the contest needs.
 * - `::fishingcontest goto <place>` jumps to a quest location (see [PLACES]).
 */
class FishingContestCommands
@Inject
constructor(
    private val fc: FishingContestQuest,
    private val launcher: ProtectedAccessLauncher,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onCommand("fishingcontest") {
            desc = "Fishing Contest helpers (ex: ::fishingcontest hint | kit | goto bonzo)"
            invalidArgs = USAGE
            cheat {
                when (args.firstOrNull()?.lowercase()) {
                    "hint" -> player.mes("Hint: ${fc.hint(player)}")
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

    private fun isAdmin(player: Player): Boolean {
        if (player.modLevel.isAtLeast(Rights.ADMINISTRATOR)) {
            return true
        }
        player.mes("Only administrators can use that.")
        return false
    }

    private fun kit(player: Player) {
        if (player.inv.freeSpace() < KIT_SLOTS) {
            player.mes("You need $KIT_SLOTS free inventory slots for the kit.")
            return
        }
        player.invAdd(player.inv, SPADE, 1)
        player.invAdd(player.inv, COINS, KIT_COINS)
        player.invAdd(player.inv, FOOD, KIT_FOOD)
        player.mes("Added a spade, $KIT_COINS coins and $KIT_FOOD trout to your pack.")
        if (player.baseFishingLvl < FISHING_LEVEL) {
            val xp = PlayerSkillXPTable.getXPFromLevel(FISHING_LEVEL) - player.statMap.getXP(FISHING)
            player.statAdvance(FISHING, xp.toDouble(), rate = 1.0)
            player.mes("Your Fishing has been raised to level $FISHING_LEVEL for the contest.")
        }
    }

    private companion object {
        const val USAGE = "Use as ::fishingcontest hint, ::fishingcontest kit or ::fishingcontest goto <place>"
        const val FISHING = "stat.fishing"
        const val FOOD = "obj.trout"
        const val KIT_COINS = 20
        const val KIT_FOOD = 4
        const val KIT_SLOTS = 2 + KIT_FOOD

        val PLACES =
            linkedMapOf(
                "vestri" to CoordGrid(2820, 3486, 0),
                "austri" to CoordGrid(2876, 3482, 0),
                "garlic" to CoordGrid(2714, 3477, 0),
                "railing" to CoordGrid(2661, 3500, 0),
                "vines" to CoordGrid(2632, 3497, 0),
                "jack" to CoordGrid(2649, 3451, 0),
                "gate" to CoordGrid(2643, 3441, 0),
                "bonzo" to CoordGrid(2642, 3438, 0),
                "pipes" to CoordGrid(2638, 3445, 0),
                "tunnel" to CoordGrid(2848, 9880, 0),
            )
    }
}
