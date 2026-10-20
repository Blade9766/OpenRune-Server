package org.rsmod.content.areas.misc.waterbirth

import org.rsmod.map.CoordGrid

/** Landing tiles for the boat, the dungeon mouths and its ladders, checked against the map. */
object WaterbirthCoords {
    val RELLEKKA_PIER = CoordGrid(2620, 3685, 0)
    val ISLAND_SHORE = CoordGrid(2544, 3759, 0)

    val CAVE_MOUTH = CoordGrid(2523, 3740, 0)
    val CAVERN_STEPS = CoordGrid(2442, 10146, 0)

    val MOUNTAIN_TOP = CoordGrid(2541, 3741, 0)
    val LADDER_ROOM = CoordGrid(2545, 10143, 0)

    val SUBLEVEL_2_ENTRANCE = CoordGrid(1799, 4406, 3)

    val KINGS_LADDER_TOP = CoordGrid(1912, 4367, 0)
    val KINGS_LAIR = CoordGrid(2900, 4449, 0)
    val SLAYER_LAIR = CoordGrid(2900, 4385, 0)

    val LIGHTHOUSE_CAVE = CoordGrid(2515, 10008, 0)

    /** The lair copies' players are counted over these squares, for the ladder's Peek option. */
    const val KINGS_LAIR_MIN_X = 2880
    const val KINGS_LAIR_MAX_X = 2943
    const val KINGS_LAIR_MIN_Z = 4416
    const val KINGS_LAIR_MAX_Z = 4479
}
