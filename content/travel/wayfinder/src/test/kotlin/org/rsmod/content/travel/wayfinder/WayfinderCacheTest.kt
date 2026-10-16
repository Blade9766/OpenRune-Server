package org.rsmod.content.travel.wayfinder

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import java.io.File
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.area.checker.isInWildernessBasic
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Pins the built cache to what the wayfinder network promises: every destination row is valid
 * and lands on an open tile with room to walk, wilderness rows warn and keep bots out, and the
 * two mages stand where they leave Edgeville bank and Lumbridge church free to walk into.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class WayfinderCacheTest {
    private val registry = WayfinderRegistry()

    @Test
    fun `the destination table loads with every category filled`() {
        val all = registry.all
        assertTrue(all.size >= 90, "only ${all.size} destinations")
        for (category in WayfinderCategory.entries) {
            assertTrue(registry.inCategory(category).isNotEmpty(), "$category is empty")
        }
        for (destination in all) {
            assertFalse('|' in destination.name, destination.key)
            assertTrue(destination.description.length <= 90, "${destination.key} description is too long")
        }
    }

    @Test
    fun `towns start in the order the menu design shows`() {
        val towns = registry.inCategory(WayfinderCategory.Towns).take(8).map { it.name }
        val expected = listOf("Lumbridge", "Varrock", "Falador", "Edgeville", "Draynor Village", "Al Kharid", "Catherby", "Ardougne")
        assertEquals(expected, towns)
    }

    @Test
    fun `every destination has its details, icon and preview`() {
        val manifest =
            File(SPRITES, "manifest.toml").readLines()
                .mapNotNull { MANIFEST_LINE.matchEntire(it.trim()) }
                .associate { it.groupValues[1] to it.groupValues[2].toInt() }
        for (destination in registry.all) {
            assertTrue(destination.arrival.isNotBlank(), "${destination.key} has no arrival")
            assertTrue(destination.tags.isNotEmpty(), "${destination.key} has no tags")
            assertTrue(ServerCacheManager.getItem(destination.icon) != null, "${destination.key} icon ${destination.icon}")
            val sprite = "wayfinder_${destination.key}"
            assertEquals(manifest[sprite], destination.preview, "$sprite preview id")
            assertTrue(File(SPRITES, "$sprite.png").isFile, "$sprite.png is missing")
        }
    }

    @Test
    fun `quest requirements name real quests`() {
        for (destination in registry.all) {
            destination.quest?.let { quest ->
                assertTrue(runCatching { "dbrow.$quest".asRSCM(RSCMType.DBROW) }.isSuccess, "${destination.key}: unknown quest $quest")
            }
        }
    }

    @Test
    fun `wilderness destinations warn and are closed to bots`() {
        for (destination in registry.all) {
            val wild = destination.coords.isInWildernessBasic()
            if (wild || destination.category == WayfinderCategory.Wilderness) {
                assertTrue(destination.needsConfirmation, "${destination.key} is in the Wilderness without a warning")
                assertFalse(destination.botAllowed, "${destination.key} lets bots into the Wilderness")
                assertTrue(destination.warning != null, "${destination.key} has no warning text")
            }
            if (destination.danger >= WayfinderDanger.High) {
                assertFalse(destination.botAllowed, "${destination.key} is dangerous but open to bots")
            }
        }
    }

    @Test
    fun `every arrival tile is open with room to walk`() {
        val failures = ArrayList<String>()
        for (destination in registry.all) {
            val collision = loadAround(destination.coords)
            val room = reachable(collision, destination.coords, ROOM_RADIUS).size
            if (room < MIN_ROOM) {
                failures += "${destination.key} at ${destination.coords}: only $room reachable tiles"
            }
        }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun `the mages are real npcs with talk and teleport ops`() {
        for (mage in WayfinderMages.ALL) {
            val type = checkNotNull(ServerCacheManager.getNpc(mage.npc.asRSCM(RSCMType.NPC))) { mage.npc }
            assertEquals("Talk-to", type.actions.getOpOrNull(0), mage.npc)
            assertEquals("Teleport", type.actions.getOpOrNull(2), mage.npc)
            assertEquals(0, type.wanderRange, mage.npc)
        }
    }

    @Test
    fun `vaelor leaves the edgeville bank entrance clear`() {
        assertMageClear(VAELOR, routesInto = EDGEVILLE_BOOTHS, from = listOf(EDGEVILLE_SQUARE, EDGEVILLE_ROAD))
    }

    @Test
    fun `elowen leaves the lumbridge church doors clear`() {
        assertMageClear(ELOWEN, routesInto = CHURCH_DOORS, from = listOf(LUMBRIDGE_CASTLE, LUMBRIDGE_NORTH_ROAD))
    }

    private fun assertMageClear(mage: CoordGrid, routesInto: CoordGrid, from: List<CoordGrid>) {
        val collision = loadAround(mage)
        assertTrue(reachable(collision, mage, 2).size > 1, "$mage is boxed in")
        collision.add(mage.x, mage.z, mage.level, CollisionFlag.BLOCK_NPCS or CollisionFlag.LOC)
        for (start in from) {
            val area = reachable(collision, start, SPAWN_CHECK_RADIUS)
            assertTrue(routesInto in area, "$routesInto is cut off from $start while the mage stands at $mage")
            assertTrue((-1..1).any { dx -> (-1..1).any { dz -> mage.translate(dx, dz) in area } }, "$mage can't be reached from $start")
        }
    }

    private fun reachable(collision: CollisionFlagMap, start: CoordGrid, radius: Int): Set<CoordGrid> {
        val steps = StepValidator(collision)
        val seen = hashSetOf(start)
        if (collision[start.x, start.z, start.level] and BLOCKED != 0) {
            return emptySet()
        }
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            for (dx in -1..1) for (dz in -1..1) {
                if ((dx == 0 && dz == 0) || !steps.canTravel(c.level, c.x, c.z, dx, dz)) continue
                val next = c.translate(dx, dz)
                if (next.chebyshevDistance(start) <= radius && seen.add(next)) queue += next
            }
        }
        return seen
    }

    private fun loadAround(center: CoordGrid): CollisionFlagMap {
        val collision = CollisionFlagMap()
        val squares = LinkedHashSet<MapSquareKey>()
        for (dx in -1..1) for (dz in -1..1) {
            squares += MapSquareKey.from(center.translate(dx * LOAD_REACH, dz * LOAD_REACH))
        }
        for (square in squares) {
            val group = (square.x shl 8) or square.z
            val tileData = cache.data(MAPS, group, 0) ?: continue
            val locData = cache.data(MAPS, group, 1) ?: continue
            val tiles = MapTileDecoder.decode(InlineByteBuf(tileData))
            val locs = MapLocListDecoder.decode(InlineByteBuf(locData))
            for (level in 0..3) for (x in square.x * 64 until square.x * 64 + 64 step 8) for (z in square.z * 64 until square.z * 64 + 64 step 8) {
                collision.allocateIfAbsent(x, z, level)
            }
            GameMapDecoder.putMaps(collision, square, tiles)
            GameMapDecoder.putLocs(GameMapBuilder(), collision, square, tiles, locs)
        }
        return collision
    }

    companion object {
        private val SPRITES = File("content/travel/wayfinder/pack/src/main/resources/pack/sprites")
        private val MANIFEST_LINE = Regex("""(\w+)\s*=\s*(\d+)""")

        private const val MIN_ROOM = 20
        private const val ROOM_RADIUS = 6
        private const val LOAD_REACH = 24
        private const val SPAWN_CHECK_RADIUS = 30
        private const val BLOCKED = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC

        private val VAELOR = CoordGrid(3088, 3495, 0)
        private val EDGEVILLE_BOOTHS = CoordGrid(3094, 3491, 0)
        private val EDGEVILLE_SQUARE = CoordGrid(3087, 3500, 0)
        private val EDGEVILLE_ROAD = CoordGrid(3092, 3484, 0)

        private val ELOWEN = CoordGrid(3239, 3222, 0)
        private val CHURCH_DOORS = CoordGrid(3243, 3217, 0)
        private val LUMBRIDGE_CASTLE = CoordGrid(3222, 3218, 0)
        private val LUMBRIDGE_NORTH_ROAD = CoordGrid(3235, 3226, 0)

        private lateinit var cache: dev.openrune.filesystem.Cache

        @JvmStatic
        @BeforeAll
        fun load() {
            cache = ServerCacheManager.init(241)
        }

        @JvmStatic
        @AfterAll
        fun close() {
            cache.close()
        }
    }
}
