package org.rsmod.content.quest.area.paterdomus.priestinperil

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.content.generic.locs.doors.DoorTranslations
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid

internal object PaterdomusCoords {
    /** The large double doors on the temple's west wall; inside is east of x 3408. */
    val TEMPLE_DOOR_LEFT = CoordGrid(3408, 3489, 0)
    val TEMPLE_DOOR_RIGHT = CoordGrid(3408, 3488, 0)
    const val TEMPLE_DOOR_X = 3408

    /** The mausoleum trapdoor north of the temple, and the tile beside it. */
    val NORTH_TRAPDOOR = CoordGrid(3405, 3507, 0)
    val NORTH_TRAPDOOR_LANDING = CoordGrid(3405, 3506, 0)

    /** At the foot of the ladder up to [NORTH_TRAPDOOR_LANDING]. */
    val MAUSOLEUM_ENTRY = CoordGrid(3405, 9906, 0)
    val GUARDIAN_SPAWN = CoordGrid(3405, 9902, 0)

    /** On the south edge of its tile; the monument room is to the south. */
    val WEST_GATE = CoordGrid(3405, 9895, 0)

    /** On the east edge of its tile; Drezel's passage is to the east. */
    val EAST_GATE = CoordGrid(3431, 9897, 0)

    /** Beneath the holy barrier trapdoor on the Morytania side, north of the barrier. */
    val BARRIER_NORTH = CoordGrid(3440, 9887, 0)

    /** Inside the small mausoleum on the Morytania bank of the Salve. */
    val MORYTANIA_EXIT = CoordGrid(3423, 3485, 0)

    /** On the east edge of its tile on the top floor; the cell is to the east. */
    val CELL_GATE = CoordGrid(3415, 3489, 2)

    val COFFIN = CoordGrid(3413, 3486, 2)
}

/**
 * Doors and gates that have no open form the generic door scripts can find. The closed locs are
 * swung aside for a few cycles while the player walks through, then put back.
 *
 * Nothing here suspends: deleting the loc the player is interacting with ends their script, so
 * the closing sound is scheduled on the world queue.
 */
@Singleton
class PaterdomusDoors
@Inject
constructor(
    private val locRepo: LocRepository,
    private val worldQueues: WorldQueueList,
    private val playerList: PlayerList,
    private val launcher: ProtectedAccessLauncher,
) {
    fun walkThrough(
        access: ProtectedAccess,
        panels: List<Pair<CoordGrid, String>>,
        dest: CoordGrid,
        openSound: String,
        closeSound: String,
    ) {
        access.soundSynth(openSound)
        for ((index, panel) in panels.withIndex()) {
            val (coords, type) = panel
            val loc = find(coords, type) ?: continue
            val rotations = if (panels.size > 1 && index == 0) 3 else 1
            val opened = DoorTranslations.translateOpen(loc.coords, loc.shape, loc.angle)
            locRepo.del(loc, CLOSE_TICKS)
            locRepo.add(opened, type, CLOSE_TICKS, loc.angle.turn(rotations), loc.shape)
        }
        access.player.walk(dest)
        val uid = access.player.uid
        worldQueues.add(CLOSE_TICKS) { uid.resolve(playerList)?.soundSynth(closeSound) }
    }

    /**
     * Runs [block] for the player behind [uid] once they are free of whatever they are doing,
     * retrying every cycle for a while (a kill lands mid-combat, when protected access is busy).
     */
    fun launchWhenFree(uid: PlayerUid, block: suspend ProtectedAccess.() -> Unit) {
        launchWhenFree(uid, LAUNCH_ATTEMPTS, block)
    }

    private fun launchWhenFree(uid: PlayerUid, attempts: Int, block: suspend ProtectedAccess.() -> Unit) {
        worldQueues.add(1) {
            val player = uid.resolve(playerList) ?: return@add
            if (!launcher.launch(player, block = block) && attempts > 0) {
                launchWhenFree(uid, attempts - 1, block)
            }
        }
    }

    private fun find(coords: CoordGrid, type: String): LocInfo? {
        val id = type.asRSCM(RSCMType.LOC)
        return locRepo.findAll(coords).firstOrNull { it.id == id }
    }

    private companion object {
        const val CLOSE_TICKS = 3
        const val LAUNCH_ATTEMPTS = 20
    }
}
