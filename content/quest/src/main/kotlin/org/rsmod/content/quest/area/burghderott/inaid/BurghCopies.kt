package org.rsmod.content.quest.area.burghderott.inaid

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.manager.QuestInstances
import org.rsmod.game.entity.Npc
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid

/**
 * The quest's private copies of the map (the rubble cellar, the store fight and the ambush),
 * through [QuestInstances]. Scripts work in world tiles and translate with [Copy]; the calls are
 * open so tests can run a copy in place.
 */
@Singleton
open class BurghCopies @Inject constructor(private val instances: QuestInstances) {

    /** A copy of the map; [at] turns a world tile into the copy's, [toWorld] back again. */
    class Copy(internal val visit: QuestInstances.Visit?, private val dx: Int, private val dz: Int, private val dl: Int) {
        fun at(world: CoordGrid): CoordGrid = CoordGrid(world.x + dx, world.z + dz, world.level + dl)

        fun toWorld(coords: CoordGrid): CoordGrid = CoordGrid(coords.x - dx, coords.z - dz, coords.level - dl)

        companion object {
            fun of(visit: QuestInstances.Visit, world: CoordGrid): Copy {
                val inside = visit.at(world)
                return Copy(visit, inside.x - world.x, inside.z - world.z, inside.level - world.level)
            }
        }
    }

    open fun ProtectedAccess.enter(key: String, world: CoordGrid, exit: CoordGrid): Copy? {
        val visit = with(instances) { enterCopy(key, world, exit) } ?: return null
        return Copy.of(visit, world)
    }

    open fun ProtectedAccess.leave() {
        with(instances) { leaveCopy() }
    }

    open fun ProtectedAccess.isInside(): Boolean = with(instances) { insideCopy() }

    open fun spawn(copy: Copy, type: String, world: CoordGrid, face: Direction): Npc {
        val visit = checkNotNull(copy.visit) { "Not an instance copy" }
        val npc = instances.spawn(visit, type, world, face)
        npc.respawns = false
        return npc
    }

    open fun remove(npc: Npc) {
        instances.remove(npc)
    }
}
