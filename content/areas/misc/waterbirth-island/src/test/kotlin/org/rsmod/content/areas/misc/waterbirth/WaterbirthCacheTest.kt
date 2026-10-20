package org.rsmod.content.areas.misc.waterbirth

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
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Checks the dungeon rules against the real map: which path each tile east of the pressure doors
 * belongs to, that each prop alone seals its path off from the ladder room, and that every
 * landing tile is open floor.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WaterbirthCacheTest {
    private class Square(val key: MapSquareKey, val tiles: ByteArray, val locs: ByteArray) {
        fun decodeLocs() = MapLocListDecoder.decode(InlineByteBuf(locs))
    }

    private lateinit var squares: List<Square>

    @BeforeAll
    fun loadMaps() {
        val cache = ServerCacheManager.init(241)
        try {
            squares =
                SQUARES.mapNotNull { key ->
                    val group = (key.x shl 8) or key.z
                    val tiles = cache.data(MAPS, group, 0) ?: return@mapNotNull null
                    val locs = cache.data(MAPS, group, 1) ?: return@mapNotNull null
                    Square(key, tiles, locs)
                }
        } finally {
            cache.close()
        }
    }

    @Test
    fun `every tile behind a pressure door belongs to that door's path`() {
        val steps = StepValidator(collision())
        for (path in WaterbirthPath.entries) {
            val region = flood(steps, path.throughDoor(path.pads[0]), PATHS_BOX)
            assertTrue(region.size > MIN_PATH_TILES, "$path path is only ${region.size} tiles")
            for (tile in region) {
                assertEquals(path, WaterbirthDoors.pathOf(tile), "$tile reached through the $path door")
            }
        }
    }

    @Test
    fun `the ladder room is sealed until a prop falls`() {
        val shut = StepValidator(collision())
        val room = flood(shut, WaterbirthCoords.LADDER_ROOM, PATHS_BOX)
        assertTrue(room.all(WaterbirthDoors::inLadderRoom), "ladder room leaks: $room")
        for (path in WaterbirthPath.entries) {
            assertFalse(path.throughDoor(path.pads[0]) in room, "$path reaches the room with every prop up")
        }

        for (fallen in WaterbirthPath.entries) {
            val steps = StepValidator(collision(withoutBlockerAt = fallen.supportCoords))
            val open = flood(steps, WaterbirthCoords.LADDER_ROOM, PATHS_BOX)
            for (path in WaterbirthPath.entries) {
                val reached = path.throughDoor(path.pads[0]) in open
                assertEquals(path == fallen, reached, "$path with the $fallen prop down")
            }
        }
    }

    @Test
    fun `the pressure plates and doors stand where the rules expect`() {
        val pads = locTiles("loc.dagannoth_pressure_pad_1") + locTiles("loc.dagannoth_pressure_pad_2")
        assertEquals(WaterbirthPath.entries.flatMap { it.pads }.toSet(), pads.toSet())
        for (path in WaterbirthPath.entries) {
            assertEquals(listOf(path.doorOrigin), locTiles(path.door), path.door)
        }
    }

    @Test
    fun `every landing tile is open floor`() {
        val steps = StepValidator(collision())
        val landings =
            mapOf(
                "Rellekka pier" to WaterbirthCoords.RELLEKKA_PIER,
                "island shore" to WaterbirthCoords.ISLAND_SHORE,
                "cave mouth" to WaterbirthCoords.CAVE_MOUTH,
                "cavern steps" to WaterbirthCoords.CAVERN_STEPS,
                "mountain top" to WaterbirthCoords.MOUNTAIN_TOP,
                "ladder room" to WaterbirthCoords.LADDER_ROOM,
                "sublevel 2" to WaterbirthCoords.SUBLEVEL_2_ENTRANCE,
                "Kings' ladder" to WaterbirthCoords.KINGS_LADDER_TOP,
                "Kings' lair" to WaterbirthCoords.KINGS_LAIR,
                "slayer lair" to WaterbirthCoords.SLAYER_LAIR,
                "lighthouse" to WaterbirthCoords.LIGHTHOUSE_CAVE,
            )
        for ((name, tile) in landings) {
            assertTrue(steps.isOpen(tile), "$name landing $tile is not open")
        }
        for (n in WaterbirthDungeon.DUNGEON_LADDERS) {
            val ladder = WaterbirthDungeon.ladderName(n)
            val partner = locTiles(WaterbirthDungeon.ladderName(WaterbirthDungeon.partnerOf(n))).single()
            val tile = locTiles(ladder).single()
            assertEquals(1, tile.chebyshevDistance(partner.copy(level = tile.level)), "$ladder partner")
            val landing = WaterbirthDungeon.ladderLanding(tile, partner)
            assertTrue(steps.isOpen(landing), "$ladder at $tile lands on $landing")
            val around = flood(steps, landing, intArrayOf(landing.x - 8, landing.z - 8, landing.x + 8, landing.z + 8))
            assertTrue(around.size > MIN_LANDING_AREA, "$ladder lands in a pocket at $landing")
        }
    }

    @Test
    fun `the island shore reaches the cave mouth and the cavern steps reach the doors`() {
        val steps = StepValidator(collision())
        val island = flood(steps, WaterbirthCoords.ISLAND_SHORE, ISLAND_BOX)
        assertTrue(WaterbirthCoords.CAVE_MOUTH in island, "cave mouth unreachable from the shore")
        assertFalse(WaterbirthCoords.MOUNTAIN_TOP in island, "mountain top reachable without climbing")
        val cavern = flood(steps, WaterbirthCoords.CAVERN_STEPS, CAVERN_BOX)
        for (pad in WaterbirthPath.entries.flatMap { it.pads }) {
            assertTrue(pad in cavern, "pressure plate $pad unreachable from the steps")
        }
    }

    private fun StepValidator.isOpen(tile: CoordGrid): Boolean =
        DIRECTIONS.any { (dx, dz) -> canTravel(tile.level, tile.x, tile.z, dx, dz) }

    private fun locTiles(name: String): List<CoordGrid> {
        val id = name.asRSCM(RSCMType.LOC)
        return squares.flatMap { square ->
            square.decodeLocs().spawns.toLongArray()
                .map(::MapLocDefinition)
                .filter { it.id == id }
                .map { CoordGrid(square.key.x * 64 + it.localX, square.key.z * 64 + it.localZ, it.level) }
        }
    }

    private fun collision(withoutBlockerAt: CoordGrid? = null): CollisionFlagMap {
        val blocker = "loc.dagcave_invisibleblocking".asRSCM(RSCMType.LOC)
        val map = CollisionFlagMap()
        for (square in squares) {
            val tiles = MapTileDecoder.decode(InlineByteBuf(square.tiles))
            val locs = square.decodeLocs()
            if (withoutBlockerAt != null) {
                locs.spawns.removeIf {
                    val loc = MapLocDefinition(it)
                    val at = CoordGrid(square.key.x * 64 + loc.localX, square.key.z * 64 + loc.localZ, loc.level)
                    loc.id == blocker && at == withoutBlockerAt
                }
            }
            for (level in 0..3) for (x in 0 until 64 step 8) for (z in 0 until 64 step 8) {
                map.allocateIfAbsent(square.key.x * 64 + x, square.key.z * 64 + z, level)
            }
            GameMapDecoder.putMaps(map, square.key, tiles)
            GameMapDecoder.putLocs(GameMapBuilder(), map, square.key, tiles, locs)
        }
        return map
    }

    private fun flood(steps: StepValidator, start: CoordGrid, box: IntArray): Set<CoordGrid> {
        val (minX, minZ, maxX, maxZ) = box.toList()
        val seen = hashSetOf(start)
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            for ((dx, dz) in DIRECTIONS) {
                if (!steps.canTravel(c.level, c.x, c.z, dx, dz)) continue
                val next = c.translate(dx, dz)
                if (next.x !in minX..maxX || next.z !in minZ..maxZ) continue
                if (seen.add(next)) queue += next
            }
        }
        return seen
    }

    private companion object {
        const val MIN_PATH_TILES = 300
        const val MIN_LANDING_AREA = 20
        val PATHS_BOX = intArrayOf(2492, 10112, 2560, 10176)
        val ISLAND_BOX = intArrayOf(2496, 3712, 2575, 3791)
        val CAVERN_BOX = intArrayOf(2432, 10112, 2491, 10176)
        val DIRECTIONS = (-1..1).flatMap { dx -> (-1..1).map { dz -> dx to dz } }.filter { it != 0 to 0 }
        val SQUARES =
            listOf(38, 39, 40).flatMap { x -> listOf(157, 158, 159).map { MapSquareKey(x, it) } } +
                listOf(39, 40, 41).flatMap { x -> listOf(56, 57, 58, 59).map { MapSquareKey(x, it) } } +
                listOf(28, 29, 30, 31).flatMap { x -> listOf(68, 69).map { MapSquareKey(x, it) } } +
                listOf(MapSquareKey(45, 68), MapSquareKey(45, 69), MapSquareKey(39, 156))
    }
}
