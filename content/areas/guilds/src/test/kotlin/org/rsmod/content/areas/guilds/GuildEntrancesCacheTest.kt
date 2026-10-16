package org.rsmod.content.areas.guilds

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Checks each side of the Warriors' Guild door against the map: with the door shut, the side
 * reachable from Burthorpe is outside and the other is the guild hall.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class GuildEntrancesCacheTest {
    @Test
    fun `warriors guild treats the burthorpe side of its door as outside`() {
        val cache = ServerCacheManager.init(241)
        val collision = CollisionFlagMap()
        val doors = ArrayList<MapLocDefinition>()
        val doorType = "loc.warguild_door_front".asRSCM(RSCMType.LOC)
        try {
            for (square in WARRIORS_SQUARES) {
                val group = (square.x shl 8) or square.z
                val tiles = MapTileDecoder.decode(InlineByteBuf(cache.data(MAPS, group, 0)!!))
                val locs = MapLocListDecoder.decode(InlineByteBuf(cache.data(MAPS, group, 1)!!))
                for (packed in locs.spawns.longIterator()) {
                    val loc = MapLocDefinition(packed)
                    if (loc.id == doorType) doors += loc
                }
                for (level in 0..3) for (x in 0 until 64 step 8) for (z in 0 until 64 step 8) {
                    collision.allocateIfAbsent(square.x * 64 + x, square.z * 64 + z, level)
                }
                GameMapDecoder.putMaps(collision, square, tiles)
                GameMapDecoder.putLocs(GameMapBuilder(), collision, square, tiles, locs)
            }
        } finally {
            cache.close()
        }

        assertEquals(1, doors.size, "expected one front door")
        val door = doors.single()
        assertEquals(0, door.shape, "front door is not a straight wall")
        val doorTile = CoordGrid(WARRIORS_DOOR_SQUARE.x * 64 + door.localX, WARRIORS_DOOR_SQUARE.z * 64 + door.localZ, door.level)
        val (dx, dz) = WALL_SIDES[door.angle]
        val beyond = doorTile.translate(dx, dz)

        val outside = reachable(collision, BURTHORPE)
        val (outer, inner) = if (doorTile in outside) doorTile to beyond else beyond to doorTile
        assertTrue(outer in outside, "$outer is not reachable from Burthorpe")
        assertFalse(inner in outside, "$inner is reachable from Burthorpe with the door shut")
        assertFalse(GUILD_HALL in outside, "$GUILD_HALL is reachable from Burthorpe with the door shut")

        val entrance = GuildEntrances.warriors
        assertFalse(entrance.inside(outer), "$outer counts as inside")
        assertTrue(entrance.inside(inner), "$inner counts as outside")
        assertTrue(entrance.inside(GUILD_HALL), "$GUILD_HALL counts as outside")
    }

    private fun reachable(collision: CollisionFlagMap, start: CoordGrid): Set<CoordGrid> {
        val steps = StepValidator(collision)
        val seen = hashSetOf(start)
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            for (dx in -1..1) for (dz in -1..1) {
                if ((dx == 0 && dz == 0) || !steps.canTravel(c.level, c.x, c.z, dx, dz)) continue
                val next = c.translate(dx, dz)
                if (next.chebyshevDistance(start) <= SEARCH_RADIUS && seen.add(next)) queue += next
            }
        }
        return seen
    }

    private companion object {
        const val SEARCH_RADIUS = 48
        val WARRIORS_DOOR_SQUARE = MapSquareKey(44, 55)
        val WARRIORS_SQUARES = listOf(WARRIORS_DOOR_SQUARE, MapSquareKey(45, 55))
        val BURTHORPE = CoordGrid(2895, 3545, 0)
        val GUILD_HALL = CoordGrid(2860, 3545, 0)
        val WALL_SIDES = listOf(-1 to 0, 0 to 1, 1 to 0, 0 to -1)
    }
}
