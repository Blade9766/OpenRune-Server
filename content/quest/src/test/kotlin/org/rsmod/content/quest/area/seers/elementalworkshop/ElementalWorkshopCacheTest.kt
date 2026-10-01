package org.rsmod.content.quest.area.seers.elementalworkshop

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.baseVar
import dev.openrune.types.varp.bits
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.table.QuestRow
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey

/**
 * Pins the cache facts Elemental Workshop I is written against: the quest row, which varbit drives
 * which machine, where the controls stand (the east/west order depends on it), and that the two
 * server-only flags sit on free bits of the workshop varp.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class ElementalWorkshopCacheTest {
    @Test
    fun questRowMatchesTheStages() {
        val row = QuestRow.getRow("dbrow.${ElementalWorkshopQuest.QUEST_KEY}".asRSCM())
        assertEquals(ElementalWorkshopQuest.STAGE_COMPLETE, row.endstate)
        assertEquals(1, row.questpoints)
        val levels = row.requirementStats.associate { it.t0.displayName to it.t1 }
        assertEquals(mapOf("mining" to 20, "smithing" to 20, "crafting" to 20), levels)
    }

    @Test
    fun eachMachineFollowsItsQuestVarbit() {
        assertMulti("loc.elemental_workshop_valve_1", "varbit.elemental_workshop_gate2")
        assertMulti("loc.elemental_workshop_valve_2", "varbit.elemental_workshop_gate1")
        assertMulti("loc.elemental_workshop_wheel", "varbit.elemental_workshop_switch")
        assertMulti("loc.elemental_workshop_bellows_multiloc", "varbit.elemental_workshop_bellows_switch")
        assertMulti("loc.elemental_workshop_furnace", "varbit.elemental_workshop_fire")
        assertMulti("loc.elemental_workshop_furnace_glow", "varbit.elemental_workshop_fire")
        val wheel = loc("loc.elemental_workshop_wheel").transforms!!
        assertEquals("loc.elemental_workshop_wheel_anim".asRSCM(RSCMType.LOC), wheel[1])
        val bellows = loc("loc.elemental_workshop_bellows_multiloc").transforms!!
        assertEquals("loc.elemental_workshop_bellows_anim".asRSCM(RSCMType.LOC), bellows[1])
        val furnace = loc("loc.elemental_workshop_furnace").transforms!!
        assertEquals("loc.elemental_workshop_furnace_lit".asRSCM(RSCMType.LOC), furnace[1])
    }

    @Test
    fun theEastControlIsValveOne() {
        assertLocAt("loc.elemental_workshop_valve_1", CoordGrid(2726, 9908, 0))
        assertLocAt("loc.elemental_workshop_valve_2", CoordGrid(2713, 9908, 0))
    }

    @Test
    fun theEntranceAndStairsAreWhereTheScriptsExpect() {
        assertLocAt("loc.elemental_workshop_bookcase", CoordGrid(2716, 3481, 0))
        assertLocAt("loc.elemental_workshop_oddwall_l", CoordGrid(2709, 3495, 0))
        assertLocAt("loc.elemental_workshop_oddwall_r", CoordGrid(2710, 3495, 0))
        assertLocAt("loc.elemental_workshop_spiralstairstop", CoordGrid(2710, 3497, 0))
        assertLocAt("loc.elemental_workshop_spiralstairs", CoordGrid(2714, 9887, 0))
        val landing = WorkshopEntrance.WORKSHOP_LANDING
        assertTrue(landing.chebyshevDistance(CoordGrid(2714, 9887, 0)) <= 2)
    }

    @Test
    fun theServerFlagsUseFreeBitsOfTheWorkshopVarp() {
        val workshop = "varp.elemental_workshop_bits".asRSCM(RSCMType.VARP)
        val used = HashSet<Int>()
        for ((_, varbit) in ServerCacheManager.getVarbits()) {
            if (varbit.baseVar.id != workshop) continue
            for (bit in varbit.bits) assertTrue(used.add(bit), "bit $bit is shared")
        }
        for (flag in listOf("varbit.elemental_workshop_ore_found", "varbit.elemental_workshop_metal_smelted")) {
            val varbit = ServerCacheManager.getVarbit(flag.asRSCM(RSCMType.VARBIT))!!
            assertEquals(workshop, varbit.baseVar.id, flag)
        }
    }

    @Test
    fun theRocksAndElementalsHaveTheExpectedOps() {
        val rock = ServerCacheManager.getNpc(ElementalRocks.ROCK.asRSCM(RSCMType.NPC))!!
        assertEquals("Mine", rock.actions.getOpOrNull(0))
        val awakened = ServerCacheManager.getNpc(ElementalRocks.AWAKENED.asRSCM(RSCMType.NPC))!!
        assertEquals(35, awakened.combatLevel)
        assertEquals("Read", item(ElementalWorkshopQuest.BOOK).interfaceOptions[0])
        assertEquals("Read", item(ElementalWorkshopQuest.SLASHED_BOOK).interfaceOptions[0])
    }

    private fun loc(name: String) =
        checkNotNull(ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC))) { "$name missing" }

    private fun item(name: String) =
        checkNotNull(ServerCacheManager.getItem(name.asRSCM(RSCMType.OBJ))) { "$name missing" }

    private fun assertMulti(loc: String, varbit: String) {
        assertEquals(varbit.asRSCM(RSCMType.VARBIT), loc(loc).multiVarBit, loc)
    }

    private fun assertLocAt(loc: String, coords: CoordGrid) {
        val id = loc.asRSCM(RSCMType.LOC)
        val square = MapSquareKey.from(coords)
        val data = checkNotNull(cache.data(MAPS, square.id, 1)) { "no locs in ${square.id}" }
        val spawns = MapLocListDecoder.decode(InlineByteBuf(data)).spawns.map(::MapLocDefinition)
        val match = spawns.any { it.id == id && square.toCoords(it.level).translate(it.localX, it.localZ) == coords }
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
