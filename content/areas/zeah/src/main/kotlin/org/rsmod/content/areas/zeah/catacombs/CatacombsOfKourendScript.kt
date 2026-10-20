package org.rsmod.content.areas.zeah.catacombs

import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * A side exit of the catacombs: the [vine] inside climbs out to [surface], beside the [hole] it
 * opens there for good, and entering that hole drops the player at [inside], beside the vine.
 */
data class CatacombExit(
    val vine: String,
    val surface: CoordGrid,
    val hole: String,
    val unlock: String,
    val inside: CoordGrid,
)

object CatacombsOfKourend {
    val STATUE_LANDING = CoordGrid(1666, 10050)
    val CASTLE_LANDING = CoordGrid(1639, 3673)

    val EXITS: List<CatacombExit> =
        listOf(
            CatacombExit(
                vine = "loc.zeah_cata_exit_01",
                surface = CoordGrid(1562, 3791),
                hole = "loc.cata_hole4_multi",
                unlock = "varbit.cata_hole4",
                inside = CoordGrid(1617, 10101),
            ),
            CatacombExit(
                vine = "loc.zeah_cata_exit_02",
                surface = CoordGrid(1469, 3653),
                hole = "loc.cata_hole2_multi",
                unlock = "varbit.cata_hole2",
                inside = CoordGrid(1650, 9987),
            ),
            CatacombExit(
                vine = "loc.zeah_cata_exit_03",
                surface = CoordGrid(1803, 9968),
                hole = "loc.cata_hole1_multi",
                unlock = "varbit.cata_hole1",
                inside = CoordGrid(1726, 9994),
            ),
            CatacombExit(
                vine = "loc.zeah_cata_exit_04",
                surface = CoordGrid(1696, 3864),
                hole = "loc.cata_hole3_multi",
                unlock = "varbit.cata_hole3",
                inside = CoordGrid(1719, 10101),
            ),
            CatacombExit(
                vine = "loc.zeah_cata_exit_giants_den",
                surface = CoordGrid(1461, 9879),
                hole = "loc.cata_hole_giants_den_multi",
                unlock = "varbit.cata_hole_giants_den",
                inside = CoordGrid(1639, 10046),
            ),
        )
}

class CatacombsOfKourendScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.zeah_kourend_statue_plinth") { enterFromStatue() }
        onOpLoc1("loc.cata_exit") { climbVine(CatacombsOfKourend.CASTLE_LANDING) }
        for (exit in CatacombsOfKourend.EXITS) {
            onOpLoc1(exit.vine) { climbVine(exit.surface, exit.unlock) }
            onOpLoc1(exit.hole) { enterHole(exit.inside) }
        }
    }

    private suspend fun ProtectedAccess.enterFromStatue() {
        arriveDelay()
        mes("You investigate what looks like hinges on the plaque and find it opens.")
        anim("seq.human_pickupfloor")
        delay(2)
        mes("You climb down the hole..")
        telejump(CatacombsOfKourend.STATUE_LANDING, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.climbVine(dest: CoordGrid, unlock: String? = null) {
        arriveDelay()
        anim("seq.human_reachforladder")
        delay(2)
        if (unlock != null) {
            vars[unlock] = 1
        }
        telejump(dest, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.enterHole(dest: CoordGrid) {
        arriveDelay()
        anim("seq.human_pickupfloor")
        delay(2)
        telejump(dest, TeleportType.Exempt)
    }
}
