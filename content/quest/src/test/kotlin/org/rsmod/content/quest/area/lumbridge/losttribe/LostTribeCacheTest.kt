package org.rsmod.content.quest.area.lumbridge.losttribe

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
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_ASKING
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_CONTACT
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_SILVERWARE_MISSING
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_TREATY
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_TUNNEL_DUG
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey

/**
 * Pins the cache facts The Lost Tribe is written against: the stage values the multinpcs and the
 * cellar multiloc switch on, the varbit layout and where the quest's locs stand.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class LostTribeCacheTest {
    @Test
    fun questRowEndsAtTheCompleteStage() {
        val row = QuestRow.getRow("dbrow.${LostTribeQuest.QUEST_KEY}".asRSCM())
        assertEquals(STAGE_COMPLETE, row.endstate)
        assertEquals(1, row.questpoints)
    }

    @Test
    fun theStageVarbitSitsOnTheQuestVarpAndHoldsEveryStage() {
        val varbit = checkNotNull(ServerCacheManager.getVarbit("varbit.lost_tribe_quest".asRSCM(RSCMType.VARBIT)))
        assertEquals("varp.lost_tribe".asRSCM(RSCMType.VARP), varbit.varp)
        assertTrue((1 shl (varbit.endBit - varbit.startBit + 1)) > STAGE_COMPLETE)
        val witness = checkNotNull(ServerCacheManager.getVarbit("varbit.lost_tribe_contact".asRSCM(RSCMType.VARBIT)))
        assertEquals(LostTribeQuest.Witness.entries.size, 1 shl (witness.endBit - witness.startBit + 1))
    }

    @Test
    fun sigmundLeavesTheCastleWhenTheTreatyIsSigned() {
        val castle = transforms(LostTribeQuest.SIGMUND)
        assertEquals(npcId("npc.lost_tribe_sigmund_there"), castle[STAGE_SILVERWARE_MISSING])
        assertEquals(-1, castle.getOrElse(STAGE_TREATY) { castle.last() })
        val hideout = transforms(LostTribeQuest.SIGMUND_HAM)
        assertEquals(-1, hideout[STAGE_SILVERWARE_MISSING])
        assertEquals(npcId("npc.lost_tribe_sigmund_ham_there"), hideout[STAGE_TREATY])
        assertEquals(npcId("npc.lost_tribe_sigmund_ham_there"), hideout[STAGE_COMPLETE])
        assertEquals("Pickpocket", npc("npc.lost_tribe_sigmund_there").actions.getOpOrNull(2))
    }

    @Test
    fun kazgarAndMistagsFollowAppearOnceTheSilverwareGoesMissing() {
        val kazgar = transforms(LostTribeQuest.KAZGAR)
        assertEquals(-1, kazgar[STAGE_CONTACT])
        assertEquals(npcId("npc.lost_tribe_guide_2ops"), kazgar[STAGE_SILVERWARE_MISSING])
        assertEquals(npcId("npc.lost_tribe_guide_2ops"), kazgar[STAGE_COMPLETE])
        val mistag = transforms(LostTribeQuest.MISTAG)
        assertEquals(npcId("npc.lost_tribe_mistag_1op"), mistag[STAGE_CONTACT])
        assertEquals(npcId("npc.lost_tribe_mistag_2ops"), mistag[STAGE_COMPLETE])
        assertEquals("Follow", npc("npc.lost_tribe_mistag_2ops").actions.getOpOrNull(2))
    }

    @Test
    fun theCellarWallIsRubbleUntilDugThenAHole() {
        val wall = checkNotNull(ServerCacheManager.getObject(LostTribeCellar.CELLAR_WALL.asRSCM(RSCMType.LOC)))
        val transforms = checkNotNull(wall.transforms)
        assertEquals(locId(LostTribeCellar.RUBBLE), transforms[STAGE_ASKING])
        assertEquals(locId("loc.lost_tribe_cavewall_hole_walldecor"), transforms[STAGE_TUNNEL_DUG])
        val hole = checkNotNull(ServerCacheManager.getObject(locId("loc.lost_tribe_cavewall_hole_walldecor")))
        assertEquals("Squeeze-through", hole.actions.getOpOrNull(0))
    }

    @Test
    fun theQuestLocsStandWhereTheScriptsExpect() {
        assertLocAt(LostTribeCellar.CELLAR_WALL, LostTribeCellar.CELLAR_SIDE)
        assertLocAt(LostTribeCellar.CELLAR_WALL_BACK, LostTribeCellar.TUNNEL_SIDE)
        assertLocAt("loc.lost_tribe_bookcase", CoordGrid(3207, 3496, 0))
        assertLocAt("loc.lost_tribe_chest", CoordGrid(3209, 3217, 1))
        assertLocAt("loc.lost_tribe_crate", CoordGrid(3152, 9645, 0))
        assertLocAt("loc.lost_tribe_trap_floor", CoordGrid(3238, 9622, 0))
        assertLocAt("loc.lost_tribe_trap_ceiling", CoordGrid(3255, 9616, 0))
    }

    @Test
    fun nardokTradesOnHisThirdOp() {
        val nardok = npc(LostTribeQuest.NARDOK)
        assertEquals("Talk-to", nardok.actions.getOpOrNull(0))
        assertEquals("Trade", nardok.actions.getOpOrNull(2))
    }

    private fun npc(name: String) =
        checkNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC))) { "$name missing" }

    private fun npcId(name: String) = name.asRSCM(RSCMType.NPC)

    private fun locId(name: String) = name.asRSCM(RSCMType.LOC)

    private fun transforms(name: String): List<Int> = checkNotNull(npc(name).transforms)

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
