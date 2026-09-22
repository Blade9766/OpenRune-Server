package org.rsmod.content.areas.city.portsarim

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PortSarimLocScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.fai_trapdoor") { climbDownTrapdoor() }
        onOpLoc1("loc.vc_manhole_open") { climbDownManhole() }
        onOpLoc1("loc.vc_ladder") { climbUpFromSewer() }
        onOpLoc1("loc.farming_style") { climbStile(it.loc) }
    }

    private suspend fun ProtectedAccess.climbDownTrapdoor() {
        arriveDelay()
        spam("You climb down through the trapdoor.")
        telejump(player.coords.translateZ(UNDERGROUND_OFFSET))
    }

    private suspend fun ProtectedAccess.climbDownManhole() {
        arriveDelay()
        anim("seq.human_pickupfloor")
        delay(1)
        mes("You climb through the manhole")
        telejump(SEWER_LANDING)
    }

    private suspend fun ProtectedAccess.climbUpFromSewer() {
        arriveDelay()
        anim("seq.human_reachforladder")
        delay(1)
        telejump(MANHOLE_EXIT)
    }

    private suspend fun ProtectedAccess.climbStile(stile: BoundLocInfo) {
        val alongZ = stile.angle == LocAngle.West || stile.angle == LocAngle.East
        val near = stile.coords
        val far = if (alongZ) near.translateZ(1) else near.translateX(1)
        val fromNear = if (alongZ) coords.z <= near.z else coords.x <= near.x
        val (start, end) = if (fromNear) near to far else far to near
        if (coords != start) {
            playerMove(start)
        }
        val facing =
            when {
                alongZ && fromNear -> FACE_NORTH
                alongZ -> FACE_SOUTH
                fromNear -> FACE_EAST
                else -> FACE_WEST
            }
        anim("seq.human_walk_style", delay = STILE_START_CYCLES)
        exactMove(start, end, STILE_START_CYCLES, STILE_END_CYCLES, facing)
        delay(STILE_TICKS)
    }

    private companion object {
        const val UNDERGROUND_OFFSET = 6400
        const val STILE_START_CYCLES = 30
        const val STILE_END_CYCLES = 94
        const val STILE_TICKS = 3
        const val FACE_SOUTH = 0
        const val FACE_WEST = 512
        const val FACE_NORTH = 1024
        const val FACE_EAST = 1536

        val SEWER_LANDING = CoordGrid(2962, 9650, 0)
        val MANHOLE_EXIT = CoordGrid(3018, 3233, 0)
    }
}
