package org.rsmod.content.areas.misc.dwarvenmine.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DwarvenMineLocScript
@Inject
constructor(private val locRepo: LocRepository, private val worldRepo: WorldRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.stairs_falador") { useStairs(FALADOR_HOUSE) }
        onOpLoc1("loc.stairs_cellar") {
            if (it.loc.coords == FALADOR_CELLAR_STAIRS) {
                useStairs(MINE_STAIRS_LANDING)
            }
        }

        onOpLoc1("loc.dwarf_mines_sc_wall_crack") { squeezeThroughCrevice(it.loc) }

        // Thalzar's door and chest belong to Dragon Slayer; see quest/../dragonslayer/MagicDoor.
    }

    private suspend fun ProtectedAccess.useStairs(dest: CoordGrid) {
        arriveDelay()
        delay(1)
        telejump(dest)
    }

    /** Three forced steps through the wall: into the crack, along the tunnel, and back out. */
    private suspend fun ProtectedAccess.squeezeThroughCrevice(crevice: BoundLocInfo) {
        arriveDelay()
        if (player.agilityLvl < CREVICE_AGILITY) {
            mes("You need an Agility level of $CREVICE_AGILITY to negotiate this obstacle.")
            return
        }
        val westbound = crevice.coords == CREVICE_EAST
        val step = if (westbound) -1 else 1
        val facing = if (westbound) FACE_WEST else FACE_EAST
        val tunnelEnd = if (westbound) CREVICE_WEST else CREVICE_EAST

        faceSquare(crevice.coords)
        exactMove(coords, crevice.coords, delay1 = 0, delay2 = STEP_CLIENT_CYCLES, dir = facing)
        anim("seq.agility_shortcut_crack_enter")
        soundSynth(CREVICE_ENTER_SOUND)
        delay(1)

        exactMove(coords, tunnelEnd, delay1 = 0, delay2 = STEP_CLIENT_CYCLES, dir = facing)
        anim("seq.agilty_shortcut_tunnel_walk")
        delay(1)

        exactMove(coords, tunnelEnd.translateX(step), delay1 = 0, delay2 = STEP_CLIENT_CYCLES, dir = facing)
        anim("seq.agility_shortcut_crack_leave")
        soundSynth(CREVICE_LEAVE_SOUND)
        spam("You climb your way through the narrow crevice.")
    }

    private fun spawn(coords: CoordGrid, loc: String, angle: LocAngle, shape: LocShape): LocInfo =
        locRepo.add(coords, loc, Int.MAX_VALUE, angle, shape)

    private companion object {
        val FALADOR_HOUSE = CoordGrid(3061, 3377, 0)
        val MINE_STAIRS_LANDING = CoordGrid(3058, 9776, 0)
        val FALADOR_CELLAR_STAIRS = CoordGrid(3058, 3376, 0)

        val CREVICE_EAST = CoordGrid(3034, 9806, 0)
        val CREVICE_WEST = CoordGrid(3029, 9806, 0)
        const val CREVICE_AGILITY = 42
        const val CREVICE_ENTER_SOUND = 2489
        const val CREVICE_LEAVE_SOUND = 2490
        const val STEP_CLIENT_CYCLES = 30
        const val FACE_WEST = 512
        const val FACE_EAST = 1536

        const val DRAGON_SLAYER = "quest_dragonslayer1"
        const val MAGIC_DOOR_OPEN_CYCLES = 6
        const val PANEL_CYCLES = 3
        const val INVISIBLE_WALL = "loc.inviswall"
    }
}
