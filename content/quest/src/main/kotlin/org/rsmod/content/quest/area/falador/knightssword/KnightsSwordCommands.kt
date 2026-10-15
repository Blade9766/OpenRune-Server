package org.rsmod.content.quest.area.falador.knightssword

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.baseMiningLvl
import org.rsmod.api.script.onCommand
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.IRON_BAR
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.IRON_BARS_NEEDED
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.REDBERRY_PIE
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * `::knightssword hint` (anyone) names the current obstacle and nothing beyond it.
 *
 * Testing shortcuts for administrators:
 * - `::knightssword kit` packs the pie, a pickaxe, two iron bars, food and a little armour.
 * - `::knightssword goto <place>` jumps to a quest location (see [PLACES]).
 */
class KnightsSwordCommands
@Inject
constructor(
    private val ks: KnightsSwordQuest,
    private val vyvinsRoom: VyvinsRoom,
    private val launcher: ProtectedAccessLauncher,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onCommand("knightssword") {
            desc = "The Knight's Sword helpers (ex: ::knightssword hint | kit | goto thurgo)"
            invalidArgs = USAGE
            cheat {
                when (args.firstOrNull()?.lowercase()) {
                    "hint" -> player.mes("Hint: ${hint(player)}")
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

    private fun hint(player: Player): String {
        val base = ks.hint(player)
        if (ks.stage(player) != KnightsSwordQuest.STAGE_PORTRAIT_LOCATED) {
            return base
        }
        val now = if (vyvinsRoom.isLookingAway()) "He is at the window now." else "He is at his desk now."
        return "$base $now"
    }

    private fun isAdmin(player: Player): Boolean {
        if (player.modLevel.isAtLeast(Rights.ADMINISTRATOR)) {
            return true
        }
        player.mes("Only administrators can use that.")
        return false
    }

    private fun kit(player: Player) {
        if (player.inv.freeSpace() < KIT.size) {
            player.mes("You need ${KIT.size} free inventory slots for the kit.")
            return
        }
        for (obj in KIT) {
            player.invAdd(player.inv, obj, 1)
        }
        player.mes("Added a redberry pie, a pickaxe, two iron bars, food and armour to your pack.")
        if (player.baseMiningLvl < MINING_LEVEL) {
            player.mes("Blurite needs level $MINING_LEVEL Mining: use ::setlevel mining $MINING_LEVEL.")
        }
    }

    private companion object {
        const val USAGE = "Use as ::knightssword hint, ::knightssword kit or ::knightssword goto <place>"
        const val MINING_LEVEL = 10

        val KIT: List<String> =
            listOf(REDBERRY_PIE, "obj.bronze_pickaxe") +
                List(IRON_BARS_NEEDED) { IRON_BAR } +
                List(4) { "obj.lobster" } +
                listOf("obj.iron_full_helm", "obj.iron_kiteshield")

        val PLACES =
            linkedMapOf(
                "squire" to CoordGrid(2977, 3341, 0),
                "vyvin" to CoordGrid(2983, 3336, 2),
                "reldo" to CoordGrid(3210, 3492, 0),
                "thurgo" to CoordGrid(3000, 3146, 0),
                "trapdoor" to CoordGrid(3008, 3151, 0),
                "ice" to CoordGrid(3008, 9551, 0),
                "blurite" to CoordGrid(3049, 9565, 0),
            )
    }
}
