package org.rsmod.content.quest.area.ardougne.undergroundpass

import org.rsmod.map.CoordGrid

/**
 * Every tile the quest names. They are taken from the cache's own loc spawns and checked against
 * the map's collision flags, so each landing tile below is one the routefinder can stand on.
 */
internal object UpassCoords {
    /* The cave mouth in West Ardougne and the far end of the pass it opens onto. */
    val PASS_ARRIVAL = CoordGrid(0, 38, 151, 62, 52)
    val CAVE_EXIT_LANDING = CoordGrid(0, 38, 51, 4, 51)

    /** The crevasse the swamp and a failed rope swing both drop the player into. */
    val CREVASSE_FLOOR = CoordGrid(0, 38, 150, 53, 49)
    val ROCKPILE_TOP = CoordGrid(0, 38, 151, 50, 51)

    /* Koftik's fire and the bridge he cannot cross. */
    val KOFTIK_FIRE = CoordGrid(0, 38, 151, 19, 51)
    val GUIDE_ROPE = CoordGrid(0, 38, 151, 11, 54)
    val BRIDGE = CoordGrid(0, 38, 151, 11, 52)
    val BRIDGE_WEST = CoordGrid(0, 38, 151, 10, 52)
    val BRIDGE_EAST = CoordGrid(0, 38, 151, 15, 52)
    const val ROPE_SHOT_MIN_Z = 9720

    /** The walk round the chasm from where the rope is shot to the east end of the bridge. */
    val ROPE_SHOT_WALK =
        listOf(
            CoordGrid(0, 38, 151, 22, 59),
            CoordGrid(0, 38, 151, 23, 59),
            CoordGrid(0, 38, 151, 23, 53),
            CoordGrid(0, 38, 151, 22, 52),
            CoordGrid(0, 38, 151, 15, 52),
        )
    val BRIDGE_LEVER_STAND = CoordGrid(0, 38, 151, 7, 51)

    /* The rope swing over the pit: out from the rock on the west lip, back on the rope to the east. */
    val SWING_ROCK = CoordGrid(0, 38, 151, 28, 35)
    val SWING_START = CoordGrid(0, 38, 151, 30, 35)
    val SWING_LANDING = CoordGrid(0, 38, 151, 34, 35)
    val SWING_BACK_START = CoordGrid(0, 38, 151, 33, 28)
    val SWING_BACK_LANDING = CoordGrid(0, 38, 151, 28, 28)

    /* The collapsing grid: ten columns west from the corridor, ten rows deep. */
    const val GRID_WEST_X = 2467
    const val GRID_EAST_X = 2476
    const val GRID_SOUTH_Z = 9673
    const val GRID_NORTH_Z = 9682
    const val GRID_SIZE = 10

    val PORTCULLIS =
        listOf(
            CoordGrid(0, 38, 151, 33, 10),
            CoordGrid(0, 38, 151, 33, 12),
            CoordGrid(0, 38, 151, 33, 14),
            CoordGrid(0, 38, 151, 33, 16),
        )

    /** From the lever on the grid side, north and under the portcullis to the furnace side. */
    val PORTCULLIS_WALK =
        listOf(
            CoordGrid(0, 38, 151, 34, 9),
            CoordGrid(0, 38, 151, 34, 10),
            CoordGrid(0, 38, 151, 34, 11),
            CoordGrid(0, 38, 151, 33, 12),
            CoordGrid(0, 38, 151, 32, 12),
            CoordGrid(0, 38, 151, 32, 13),
        )

    /** The spike pit under the grid, and the ledge its hand holds climb back up to. */
    val GRID_PIT = CoordGrid(0, 37, 149, 27, 23)
    val GRID_CLIMB_OUT = CoordGrid(0, 38, 151, 45, 13)

