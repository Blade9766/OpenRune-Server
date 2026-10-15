package org.rsmod.content.quest.area.lumbridge.dorgeshuun

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
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_MACHINE_SMASHED
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_MILL
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_REVIVED
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_SHOWDOWN
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_TEARS
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_ZANIK_DEAD
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey

/**
 * Pins the cache facts Death to the Dorgeshuun is written against: the stage values the multis
 * switch on, the varbit layout, the ops the scripts bind and where the quest's locs stand.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class DttdCacheTest {
    @Test
    fun questRowEndsAtTheCompleteStage() {
        val row = QuestRow.getRow("dbrow.${DeathToTheDorgeshuunQuest.QUEST_KEY}".asRSCM())
        assertEquals(STAGE_COMPLETE, row.endstate)
        assertEquals(1, row.questpoints)
    }

    @Test
    fun theStageVarbitSitsOnTheQuestVarpAndHoldsEveryStage() {
        val varbit = checkNotNull(ServerCacheManager.getVarbit("varbit.dttd_main".asRSCM(RSCMType.VARBIT)))
        assertEquals("varp.dttd_base".asRSCM(RSCMType.VARP), varbit.varp)
        assertTrue((1 shl (varbit.endBit - varbit.startBit + 1)) > STAGE_COMPLETE)
        val guard = checkNotNull(ServerCacheManager.getVarbit("varbit.dttd_guard_1_dead".asRSCM(RSCMType.VARBIT)))
        assertEquals("varp.dttd_temp".asRSCM(RSCMType.VARP), guard.varp)
    }

    @Test
    fun theMillIsBusyFromTheTearsUntilTheFight() {
        val dwarf = npcTransforms(WaterMill.DWARF)
        assertEquals(-1, dwarf[STAGE_ZANIK_DEAD])
        assertEquals(npcId("npc.dttd_delivery_dwarf_there"), dwarf[STAGE_TEARS])
        assertEquals(npcId("npc.dttd_delivery_dwarf_there"), dwarf[STAGE_MILL])
        assertEquals(-1, dwarf[STAGE_SHOWDOWN])
        val trapdoor = locTransforms(WaterMill.MILL_TRAPDOOR)
        assertEquals(locId("loc.dttd_mill_trapdoor_closed"), trapdoor[STAGE_ZANIK_DEAD])
        assertEquals(locId("loc.dttd_mill_trapdoor_open"), trapdoor[STAGE_TEARS])
        assertEquals(locId("loc.dttd_mill_trapdoor_open"), trapdoor[STAGE_COMPLETE])
        assertEquals("Search", loc(WaterMill.EMPTY_CRATE).actions.getOpOrNull(0))
        assertEquals("Smash", loc(WaterMill.DRILL).actions.getOpOrNull(0))
    }

    @Test
    fun zaniksBodyLiesBeforeJunaWhileTheTearsAreCollected() {
        val body = locTransforms("loc.dttd_zanik_corpse_juna")
        assertEquals(locId("loc.dttd_zanik_revival"), body[STAGE_TEARS])
        assertEquals(-1, body[STAGE_REVIVED])
        val corpse = locTransforms("loc.dttd_zanik_corpse")
        assertEquals(locId("loc.dttd_zanik_dead_body"), corpse[1])
        assertEquals("Inspect", loc("loc.dttd_zanik_dead_body").actions.getOpOrNull(0))
    }

    @Test
    fun theTunnelToTheMineOpensWithTheQuest() {
        val millside = locTransforms(WaterMill.TUNNEL_MILLSIDE)
        assertEquals(locId("loc.dttd_cave_entrance_millside_blocked"), millside[STAGE_MACHINE_SMASHED])
        assertEquals(locId("loc.dttd_cave_entrance_millside_open"), millside[STAGE_COMPLETE])
        val dartog = npcTransforms(DeathToTheDorgeshuunQuest.DARTOG)
        assertEquals(-1, dartog[STAGE_MACHINE_SMASHED])
        assertEquals(npcId("npc.dttd_maze_guide_there"), dartog[STAGE_COMPLETE])
        val mistag = npcTransforms("npc.lost_tribe_mistag")
        assertEquals(npcId("npc.lost_tribe_mistag_3ops"), mistag[DeathToTheDorgeshuunQuest.LOST_TRIBE_AFTER_DTTD])
        assertEquals("Watermill", npc("npc.lost_tribe_mistag_3ops").actions.getOpOrNull(3))
    }

    @Test
    fun theHideoutTrapdoorFollowsItsStateVarbit() {
        val trapdoor = locTransforms(HamHideout.HIDDEN_TRAPDOOR)
        assertEquals(locId("loc.dttd_ham_trapdoor_hidden"), trapdoor[HamHideout.TRAPDOOR_RUBBLE])
        assertEquals(locId("loc.dttd_ham_trapdoor_closed"), trapdoor[HamHideout.TRAPDOOR_HIDDEN])
        assertEquals(locId("loc.dttd_ham_trapdoor_open"), trapdoor[HamHideout.TRAPDOOR_OPEN])
        assertEquals(locId("loc.dttd_ham_trapdoor_hidden"), trapdoor[HamHideout.TRAPDOOR_BURIED])
        assertEquals("Pick-lock", loc("loc.dttd_ham_trapdoor_closed").actions.getOpOrNull(1))
    }

    @Test
    fun zanikWaitsInTheCellarOnHerOwnVarbit() {
        val cellar = npcTransforms(DeathToTheDorgeshuunQuest.ZANIK_CELLAR)
        assertEquals(npcId("npc.dttd_zanik_marked"), cellar[1])
        assertEquals("Talk-to", npc(DeathToTheDorgeshuunQuest.ZANIK_FOLLOWER).actions.getOpOrNull(0))
    }

    @Test
    fun theCrateCarriesZanikInBothHands() {
        val crate = checkNotNull(ServerCacheManager.getItem(DeathToTheDorgeshuunQuest.ZANIK_CRATE.asRSCM(RSCMType.OBJ)))
        assertEquals(DeathToTheDorgeshuunQuest.WEAPON_SLOT, crate.wearpos1)
        assertEquals(DeathToTheDorgeshuunQuest.SHIELD_SLOT, crate.wearpos2)
    }

    @Test
    fun theQuestLocsStandWhereTheScriptsExpect() {
        assertLocAt(HamHideout.HIDDEN_TRAPDOOR, HamHideout.TRAPDOOR_TILE)
        assertLocAt(WaterMill.MILL_TRAPDOOR, WaterMill.TRAPDOOR)
        assertLocAt("loc.dttd_zanik_corpse", CoordGrid(3161, 3245, 0))
        assertLocAt(WaterMill.DRILL, WaterMill.DRILL_TILE)
        assertLocAt(WaterMill.CELLAR_LADDER, CoordGrid(2024, 5087, 0))
        assertLocAt(HamStorerooms.LADDER_UP, CoordGrid(2567, 5185, 0))
        assertLocAt(HamStorerooms.CRACK, CoordGrid(2569, 5190, 0))
        assertLocAt(HamStorerooms.CRACK, CoordGrid(2569, 5194, 0))
        assertLocAt("loc.poordoor_double_inner", CoordGrid(2571, 5204, 0))
        assertLocAt("loc.poordoor_doubler_inner", CoordGrid(2572, 5204, 0))
        assertLocAt("loc.tog_juna", CoordGrid(3252, 9516, 2))
        assertLocAt("loc.swamp_cave_steppingstone_b", CoordGrid(3221, 9554, 0))
    }

    private fun npc(name: String) =
        checkNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC))) { "$name missing" }

    private fun loc(name: String) =
        checkNotNull(ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC))) { "$name missing" }

    private fun npcId(name: String) = name.asRSCM(RSCMType.NPC)

    private fun locId(name: String) = name.asRSCM(RSCMType.LOC)

    private fun npcTransforms(name: String): List<Int> = checkNotNull(npc(name).transforms)

    private fun locTransforms(name: String): List<Int> = checkNotNull(loc(name).transforms)

    private fun assertLocAt(loc: String, coords: CoordGrid) {
        val id = locId(loc)
        val square = MapSquareKey.from(coords)
        val data = checkNotNull(cache.data(MAPS, square.id, 1)) { "no locs in ${square.id}" }
        val spawns = MapLocListDecoder.decode(InlineByteBuf(data)).spawns.map(::MapLocDefinition)
        val match =
            spawns.any { it.id == id && square.toCoords(it.level).translate(it.localX, it.localZ) == coords }
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
