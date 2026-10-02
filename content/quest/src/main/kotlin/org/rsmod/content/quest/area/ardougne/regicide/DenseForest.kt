package org.rsmod.content.quest.area.ardougne.regicide

import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid

/**
 * Which way each patch of dense forest in Isafdar leads back towards the Underground Pass.
 *
 * The sides were found by walking the real map from the cave mouth: every trap and log balance is
 * free to cross, and each dense forest costs one step; the side of a forest that takes fewer
 * forests to reach from the cave is its pass side (`RegicideCacheTest` re-derives this table from
 * the cache). Forests in the middle of a band, and the band that sits inside the clearing by the
 * cave, are equally far either way and have no pass side.
 */
object DenseForest {
    enum class Side { WEST, EAST, SOUTH, NORTH }

    enum class Direction { TOWARDS_PASS, AWAY_FROM_PASS, LEVEL }

    /** The dense forest west of the tracker; coming out of it on the far side brings Tyras's guard. */
    val GUARD_TRIGGER = CoordGrid(2232, 3148, 0)

    val PASS_SIDES: Map<CoordGrid, Side?> =
        mapOf(
            CoordGrid(2165, 3189, 0) to null,
            CoordGrid(2173, 3156, 0) to null,
            CoordGrid(2187, 3163, 0) to Side.NORTH,
            CoordGrid(2187, 3166, 0) to Side.NORTH,
            CoordGrid(2187, 3169, 0) to Side.NORTH,
            CoordGrid(2216, 3161, 0) to Side.SOUTH,
            CoordGrid(2216, 3164, 0) to Side.SOUTH,
            CoordGrid(2216, 3167, 0) to Side.SOUTH,
            CoordGrid(2227, 3218, 0) to Side.WEST,
            CoordGrid(2230, 3218, 0) to Side.WEST,
            CoordGrid(2231, 3248, 0) to Side.WEST,
            CoordGrid(2232, 3148, 0) to Side.EAST,
            CoordGrid(2233, 3218, 0) to Side.EAST,
            CoordGrid(2234, 3248, 0) to null,
            CoordGrid(2235, 3148, 0) to Side.EAST,
            CoordGrid(2236, 3218, 0) to Side.EAST,
            CoordGrid(2237, 3248, 0) to Side.EAST,
            CoordGrid(2238, 3148, 0) to Side.EAST,
            CoordGrid(2266, 3191, 0) to Side.WEST,
            CoordGrid(2269, 3191, 0) to null,
            CoordGrid(2272, 3191, 0) to Side.EAST,
            CoordGrid(2278, 3223, 0) to Side.SOUTH,
            CoordGrid(2278, 3226, 0) to null,
            CoordGrid(2278, 3229, 0) to Side.NORTH,
            CoordGrid(2302, 3214, 0) to null,
            CoordGrid(2302, 3217, 0) to null,
            CoordGrid(2302, 3220, 0) to null,
            CoordGrid(2302, 3223, 0) to null,
        )

    fun directionOf(forest: BoundLocInfo, dest: CoordGrid): Direction {
        val side = PASS_SIDES[forest.coords] ?: return Direction.LEVEL
        val minX = forest.coords.x
        val maxX = minX + forest.adjustedWidth - 1
        val minZ = forest.coords.z
        val maxZ = minZ + forest.adjustedLength - 1
        val towards =
            when (side) {
                Side.WEST -> dest.x < minX
                Side.EAST -> dest.x > maxX
                Side.SOUTH -> dest.z < minZ
                Side.NORTH -> dest.z > maxZ
            }
        return if (towards) Direction.TOWARDS_PASS else Direction.AWAY_FROM_PASS
    }
}
