package org.rsmod.content.generic.locs.passages

import kotlin.math.absoluteValue
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.flag.BlockAccessFlag
import org.rsmod.routefinder.util.Rotations

/** What a click on a passage-like loc should do, worked out from the loc's name, op and shape. */
enum class PassageAction {
    OpenDoor,
    CloseDoor,
    OpenTrapdoor,
    ClimbUp,
    ClimbDown,
    ClimbEither,
    Enter,
    ClimbOver,
}

/**
 * Pure rules for the generic passage handling: which locs count as doors, ladders, caves and
 * stiles, where climbing leads, and which side of an obstacle a player ends up on.
 */
object Passages {
    private val DOOR_NAMES =
        setOf(
            "Door",
            "Doors",
            "Gate",
            "Gates",
            "Large door",
            "Big door",
            "Wooden door",
            "Metal door",
            "Iron door",
            "Cell door",
            "Prison door",
            "Wooden gate",
            "Iron gate",
        )

    private val CLIMB_NAMES =
        setOf(
            "Ladder",
            "Staircase",
            "Stairs",
            "Rope",
            "Trapdoor",
            "Hole",
            "Exit",
            "Steps",
            "Spiral staircase",
            "Rope ladder",
            "Vine",
            "Climbing rocks",
        )

    private val ENTER_NAMES =
        setOf(
            "Cave",
            "Cave entrance",
            "Cave exit",
            "Tunnel",
            "Tunnel entrance",
            "Hole",
            "Passage",
            "Passageway",
            "Crevice",
            "Entrance",
            "Exit",
            "Opening",
            "Dungeon entrance",
            "Cavern",
            "Cavern entrance",
            "Mine entrance",
            "Crack",
            "Gap",
        )

    private val CLIMB_OVER_NAMES = setOf("Stile", "Fence", "Low fence", "Broken fence", "Wall")

    private val ENTER_OPS =
        setOf(
            "Enter",
            "Exit",
            "Leave",
            "Pass",
            "Pass-through",
            "Squeeze-through",
            "Crawl",
            "Crawl-through",
            "Go-through",
            "Walk-through",
        )

    private val WALL_SHAPES =
        setOf(
            LocShape.WallStraight,
            LocShape.WallDiagonalCorner,
            LocShape.WallL,
            LocShape.WallSquareCorner,
            LocShape.WallDiagonal,
        )

    /** The z coordinate from which the map is the underground copy of the surface above it. */
    const val UNDERGROUND_Z = 6400

    fun classify(name: String, op: String, shape: LocShape): PassageAction? {
        val isWall = shape in WALL_SHAPES
        return when (op) {
            "Open" ->
                when {
                    name == "Trapdoor" -> PassageAction.OpenTrapdoor
                    name in DOOR_NAMES || isWall -> PassageAction.OpenDoor
                    else -> null
                }
            "Close" -> if (name in DOOR_NAMES || isWall) PassageAction.CloseDoor else null
            "Climb-up",
            "Walk-up" -> if (name in CLIMB_NAMES) PassageAction.ClimbUp else null
            "Climb-down",
            "Walk-down" -> if (name in CLIMB_NAMES) PassageAction.ClimbDown else null
            "Climb" -> if (name in CLIMB_NAMES) PassageAction.ClimbEither else null
            "Climb-over" -> if (name in CLIMB_OVER_NAMES) PassageAction.ClimbOver else null
            in ENTER_OPS ->
                if (name in ENTER_NAMES || name in DOOR_NAMES) PassageAction.Enter else null
            else -> null
        }
    }

    /**
     * Where climbing up or down from [from] should land, before the tile is checked for space.
     * Surface dungeons live 6400 tiles north of the ground they sit under, so a ground-level
     * climb-down goes there and a dungeon climb-up comes back; anything else is one level.
     */
    fun climbDestination(from: CoordGrid, up: Boolean): CoordGrid? =
        climbPlanes(from, up).firstOrNull()

    /**
     * Every level a climb from [from] might reach, likeliest first: [climbDestination], then the
     * levels beyond it. Some dungeons leave a level empty between floors, and a dungeon flight
     * can go up a level rather than out to the surface, so a climb tries each in turn until one
     * has floor.
     */
    fun climbPlanes(from: CoordGrid, up: Boolean): List<CoordGrid> {
        val underground = from.z >= UNDERGROUND_Z
        val planes = mutableListOf<CoordGrid>()
        if (up) {
            if (underground && from.level == 0) {
                planes += from.translateZ(-UNDERGROUND_Z)
            }
            for (level in from.level + 1 until CoordGrid.LEVEL_COUNT) {
                planes += CoordGrid(from.x, from.z, level)
            }
        } else {
            for (level in from.level - 1 downTo 0) {
                planes += CoordGrid(from.x, from.z, level)
            }
            if (!underground && from.level == 0) {
                planes += from.translateZ(UNDERGROUND_Z)
            }
        }
        return planes
    }

