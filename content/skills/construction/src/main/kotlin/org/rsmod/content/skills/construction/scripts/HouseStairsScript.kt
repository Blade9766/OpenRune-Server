package org.rsmod.content.skills.construction.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.content.skills.construction.data.Floor
import org.rsmod.content.skills.construction.house.HouseRegistry
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Climbing between floors.
 *
 * Both floors of a house live in the same region, so a staircase only has to move the player one
 * level up or down on the spot - no rebuild needed, because the client already holds every level of
 * the scene it was sent.
 */
class HouseStairsScript @Inject constructor(private val registry: HouseRegistry) : PluginScript() {
    override fun ScriptContext.startup() {
        for (stairs in UP_STAIRS) {
            onOpLoc1(stairs) { climb(it.loc, up = true) }
        }
        for (stairs in DOWN_STAIRS) {
            onOpLoc1(stairs) { climb(it.loc, up = false) }
        }
        for (stairs in SPIRAL_STAIRS) {
            onOpLoc1(stairs) { climb(it.loc, up = true) }
            onOpLoc2(stairs) { climb(it.loc, up = true) }
            onOpLoc3(stairs) { climb(it.loc, up = false) }
        }
    }

    private fun ProtectedAccess.climb(loc: BoundLocInfo, up: Boolean) {
        val house = registry.active(player) ?: return
        val cell = registry.cellOf(house, loc.coords) ?: return
        val (floor, gx, gz) = cell
        val target = Floor.entries.getOrNull(floor.ordinal + if (up) 1 else -1)
        if (target == null) {
            mes("You cannot go any further ${if (up) "up" else "down"}.")
            return
        }
        if (house.state[target, gx, gz] == null) {
            mes("There is no room built ${if (up) "above" else "below"} this staircase.")
            return
        }
        telejump(CoordGrid(player.coords.x, player.coords.z, target.regionLevel))
    }

    private companion object {
        val UP_STAIRS = listOf("loc.poh_stairs_3", "loc.poh_stairs_4", "loc.poh_stairs_5")
        val DOWN_STAIRS =
            listOf("loc.poh_stairstop_3", "loc.poh_stairstop_4", "loc.poh_stairstop_5")
        val SPIRAL_STAIRS = listOf("loc.poh_spiralstairs", "loc.poh_spiralstairs_2")
    }
}
