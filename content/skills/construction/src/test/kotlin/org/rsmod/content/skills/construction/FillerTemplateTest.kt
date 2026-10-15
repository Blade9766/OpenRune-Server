package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.skills.construction.data.HouseStyle
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/** Pins the style blocks' filler templates: a lawn anyone can walk across, and rock nobody can. */
@ResourceLock("ServerCacheManager")
class FillerTemplateTest {
    @Test
    fun `every style's lawn can be walked all over`() {
        for (style in HouseStyle.entries) {
            assertEquals(ZONE_TILES, walkable(style.grassZone), style.label)
        }
    }

    @Test
    fun `every style's dungeon rock is solid`() {
        for (style in HouseStyle.entries) {
            assertEquals(0, walkable(style.rockZone), style.label)
        }
    }

    private fun walkable(zone: ZoneKey): Int {
        val base = zone.toCoords()
        var count = 0
        for (dx in 0 until ZONE_SIZE) {
            for (dz in 0 until ZONE_SIZE) {
                val flags = collision[base.x + dx, base.z + dz, zone.level]
                if (flags and (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC) == 0) {
                    count++
                }
            }
        }
        return count
    }

    companion object {
        private const val ZONE_SIZE = 8
        private const val ZONE_TILES = ZONE_SIZE * ZONE_SIZE

        private val collision = CollisionFlagMap()
        private lateinit var cache: dev.openrune.filesystem.Cache

        @JvmStatic
        @BeforeAll
        fun load() {
            cache = ServerCacheManager.init(240)
            val squares = HouseStyle.entries.map { it.blockZoneX / ZONE_SIZE }.distinct()
            for (mx in squares) {
                val mz = HouseStyle.entries.first().grassZone.z / ZONE_SIZE
                val square = MapSquareKey(mx, mz)
                val group = (mx shl 8) or mz
                val tiles = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 0))))
                val spawns = MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 1))))
                for (level in 0..3) {
                    for (x in mx * 64 until mx * 64 + 64 step ZONE_SIZE) {
                        for (z in mz * 64 until mz * 64 + 64 step ZONE_SIZE) {
                            collision.allocateIfAbsent(x, z, level)
                        }
                    }
                }
                GameMapDecoder.putMaps(collision, square, tiles)
                GameMapDecoder.putLocs(GameMapBuilder(), collision, square, tiles, spawns)
            }
        }

        @JvmStatic
        @AfterAll
        fun close() {
            cache.close()
        }
    }
}
