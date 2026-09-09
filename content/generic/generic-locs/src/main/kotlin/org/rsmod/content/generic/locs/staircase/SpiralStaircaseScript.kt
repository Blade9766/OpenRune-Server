package org.rsmod.content.generic.locs.staircase

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpContentLoc1
import org.rsmod.api.script.onOpContentLoc2
import org.rsmod.api.script.onOpContentLoc3
import org.rsmod.api.script.onOpLoc2
import org.rsmod.content.generic.locs.passages.StairNavigator
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.map.CoordGrid
import org.rsmod.map.util.Translation
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Spiral staircases. The player is put at the foot of the matching flight on the other level
 * when there is one (see [StairNavigator]); the fixed per-angle translations are only the
 * fallback for spirals whose other end is not in the map, and even then the player is never put
 * down on a blocked tile.
 */
class SpiralStaircaseScript @Inject constructor(private val stairs: StairNavigator) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpContentLoc1("content.spiralstaircase_down") { climbDown(it.loc) }
        onOpContentLoc1("content.spiralstaircase_up") { climbUp(it.loc) }
        onOpContentLoc1("content.spiralstaircase_option") { climOption(it.loc) }
        onOpContentLoc2("content.spiralstaircase_option") {
            arriveDelay()
            climbUp(it.loc)
        }
        onOpContentLoc3("content.spiralstaircase_option") {
            arriveDelay()
            climbDown(it.loc)
        }

        onOpLoc2("loc.spiralstairsbottom_3") { climbLumbridgeTop(it.loc) }
        onOpLoc2("loc.spiralstairstop_3") { climbLumbridgeBottom(it.loc) }
    }

    private fun ProtectedAccess.climbDown(loc: BoundLocInfo) =
        climb(loc, loc.climbDownTranslation())

    private fun BoundLocInfo.climbDownTranslation(): Translation =
        when (angle) {
            LocAngle.West -> Translation(x = adjustedWidth - 1, z = -1, level = -1)
            LocAngle.North -> Translation(x = -adjustedWidth, z = 0, level = -1)
            LocAngle.East -> Translation(x = 0, z = adjustedLength, level = -1)
            LocAngle.South -> Translation(x = adjustedWidth, z = adjustedLength - 1, level = -1)
        }

    private fun ProtectedAccess.climbUp(loc: BoundLocInfo) = climb(loc, loc.climbUpTranslation())

    private fun BoundLocInfo.climbUpTranslation(): Translation =
        when (angle) {
            LocAngle.West -> Translation(x = adjustedWidth, z = 0, level = 1)
            LocAngle.North -> Translation(x = 0, z = -(adjustedLength - 1), level = 1)
            LocAngle.East ->
                Translation(x = -(adjustedWidth - 1), z = adjustedLength - 1, level = 1)
            LocAngle.South -> Translation(x = adjustedWidth - 1, z = adjustedLength, level = 1)
        }

    private fun ProtectedAccess.climb(loc: BoundLocInfo, translation: Translation) {
        telejump(destination(loc, translation))
    }

    /**
     * The foot of the flight that meets [loc] on the level [translation] leads to, else the
     * hand-placed tile if it is free, else the nearest free tile to it.
     */
    private fun ProtectedAccess.destination(loc: BoundLocInfo, translation: Translation): CoordGrid {
        val guess = loc.coords.translate(translation)
        val plane = loc.coords.translateLevel(translation.level)
        val up = translation.level > 0
        return stairs.counterpartLanding(loc, stairs.carry(coords, loc, plane), plane, up)
            ?: guess.takeIf(stairs::walkable)
            ?: stairs.landing(guess)
            ?: guess
    }

    private suspend fun ProtectedAccess.climOption(loc: BoundLocInfo) {
        startDialogue {
            val translation =
                choice2(
                    "Climb up the stairs.",
                    loc.climbUpTranslation(),
                    "Climb down the stairs.",
                    loc.climbDownTranslation(),
                    title = "Climb up or down the stairs?",
                )
            climb(loc, translation)
        }
    }

    private fun ProtectedAccess.climbLumbridgeTop(loc: BoundLocInfo) {
        climb(loc, loc.climbUpTranslation().copy(level = 2))
    }

    private fun ProtectedAccess.climbLumbridgeBottom(loc: BoundLocInfo) {
        climb(loc, loc.climbDownTranslation().copy(level = -2))
    }
}
