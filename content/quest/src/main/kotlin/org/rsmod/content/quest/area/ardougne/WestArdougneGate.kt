package org.rsmod.content.quest.area.ardougne

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The great doors in the wall between East and West Ardougne. They stay shut until Plague City
 * is done; after that the mourners let the player through in either direction. The four door
 * pieces are scenery rather than wall locs, so they are swapped for their open forms in place
 * and the player is moved to the far side.
 */
class WestArdougneGate
@Inject
constructor(
    private val plagueCity: PlagueCityQuest,
    private val locRepo: LocRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(LEFT) { open(it.loc) }
        onOpLoc1(RIGHT) { open(it.loc) }
        onOpLoc1(LEFT_OPEN) { walkThrough(it.loc) }
        onOpLoc1(RIGHT_OPEN) { walkThrough(it.loc) }
    }

    private suspend fun ProtectedAccess.open(door: BoundLocInfo) {
        arriveDelay()
        faceLoc(door)
        if (!plagueCity.quest.isQuestCompleted(player)) {
            mes("You pull on the large wooden doors...")
            delay(2)
            mes("...But they will not open.")
            return
        }
        soundSynth(OPEN_SOUND)
        for (tile in DOOR_TILES) {
            for (piece in locRepo.findAll(tile)) {
                when (piece.id) {
                    leftId -> locRepo.add(piece.coords, LEFT_OPEN, OPEN_TICKS, piece.angle, piece.shape)
                    rightId -> locRepo.add(piece.coords, RIGHT_OPEN, OPEN_TICKS, piece.angle, piece.shape)
                }
            }
        }
        delay(1)
        walkThrough(door)
    }

    /** Puts the player on whichever side of the wall they are not already on. */
    private suspend fun ProtectedAccess.walkThrough(door: BoundLocInfo) {
        val z = door.coords.z.coerceIn(GATE_MIN_Z, GATE_MAX_Z)
        val dest = if (player.coords.x <= WALL_WEST_X) CoordGrid(EAST_SIDE_X, z, 0) else CoordGrid(WEST_SIDE_X, z, 0)
        anim(WALK_SEQ)
        delay(1)
        teleport(dest)
    }

    private val leftId = LEFT.asRSCM(RSCMType.LOC)
    private val rightId = RIGHT.asRSCM(RSCMType.LOC)

    private companion object {
        const val LEFT = "loc.ardougnedoor_l"
        const val RIGHT = "loc.ardougnedoor_r"
        const val LEFT_OPEN = "loc.ardougnedoor_l_open"
        const val RIGHT_OPEN = "loc.ardougnedoor_r_open"

        val DOOR_TILES =
            listOf(
                CoordGrid(2557, 3299, 0),
                CoordGrid(2558, 3299, 0),
                CoordGrid(2557, 3300, 0),
                CoordGrid(2558, 3300, 0),
            )

        const val WALL_WEST_X = 2557
        const val WEST_SIDE_X = 2556
        const val EAST_SIDE_X = 2559
        const val GATE_MIN_Z = 3299
        const val GATE_MAX_Z = 3300

        const val OPEN_SOUND = "synth.big_wooden_door_open"
        const val WALK_SEQ = "seq.human_walk_fence_north"
        const val OPEN_TICKS = 10
    }
}
