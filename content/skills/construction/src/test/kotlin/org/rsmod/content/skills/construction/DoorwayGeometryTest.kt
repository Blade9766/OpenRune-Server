package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.skills.construction.data.HouseStyle
import org.rsmod.content.skills.construction.data.RoomType
import org.rsmod.content.skills.construction.data.Side
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocShape
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Pins the room-template facts doors are hung by: every door hotspot is a straight wall on its
 * room's edge, facing out of the room, on tile 3 or 4 along that wall. Facing out means a doorway
 * sits on the zone's outer boundary, so two joined rooms author theirs on the same wall line.
 */
@ResourceLock("ServerCacheManager")
class DoorwayGeometryTest {
    @Test
    fun `door hotspots sit on the room edge they face`() {
        val style = HouseStyle.BASIC_WOOD
        val hotspots = setOf(style.doorLeft, style.doorRight, DUNGEON_DOOR)
        val wrong = ArrayList<String>()
        var seen = 0
        for (room in RoomType.entries) {
            val zone = ZoneKey(style.blockZoneX + room.zoneOffsetX, room.zoneZ, style.templateLevel)
            val base = zone.toCoords()
            for ((loc, coords) in placed.filter { (_, c) -> ZoneKey.from(c) == zone }) {
                val name = runCatching { RSCM.getReverseMapping(RSCMType.LOC, loc.id) }.getOrNull()
                if (name !in hotspots) {
                    continue
                }
                seen++
                val lx = coords.x - base.x
                val lz = coords.z - base.z
                val side = loc.angle
                val edge =
                    when (side) {
                        Side.WEST -> lx == 0 && lz in 3..4
                        Side.EAST -> lx == 7 && lz in 3..4
                        Side.SOUTH -> lz == 0 && lx in 3..4
                        else -> lz == 7 && lx in 3..4
                    }
                if (loc.shape != LocShape.WallStraight.id || !edge) {
                    wrong += "${room.label} $name at ($lx,$lz) shape=${loc.shape} angle=$side"
                }
            }
        }
        assertTrue(seen > 0, "No door hotspots found in the templates")
        assertTrue(wrong.isEmpty(), "Door hotspots off their edge: $wrong")
    }

    @Test
    fun `windows sit on the room edge they face`() {
        val style = HouseStyle.BASIC_WOOD
        val wrong = ArrayList<String>()
        var seen = 0
        for (room in RoomType.entries) {
            val zone = ZoneKey(style.blockZoneX + room.zoneOffsetX, room.zoneZ, style.templateLevel)
            val base = zone.toCoords()
            for ((loc, coords) in placed.filter { (_, c) -> ZoneKey.from(c) == zone }) {
                val name = runCatching { RSCM.getReverseMapping(RSCMType.LOC, loc.id) }.getOrNull()
                if (name != WINDOW) {
                    continue
                }
                seen++
                val lx = coords.x - base.x
                val lz = coords.z - base.z
                val edge =
                    when (loc.angle) {
                        Side.WEST -> lx == 0
                        Side.EAST -> lx == 7
                        Side.SOUTH -> lz == 0
                        else -> lz == 7
                    }
                if (!edge) {
                    wrong += "${room.label} window at ($lx,$lz) angle=${loc.angle}"
                }
            }
        }
        assertTrue(seen > 0, "No windows found in the templates")
        assertTrue(wrong.isEmpty(), "Windows off their edge: $wrong")
    }

    @Test
    fun `every room's doors are where its template has door hotspots`() {
        val style = HouseStyle.BASIC_WOOD
        val hotspots = setOf(style.doorLeft, style.doorRight, DUNGEON_DOOR)
        val wrong = ArrayList<String>()
        for (room in RoomType.entries) {
            val zone = ZoneKey(style.blockZoneX + room.zoneOffsetX, room.zoneZ, style.templateLevel)
            val sides =
                placed
                    .filter { (loc, c) ->
                        ZoneKey.from(c) == zone &&
                            runCatching { RSCM.getReverseMapping(RSCMType.LOC, loc.id) }.getOrNull() in hotspots
                    }
                    .map { (loc, _) -> loc.angle }
                    .toSet()
            val mask = sides.fold(0) { acc, side -> acc or (1 shl side) }
            if (mask != room.doors) {
                wrong += "${room.label}: template ${Integer.toBinaryString(mask)} vs ${Integer.toBinaryString(room.doors)}"
            }
        }
        assertTrue(wrong.isEmpty(), "Door masks off: $wrong")
    }

    companion object {
        const val DUNGEON_DOOR = "loc.poh_hotspot_door_dungeon"
        const val WINDOW = "loc.poh_dynamic_window"
        val collision = CollisionFlagMap()
        val placed = mutableListOf<Pair<LocEntity, CoordGrid>>()
        lateinit var cache: dev.openrune.filesystem.Cache

        @JvmStatic
        @BeforeAll
        fun load() {
            cache = ServerCacheManager.init(240)
            for ((mx, mz) in listOf(29 to 110, 29 to 111)) {
                val square = MapSquareKey(mx, mz)
                val group = (mx shl 8) or mz
                val tiles = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 0))))
                val spawns = MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 1))))
                for (level in 0..3) {
                    for (x in mx * 64 until mx * 64 + 64 step 8) {
                        for (z in mz * 64 until mz * 64 + 64 step 8) {
                            collision.allocateIfAbsent(x, z, level)
                        }
                    }
                }
                val builder = GameMapBuilder()
                GameMapDecoder.putMaps(collision, square, tiles)
                GameMapDecoder.putLocs(builder, collision, square, tiles, spawns)
                for ((packed, zone) in builder.zoneBuilders) {
                    val zoneBase = ZoneKey(packed).toCoords()
                    for (entry in zone.build().byte2IntEntrySet()) {
                        val key = LocZoneKey(entry.byteKey)
                        placed += LocEntity(entry.intValue) to zoneBase.translate(key.x, key.z)
                    }
                }
            }
        }

        @JvmStatic
        @AfterAll
        fun close() {
            cache.close()
        }
    }
}
