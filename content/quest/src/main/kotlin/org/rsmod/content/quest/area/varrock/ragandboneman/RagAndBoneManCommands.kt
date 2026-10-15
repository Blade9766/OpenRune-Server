package org.rsmod.content.quest.area.varrock.ragandboneman

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onCommand
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.COINS
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.LOGS
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.POT
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.TINDERBOX
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * `::ragboneman hint` (anyone) names the next step and nothing beyond it.
 *
 * Testing shortcuts for administrators:
 * - `::ragboneman kit` packs eight pots, eight logs, a tinderbox, food, and coins for eight jugs
 *   of vinegar plus the Port Sarim boat there and back.
 * - `::ragboneman goto <place>` jumps to the camp, Fortunato or a specimen's habitat (see [PLACES]).
 */
class RagAndBoneManCommands
@Inject
constructor(
    private val rb: RagAndBoneManQuest,
    private val launcher: ProtectedAccessLauncher,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onCommand("ragboneman") {
            desc = "Rag and Bone Man I helpers (ex: ::ragboneman hint | kit | goto camp)"
            invalidArgs = USAGE
            cheat {
                when (args.firstOrNull()?.lowercase()) {
                    "hint" -> player.mes("Hint: ${rb.hint(player)}")
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
        player.invAdd(player.inv, POT, KIT_POTS)
        player.invAdd(player.inv, LOGS, KIT_POTS)
        player.invAdd(player.inv, TINDERBOX, 1)
        player.invAdd(player.inv, COINS, KIT_COINS)
        player.invAdd(player.inv, FOOD, KIT_FOOD)
        player.mes("Added $KIT_POTS pots, $KIT_POTS logs, a tinderbox, $KIT_COINS coins and $KIT_FOOD trout.")
    }

    companion object {
        private const val USAGE = "Use as ::ragboneman hint, ::ragboneman kit or ::ragboneman goto <place>"
        private const val FOOD = "obj.trout"
        private const val KIT_POTS = 8
        private const val KIT_FOOD = 4
        private const val KIT_COINS = KIT_POTS + 60
        private const val KIT_SLOTS = KIT_POTS * 2 + 2 + KIT_FOOD

        internal val PLACES =
            linkedMapOf(
                "camp" to CoordGrid(3360, 3503, 0),
                "fortunato" to CoordGrid(3085, 3249, 0),
                "rats" to CoordGrid(3182, 3169, 0),
                "frogs" to CoordGrid(3198, 3178, 0),
                "goblins" to CoordGrid(3252, 3240, 0),
                "rams" to CoordGrid(3200, 3266, 0),
                "unicorns" to CoordGrid(3285, 3352, 0),
                "bear" to CoordGrid(3296, 3347, 0),
                "sarim" to CoordGrid(3029, 3217, 0),
                "monkeys" to CoordGrid(2876, 3154, 0),
                "volcano" to CoordGrid(2852, 9570, 0),
            )
    }
}
