package org.rsmod.content.quest.area.draynor

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.generic.locs.doors.DoorTranslations
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The ways in and out of Draynor Manor. The front doors only open from outside and slam shut once
 * the player is through; the back door in the eastern-most room only opens from inside. The
 * stairs in the room east of the entrance hall lead down to Count Draynor's basement, which sits
 * off the usual +6400 dungeon offset, so the generic stairs cannot pair them.
 *
 * Swapping the door locs ends the player's interaction, so the walk is started in the same cycle
 * and the closing sound and message are scheduled on the world queue.
 */
class DraynorManor
@Inject
constructor(
    private val locRepo: LocRepository,
    private val worldQueues: WorldQueueList,
    private val playerList: PlayerList,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(FRONT_DOOR_LEFT) { frontDoors() }
        onOpLoc1(FRONT_DOOR_RIGHT) { frontDoors() }
        onOpLoc1(BACK_DOOR) { backDoor() }
        onOpLoc1("loc.cryptstairsdown") { climb(BASEMENT_LANDING) }
        onOpLoc1("loc.cryptstairsup") { climb(MANOR_LANDING) }
    }

    /** The doors sit on the north edge of their tiles; the manor is to the north. */
    private fun ProtectedAccess.frontDoors() {
        if (coords.z > FRONT_DOOR_Z) {
            mes("The doors won't open.")
            return
        }
        soundSynth(FRONT_DOOR_OPEN_SOUND)
        find(LEFT_DOOR_COORDS, FRONT_DOOR_LEFT)?.let { swing(it, FRONT_DOOR_LEFT, rotations = 3) }
        find(RIGHT_DOOR_COORDS, FRONT_DOOR_RIGHT)?.let { swing(it, FRONT_DOOR_RIGHT, rotations = 1) }
        player.walk(CoordGrid(coords.x, FRONT_DOOR_Z + 1, coords.level))
        afterPassing("The doors slam shut behind you!")
    }

    /** The door sits on the south edge of its tile; the room is to the south. */
    private fun ProtectedAccess.backDoor() {
        if (coords.z >= BACK_DOOR_COORDS.z) {
            mes("The door won't open.")
            return
        }
        soundSynth(DOOR_OPEN_SOUND)
        find(BACK_DOOR_COORDS, BACK_DOOR)?.let { swing(it, BACK_DOOR, rotations = 1) }
        player.walk(BACK_DOOR_COORDS)
        afterPassing(message = null)
    }

    private fun ProtectedAccess.afterPassing(message: String?) {
        val uid = player.uid
        worldQueues.add(CLOSE_TICKS) {
            val walker = uid.resolve(playerList) ?: return@add
            walker.soundSynth(DOOR_CLOSE_SOUND)
            message?.let(walker::mes)
        }
    }

    private fun swing(door: LocInfo, type: String, rotations: Int) {
        val coords = DoorTranslations.translateOpen(door.coords, door.shape, door.angle)
        locRepo.del(door, CLOSE_TICKS)
        locRepo.add(coords, type, CLOSE_TICKS, door.angle.turn(rotations), door.shape)
    }

    private fun find(coords: CoordGrid, type: String): LocInfo? {
        val id = type.asRSCM(RSCMType.LOC)
        return locRepo.findAll(coords).firstOrNull { it.id == id }
    }

    private suspend fun ProtectedAccess.climb(dest: CoordGrid) {
        arriveDelay()
        delay(1)
        telejump(dest, TeleportType.Exempt)
    }

    private companion object {
        const val FRONT_DOOR_LEFT = "loc.haunteddoorl"
        const val FRONT_DOOR_RIGHT = "loc.haunteddoorr"
        const val BACK_DOOR = "loc.hauntedbackdoor"

        const val FRONT_DOOR_Z = 3353
        val LEFT_DOOR_COORDS = CoordGrid(3108, FRONT_DOOR_Z, 0)
        val RIGHT_DOOR_COORDS = CoordGrid(3109, FRONT_DOOR_Z, 0)
        val BACK_DOOR_COORDS = CoordGrid(3123, 3361, 0)

        /** North of the basement stairs, facing the coffin. */
        val BASEMENT_LANDING = CoordGrid(3077, 9771, 0)

        /** In the hallway at the foot of the manor's basement stairs. */
        val MANOR_LANDING = CoordGrid(3116, 3356, 0)

        const val CLOSE_TICKS = 3
        const val FRONT_DOOR_OPEN_SOUND = "synth.big_wooden_door_open"
        const val DOOR_OPEN_SOUND = "synth.door_open"
        const val DOOR_CLOSE_SOUND = "synth.door_close"
    }
}
