package org.rsmod.content.quest.area.desert.shadowofthestorm

import org.rsmod.map.CoordGrid

/**
 * Thammaron's throne room, level 2 of the temple beneath Uzer.
 *
 * The cult meets on the ring of marked floor in the middle of the room. [CIRCLE_CENTRE] is the
 * diamond of patterned tiles the demon rises through; the five points around it are where the
 * casters stand, and which one is left free for the player depends on who else is casting.
 */
internal object ThroneRoom {
    /** Where the demon door in the temple corridor puts the player, at the room's south end. */
    val ARRIVAL = CoordGrid(2720, 4885, 2)

    /** The portal back to the corridor, in the throne room's south wall. */
    val EXIT_PORTAL = CoordGrid(2719, 4883, 2)

    /** In the corridor below, in front of the demon door. */
    val CORRIDOR = CoordGrid(2721, 4910, 0)

    val CIRCLE_CENTRE = CoordGrid(2720, 4900, 2)

    val CIRCLE_NORTH = CoordGrid(2720, 4903, 2)
    val CIRCLE_NORTH_EAST = CoordGrid(2722, 4902, 2)
    val CIRCLE_SOUTH_EAST = CoordGrid(2722, 4898, 2)
    val CIRCLE_SOUTH_WEST = CoordGrid(2718, 4898, 2)
    val CIRCLE_NORTH_WEST = CoordGrid(2718, 4902, 2)

    /** Agrith-Naar is 3x3; this puts him over the marked diamond. */
    val DEMON_TILE = CoordGrid(2719, 4899, 2)

    /** Denath holds court at the foot of the throne until he calls the circle. */
    val DENATH_TILE = CoordGrid(2720, 4912, 2)

    val JENNIFER_TILE = CoordGrid(2723, 4901, 2)
    val MATTHEW_TILE = CoordGrid(2727, 4897, 2)
    val PATRICK_TILE = CoordGrid(2716, 4897, 2)
    val TANYA_TILE = CoordGrid(2717, 4906, 2)
    val ERIC_TILE = CoordGrid(2723, 4906, 2)
    val DAVE_TILE = CoordGrid(2720, 4907, 2)

    /** Where the clay golem waits once it has agreed to hold a point of the circle. */
    val GOLEM_TILE = CoordGrid(2720, 4904, 2)

    val CAMERA_FROM = CoordGrid(2720, 4893, 2)
    val CAMERA_AT = CoordGrid(2720, 4900, 2)

    const val CAMERA_HEIGHT = 900
    const val LOOK_HEIGHT = 240
    const val CAMERA_RATE = 100
}

/** The temple passage the cult flees into once the first summoning goes wrong. */
internal object TemplePassage {
    /** Tanya, run down by the temple's ghosts. */
    val TANYA_TILE = CoordGrid(2721, 4905, 0)
    val GHOST_TILE = CoordGrid(2722, 4906, 0)

    /** Eric, under the rubble that came down on him; `loc.agrith_wizard_rubble` sits here. */
    val RUBBLE_TILE = CoordGrid(2724, 4897, 0)

    /** Evil Dave, too frightened to go any further. */
    val DAVE_TILE = CoordGrid(2723, 4897, 0)
}
