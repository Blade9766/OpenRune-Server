package org.rsmod.content.quest.area.mortton.shades

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.table.QuestRow
import org.rsmod.content.quest.area.mortton.shades.catacombs.ShadeCatacombs
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey

/**
 * Pins the cache facts Shades of Mort'ton is written against: the quest row, the temple's walls
 * and altar, the pyre stands the rewards land on, and the varbits sharing `varp.morttonmulti`.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class ShadesOfMorttonCacheTest {
    @Test
    fun questRowMatchesTheScript() {
        val row = QuestRow.getRow("dbrow.quest_shadesofmortton".asRSCM())
        assertEquals(ShadesOfMorttonQuest.STAGE_COMPLETE, row.endstate)
        assertEquals(3, row.questpoints)
    }

    @Test
    fun templeWallsAndAltarStandWhereTheTempleExpects() {
        val corners = listOf(CoordGrid(3504, 3314, 0), CoordGrid(3504, 3318, 0), CoordGrid(3508, 3314, 0), CoordGrid(3508, 3318, 0))
        for (corner in corners) {
            assertLocAt("loc.templewallcorner_base", corner)
        }
        val straights =
            listOf(
                CoordGrid(3504, 3315, 0),
                CoordGrid(3504, 3316, 0),
                CoordGrid(3504, 3317, 0),
                CoordGrid(3505, 3314, 0),
                CoordGrid(3505, 3318, 0),
                CoordGrid(3506, 3318, 0),
                CoordGrid(3507, 3314, 0),
                CoordGrid(3507, 3318, 0),
                CoordGrid(3508, 3315, 0),
                CoordGrid(3508, 3316, 0),
                CoordGrid(3508, 3317, 0),
            )
        for (wall in straights) {
            assertLocAt("loc.templewall_base", wall)
        }
        assertLocAt("loc.templefire_altar_nofire_broken", MorttonCoords.FIRE_ALTAR)
    }

    @Test
    fun diaryShelfAndTableAreInHerbisHouse() {
        assertLocAt("loc.shades_experimentshelf", MorttonCoords.SHELF)
        assertLocAt("loc.shades_experimenttable", MorttonCoords.SMASHED_TABLE)
    }

    @Test
    fun resourcesVarbitSharesTheMultiVarp() {
        val multi = "varp.morttonmulti".asRSCM(RSCMType.VARP)
        val resources = checkNotNull(ServerCacheManager.getVarbit("varbit.temple_resources".asRSCM(RSCMType.VARBIT)))
        assertEquals(multi, resources.varp)
        assertTrue(resources.endBit - resources.startBit + 1 >= 10, "resources must hold 1000 units")
        val perm = checkNotNull(ServerCacheManager.getVarbit("varbit.made_perm_serum".asRSCM(RSCMType.VARBIT)))
        assertEquals(multi, perm.varp)
        val cureBits =
            listOf(
                ShadesOfMorttonQuest.ULSQUIRE_TEMP_BIT,
                ShadesOfMorttonQuest.RAZMIRE_TEMP_BIT,
                ShadesOfMorttonQuest.ULSQUIRE_PERM_BIT,
                ShadesOfMorttonQuest.RAZMIRE_PERM_BIT,
            )
        for (bit in cureBits) {
            assertTrue(bit !in resources.startBit..resources.endBit && bit != perm.startBit)
        }
    }

    @Test
    fun overlayScriptListensToTheTempleVarps() {
        val overlay = ServerCacheManager.getInterface("interface.flamtaer_status".asRSCM(RSCMType.INTERFACE))
        val watched = overlay.toString()
        for (varp in listOf("varp.temple_repaired_p", "varp.temple_resources_p", "varp.temple_sanctity_p")) {
            assertTrue(watched.contains(varp.asRSCM(RSCMType.VARP).toString()), "$varp is not watched")
        }
    }

    @Test
    fun everyCatacombDoorIsWhereTheDoorListSays() {
        val doors = listOf("bronze", "steel", "black", "silver", "gold").map { "loc.shadelair_${it}door" }
        val ids = doors.map { it.asRSCM(RSCMType.LOC) }.toSet()
        val square = MapSquareKey.from(ShadeCatacombs.CATACOMB_ENTRY)
        val data = checkNotNull(cache.data(MAPS, square.id, 1))
        val placed =
            MapLocListDecoder.decode(InlineByteBuf(data)).spawns.map(::MapLocDefinition)
                .filter { it.id in ids }
                .map { square.toCoords(it.level).translate(it.localX, it.localZ) }
                .toSet()
        assertEquals(placed, ShadeCatacombs.DOORS.toSet())
        assertTrue(ShadeCatacombs.DOORS.size <= 32, "door bits must fit one varp")
        assertTrue(ShadeCatacombs.EXIT_DOOR in ShadeCatacombs.DOORS)
    }

    @Test
    fun catacombEntranceAndAltarStandWhereTheScriptExpects() {
        assertLocAt("loc.shadelairentrancel", ShadeCatacombs.ENTRANCE_LEFT_COORDS)
        assertLocAt("loc.shadelairentrancer", ShadeCatacombs.ENTRANCE_RIGHT_COORDS)
        assertLocAt("loc.shade_lair_temple_altar", CoordGrid(3492, 9694, 0))
    }

    private fun assertLocAt(loc: String, coords: CoordGrid) {
        val id = loc.asRSCM(RSCMType.LOC)
        val square = MapSquareKey.from(coords)
        val data = checkNotNull(cache.data(MAPS, square.id, 1)) { "no locs in ${square.id}" }
        val spawns = MapLocListDecoder.decode(InlineByteBuf(data)).spawns.map(::MapLocDefinition)
        val match =
            spawns.any {
                it.id == id && square.toCoords(it.level).translate(it.localX, it.localZ) == coords
            }
        assertTrue(match, "$loc is not at $coords")
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
