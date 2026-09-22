package org.rsmod.content.quest.area.varrock.familycrest

import jakarta.inject.Inject
import org.rsmod.api.player.events.skilling.SkillingProductPrepareEvent
import org.rsmod.api.player.events.skilling.SkillingProductSource
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onEvent
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest.Companion.PERFECT_GOLD_ORE
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The four gold seams in the hellhound room under Witchaven. They look like any other gold rock
 * and give ordinary ore to anyone else, but a player carrying Avan's errand pulls 'perfect' gold
 * out of them instead.
 */
class PerfectGold @Inject constructor(private val familyCrest: FamilyCrestQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onEvent<SkillingProductPrepareEvent> {
            val source = product.source as? SkillingProductSource.Mining ?: return@onEvent
            if (source.rock.coords !in PERFECT_SEAMS) {
                return@onEvent
            }
            val player = product.player
            if (!player.avanAsked || player.avanDone || familyCrest.isComplete(player)) {
                return@onEvent
            }
            product.item = PERFECT_GOLD_ORE
            if (player.perfectGoldMined == 0) {
                player.mes("This gold is flawless - not a speck of tarnish in it.")
            }
            player.perfectGoldMined = (player.perfectGoldMined + 1).coerceAtMost(MINED_CAP)
        }
    }

    private companion object {
        /** `loc.goldrock2` spawns behind `loc.famcrest_doori2h1`. */
        val PERFECT_SEAMS =
            setOf(
                CoordGrid(2732, 9680, 0),
                CoordGrid(2743, 9676, 0),
                CoordGrid(2740, 9700, 0),
                CoordGrid(2743, 9699, 0),
            )

        /** The counter only exists to fire the discovery message once; it never gates anything. */
        const val MINED_CAP = 3
    }
}
