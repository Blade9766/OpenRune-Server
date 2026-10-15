package org.rsmod.content.quest.area.desert.touristtrap

import org.rsmod.map.CoordGrid

/**
 * The tiles the Tourist Trap scripts work from.
 *
 * The Desert Mining Camp compound is map square (51, 47); its mine is the whole of (51, 147). The
 * mine is several unconnected caverns joined only by the cave passage and the mine carts, which
 * is why so much of the quest is a teleport from one named tile to another.
 */
internal object TouristTrapCoords {
    /* The compound, including Captain Siad's office upstairs. */
    const val CAMP_MIN_X = 3274
    const val CAMP_MAX_X = 3306
    const val CAMP_MIN_Z = 3011
    const val CAMP_MAX_Z = 3043

    /* The mine below it. */
    const val MINE_MIN_X = 3264
    const val MINE_MAX_X = 3327
    const val MINE_MIN_Z = 9408
    const val MINE_MAX_Z = 9471

    /** The compound gate is the east edge of x 3273; the camp side is everything east of it. */
    const val CAMP_GATE_X = 3273

    /** The cell in the northern building, and the punishment pit in the mine. */
    val SURFACE_CELL = CoordGrid(3285, 3034)
    val UNDERGROUND_CELL = CoordGrid(3286, 9437)

    /** The prison mine's gate is the east edge of x 3292; the prisoners' side is west of it. */
    const val PRISON_GATE_X = 3292

    /** The wrought iron gates are the north edge of z 9448; the good ore is north of them. */
    const val WROUGHT_GATE_Z = 9448

    /** Where the mercenaries leave anyone who has annoyed them once too often. */
    val DESERT_DUMPS =
        listOf(CoordGrid(3238, 3091), CoordGrid(3209, 3119), CoordGrid(3229, 3046))

    /* The big wooden doors: the compound side, and the tunnel they open onto. */
    val MINE_DOORS_SURFACE = CoordGrid(3301, 3036)
    val MINE_DOORS_UNDERGROUND = CoordGrid(3278, 9427)

    /* Either end of the dark passage past the pineapple guards. */
    val CAVE_WEST = CoordGrid(3278, 9415)
    val CAVE_EAST = CoordGrid(3286, 9415)

    /* The two mine carts: by the barrels and the lift, and out in the far cavern. */
    val BARREL_ROOM_CART = CoordGrid(3303, 9417)
    val FAR_CART = CoordGrid(3318, 9430)
    val FAR_CART_LANDING = CoordGrid(3319, 9431)
    val BARREL_ROOM_CART_LANDING = CoordGrid(3302, 9417)

    /** Ana's barrel turns up among the barrels within this many tiles of the barrel-room cart. */
    const val CART_BARREL_RADIUS = 3

    /** Where the wooden cart puts the player down, west of the compound gate. */
    val WAGON_DROP_OFF = CoordGrid(3258, 3029)

    /* The escape from the surface cell: the barred window, the rock, and the cliff above it. */
    val CELL_WINDOW_INSIDE = CoordGrid(3284, 3034)
    val CELL_WINDOW_OUTSIDE = CoordGrid(3283, 3034)
    val ROCK_EAST = CoordGrid(3282, 3037)
    val ROCK_WEST = CoordGrid(3279, 3037)
    val CLIFF_FOOT = CoordGrid(3279, 3037)
    val CLIFF_TOP = CoordGrid(3277, 3037)
    val CLIFF_EDGE = CoordGrid(3274, 3039)
    val CLIFF_BOTTOM = CoordGrid(3269, 3039)

    /** The prison door of the surface cell is the east edge of x 3287. */
    const val CELL_DOOR_X = 3287

    /* The tables in the northern building. */
    val BOWL_TABLE = CoordGrid(3291, 3034, 0)
    val SIAD_DESK = CoordGrid(3290, 3033, 1)

    /* The Bedabin anvil tent: the door is the south edge of z 3046. */
    val TENT_OUTSIDE = CoordGrid(3169, 3045)
    val TENT_INSIDE = CoordGrid(3169, 3046)
    const val TENT_DOOR_Z = 3046

    /** Already locked up: the surface cell, or the punishment pit west of its gate. */
    fun inCell(coords: CoordGrid): Boolean =
        (coords.level == 0 && coords.x in 3284..CELL_DOOR_X && coords.z in 3031..3036) ||
            (inMine(coords) && coords.x in 3283..PRISON_GATE_X && coords.z in 9427..9453)

    fun inCamp(coords: CoordGrid): Boolean =
        coords.x in CAMP_MIN_X..CAMP_MAX_X && coords.z in CAMP_MIN_Z..CAMP_MAX_Z

    fun inMine(coords: CoordGrid): Boolean =
        coords.level == 0 && coords.x in MINE_MIN_X..MINE_MAX_X && coords.z in MINE_MIN_Z..MINE_MAX_Z
}
