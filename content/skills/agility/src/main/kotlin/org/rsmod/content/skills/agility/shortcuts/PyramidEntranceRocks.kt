package org.rsmod.content.skills.agility.shortcuts

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.skills.agility.BalanceStyle
import org.rsmod.content.skills.agility.balanceAlong
import org.rsmod.content.skills.agility.line
import org.rsmod.content.skills.agility.stepOnto
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The two rock faces on the way from the desert down to the Agility Pyramid: the first drops into
 * the hollow where Simon Templeton camps, the second down to the pyramid itself. Each is a column
 * of rocks the player scrambles across on whichever row they clicked, and climbing down the
 * second face is the only crossing that gives more than a token amount of experience.
 */
class PyramidEntranceRocks : PluginScript() {
    override fun ScriptContext.startup() {
        for (face in FACES) {
            onOpLoc1(face.loc) { climb(face) }
        }
    }

    private suspend fun ProtectedAccess.climb(face: Face) {
        arriveDelay()
        if (player.agilityLvl < face.level) {
            mes("You need an Agility level of ${face.level} to climb these rocks.")
            return
        }
        val z = coords.z.coerceIn(face.rows)
        val top = CoordGrid(face.topX, z, 0)
        val bottom = CoordGrid(face.bottomX, z, 0)
        val down = coords.x <= (face.topX + face.bottomX) / 2
        val start = if (down) top else bottom
        stepOnto(start)
        balanceAlong(line(start, if (down) bottom else top), BalanceStyle.Climbing)
        val xp = if (down) face.downXp else UP_XP
        if (xp > 0.0) {
            statAdvance("stat.agility", xp)
        }
    }

    private data class Face(
        val loc: String,
        val topX: Int,
        val bottomX: Int,
        val rows: IntRange,
        val level: Int,
        val downXp: Double,
    )

    private companion object {
        const val UP_XP = 1.0

        val FACES =
            listOf(
                Face("loc.ntk_agility_climbing_rocks_1", 3334, 3338, 2826..2829, level = 1, downXp = 0.0),
                Face("loc.ntk_agility_climbing_rocks_2", 3348, 3352, 2827..2829, level = 30, downXp = 5.0),
            )
    }
}
