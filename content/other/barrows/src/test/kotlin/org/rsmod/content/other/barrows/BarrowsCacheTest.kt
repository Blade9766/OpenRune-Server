package org.rsmod.content.other.barrows

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
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.routefinder.loc.LocLayerConstants

/**
 * Pins the map facts the Barrows scripts rely on: where each brother's crypt locs stand, that
 * every tile a player is put on is open floor, and that the tunnel door leaves join exactly the
 * rooms [TunnelCorridor] says they do.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class BarrowsCacheTest {
    @Test
    fun eachCryptHoldsItsBrothersStairsAndSarcophagus() {
        for (brother in Brother.entries) {
            val stairs = spawnsOf(brother.stairs)
            val sarcophagus = spawnsOf(brother.sarcophagus)
            assertEquals(1, stairs.size, "${brother.stairs} spawns")
            assertEquals(1, sarcophagus.size, "${brother.sarcophagus} spawns")
            assertTrue(stairs.single().first.chebyshevDistance(brother.cryptLanding) <= 3)
            assertTrue(stairs.single().first.level == BarrowsCoords.CRYPT_LEVEL)
            assertTrue(sarcophagus.single().first.chebyshevDistance(brother.cryptLanding) <= 20)
        }
    }

    @Test
    fun playersAreOnlyEverPutOnOpenFloor() {
        val tiles =
            Brother.entries.flatMap { listOf(it.mound, it.cryptLanding) } +
                BarrowsCoords.CORNER_ENTRIES
        for (tile in tiles) {
            assertTrue(isOpenFloor(tile), "$tile is blocked")
        }
        for (dx in 0..1) {
            for (dz in 0..1) {
                val tile = BarrowsCoords.CHEST.translate(dx, dz)
                assertTrue(isOpenFloor(tile), "chest tile $tile is blocked")
                assertTrue(BarrowsCoords.inCentreRoom(tile))
            }
        }
    }

    @Test
    fun cornerEntriesSitBesideTheLadders() {
        val ladders =
            (20674..20677).flatMap { id -> spawnsOfId(id) }.map { it.first }
        assertEquals(4, ladders.size)
        for (entry in BarrowsCoords.CORNER_ENTRIES) {
            assertTrue(ladders.any { it.chebyshevDistance(entry) <= 1 }, "no ladder by $entry")
        }
    }

    @Test
    fun doorLeavesJoinTheRoomsTheirCorridorLinks() {
        for (corridor in TunnelCorridor.entries) {
            val leaves = spawnsOf(corridor.rightDoor) + spawnsOf(corridor.leftDoor)
            assertEquals(4, leaves.size, "leaves of $corridor")
            val rooms = mutableSetOf<TunnelRoom>()
            for ((coords, angle) in leaves) {
                val door = boundDoor(coords, angle)
                val sides = listOf(coords, BarrowsTunnelScript.tileAcross(door, coords))
                for (side in sides) {
                    assertTrue(isOpenFloor(side), "$corridor door side $side is blocked")
                    roomOf(side)?.let(rooms::add)
                }
                assertEquals(coords, BarrowsTunnelScript.tileAcross(door, sides[1]))
            }
            val expected = corridor.links.flatMap { it.toList() }.toSet()
            assertEquals(expected, rooms, "rooms touched by $corridor")
        }
    }

    @Test
    fun puzzleAndFaceModelsExist() {
        val models =
            (0 until BarrowsPuzzle.TYPES).flatMap {
                BarrowsPuzzle.sequence(it) + BarrowsPuzzle.answers(it, 0)
            } + Brother.entries.map { it.faceModel }
        for (model in models) {
            assertNotNull(live.data(MODELS, model, 0), "model $model")
        }
    }

    @Test
    fun everyNamedTypeResolves() {
        for (brother in Brother.entries) {
            for (obj in brother.items) {
                assertNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)), obj)
            }
            assertNotNull(ServerCacheManager.getNpc(brother.npc.asRSCM(RSCMType.NPC)), brother.npc)
        }
        for (npc in BarrowsSpawns.TUNNEL_MONSTERS) {
            assertNotNull(ServerCacheManager.getNpc(npc.asRSCM(RSCMType.NPC)), npc)
        }
        val runVarp = "varp.barrows_run".asRSCM(RSCMType.VARP)
        val runBits =
            listOf(
                BarrowsRun.TUNNEL_BROTHER,
                BarrowsRun.PUZZLE_TYPE,
                BarrowsRun.PUZZLE_ANSWER,
                BarrowsRun.PUZZLE_SOLVED,
                BarrowsRun.CHEST_LOOTED,
            )
        for (varbit in runBits) {
            val type = ServerCacheManager.getVarbit(varbit.asRSCM(RSCMType.VARBIT))
            assertEquals(runVarp, type?.varp, "$varbit base varp")
        }
        for (obj in BarrowsAreaScript.IMMUNITY_HILTS + BarrowsLoot.ELITE_CLUE) {
            assertNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)), obj)
        }
    }

    /** The room a tunnel tile belongs to, or null for a corridor between two rooms. */
    private fun roomOf(tile: CoordGrid): TunnelRoom? {
        if (tile.x <= 3528 || tile.x >= 3575 || tile.z >= 9718 || tile.z <= 9671) {
            return TunnelRoom.Ring
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

    private fun boundDoor(coords: CoordGrid, angle: LocAngle): BoundLocInfo {
        val shape = LocShape.WallStraight
        val entity = LocEntity(0, shape.id, angle.id)
        return BoundLocInfo(coords, entity, LocLayerConstants.of(shape.id), 1, 1, 0)
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
        val data = cache.data(MAPS, square.id, 0) ?: return MapTileSimpleDefinition.BLOCK_MAP_SQUARE
        return MapTileDecoder.decode(InlineByteBuf(data))[coords.x and 63, coords.z and 63, coords.level]
            .toInt()
    }

    private fun isOpenFloor(coords: CoordGrid): Boolean =
        tileFlags(coords) and MapTileSimpleDefinition.BLOCK_MAP_SQUARE == 0

    private companion object {
        const val MODELS = 7

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
