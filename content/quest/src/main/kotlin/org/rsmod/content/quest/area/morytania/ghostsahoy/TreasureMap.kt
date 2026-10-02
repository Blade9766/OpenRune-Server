package org.rsmod.content.quest.area.morytania.ghostsahoy

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpHeldU
import org.rsmod.content.quest.area.SpadeDigging
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BOOK
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.MAP
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.SCRAP_1
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.SCRAP_2
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.SCRAP_3
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The three map scraps, the treasure map they make and the Book of Haricanto it leads to.
 *
 * Only one of each scrap can be carried into the map: the three distinct scraps are removed
 * together in one transaction, so a spare copy of one never stands in for another. The map
 * (the cache's `interface.ahoy_islandmap`) reads, from the Saradomin statue on Dragontooth
 * Island: six south, eight east, two north, four east, twenty-two south. The walk starts on the
 * map's own `loc.ahoy_x_start` marker beside the statue, which puts the dig at [DIG_TILE].
 */
class TreasureMap
@Inject
constructor(private val ahoy: GhostsAhoyQuest, private val spadeDigging: SpadeDigging) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeldU(SCRAP_1, SCRAP_2) { combine() }
        onOpHeldU(SCRAP_1, SCRAP_3) { combine() }
        onOpHeldU(SCRAP_2, SCRAP_3) { combine() }
        onOpHeld1(MAP) { read() }
        onOpHeld2(MAP) { read() }
        spadeDigging.register(DIG_TILE, radius = 0) { dig() }
    }

    private fun ProtectedAccess.combine() {
        if (!(inv.contains(SCRAP_1) && inv.contains(SCRAP_2) && inv.contains(SCRAP_3))) {
            mes("You need all three pieces of the map to put it together.")
            return
        }
        val joined =
            player.invTransaction(inv) {
                val pack = select(inv)
                for (scrap in listOf(SCRAP_1, SCRAP_2, SCRAP_3)) {
                    delete {
                        from = pack
                        obj = scrap.asRSCM()
                        strictCount = 1
                    }
                }
                insert {
                    into = pack
                    obj = MAP.asRSCM()
                    strictCount = 1
                }
            }
        if (joined.success) {
            mes("You put the three pieces of the map together.")
        }
    }

    private fun ProtectedAccess.read() {
        ifOpenMainModal(MAP_INTERFACE)
    }

    private suspend fun ProtectedAccess.dig() {
        anim(DIG_SEQ)
        soundSynth(DIG_SOUND)
        delay(DIG_TICKS)
        if (!ahoy.needsBook(player) || !inv.contains(MAP)) {
            mes("You dig, but find nothing of interest.")
            return
        }
        if (invAdd(inv, BOOK).failure) {
            mes("You uncover something, but have no room to carry it.")
            return
        }
        objbox(BOOK, "You dig where the map shows and unearth the Book of Haricanto!")
    }

    companion object {
        const val MAP_INTERFACE = "interface.ahoy_islandmap"
        val STATUE_START = CoordGrid(3791, 3556, 0)

        /** Six south, eight east, two north, four east and twenty-two south of [STATUE_START]. */
        val DIG_TILE: CoordGrid = STATUE_START.translate(8 + 4, -6 + 2 - 22)

        private const val DIG_SEQ = "seq.human_dig"
        private const val DIG_SOUND = "synth.digspade"
        private const val DIG_TICKS = 2
    }
}
