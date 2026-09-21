package org.rsmod.content.quest.area.ardougne.undergroundpass

import org.rsmod.map.CoordGrid

/**
 * Every tile the quest names. They are taken from the cache's own loc spawns and checked against
 * the map's collision flags, so each landing tile below is one the routefinder can stand on.
 */
internal object UpassCoords {
    /* The cave mouth in West Ardougne and the far end of the pass it opens onto. */
    val CAVE_ENTRANCE = CoordGrid(0, 38, 51, 1, 49)
    val CAVE_ENTRANCE_STEP = CoordGrid(0, 38, 51, 1, 48)
    val KOFTIK_OUTSIDE_SPAWN = CoordGrid(0, 38, 51, 2, 46)
    val PASS_ARRIVAL = CoordGrid(0, 38, 151, 63, 49)
    val CAVE_EXIT = CoordGrid(0, 39, 151, 0, 49)

    /* Koftik's fire, the abandoned equipment and the bridge he cannot cross. */
    val KOFTIK_FIRE = CoordGrid(0, 38, 151, 19, 51)
    val ABANDONED_GEAR = CoordGrid(0, 38, 151, 20, 51)
    val GUIDE_ROPE = CoordGrid(0, 38, 151, 11, 54)
    val BRIDGE = CoordGrid(0, 38, 151, 11, 52)
    val BRIDGE_LEVER = CoordGrid(0, 38, 151, 4, 52)
    val BRIDGE_WEST_EDGE = listOf(CoordGrid(0, 38, 151, 10, 52), CoordGrid(0, 38, 151, 10, 53))
    val BRIDGE_EAST_EDGE = listOf(CoordGrid(0, 38, 151, 15, 52), CoordGrid(0, 38, 151, 15, 53))
    val BRIDGE_WEST_LANDING = CoordGrid(0, 38, 151, 10, 52)
    val BRIDGE_EAST_LANDING = CoordGrid(0, 38, 151, 15, 52)

    /* The collapsing grid: ten columns west from the corridor, ten rows deep. */
    const val GRID_WEST_X = 2467
    const val GRID_EAST_X = 2476
    const val GRID_SOUTH_Z = 9673
    const val GRID_NORTH_Z = 9682
    const val GRID_SIZE = 10

    val GRID_EAST_LANDING = CoordGrid(0, 38, 151, 45, 13)
    val GRID_WEST_LANDING = CoordGrid(0, 38, 151, 34, 13)
    val PORTCULLIS_LEVER = CoordGrid(0, 38, 151, 34, 8)
    val PORTCULLIS = listOf(
        CoordGrid(0, 38, 151, 33, 10),
        CoordGrid(0, 38, 151, 33, 12),
        CoordGrid(0, 38, 151, 33, 14),
        CoordGrid(0, 38, 151, 33, 16),
    )

    /** Where the grid drops anyone who steps on a rotten tile, and the ledge they climb back to. */
    val GRID_FALL_LANDING = CoordGrid(0, 38, 151, 45, 9)

    /* The furnace the orbs burn in, and the well the pass carries on below. */
    val FURNACE = CoordGrid(0, 38, 151, 22, 19)
    val WELL_OF_IBAN = CoordGrid(0, 37, 151, 48, 10)
    val WELL_LANDING = CoordGrid(0, 37, 150, 55, 60)
    val WELL_CLIMB_BACK = CoordGrid(0, 37, 151, 47, 10)

    /* The prison below the well. */
    val LOOSE_MUD = CoordGrid(0, 37, 150, 25, 50)
    val MUD_TUNNEL_EXIT = CoordGrid(0, 37, 150, 24, 46)
    val CELL_TUNNEL = CoordGrid(0, 37, 150, 24, 47)
    val CELL_TUNNEL_INSIDE = CoordGrid(0, 37, 150, 25, 51)