    /** The other side of a cave or tunnel mouth: the matching tile above or below ground. */
    fun enterDestination(from: CoordGrid): CoordGrid =
        if (from.z >= UNDERGROUND_Z) {
            from.translateZ(-UNDERGROUND_Z)
        } else {
            from.translateZ(UNDERGROUND_Z)
        }

    /**
     * The tile just past [loc] from where [from] stands, or `null` if [from] is not beside it.
     * A stile or ditch is crossed along whichever axis the player is outside of it on.
     */
    fun farSide(loc: BoundLocInfo, from: CoordGrid): CoordGrid? {
        val minX = loc.x
        val maxX = loc.x + loc.adjustedWidth - 1
        val minZ = loc.z
        val maxZ = loc.z + loc.adjustedLength - 1
        val x =
            when {
                from.x < minX -> maxX + 1
                from.x > maxX -> minX - 1
                else -> from.x
            }
        val z =
            when {
                from.z < minZ -> maxZ + 1
                from.z > maxZ -> minZ - 1
                else -> from.z
            }
        if (x == from.x && z == from.z) {
            return null
        }
        return CoordGrid(x, z, from.level)
    }

    /**
     * Candidate landing tiles around [dest], nearest first, so a player climbing onto a tile a
     * ladder occupies is put beside it rather than inside it.
     */
    fun landingCandidates(dest: CoordGrid, radius: Int = 2): List<CoordGrid> {
        val tiles = mutableListOf<CoordGrid>()
        for (dz in -radius..radius) {
            for (dx in -radius..radius) {
                tiles += tileOrNull(dest.x + dx, dest.z + dz, dest.level) ?: continue
            }
        }
        return tiles.sortedWith(
            compareBy(
                { it.chebyshevDistance(dest) },
                { (it.x - dest.x).absoluteValue + (it.z - dest.z).absoluteValue },
                { it.z },
                { it.x },
            )
        )
    }

    /* Staircases */

    /** How far past the end of a staircase to look for floor before giving up. */
    const val MAX_EXIT_STEPS = 4

    /** [CoordGrid] for a tile that may lie off the edge of the map, or `null` if it does. */
    private fun tileOrNull(x: Int, z: Int, level: Int): CoordGrid? =
        if (x in 0..CoordGrid.X_BIT_MASK && z in 0..CoordGrid.Z_BIT_MASK) {
            CoordGrid(x, z, level)
        } else {
            null
        }

    private fun CoordGrid.step(side: Direction, count: Int): CoordGrid? =
        tileOrNull(x + side.xOff * count, z + side.zOff * count, level)

    /**
     * Whether a loc named [name] with [ops] is the other end of a flight of stairs climbed in
     * direction [up]: something climbable whose op goes the opposite way, or either way.
     */
    fun isClimbCounterpart(name: String, ops: List<String?>, up: Boolean): Boolean =
        name in CLIMB_NAMES && ops.any { it != null && counterpartOpRank(it, up) > 0 }

    /**
     * How well an op on a candidate counterpart matches a climb in direction [up]: 2 for the
     * explicit opposite op, 1 for a "Climb" that goes either way, 0 for anything else.
     */
    fun counterpartOpRank(op: String, up: Boolean): Int =
        when (op) {
            "Climb-down",
            "Walk-down" -> if (up) 2 else 0
            "Climb-up",
            "Walk-up" -> if (up) 0 else 2
            "Climb" -> 1
            else -> 0
        }

    /**
     * The one side a loc can be approached from, or `null` when it is open on several sides.
     * Staircases block every side but the foot of the stairs, so this is where the player stands
     * to climb them and where a player coming the other way is put down.
     */
    fun openSide(forceApproachFlags: Int, angle: LocAngle): Direction? {
        val blocked = Rotations.rotate(angle.id, forceApproachFlags)
        return when (blocked.inv() and BlockAccessFlag.DIRECTIONS) {
            BlockAccessFlag.NORTH -> Direction.North
            BlockAccessFlag.EAST -> Direction.East
            BlockAccessFlag.SOUTH -> Direction.South
            BlockAccessFlag.WEST -> Direction.West
            else -> null
        }
    }

    /**
     * Which side of [loc] the tile [from] lies on. A tile off a corner counts as being on the
     * short end, since stairs run along their long axis; off the corner of a square loc it is
     * nowhere in particular and this returns `null`, as it does for a tile inside the loc.
     */
    fun sideOf(loc: BoundLocInfo, from: CoordGrid): Direction? {
        val minX = loc.x
        val maxX = loc.x + loc.adjustedWidth - 1
        val minZ = loc.z
        val maxZ = loc.z + loc.adjustedLength - 1
        val alongX =
            when {
                from.x < minX -> Direction.West
                from.x > maxX -> Direction.East
                else -> null
            }
        val alongZ =
            when {
                from.z < minZ -> Direction.South
                from.z > maxZ -> Direction.North
                else -> null
            }
        return when {
            alongX == null -> alongZ
            alongZ == null -> alongX
            loc.adjustedLength > loc.adjustedWidth -> alongZ
            loc.adjustedWidth > loc.adjustedLength -> alongX
            else -> null
        }
    }

