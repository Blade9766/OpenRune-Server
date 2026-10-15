package org.rsmod.content.quest.area.varrock.dragonslayer

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Getting around Crandor: the hole at the top of the volcano and the rope beneath it, the
 * secret wall in Elvarg's lair that joins the Crandor dungeon to the Karamja volcano dungeon,
 * and the Karamja end of that dungeon (the rocks on the volcano and the rope back up them).
 * The wall must be found from the Crandor side first; after that it opens from either side, so
 * the island can be reached without another ship.
 *
 * The cave entrance beside the Karamja rope leads to the TzHaar city, which does not exist on
 * this server, so it is sealed: the rope is the only way out of the dungeon.
 */
class Crandor
@Inject
constructor(
    private val dragonSlayer: DragonSlayerQuest,
    private val doors: DoorPassage,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(VOLCANO_HOLE) { enterHole() }
        onOpLoc1(CLIMBING_ROPE) { climbRope() }
        onOpLoc1(SECRET_WALL) { secretWall(it.loc) }
        onOpLoc1(KARAMJA_ROCKS) { climbDownRocks() }
        onOpLoc1(KARAMJA_ROPE) { climbKaramjaRope() }
        onOpLoc1(TZHAAR_CAVE) { sealedCave() }
    }

    /* Karamja volcano */

    private suspend fun ProtectedAccess.climbDownRocks() {
        arriveDelay()
        mes("You climb down through the rocks into the volcano.")
        anim(CLIMB_SEQ)
        delay(2)
        telejump(KARAMJA_ROPE_BOTTOM)
    }

    private suspend fun ProtectedAccess.climbKaramjaRope() {
        arriveDelay()
        anim(CLIMB_SEQ)
        soundSynth(ROPE_SOUND)
        delay(2)
        telejump(KARAMJA_ROCKS_TOP)
    }

    private suspend fun ProtectedAccess.sealedCave() {
        arriveDelay()
        mesbox("The cave is choked with fallen rock. There is no way through; the rope is the only way out of here.")
    }

    private suspend fun ProtectedAccess.enterHole() {
        arriveDelay()
        mes("You climb down the rope into the darkness below.")
        anim(CLIMB_SEQ)
        soundSynth(ROPE_SOUND)
        delay(2)
        telejump(ROPE_BOTTOM)
    }

    private suspend fun ProtectedAccess.climbRope() {
        arriveDelay()
        anim(CLIMB_SEQ)
        soundSynth(ROPE_SOUND)
        delay(2)
        telejump(HOLE_TOP)
    }

    private suspend fun ProtectedAccess.secretWall(wall: BoundLocInfo) {
        val fromCrandor = wall.sideOf(player.coords) == WallSide.NORTH
        if (fromCrandor) {
            if (!dragonSlayer.secretDoorFound.get(player)) {
                dragonSlayer.secretDoorFound.set(player, true)
                dragonSlayer.syncVars(player)
                mesbox(
                    "You find a loose stone in the wall. When you press it, a section of the " +
                        "wall swings open onto a passage that must lead into the Karamja " +
                        "volcano. You won't need a ship to leave the island after all!",
                )
            }
        } else if (!dragonSlayer.secretDoorFound.get(player)) {
            mesbox("There is something strange about this wall, but you can't see any way to open it from this side.")
            return
        }
        doors.walkThrough(this, wall, SECRET_WALL, sound = WALL_SOUND)
    }

    private companion object {
        const val VOLCANO_HOLE = "loc.dragon_slayer_qip_ruin_entrance"
        const val CLIMBING_ROPE = "loc.dragon_slayer_qip_climbing_rope"
        const val SECRET_WALL = "loc.dragonsecretdoor"
        const val KARAMJA_ROCKS = "loc.volcano_entrance"
        const val KARAMJA_ROPE = "loc.climbing_rope2"
        const val TZHAAR_CAVE = "loc.tzhaar_karamjadungeon_wall_entrance"

        const val CLIMB_SEQ = "seq.human_reachforladder"
        const val ROPE_SOUND = "synth.ropeclimb"
        const val WALL_SOUND = "synth.stone_door"

        /** South of the rope that hangs below the hole at 2833,3255. */
        val ROPE_BOTTOM = CoordGrid(2833, 9656, 0)

        /** North of the hole on the volcano's rim. */
        val HOLE_TOP = CoordGrid(2834, 3258, 0)

        /** South of the rope that hangs below the Karamja volcano rocks at 2856,3168. */
        val KARAMJA_ROPE_BOTTOM = CoordGrid(2856, 9568, 0)

        /** Just south of the rocks on the volcano. */
        val KARAMJA_ROCKS_TOP = CoordGrid(2856, 3167, 0)
    }
}
