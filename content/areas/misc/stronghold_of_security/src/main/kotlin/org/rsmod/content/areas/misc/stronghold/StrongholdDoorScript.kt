package org.rsmod.content.areas.misc.stronghold

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.mesanims
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Gates of War, Rickety doors, Oozing barriers and Portals of Death.
 *
 * Each doorway is a pair of wall locs (`_door_face` and `_door_face_mirr`) that open together.
 * Doorways come in sets of two with a short corridor between them; stepping out of that corridor
 * makes the door ask an account-security question until the level's reward has been claimed.
 * Which side the corridor lies on comes from [StrongholdDoorTable].
 *
 * Opening swaps both halves for their open forms, turned onto the door frames so the doorway is
 * clear, walks the player through and lets the timed locs close behind them. Nothing suspends
 * after the swap: deleting the loc a player is interacting with ends their script.
 */
class StrongholdDoorScript
@Inject
constructor(
    private val locRepo: LocRepository,
    private val worldQueues: WorldQueueList,
    private val playerList: PlayerList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (level in Level.entries) {
            onOpLoc1(level.doorFace) { openDoor(it.loc, level) }
            onOpLoc1(level.doorMirror) { openDoor(it.loc, level) }
        }
    }

    private suspend fun ProtectedAccess.openDoor(loc: BoundLocInfo, level: Level) {
        val door = StrongholdDoorTable.at(loc.coords)
        if (door == null) {
            // A door outside the known layout: let the player through without a question.
            walkThrough(loc, loc.tileAcrossWall(player.coords), level)
            return
        }
        val exiting = door.isExiting(player.coords)
        if (exiting && !level.isCompleted(player)) {
            val question = StrongholdQuestions.ALL[random.of(StrongholdQuestions.ALL.size)]
            val passed = askQuestion(level, question)
            if (!passed) {
                return
            }
        }
        val dest = if (door.isAcross(player.coords)) door.tile else door.across
        val partner = locRepo.findAll(door.partnerTile).firstOrNull { it.entity.shape == loc.shape.id }
        player.soundSynth(Stronghold.DOOR_OPEN_SOUND)
        locRepo.del(loc, OPEN_TICKS)
        locRepo.add(door.tile, door.openLoc, OPEN_TICKS, door.openAngle(), LocShape.WallStraight)
        val partnerDoor = StrongholdDoorTable.at(door.partnerTile)
        if (partner != null && partnerDoor != null) {
            locRepo.del(partner, OPEN_TICKS)
            locRepo.add(
                partnerDoor.tile,
                partnerDoor.openLoc,
                OPEN_TICKS,
                partnerDoor.openAngle(),
                LocShape.WallStraight,
            )
        }
        player.walk(dest)
        scheduleClose()
    }

    /** The door head asks [question]; returns true if the player picked the right answer. */
    private suspend fun ProtectedAccess.askQuestion(level: Level, question: SecurityQuestion): Boolean {
        chatNpcSpecific(level.doorTitle, level.doorNpc, mesanims.quiz, "To pass you must answer me this:")
        chatNpcSpecific(level.doorTitle, level.doorNpc, mesanims.quiz, question.text)
        val options = question.options
        val picked =
            if (options.size == 2) {
                choice2(options[0], 0, options[1], 1)
            } else {
                choice3(options[0], 0, options[1], 1, options[2], 2)
            }
        return if (picked == question.correct) {
            chatNpcSpecific(level.doorTitle, level.doorNpc, mesanims.happy, "Correct! ${question.explanation}")
            mes("You may pass.")
            true
        } else {
            chatNpcSpecific(level.doorTitle, level.doorNpc, mesanims.sad, "Wrong! ${question.explanation}")
            mes("The ${level.doorTitle.lowercase()} stays firmly shut.")
            false
        }
    }

    /** A single door half with no table entry: open it turned a quarter and step across. */
    private fun ProtectedAccess.walkThrough(loc: BoundLocInfo, dest: CoordGrid, level: Level) {
        val open = if (loc.id == level.doorMirror.locId()) level.doorMirrorOpen else level.doorFaceOpen
        player.soundSynth(Stronghold.DOOR_OPEN_SOUND)
        locRepo.del(loc, OPEN_TICKS)
        locRepo.add(loc.coords, open, OPEN_TICKS, loc.turnAngle(1), loc.shape)
        player.walk(dest)
        scheduleClose()
    }

    private fun ProtectedAccess.scheduleClose() {
        val uid = player.uid
        worldQueues.add(OPEN_TICKS) {
            val walker = uid.resolve(playerList) ?: return@add
            walker.soundSynth(Stronghold.DOOR_CLOSE_SOUND)
        }
    }

    /** The open half sits on the edge facing away from its partner, over the door frame. */
    private fun Door.openAngle(): LocAngle =
        when {
            partnerX > x -> LocAngle.West
            partnerX < x -> LocAngle.East
            partnerZ > z -> LocAngle.South
            else -> LocAngle.North
        }

    private fun BoundLocInfo.tileAcrossWall(from: CoordGrid): CoordGrid =
        when (angle.id) {
            0 -> if (from.x < coords.x) coords else coords.translateX(-1)
            1 -> if (from.z > coords.z) coords else coords.translateZ(1)
            2 -> if (from.x > coords.x) coords else coords.translateX(1)
            else -> if (from.z < coords.z) coords else coords.translateZ(-1)
        }

    private fun String.locId(): Int = asRSCM(RSCMType.LOC)

    private companion object {
        /** Long enough to walk the one tile through the doorway. */
        const val OPEN_TICKS = 3
    }
}