    /** The tiles hugging [loc] on its [side], on the loc's own level, in map order. */
    fun approachTiles(loc: BoundLocInfo, side: Direction): List<CoordGrid> {
        val maxX = loc.x + loc.adjustedWidth - 1
        val maxZ = loc.z + loc.adjustedLength - 1
        return when (side) {
            Direction.South -> (loc.x..maxX).mapNotNull { tileOrNull(it, loc.z - 1, loc.level) }
            Direction.North -> (loc.x..maxX).mapNotNull { tileOrNull(it, maxZ + 1, loc.level) }
            Direction.West -> (loc.z..maxZ).mapNotNull { tileOrNull(loc.x - 1, it, loc.level) }
            Direction.East -> (loc.z..maxZ).mapNotNull { tileOrNull(maxX + 1, it, loc.level) }
            else -> emptyList()
        }
    }

    fun opposite(side: Direction): Direction =
        when (side) {
            Direction.North -> Direction.South
            Direction.South -> Direction.North
            Direction.East -> Direction.West
            Direction.West -> Direction.East
            Direction.NorthEast -> Direction.SouthWest
            Direction.SouthWest -> Direction.NorthEast
            Direction.NorthWest -> Direction.SouthEast
            Direction.SouthEast -> Direction.NorthWest
        }

    /**
     * Where a player who climbed [counterpart] from its far end is put down: the tile at the
     * foot of it nearest to [target] (the climber's own tile carried to the new level), or
     * failing that the first free tile further out from the foot, since the lower flight of a
     * pair often reaches a tile further than the upper one. `null` when the counterpart can be
     * approached from several sides, or nothing near its foot is free.
     */
    fun counterpartLanding(
        counterpart: BoundLocInfo,
        target: CoordGrid,
        walkable: (CoordGrid) -> Boolean,
    ): CoordGrid? {
        val side = openSide(counterpart.forceApproachFlags, counterpart.angle) ?: return null
        val foot =
            approachTiles(counterpart, side).sortedBy {
                it.chebyshevDistance(target) * 4 + (it.x - target.x).absoluteValue
            }
        foot.firstOrNull(walkable)?.let {
            return it
        }
        val nearest = foot.firstOrNull() ?: return null
        for (step in 1..MAX_EXIT_STEPS) {
            val tile = nearest.step(side, step) ?: return null
            if (walkable(tile)) {
                return tile
            }
        }
        return null
    }

    /**
     * Where climbing [loc] from [from] comes out when nothing on the other level says
     * otherwise: straight through the stairs to the tile past their far end, on the same lane
     * the player climbed in, moved to [plane] (the loc's own origin carried to the new level).
     * Walks further out past a blocked tile, since the other end of the pair usually stands
     * there. With no floor past the far end at all, the flight doubles back and the player comes
     * out past the end they climbed from instead. `null` when it cannot tell which end the
     * player is at, or finds no floor either way.
     */
    fun exitLanding(
        loc: BoundLocInfo,
        from: CoordGrid,
        plane: CoordGrid,
        walkable: (CoordGrid) -> Boolean,
    ): CoordGrid? {
        val side = openSide(loc.forceApproachFlags, loc.angle) ?: sideOf(loc, from) ?: return null
        return pastEnd(loc, from, plane, opposite(side), walkable)
            ?: pastEnd(loc, from, plane, side, walkable)
    }

    /**
     * The first free tile out from [loc]'s [end], on [plane], on the lane [from] stands in,
     * looking up to [MAX_EXIT_STEPS] tiles further out.
     */
    private fun pastEnd(
        loc: BoundLocInfo,
        from: CoordGrid,
        plane: CoordGrid,
        end: Direction,
        walkable: (CoordGrid) -> Boolean,
    ): CoordGrid? {
        val maxX = loc.x + loc.adjustedWidth - 1
        val maxZ = loc.z + loc.adjustedLength - 1
        val (laneX, laneZ) =
            when (end) {
                Direction.North -> from.x.coerceIn(loc.x, maxX) to maxZ + 1
                Direction.South -> from.x.coerceIn(loc.x, maxX) to loc.z - 1
                Direction.East -> maxX + 1 to from.z.coerceIn(loc.z, maxZ)
                else -> loc.x - 1 to from.z.coerceIn(loc.z, maxZ)
            }
        val exit =
            tileOrNull(laneX + plane.x - loc.x, laneZ + plane.z - loc.z, plane.level) ?: return null
        for (step in 0..MAX_EXIT_STEPS) {
            val tile = exit.step(end, step) ?: return null
            if (walkable(tile)) {
                return tile
            }
        }
        return null
    }
}
