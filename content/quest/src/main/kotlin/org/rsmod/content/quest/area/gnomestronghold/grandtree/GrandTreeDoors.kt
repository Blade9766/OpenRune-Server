package org.rsmod.content.quest.area.gnomestronghold.grandtree

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Grand Tree's entrance: a pair of 2x1 centrepiece doors whose open forms still fill the
 * doorway, so the generic door handling cannot swing them. Opening either leaf swaps both for
 * their open models and walks the player through the doorway.
 *
 * Nothing here suspends: swapping the clicked loc ends the player's script, so the move happens
 * in the same cycle and the closing sound is scheduled on the world queue.
 */
class GrandTreeDoors
@Inject
constructor(
    private val locRepo: LocRepository,
    private val worldQueues: WorldQueueList,
    private val playerList: PlayerList,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(LEFT_DOOR) { walkThrough(it.loc) }
        onOpLoc1(RIGHT_DOOR) { walkThrough(it.loc) }
    }

    private fun ProtectedAccess.walkThrough(door: BoundLocInfo) {
        val goingIn = player.coords.z < DOORWAY_Z
        val start = CoordGrid(coords.x.coerceIn(DOORWAY_MIN_X, DOORWAY_MAX_X), coords.z, coords.level)
        val end = start.translateZ(if (goingIn) 2 else -2)

        soundSynth(OPEN_SOUND)
        locRepo.add(LEFT_COORDS, LEFT_DOOR_OPEN, OPEN_TICKS, LocAngle.West, door.shape)
        locRepo.add(RIGHT_COORDS, RIGHT_DOOR_OPEN, OPEN_TICKS, LocAngle.East, door.shape)

        anim(WALK_SEQ)
        exactMove(
            start = start,
            end = end,
            delay1 = 0,
            delay2 = WALK_TICKS * CLIENT_CYCLES_PER_TICK,
            dir = if (goingIn) constants.em_face_north else constants.em_face_south,
            teleportType = TeleportType.Exempt,
        )

        val uid = player.uid
        worldQueues.add(OPEN_TICKS) {
            val walker = uid.resolve(playerList) ?: return@add
            walker.soundSynth(CLOSE_SOUND)
        }
    }

    private companion object {
        const val LEFT_DOOR = "loc.treedoorl"
        const val RIGHT_DOOR = "loc.treedoorr"
        const val LEFT_DOOR_OPEN = "loc.treedoorl_open"
        const val RIGHT_DOOR_OPEN = "loc.treedoorr_open"

        val LEFT_COORDS = CoordGrid(2464, 3492, 0)
        val RIGHT_COORDS = CoordGrid(2466, 3492, 0)
        const val DOORWAY_Z = 3492
        const val DOORWAY_MIN_X = 2464
        const val DOORWAY_MAX_X = 2467

        const val OPEN_SOUND = "synth.door_open"
        const val CLOSE_SOUND = "synth.door_close"
        const val WALK_SEQ = "seq.human_walk_f"
        const val WALK_TICKS = 2
        const val OPEN_TICKS = 3
        const val CLIENT_CYCLES_PER_TICK = 30
    }
}
