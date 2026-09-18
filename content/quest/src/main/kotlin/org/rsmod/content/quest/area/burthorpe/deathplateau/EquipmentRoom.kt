package org.rsmod.content.quest.area.burthorpe.deathplateau

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.STAGE_COMBINATION
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.STAGE_ROOM_OPEN
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.TRAPPED_ARCHER
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The equipment room on the ground floor of Burthorpe Castle and the stone mechanism that locks
 * it.
 *
 * The mechanism is a two-by-three block of pedestals west of the room's door with no ops of its
 * own; a stone ball used on a pedestal is left lying on it as a ground item only the player can
 * see, so a wrong ball can be picked straight back up. The door opens once every ball sits where
 * the combination says and the combination has been read - balls put down before then have to
 * be lifted and put back.
 */
class EquipmentRoom
@Inject
constructor(
    private val quest: DeathPlateauQuest,
    private val passages: GenericPassageScript,
    private val objRepo: ObjRepository,
) : PluginScript() {

    private val doorType: ObjectServerType =
        ServerCacheManager.getObject(ROOM_DOOR.asRSCM(RSCMType.LOC))
            ?: error("Missing loc: $ROOM_DOOR")

    override fun ScriptContext.startup() {
        for (pedestal in PEDESTALS) {
            for (ball in SOLUTION.values) {
                onOpLocU(pedestal, ball) { placeBall(it.loc, ball) }
            }
        }
        onOpLoc1(ROOM_DOOR) { roomDoor(it.loc) }
        onOpNpc1(TRAPPED_ARCHER) {
            startDialogue(it.npc) {
                chatPlayer(happy, "Hi!")
                if (quest.stage(player) >= STAGE_ROOM_OPEN || quest.isComplete(player)) {
                    chatNpc(
                        happy,
                        "Thank goodness you opened that door! I've been trapped here since " +
                            "last night!",
                    )
                    chatPlayer(happy, "No problem!")
                } else {
                    chatNpc(sad, "Get me out of here! Someone's locked the equipment room!")
                }
            }
        }
    }

    private suspend fun ProtectedAccess.placeBall(pedestal: BoundLocInfo, ball: String) {
        arriveDelay()
        if (ballOn(pedestal.coords) != null) {
            mes("There is already a stone ball there.")
            return
        }
        if (invDel(inv, ball).failure) {
            return
        }
        faceLoc(pedestal)
        anim(PLACE_SEQ)
        objRepo.add(ball, pedestal.coords, BALL_DURATION, receiver = player, reveal = BALL_DURATION)
        mes("You place the stone ball on the stone mechanism.")
        if (quest.stage(player) != STAGE_COMBINATION || !solved()) {
            return
        }
        delay(1)
        soundSynth(UNLOCK_SOUND)
        mes("The equipment room door has unlocked.")
        quest.advanceTo(this, STAGE_ROOM_OPEN)
    }

    private fun ProtectedAccess.ballOn(coords: CoordGrid): Int? =
        objRepo.findAll(coords).firstOrNull { it.isPrivate && it.isVisibleTo(player) }?.type

    private fun ProtectedAccess.solved(): Boolean =
        SOLUTION.all { (coords, ball) -> ballOn(coords) == ball.asRSCM(RSCMType.OBJ) }

    private suspend fun ProtectedAccess.roomDoor(door: BoundLocInfo) {
        arriveDelay()
        val leaving = coords.z > door.coords.z
        if (leaving || quest.isComplete(player) || quest.stage(player) >= STAGE_ROOM_OPEN) {
            with(passages) { walkThrough(door, doorType) }
            return
        }
        soundSynth(LOCKED_SOUND)
        mes("The door is locked.")
    }

    private companion object {
        const val ROOM_DOOR = "loc.death_castledoor"

        val PEDESTALS = listOf("loc.death_stone_mechanism_corner", "loc.death_stone_mechanism_side")

        /**
         * Where each ball belongs, reading the combination with north up: red north of blue,
         * yellow south of purple, green north of purple, blue west of yellow, purple east of red.
         */
        val SOLUTION =
            mapOf(
                CoordGrid(2894, 3563, 0) to "obj.death_cannonball_red",
                CoordGrid(2894, 3562, 0) to "obj.death_cannonball_blue",
                CoordGrid(2895, 3562, 0) to "obj.death_cannonball_yellow",
                CoordGrid(2895, 3563, 0) to "obj.death_cannonball_purple",
                CoordGrid(2895, 3564, 0) to "obj.death_cannonball_green",
            )

        /** Long enough to fetch the combination from Harold and come back to finish the puzzle. */
        const val BALL_DURATION = 3000

        const val PLACE_SEQ = "seq.human_pickupfloor"
        const val UNLOCK_SOUND = "synth.grill_pulled"
        const val LOCKED_SOUND = "synth.locked"
    }
}
