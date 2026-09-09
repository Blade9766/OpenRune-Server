package org.rsmod.content.generic.locs.passages

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.map.collision.get
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Works out where a player climbing a staircase, ladder or trapdoor comes out.
 *
 * A flight of stairs is two locs: one on each level, overlapping on the map, each of which can
 * only be approached from its foot. Climbing one puts the player at the foot of the other, which
 * is where they would stand to climb back. When the other end cannot be found the player goes
 * straight through the clicked stairs and out past their far end. Only as a last resort is the
 * player put on the nearest free tile to where they stood.
 */
class StairNavigator(
    private val collision: CollisionFlagMap,
    private val locsIn: (ZoneKey) -> Sequence<LocInfo>,
    private val slack: Int = COUNTERPART_SLACK,
) {
    @Inject
    constructor(locRepo: LocRepository, collision: CollisionFlagMap) : this(collision, locRepo::findAll)

    /**
     * The tile a player at [from] lands on after climbing [loc] in direction [up]. Each level
     * the climb might reach is tried in turn (see [Passages.climbPlanes]), taking the foot of
     * the matching flight there, else the tile straight out past the end of this one; only when
     * no level has floor either way is the player put on the nearest free tile to where they
     * stood. `null` when there is no floor anywhere sensible.
     */
    fun destination(loc: BoundLocInfo, from: CoordGrid, up: Boolean): CoordGrid? {
        val planes = Passages.climbPlanes(loc.coords, up)
        for (plane in planes) {
            val landed =
                counterpartLanding(loc, carry(from, loc, plane), plane, up)
                    ?: Passages.exitLanding(loc, from, plane, ::walkable)
            if (landed != null) {
                return landed
            }
        }
        return planes.firstNotNullOfOrNull { landing(carry(from, loc, it)) }
    }

    /** Whether any level a climb of [loc] in direction [up] might reach has stairs to meet it. */
    fun hasCounterpart(loc: BoundLocInfo, up: Boolean): Boolean =
        Passages.climbPlanes(loc.coords, up).any { hasCounterpart(loc, it, up) }

    /**
     * The foot of the stairs matching [loc] on [plane], nearest to [target], or `null` when there
     * is no matching loc there or nothing near its foot is free.
     */
    fun counterpartLanding(
        loc: BoundLocInfo,
        target: CoordGrid,
        plane: CoordGrid,
        up: Boolean,
    ): CoordGrid? {
        val counterpart = findCounterpart(loc, plane, up) ?: return null
        return Passages.counterpartLanding(counterpart, target, ::walkable)
    }

    fun hasCounterpart(loc: BoundLocInfo, plane: CoordGrid, up: Boolean): Boolean =
        findCounterpart(loc, plane, up) != null

    /** [coords] moved by the same offset that takes [loc]'s origin to [plane]. */
    fun carry(coords: CoordGrid, loc: BoundLocInfo, plane: CoordGrid): CoordGrid =
        CoordGrid(
            (coords.x + plane.x - loc.x).coerceIn(0, CoordGrid.X_BIT_MASK),
            (coords.z + plane.z - loc.z).coerceIn(0, CoordGrid.Z_BIT_MASK),
            plane.level,
        )

    /**
     * The climbable loc on [plane] that overlaps [loc]'s footprint (allowing [slack] tiles
     * around it) and goes the other way. With several to choose from, the one going explicitly the other
     * way beats one that goes either way, and the greater overlap wins after that.
     */
    private fun findCounterpart(loc: BoundLocInfo, plane: CoordGrid, up: Boolean): BoundLocInfo? {
        val minX = plane.x - slack
        val minZ = plane.z - slack
        val maxX = plane.x + loc.adjustedWidth - 1 + slack
        val maxZ = plane.z + loc.adjustedLength - 1 + slack
        var best: BoundLocInfo? = null
        var bestScore = 0
        for (zoneX in (minX shr ZoneGrid.X_BIT_COUNT)..(maxX shr ZoneGrid.X_BIT_COUNT)) {
            for (zoneZ in (minZ shr ZoneGrid.Z_BIT_COUNT)..(maxZ shr ZoneGrid.Z_BIT_COUNT)) {
                val zone =
                    ZoneKey.fromAbsolute(
                        zoneX shl ZoneGrid.X_BIT_COUNT,
                        zoneZ shl ZoneGrid.Z_BIT_COUNT,
                        plane.level,
                    )
                for (info in locsIn(zone)) {
                    val type = ServerCacheManager.getObject(info.id) ?: continue
                    val ops = (0 until 5).map { type.actions.getOpOrNull(it) }
                    if (!Passages.isClimbCounterpart(type.name, ops, up)) {
                        continue
                    }
                    val candidate = BoundLocInfo(info, type)
                    val cMaxX = candidate.x + candidate.adjustedWidth - 1
                    val cMaxZ = candidate.z + candidate.adjustedLength - 1
                    val overlapX = minOf(maxX, cMaxX) - maxOf(minX, candidate.x) + 1
                    val overlapZ = minOf(maxZ, cMaxZ) - maxOf(minZ, candidate.z) + 1
                    if (overlapX <= 0 || overlapZ <= 0) {
                        continue
                    }
                    val opRank = ops.maxOf { if (it == null) 0 else Passages.counterpartOpRank(it, up) }
                    val score = opRank * 1000 + overlapX * overlapZ
                    if (score > bestScore) {
                        best = candidate
                        bestScore = score
                    }
                }
            }
        }
        return best
    }

    /**
     * The nearest free tile to [dest], or `null` if everything around it is blocked or the map
     * there is featureless filler rather than somewhere a player can be.
     */
    fun landing(dest: CoordGrid): CoordGrid? {
        if (!hasSurroundings(dest)) {
            return null
        }
        return Passages.landingCandidates(dest).firstOrNull(::walkable)
    }

    /** Whether a player can stand on [coords]: real map, no blocked tile, no loc on it. */
    fun walkable(coords: CoordGrid): Boolean =
        collision.isZoneValid(coords) && collision[coords] and BLOCKED == 0

    /**
     * Whether anything at all (a wall, a loc, a blocked tile) sits within [SURROUNDINGS_RADIUS]
     * tiles of [centre]. Real rooms and caves always have something close by; the black filler
     * that pads out a map square has nothing, and a guessed destination that lands in it would
     * strand the player.
     */
    private fun hasSurroundings(centre: CoordGrid): Boolean {
        for (dz in -SURROUNDINGS_RADIUS..SURROUNDINGS_RADIUS) {
            for (dx in -SURROUNDINGS_RADIUS..SURROUNDINGS_RADIUS) {
                val x = centre.x + dx
                val z = centre.z + dz
                if (x !in 0..CoordGrid.X_BIT_MASK || z !in 0..CoordGrid.Z_BIT_MASK) {
                    continue
                }
                val tile = CoordGrid(x, z, centre.level)
                if (!collision.isZoneValid(tile)) {
                    continue
                }
                if (collision[tile] and SURROUNDINGS_MASK != 0) {
                    return true
                }
            }
        }
        return false
    }

    private companion object {
        /** How far outside a flight's footprint its other end may stand and still be paired. */
        private const val COUNTERPART_SLACK = 1

        private const val BLOCKED = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC

        private const val SURROUNDINGS_RADIUS = 8

        /** Every collision flag except the roof marker. */
        private const val SURROUNDINGS_MASK = CollisionFlag.ROOF.inv()
    }
}
