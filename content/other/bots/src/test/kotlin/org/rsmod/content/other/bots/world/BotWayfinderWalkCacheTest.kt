package org.rsmod.content.other.bots.world

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.routefinder.RouteFinding
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

@ResourceLock("ServerCacheManager")
class BotWayfinderWalkCacheTest {
    @Test
    fun `a bot at the exchange walks to Vaelor in hops`() {
        val routes = RouteFinding(collision)
        val starts = EXCHANGE_TILES.filter { collision[it.x, it.z, 0] and CollisionFlag.BLOCK_WALK == 0 }
        assertTrue(starts.size >= 3, "walkable exchange tiles: $starts")
        assertFalse(routes.findRoute(0, starts[0].x, starts[0].z, VAELOR.x, VAELOR.z).success, "too far for one route")
        for (start in starts) {
            var at = start
            repeat(MAX_HOPS) {
                if (at.chebyshevDistance(VAELOR) <= 1) {
                    return@repeat
                }
                val target = BotWayfinder.hopToward(at, VAELOR, maxHop = 48)
                val route = routes.findRoute(0, at.x, at.z, target.x, target.z)
                val end = route.waypoints.lastOrNull() ?: return@repeat
                at = CoordGrid(end.x, end.z, end.level)
            }
            assertTrue(at.chebyshevDistance(VAELOR) <= 1, "from $start the walk stopped at $at")
        }
    }

    private companion object {
        val VAELOR = CoordGrid(3088, 3495, 0)
        val EXCHANGE_TILES =
            listOf(
                CoordGrid(3165, 3481, 0),
                CoordGrid(3159, 3487, 0),
                CoordGrid(3171, 3487, 0),
                CoordGrid(3165, 3493, 0),
                CoordGrid(3160, 3482, 0),
            )
        const val MAX_HOPS = 8

        val collision = CollisionFlagMap()
        lateinit var cache: dev.openrune.filesystem.Cache

        @JvmStatic @BeforeAll fun load() {
            cache = ServerCacheManager.init(241)
            for ((mx, mz) in listOf(47 to 54, 48 to 54, 49 to 54, 48 to 55, 49 to 55, 48 to 53, 49 to 53)) {
                val square = MapSquareKey(mx, mz)
                val group = (mx shl 8) or mz
                val tiles = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 0))))
                val spawns = MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 1))))
                for (level in 0..3) for (x in mx * 64 until mx * 64 + 64 step 8) for (z in mz * 64 until mz * 64 + 64 step 8) {
                    collision.allocateIfAbsent(x, z, level)
                }
                GameMapDecoder.putMaps(collision, square, tiles)
                GameMapDecoder.putLocs(GameMapBuilder(), collision, square, tiles, spawns)
            }
        }

        @JvmStatic @AfterAll fun close() {
            cache.close()
        }
    }
}
