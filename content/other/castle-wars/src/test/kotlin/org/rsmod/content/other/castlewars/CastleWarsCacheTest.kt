package org.rsmod.content.other.castlewars

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.tile.MapTileSimpleDefinition
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
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
 * Pins the map facts the Castle Wars scripts are written against: every loc the game swaps or
 * checks must stand exactly where [CastleWars] and [Team] say, on the level the engine sees it
 * (castle floors are bridged, so a loc authored on level 1 sits on level 0), and every tile a
 * player is dropped on must be open floor.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class CastleWarsCacheTest {
    @Test
    fun standsCatapultsAndDoorsStandWhereTheScriptsSwapThem() {
        for (team in Team.entries) {
            assertLoc(team.standLoc, team.standCoords, team.standAngle)
            assertLoc(team.catapultLoc, team.catapultCoords, team.catapultAngle)
            for (leaf in team.mainDoors) {
                assertLoc(leaf.closed, leaf.coords, leaf.angle)
            }
            assertLoc(team.sideDoor.closed, team.sideDoor.coords, team.sideDoor.angle)
        }
    }

    @Test
    fun openedGatesSwingIntoTheirOwnCastle() {
        for (team in Team.entries) {
            for (leaf in team.mainDoors) {
                assertTrue(leaf.openCoords in team.castle, "${leaf.open} opens outside its castle")
            }
        }
    }

    @Test
    fun tunnelsStartBlockedAndEachHasFourCaveWalls() {
        for (tunnel in Team.entries.flatMap(Team::tunnels)) {
            assertLoc("loc.castlewars_blocked_tunnel_1", tunnel, CastleWars.TUNNEL_ANGLES.getValue(tunnel))
            assertEquals(4, CastleWars.CAVE_WALLS.values.count { it == tunnel }, "cave walls for $tunnel")
        }
        for (wall in CastleWars.CAVE_WALLS.keys) {
            assertLoc("loc.castlewars_cavewall_rockslide", wall, null)
        }
    }

    @Test
    fun barriersStonesAndLinkedStairsMatchTheMap() {
        for (barrier in CastleWars.SPAWN_BARRIERS.keys) {
            val name =
                if (barrier in Team.Saradomin.spawnArea) {
                    "loc.castlewars_saradomin_spawndoor"
                } else {
                    "loc.castlewars_zamorak_spawndoor"
                }
            assertLoc(name, barrier, null)
        }
        for ((barrier, sides) in CastleWars.SPAWN_BARRIERS) {
            val team = Team.entries.first { barrier in it.spawnArea }
            assertTrue(sides.first in team.spawnArea, "inside of $barrier")
            assertTrue(sides.second !in team.spawnArea, "outside of $barrier")
        }
        for (stone in CastleWars.STEPPING_STONES) {
            assertLoc("loc.castlewars_steping_stone", stone, null)
        }
        assertLoc("loc.castlewars_outsidestairs_saradomin_linked", CoordGrid(2417, 3074, 0), null)
        assertLoc("loc.castlewars_outsidestairs_zamorak_linked", CoordGrid(2382, 3131, 0), null)
    }

    @Test
    fun playersAreOnlyEverDroppedOnOpenFloor() {
        val tiles =
            Team.entries.flatMap { listOf(it.spawnRoom, it.waitingRoom) } +
                CastleWars.LOBBY +
                CastleWars.SPAWN_BARRIERS.values.flatMap { listOf(it.first, it.second) } +
                CastleWars.LINKED_STAIRS.values.flatMap { listOf(it.first, it.second) } +
                CastleWars.CASTLE_STAIRS.values
        for (tile in tiles) {
            assertTrue(isOpenFloor(tile), "$tile is blocked")
        }
        for (team in Team.entries) {
            assertTrue(team.spawnRoom in team.spawnArea, "${team.name} respawn tile")
            assertTrue(team.waitingRoom in team.waitingArea, "${team.name} waiting tile")
        }
    }

    @Test
    fun everyGameItemAndTableSupplyExists() {
        for (obj in CastleWars.GAME_ITEMS + CastleWars.TABLE_ITEMS.values + CastleWars.BRACELETS) {
            assertNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)), obj)
        }
        for (table in CastleWars.TABLE_ITEMS.keys) {
            assertNotNull(ServerCacheManager.getObject(table.asRSCM(RSCMType.LOC)), table)
        }
    }

    private fun effectiveLevel(x: Int, z: Int, rawLevel: Int): Int {
        if (rawLevel == 0) {
            return 0
        }
        return if (tileFlags(x, z, 1) and MapTileSimpleDefinition.LINK_BELOW != 0) rawLevel - 1 else rawLevel
    }

    private fun tileFlags(x: Int, z: Int, level: Int): Int {
        val square = MapSquareKey.from(CoordGrid(x, z, 0))
        val data = cache.data(MAPS, square.id, 0) ?: return MapTileSimpleDefinition.BLOCK_MAP_SQUARE
        return MapTileDecoder.decode(InlineByteBuf(data))[x and 63, z and 63, level].toInt()
    }

    private fun isOpenFloor(coords: CoordGrid): Boolean {
        val bridged = tileFlags(coords.x, coords.z, 1) and MapTileSimpleDefinition.LINK_BELOW != 0
        val raw = if (bridged) coords.level + 1 else coords.level
        return tileFlags(coords.x, coords.z, raw) and MapTileSimpleDefinition.BLOCK_MAP_SQUARE == 0
    }

    private fun assertLoc(loc: String, coords: CoordGrid, angle: LocAngle?) {
        val id = loc.asRSCM(RSCMType.LOC)
        val square = MapSquareKey.from(coords)
        val data = checkNotNull(cache.data(MAPS, square.id, 1)) { "no locs in ${square.id}" }
        val spawns = MapLocListDecoder.decode(InlineByteBuf(data)).spawns.map(::MapLocDefinition)
        val match =
            spawns.any {
                val at = square.toCoords(0).translate(it.localX, it.localZ)
                it.id == id &&
                    at.x == coords.x &&
                    at.z == coords.z &&
                    effectiveLevel(at.x, at.z, it.level) == coords.level &&
                    (angle == null || it.angle == angle.id)
            }
        assertTrue(match, "$loc is not at $coords" + (angle?.let { " facing $it" } ?: ""))
    }

    private companion object {
        lateinit var cache: dev.openrune.filesystem.Cache

        @JvmStatic
        @BeforeAll
        fun loadCache() {
            cache = ServerCacheManager.init(240)
        }
    }
}
