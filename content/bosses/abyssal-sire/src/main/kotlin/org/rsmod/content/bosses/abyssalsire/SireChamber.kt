package org.rsmod.content.bosses.abyssalsire

import org.rsmod.map.CoordGrid

/**
 * One of the Abyssal Nexus's four Sire chambers. Every position is relative to the Sire's throne
 * tile ([throne], its south-west corner). In phase 2 the Sire stands 10 tiles south of it, with the
 * wiki's "row 1" melee tile just below; in phase 3 it roots 18 tiles south, with "row 2" (where the
 * explosion pulls the player) below it and "row 3", the escape tile, two further south. The tentacles
 * and respiratory systems are the map spawns at [tentacles] and [lungs].
 */
enum class SireChamber(val throne: CoordGrid, val lungs: List<CoordGrid>, val tentacles: List<CoordGrid>) {
    NorthWest(
        throne = CoordGrid(2977, 4855),
        lungs = listOf(CoordGrid(2967, 4834), CoordGrid(2964, 4844), CoordGrid(2995, 4833), CoordGrid(2992, 4843)),
        tentacles = tentacles(2967, 4826),
    ),
    NorthEast(
        throne = CoordGrid(3102, 4855),
        lungs = listOf(CoordGrid(3092, 4834), CoordGrid(3089, 4844), CoordGrid(3120, 4833), CoordGrid(3117, 4843)),
        tentacles = tentacles(3092, 4826),
    ),
    SouthWest(
        throne = CoordGrid(2967, 4791),
        lungs = listOf(CoordGrid(2985, 4769), CoordGrid(2982, 4779), CoordGrid(2957, 4770), CoordGrid(2954, 4780)),
        tentacles = tentacles(2957, 4762),
    ),
    SouthEast(
        throne = CoordGrid(3107, 4791),
        lungs = listOf(CoordGrid(3125, 4769), CoordGrid(3122, 4779), CoordGrid(3097, 4770), CoordGrid(3094, 4780)),
        tentacles = tentacles(3097, 4762),
    ),
    ;

    val meleeSpot: CoordGrid
        get() = throne.translateZ(-MELEE_SPOT_DROP)

    val centreSpot: CoordGrid
        get() = throne.translateZ(-CENTRE_SPOT_DROP)

    /** "Row 1": where a player meleeing the phase 2 Sire stands. */
    val rowOne: CoordGrid
        get() = meleeSpot.translate(ROW_X_OFFSET, -1)

    /** "Row 2" (west tile): where the explosion teleports the player, right below the Sire. */
    val rowTwo: CoordGrid
        get() = centreSpot.translate(ROW_X_OFFSET, -1)

    /** "Row 3": two tiles south of row 2, out of the explosion. */
    val rowThree: CoordGrid
        get() = rowTwo.translateZ(-2)

    /**
     * The open ground just south of the throne alcove's lip, where phase 1 spawns land: the alcove
     * itself is walled off from the arena.
     */
    val alcoveFront: List<CoordGrid>
        get() =
            (ALCOVE_FRONT_MIN_DX..ALCOVE_FRONT_MAX_DX).flatMap { dx ->
                (ALCOVE_FRONT_NEAR_DROP..ALCOVE_FRONT_FAR_DROP).map { drop -> throne.translate(dx, -drop) }
            }

    fun contains(coords: CoordGrid): Boolean =
        coords.level == throne.level &&
            coords.x in throne.x - CHAMBER_HALF_WIDTH..throne.x + CHAMBER_HALF_WIDTH &&
            coords.z in throne.z - CHAMBER_DEPTH..throne.z + SIRE_SIZE

    companion object {
        const val SIRE_SIZE = 6
        const val TENTACLE_SIZE = 9
        private const val MELEE_SPOT_DROP = 10
        private const val CENTRE_SPOT_DROP = 18
        private const val ROW_X_OFFSET = 2
        private const val CHAMBER_HALF_WIDTH = 32
        private const val CHAMBER_DEPTH = 36
        private const val ALCOVE_FRONT_MIN_DX = -1
        private const val ALCOVE_FRONT_MAX_DX = 6
        private const val ALCOVE_FRONT_NEAR_DROP = 7
        private const val ALCOVE_FRONT_FAR_DROP = 10

        fun byThrone(coords: CoordGrid): SireChamber? = entries.firstOrNull { it.throne == coords }

        fun containing(coords: CoordGrid): SireChamber? = entries.firstOrNull { it.contains(coords) }
    }
}

/**
 * Every chamber lays out its six tentacles the same way relative to its north-west one: two at the
 * bottom, two upright either side of the centre lane and two below the throne.
 */
private fun tentacles(x: Int, z: Int): List<CoordGrid> =
    listOf(
        CoordGrid(x + 1, z),
        CoordGrid(x + 18, z),
        CoordGrid(x + 3, z + 9),
        CoordGrid(x + 15, z + 9),
        CoordGrid(x, z + 18),
        CoordGrid(x + 17, z + 18),
    )
