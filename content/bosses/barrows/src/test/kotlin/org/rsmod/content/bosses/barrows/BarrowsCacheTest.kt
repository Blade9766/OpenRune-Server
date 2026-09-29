package org.rsmod.content.bosses.barrows

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.filesystem.Cache
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.tile.MapTileSimpleDefinition
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.game.loc.LocAngle
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey

/**
 * Pins the map facts the Barrows scripts rely on: where each brother's crypt locs stand, that the
 * tiles a player is put on are open floor, and that the tunnel door leaves join exactly the rooms
 * [BarrowsDoorway] says they do.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class BarrowsCacheTest {
    @Test
    fun `each crypt holds its brother's stairs and sarcophagus`() {
        for (brother in BarrowsBrother.entries) {
            val stairs = spawnsOf(brother.staircase)
            val sarcophagus = spawnsOf(brother.sarcophagus)
            assertEquals(1, stairs.size, "${brother.staircase} spawns")
            assertEquals(1, sarcophagus.size, "${brother.sarcophagus} spawns")
            assertTrue(stairs.single().first.chebyshevDistance(brother.chamber) <= 3)
            assertEquals(CRYPT_LEVEL, stairs.single().first.level)
            assertTrue(sarcophagus.single().first.chebyshevDistance(brother.chamber) <= 20)
        }
    }

    @Test
    fun `players are only ever put on open floor`() {
        val tiles = BarrowsBrother.entries.flatMap { listOf(it.chamber, it.bySarcophagus) }
        for (tile in tiles) {
            assertTrue(isOpenFloor(tile), "$tile is blocked")
        }
    }

    @Test
    fun `corner ladders stand where the tunnel drops the player`() {
        val ladders = (20674..20677).flatMap { id -> spawnsOfId(id) }.map { it.first }
        assertEquals(4, ladders.size)
        for (corner in BarrowsCorner.entries) {
            val near = ladders.any { it.chebyshevDistance(corner.ladder) <= 2 }
            assertTrue(near, "no ladder by $corner")
        }
    }

    @Test
    fun `door leaves join the rooms their doorway links`() {
        for (doorway in BarrowsDoorway.entries) {
            val leaves = doorway.locs.flatMap(::spawnsOf)
            assertEquals(4, leaves.size, "leaves of $doorway")
            val rooms = mutableSetOf<TunnelRoom>()
            for ((coords, angle) in leaves) {
                val sides = listOf(coords, crossingTile(coords, coords, angle.id))
                for (side in sides) {
                    assertTrue(isOpenFloor(side), "$doorway door side $side is blocked")
                    roomOf(side)?.let(rooms::add)
                }
                assertEquals(coords, crossingTile(sides[1], coords, angle.id))
            }
            val expected = doorway.links.flatMap { it.toList() }.toSet()
            assertEquals(expected, rooms, "rooms touched by $doorway")
        }
    }

    @Test
    fun `puzzle models exist`() {
        val models = BarrowsPuzzleType.entries.flatMap { it.sequence + it.options }
        for (model in models) {
            assertNotNull(live.data(MODELS, model, 0), "model $model")
        }
    }

    @Test
    fun `every named type resolves`() {
        for (brother in BarrowsBrother.entries) {
            for (obj in brother.armour) {
                assertNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)), obj)
            }
            assertNotNull(ServerCacheManager.getNpc(brother.npc.asRSCM(RSCMType.NPC)), brother.npc)
        }
        for (npc in SKELETONS + BLOODWORM + CRYPT_RAT) {
            assertNotNull(ServerCacheManager.getNpc(npc.asRSCM(RSCMType.NPC)), npc)
        }
        val layoutVarp = "varp.barrows_layout".asRSCM(RSCMType.VARP)
        val layoutBits =
            listOf(
                "varbit.barrows_locked_doors",
                "varbit.barrows_puzzle_type",
                "varbit.barrows_puzzle_answer",
            )
        for (varbit in layoutBits) {
            val type = ServerCacheManager.getVarbit(varbit.asRSCM(RSCMType.VARBIT))
            assertEquals(layoutVarp, type?.varp, "$varbit base varp")
        }
    }

    /** The room a tunnel tile belongs to, or null for a corridor between two rooms. */
    private fun roomOf(tile: CoordGrid): TunnelRoom? {
        if (tile.x <= 3528 || tile.x >= 3575 || tile.z >= 9718 || tile.z <= 9671) {
            return TunnelRoom.RING
        }
        val column =
            when (tile.x) {
                in 3529..3540 -> 0
                in 3546..3557 -> 1
                in 3563..3574 -> 2
                else -> return null
            }
        val row =
            when (tile.z) {
                in 9706..9717 -> 0
                in 9689..9700 -> 1
                in 9672..9683 -> 2
                else -> return null
            }
        return TunnelRoom.entries[row * 3 + column]
    }

    private fun spawnsOf(loc: String): List<Pair<CoordGrid, LocAngle>> =
        spawnsOfId(loc.asRSCM(RSCMType.LOC))

    private fun spawnsOfId(id: Int): List<Pair<CoordGrid, LocAngle>> {
        val square = MapSquareKey.from(CoordGrid(3552, 9696, 0))
        val data = checkNotNull(cache.data(MAPS, square.id, 1))
        return MapLocListDecoder.decode(InlineByteBuf(data))
            .spawns
            .map(::MapLocDefinition)
            .filter { it.id == id }
            .map {
                val at = square.toCoords(it.level).translate(it.localX, it.localZ)
                at to LocAngle.entries[it.angle]
            }
    }

    private fun tileFlags(coords: CoordGrid): Int {
        val square = MapSquareKey.from(coords)
        val data =
            cache.data(MAPS, square.id, 0) ?: return MapTileSimpleDefinition.BLOCK_MAP_SQUARE
        val tiles = MapTileDecoder.decode(InlineByteBuf(data))
        return tiles[coords.x and 63, coords.z and 63, coords.level].toInt()
    }

    private fun isOpenFloor(coords: CoordGrid): Boolean =
        tileFlags(coords) and MapTileSimpleDefinition.BLOCK_MAP_SQUARE == 0

    private companion object {
        const val MODELS = 7
        const val CRYPT_LEVEL = 3

        lateinit var cache: Cache
        lateinit var live: Cache

        @JvmStatic
        @BeforeAll
        fun loadCache() {
            cache = ServerCacheManager.init(240)
            live = Cache.load(Path.of(".data/cache/LIVE"))
        }
    }
}
