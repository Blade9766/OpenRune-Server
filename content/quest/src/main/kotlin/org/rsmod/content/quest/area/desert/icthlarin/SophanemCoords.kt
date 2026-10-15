package org.rsmod.content.quest.area.desert.icthlarin

import org.rsmod.map.CoordGrid

/** Tiles in and around Sophanem and Klenter's pyramid, read off the cache map. */
object SophanemCoords {
    /** Inside the city's east wall, by the hole in it; where the desert tunnel comes out. */
    val TUNNEL_EXIT = CoordGrid(3320, 2796, 0)

    /** Outside the three-tile-thick east wall, where the hole leads. */
    val HOLE_OUTSIDE = CoordGrid(3324, 2796, 0)

    /** Beside the tunnel rock, north-east of the Wanderer's tent. */
    val TUNNEL_ROCK_OUTSIDE = CoordGrid(3322, 2858, 0)

    /** Just north of the pyramid's cat door, where the hypnotised player comes to. */
    val PYRAMID_DOORSTEP = CoordGrid(3295, 2782, 0)

    /** Where Klenter's shade drifts over to the player who has just woken up. */
    val KLENTER_APPROACH = CoordGrid(3292, 2783, 0)

    /** Beside the ladder at the bottom of the pyramid. */
    val PYRAMID_LANDING = CoordGrid(3278, 9172, 0)

    /** In the entrance corridor, below the secret door a trapped intruder is thrown out of. */
    val SECRET_DOOR_EXIT = CoordGrid(3274, 9171, 0)
    val SECRET_DOOR = CoordGrid(3274, 9172, 0)

    /** South of the great pit, where the flashbacks that start there put the player back. */
    val PIT_SOUTH = CoordGrid(3293, 9193, 0)

    /** North of the great pit, in the corridor that joins the two burial chambers. */
    val PIT_NORTH = CoordGrid(3293, 9197, 0)

    /** Where a failed jump over the pit leaves the player: by the second set of wall crushers. */
    val PIT_FAIL_LANDING = CoordGrid(3305, 9189, 0)

    /** The western chamber's door, and the corridor tile in front of it. */
    val WEST_DOOR = CoordGrid(3280, 9199, 0)
    val WEST_DOOR_OUTSIDE = CoordGrid(3280, 9200, 0)

    /** The eastern (ceremonial) chamber's door, and the corridor tile in front of it. */
    val EAST_DOOR = CoordGrid(3306, 9199, 0)
    val EAST_DOOR_OUTSIDE = CoordGrid(3306, 9200, 0)

    /** Inside the western chamber, in front of the canopic jar shelf. */
    val JAR_SHELF_FRONT = CoordGrid(3285, 9195, 0)

    /** Ceremony positions around the table in the eastern chamber. */
    val CEREMONY_PLAYER = CoordGrid(3306, 9198, 0)
    val CEREMONY_HIGH_PRIEST = CoordGrid(3306, 9196, 0)
    val CEREMONY_WANDERER = CoordGrid(3304, 9197, 0)
    val CEREMONY_POSSESSED = CoordGrid(3308, 9196, 0)
    val CEREMONY_CAMERA = CoordGrid(3306, 9192, 0)

    /** The corridor outside the pyramid entrance where Icthlarin stops the fleeing thief. */
    val ICTHLARIN_PLAYER = CoordGrid(3279, 9171, 0)
    val ICTHLARIN_SPOT = CoordGrid(3283, 9171, 0)
    val ICTHLARIN_CAMERA = CoordGrid(3281, 9167, 0)
    val ICTHLARIN_LOOK = CoordGrid(3281, 9171, 0)

    fun inPyramid(coords: CoordGrid): Boolean =
        coords.level == 0 && coords.x in 3272..3313 && coords.z in 9168..9206

    fun inWestChamber(coords: CoordGrid): Boolean =
        coords.level == 0 && coords.x in 3276..3287 && coords.z in 9192..9199

    fun inEastChamber(coords: CoordGrid): Boolean =
        coords.level == 0 && coords.x in 3300..3311 && coords.z in 9192..9199

    /** North of the great pit: the chamber corridor and both chambers. */
    fun northOfPit(coords: CoordGrid): Boolean =
        inPyramid(coords) && (coords.z >= 9196 || inWestChamber(coords) || inEastChamber(coords))

    fun inSophanem(coords: CoordGrid): Boolean =
        coords.level in 0..3 && coords.x in 3262..3322 && coords.z in 2751..2809

    /** The four-wide hallway whose floor hides pit traps. */
    fun inTrapHallway(coords: CoordGrid): Boolean =
        coords.level == 0 && coords.x in 3308..3311 && coords.z in 9173..9187

    /**
     * The hidden pit traps. Walking up the middle of the hallway is safe; the traps sit along the
     * walls and swap sides as the hallway goes north, which is why the wiki advice is to hug one
     * wall, cross over, and hug the other.
     */
    val PIT_TRAPS: Set<CoordGrid> =
        buildSet {
            for (z in 9174..9176) add(CoordGrid(3311, z, 0))
            for (z in 9178..9180) add(CoordGrid(3308, z, 0))
            for (z in 9182..9184) add(CoordGrid(3311, z, 0))
            for (z in 9186..9187) add(CoordGrid(3308, z, 0))
        }

    /** The wall crushers in the two crusher corridors; each slams down on its own tile. */
    val CRUSHERS: List<CoordGrid> =
        listOf(
            CoordGrid(3288, 9171, 0),
            CoordGrid(3289, 9171, 0),
            CoordGrid(3290, 9170, 0),
            CoordGrid(3291, 9170, 0),
            CoordGrid(3292, 9171, 0),
            CoordGrid(3293, 9171, 0),
            CoordGrid(3294, 9170, 0),
            CoordGrid(3295, 9170, 0),
            CoordGrid(3296, 9188, 0),
            CoordGrid(3297, 9188, 0),
            CoordGrid(3299, 9185, 0),
            CoordGrid(3299, 9189, 0),
            CoordGrid(3300, 9185, 0),
            CoordGrid(3300, 9189, 0),
            CoordGrid(3302, 9184, 0),
            CoordGrid(3302, 9188, 0),
            CoordGrid(3303, 9184, 0),
            CoordGrid(3303, 9188, 0),
        )

    /** Where scarabs can burst out of the floor: the long corridors between the traps. */
    fun scarabGround(coords: CoordGrid): Boolean =
        coords.level == 0 &&
            ((coords.z in 9170..9171 && coords.x in 3296..3311) ||
                (coords.z in 9188..9189 && coords.x in 3276..3295))
}
