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
        // The open door pieces would still block the doorway, so the doors are simply taken out of
        // the way for a few seconds and the player walks through like any other gate.
        for (tile in DOOR_TILES) {
            for (piece in locRepo.findAll(tile)) {
                if (piece.id == leftId || piece.id == rightId) {
                    locRepo.del(piece, OPEN_TICKS)
                }
            }
        }
        delay(1)
        walkThrough(door)
    }

    /**
     * Walks the player to the far side of the wall. The doorway tiles are map-blocked, so the
     * routefinder cannot cross them even with the doors gone; the player is stepped across one
     * tile per cycle instead, which the client draws as an ordinary walk.
     */
    private suspend fun ProtectedAccess.walkThrough(door: BoundLocInfo) {
        val z = door.coords.z.coerceIn(GATE_MIN_Z, GATE_MAX_Z)
        val eastbound = player.coords.x <= WALL_WEST_X
        val destX = if (eastbound) EAST_SIDE_X else WEST_SIDE_X
        val startX = if (eastbound) WEST_SIDE_X else EAST_SIDE_X
        if (player.coords.x != startX || player.coords.z != z) {
            playerWalk(CoordGrid(startX, z, 0))
            if (player.coords.x != startX || player.coords.z != z) {
                teleport(CoordGrid(startX, z, 0))
                delay(1)
            }
        }
        val step = if (eastbound) 1 else -1
        var x = startX + step
        while (x != destX + step) {
            teleport(CoordGrid(x, z, 0))
            delay(1)
            x += step
        }
    }

    private val leftId = LEFT.asRSCM(RSCMType.LOC)
    private val rightId = RIGHT.asRSCM(RSCMType.LOC)

    private companion object {
        const val LEFT = "loc.ardougnedoor_l"
        const val RIGHT = "loc.ardougnedoor_r"

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
        /** Long enough to stroll through; the doors come back on their own afterwards. */
        const val OPEN_TICKS = 15
    }
}