    /* The furnace the orbs burn in, and the well the pass carries on below. */
    val WELL_OF_IBAN = CoordGrid(0, 37, 151, 48, 10)
    val WELL_BOTTOM = CoordGrid(0, 37, 150, 55, 60)
    val MUDPILE_TOP = CoordGrid(0, 37, 151, 50, 10)
    val ORB_LOG_TRAP = CoordGrid(0, 37, 151, 12, 3)

    /* The prison below the well. */
    val MUD_TUNNEL_EXIT = CoordGrid(0, 37, 150, 24, 46)
    val CELL_TUNNEL_INSIDE = CoordGrid(0, 37, 150, 25, 51)

    /**
     * The narrow ledge over the rat pit. Its locs are stored on level 1 over bridged tiles, so they
     * sit on level 0; the player sidesteps along x 2374 between the two ends.
     */
    val LEDGE_SOUTH_END = CoordGrid(0, 37, 150, 6, 38)
    const val LEDGE_X = 2374

    /*
     * The unicorn's cave is mapped twice: once with the unicorn caged and the boulder on the shelf
     * above it, and again 25 tiles west with the cage smashed. The tunnels into it pick the copy
     * that matches the player's progress.
     */
    val UNICORN_TUNNEL_NORTH = CoordGrid(0, 37, 151, 3, 2)
    val UNICORN_CAVE_ALIVE = CoordGrid(0, 37, 150, 33, 10)
    val UNICORN_CAVE_DEAD = CoordGrid(0, 37, 150, 8, 10)
    const val UNICORN_COPY_OFFSET = 25

    /* The paladins' camp and the well that opens the Doors of Iban. */
    val WELL_OF_DOORS = CoordGrid(0, 37, 151, 5, 54)

    /*
     * Nothing is mapped behind the Doors of Iban in the pass: the same doors stand again at the
     * edge of Iban's lair, and going through one pair comes out beside the other.
     */
    val DOORS_PASS_SIDE = CoordGrid(0, 37, 151, 2, 55)
    val DOORS_LAIR_SIDE = CoordGrid(1, 33, 73, 61, 53)
    const val PASS_DOORS_MIN_Z = 6400

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

    /** Where a slip off one of the lair's broken bridges lands, in the caverns below. */
    val LAIR_FALLS = listOf(CoordGrid(0, 36, 153, 31, 29), CoordGrid(0, 36, 154, 29, 10))

    /* The dwarves' camp and Iban's tomb, whose fire is a ring of walls round the slab. */
    val IBAN_TOMB_LEFT = CoordGrid(0, 36, 153, 52, 8)
    val IBAN_TOMB_RIGHT = CoordGrid(0, 36, 153, 54, 8)

    /* The upper cavern: Kardia's house, the demons' chest, the cages and the temple. */
    val WITCH_HIDING_SPOT = CoordGrid(1, 33, 71, 45, 24)
    val IBAN_THRONE = CoordGrid(1, 33, 72, 21, 39)
    val WELL_OF_THE_DAMNED = CoordGrid(1, 33, 72, 24, 39)

    /** The hall floor Iban's bolts rain down on: six columns by twelve rows east of the well. */
    val TEMPLE_BOLT_ORIGIN = CoordGrid(1, 33, 72, 24, 31)
    const val TEMPLE_BOLT_WIDTH = 6
    const val TEMPLE_BOLT_LENGTH = 12

    /** The rocks that come down on the hall once Iban is dead. */
    val TEMPLE_ROCKFALLS =
        listOf(
            CoordGrid(1, 33, 72, 25, 38),
            CoordGrid(1, 33, 72, 27, 43),
            CoordGrid(1, 33, 72, 23, 44),
            CoordGrid(1, 33, 72, 28, 36),
        )

    /* The way out, after the temple comes down, and where Koftik leads the player back to. */
    val TEMPLE_ESCAPE_LANDING = CoordGrid(0, 38, 150, 8, 7)
    val KOFTIK_LEADS_OUT = CoordGrid(0, 38, 151, 49, 53)
}
