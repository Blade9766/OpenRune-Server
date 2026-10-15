package org.rsmod.content.quest.area.ikov

import org.rsmod.map.CoordGrid

/**
 * The tiles the Temple of Ikov scripts work from.
 *
 * The temple is one long level-0 map south of the Ranging Guild, entered by the ladder at
 * (2677, 3405) or the trapdoor at (2637, 3408); both of those are ordinary passages and are left
 * to the generic script. The storeroom half west of the lava is reached only over
 * [BRIDGE_TILES], and the temple of Armadyl north of it only by Winelda's teleport.
 */
internal object IkovCoords {
    /** The bracket the retrieved lever is fitted to, west of the entry ladder. */
    val LEVER_BRACKET = CoordGrid(2671, 9804)

    /** The stairs into the unlit rooms below, and where each flight puts the player down. */
    val DARK_ROOM_LANDING = CoordGrid(2639, 9763)
    val DARK_STAIRS_LANDING = CoordGrid(2649, 9805)

    /**
     * The span of the lava bridge. These six tiles are authored a plane up with the bridge flag,
     * so the routefinder walks anyone straight over them; the weight check is the script's.
     */
    val BRIDGE_TILES =
        setOf(
            CoordGrid(2648, 9828),
            CoordGrid(2649, 9828),
            CoordGrid(2650, 9828),
            CoordGrid(2648, 9829),
            CoordGrid(2649, 9829),
            CoordGrid(2650, 9829),
        )

    /** Where someone too heavy for the bridge scrambles back to, east and west of the lava. */
    val BRIDGE_EAST_BANK = CoordGrid(2651, 9829)
    val BRIDGE_WEST_BANK = CoordGrid(2647, 9829)

    /** The far shore of the lava, where Winelda's magic puts the player down. */
    val WINELDA_LANDING = CoordGrid(2647, 9888)

    /** The six chests along the icy path, in the order the arrow varbit indexes them. */
    val ICE_CHESTS =
        listOf(
            CoordGrid(2710, 9850),
            CoordGrid(2719, 9838),
            CoordGrid(2729, 9850),
            CoordGrid(2738, 9835),
            CoordGrid(2745, 9821),
            CoordGrid(2747, 9848),
        )
}