    /** The two ends of the narrow ledge over the chasm; crossing one puts the player on the other. */
    val LEDGE_SOUTH = CoordGrid(1, 37, 150, 6, 39)
    val LEDGE_NORTH = CoordGrid(1, 37, 150, 6, 44)

    /* The unicorn's cave. */
    val UNICORN_CAGE = CoordGrid(0, 37, 150, 3, 3)
    val BOULDER_SPAWN = CoordGrid(0, 37, 150, 6, 6)
    val UNICORN_SPAWN = CoordGrid(0, 37, 150, 4, 4)

    /* The paladins' camp and the well that opens the Doors of Iban. */
    val WELL_OF_DOORS = CoordGrid(0, 37, 151, 5, 54)
    val DOORS_OF_IBAN = listOf(CoordGrid(0, 37, 151, 0, 53), CoordGrid(0, 37, 151, 0, 55))

    /*
     * The two shafts between Iban's lair and the dwarves' camp. The cave mouths sit on the first
     * tile of each pair; the landings are the walkable tile beside the mouth at the other end.
     */
    val LAIR_SHAFT_SOUTH = CoordGrid(1, 33, 71, 38, 1)
    val CAMP_SHAFT_SOUTH = CoordGrid(0, 36, 153, 32, 1)
    val LAIR_SHAFT_SOUTH_LANDING = CoordGrid(1, 33, 71, 38, 2)
    val CAMP_SHAFT_SOUTH_LANDING = CoordGrid(0, 36, 153, 32, 2)

    val LAIR_SHAFT_NORTH = CoordGrid(1, 33, 73, 0, 57)
    val CAMP_SHAFT_NORTH = CoordGrid(0, 36, 154, 0, 59)
    val LAIR_SHAFT_NORTH_LANDING = CoordGrid(1, 33, 73, 1, 57)
    val CAMP_SHAFT_NORTH_LANDING = CoordGrid(0, 36, 154, 1, 59)

    /* Iban's lair. */
    val DWARF_CAMP = CoordGrid(0, 36, 153, 20, 10)
    val BREW_BARREL = CoordGrid(0, 36, 153, 23, 7)
    val IBAN_TOMB_LEFT = CoordGrid(0, 36, 153, 52, 8)
    val IBAN_TOMB_RIGHT = CoordGrid(0, 36, 153, 54, 8)
    val BUCKET_SPAWN = CoordGrid(0, 36, 153, 19, 8)

    /* The upper cavern: Kardia's house, the demons' chest, the cages and the temple. */
    val WITCH_DOOR = CoordGrid(1, 33, 71, 46, 22)
    val WITCH_CHEST = CoordGrid(1, 33, 71, 45, 20)
    val WITCH_WINDOW = CoordGrid(1, 33, 71, 46, 21)
    val WITCH_HOUSE_INSIDE = CoordGrid(1, 33, 71, 45, 21)
    val SHADOW_CHEST = CoordGrid(1, 33, 71, 24, 34)
    val DOVE_CAGE = CoordGrid(1, 33, 73, 22, 30)
    val TEMPLE_DOORS = listOf(CoordGrid(1, 33, 72, 31, 39), CoordGrid(1, 33, 72, 31, 40))
    val TEMPLE_ENTRY = CoordGrid(1, 33, 72, 30, 39)
    val TEMPLE_STEP_BACK = CoordGrid(1, 33, 72, 32, 39)
    val WELL_OF_THE_DAMNED = CoordGrid(1, 33, 72, 24, 39)
    val IBAN_THRONE = CoordGrid(1, 33, 72, 21, 39)

    /* The way out, after the temple comes down. */
    val PASS_EXIT_CAVE = CoordGrid(0, 38, 150, 6, 7)
    val TEMPLE_ESCAPE_LANDING = CoordGrid(0, 38, 150, 11, 10)

    /** The swamp in the first cavern: crossing it sucks the player under and spits them out here. */
    val SWAMP_SPIT_OUT = CoordGrid(0, 38, 151, 47, 15)
}
