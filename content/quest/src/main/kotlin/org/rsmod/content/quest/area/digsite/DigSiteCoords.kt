package org.rsmod.content.quest.area.digsite

import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/** The two winches on the surface and the shafts they drop into. */
enum class DigSiteShaft(
    val winch: String,
    val ropeVarbit: String,
    val ladder: String,
    /** Where the player stands after climbing back up. */
    val surface: CoordGrid,
    /** Where the player lands at the bottom, in the cleared copy of the dungeon. */
    val landing: CoordGrid,
) {
    West(
        winch = "loc.digwinch1",
        ropeVarbit = "varbit.itdigsitewinch1",
        ladder = "loc.winchladder1",
        surface = CoordGrid(3351, 3417, 0),
        landing = CoordGrid(3351, 9752, 0),
    ),
    North(
        winch = "loc.digwinch2",
        ropeVarbit = "varbit.itdigsitewinch2",
        ladder = "loc.winchladder2",
        surface = CoordGrid(3371, 3428, 0),
        landing = CoordGrid(3368, 9762, 0),
    ),
}

/**
 * The tiles the quest cares about.
 *
 * The Digsite Dungeon is mapped twice, sixty-four tiles apart: the northern copy still has the
 * bricks and the rock pile walling off the temple, the southern one has them gone. A player who
 * has not blown the bricks open is sent to the northern copy and lands in the southern one when
 * the charge goes off, which is the moment the transcript has them run for the entrance anyway.
 */
object DigSiteCoords {
    /** Distance between the blocked copy of the dungeon and the cleared one. */
    const val DUNGEON_SHIFT = 64

    /** The bush by the large blue urn that the teddy bear was dropped behind. */
    val TEDDY_BUSH = CoordGrid(3357, 3372, 0)

    /** The pile of bricks, in the cleared copy of the dungeon. */
    val BRICKS = CoordGrid(3378, 9760, 0)

    /** Where the player fetches up after running from the blast. */
    val BLAST_ESCAPE = DigSiteShaft.North.landing

    fun dungeonLevel(player: Player, cleared: CoordGrid): CoordGrid =
        if (player.digsiteBricksBlown) cleared else cleared.translateZ(DUNGEON_SHIFT)

    /** Whether [coords] is anywhere in either copy of the Digsite Dungeon. */
    fun inDungeon(coords: CoordGrid): Boolean =
        coords.level == 0 && coords.x in 3328..3391 && coords.z in 9728..9855

    /** The dig area [coords] belongs to, or `null` when it is not a marked dig. */
    fun digArea(coords: CoordGrid): DigSiteArea? =
        DigSiteArea.entries.firstOrNull { area -> area.rects.any { it.contains(coords) } }
}

/**
 * The four kinds of dig marked out by the signposts, each with its own tools and finds. The bounds
 * are the polygons the OSRS wiki traces on the Soil page.
 */
enum class DigSiteArea(val examLevel: Int, val rects: List<DigRect>) {
    Training(
        examLevel = 0,
        rects = listOf(DigRect(3367, 3397, 3373, 3401), DigRect(3352, 3396, 3358, 3401)),
    ),
    Level1(
        examLevel = 1,
        rects = listOf(DigRect(3367, 3402, 3373, 3415), DigRect(3360, 3402, 3364, 3415)),
    ),
    Level2(
        examLevel = 2,
        rects =
            listOf(
                DigRect(3350, 3424, 3364, 3431),
                DigRect(3350, 3415, 3356, 3419),
                DigRect(3367, 3423, 3373, 3431),
            ),
    ),
    Level3(
        examLevel = 3,
        rects = listOf(DigRect(3350, 3404, 3358, 3413), DigRect(3370, 3437, 3378, 3443)),
    ),
}

data class DigRect(val minX: Int, val minZ: Int, val maxX: Int, val maxZ: Int) {
    fun contains(coords: CoordGrid): Boolean =
        coords.level == 0 && coords.x in minX..maxX && coords.z in minZ..maxZ
}
