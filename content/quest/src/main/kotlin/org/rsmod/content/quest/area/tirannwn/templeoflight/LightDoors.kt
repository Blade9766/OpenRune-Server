package org.rsmod.content.quest.area.tirannwn.templeoflight

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.ardougne.undergroundpass.stepThrough
import org.rsmod.content.quest.area.tirannwn.templeoflight.npcs.Thorgel
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The doors of light. A door only shows its Pass-through option to a player whose own light
 * turns it white, but the server never takes the client's word for it: the door is checked
 * against the player's saved pillars each time, and the player steps through it from the tile
 * in front of it to the one behind.
 *
 * The first time through the black door the player meets Thorgel by the Death Altar, which also
 * opens the dwarves' tunnel from the Underground Pass for them.
 */
class LightDoors @Inject constructor(private val puzzle: TemplePuzzle, private val thorgel: Thorgel) : PluginScript() {
    override fun ScriptContext.startup() {
        for (door in TempleGeometry.doors) {
            onOpLoc1(door.loc) { pass(door, it.loc) }
        }
    }

    private suspend fun ProtectedAccess.pass(door: LightDoor, loc: BoundLocInfo) {
        arriveDelay()
        val dest = across(door, loc.coords, coords) ?: return
        if (!puzzle.isOpen(player, door)) {
            mes("The door of light blocks your way.")
            return
        }
        faceSquare(loc.coords)
        stepThrough(listOf(loc.coords, dest))
        if (door.id == TempleGeometry.ALTAR_DOOR && dest.x < loc.coords.x && player.metThorgel == 0) {
            thorgel.meet(this)
        }
    }

    companion object {
        /** The tile behind [door] from a player standing in front of its [tile], or null if they are not. */
        fun across(door: LightDoor, tile: CoordGrid, from: CoordGrid): CoordGrid? {
            if (from.level != tile.level) return null
            val dx = tile.x - from.x
            val dz = tile.z - from.z
            val inFront =
                when (door.axis) {
                    Axis.EAST_WEST -> dz == 0 && (dx == 1 || dx == -1)
                    Axis.NORTH_SOUTH -> dx == 0 && (dz == 1 || dz == -1)
                }
            return if (inFront) CoordGrid(tile.x + dx, tile.z + dz, tile.level) else null
        }
    }
}
