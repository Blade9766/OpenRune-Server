package org.rsmod.content.quest.area.desert.princealirescue

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.npc.MapNpcDefinition
import dev.openrune.map.npc.MapNpcListDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.baseVar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.table.QuestRow
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.CELL_DOOR
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.JOE
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.LADY_KELI
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.PRINCE_ALI_CELL
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.PRINCE_ALI_PALACE
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.QUEST_KEY
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.STAGE_ALI_ESCAPED
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.STAGE_JOE_DRUNK
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.STAGE_KELI_TIED
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey

/**
 * Pins the cache facts the quest is written against: the quest row, the `varp.princequest`
 * multinpcs that hide Keli, Joe and the Prince as the rescue goes on, where the cell gate and
 * its occupants stand, and the server-only progress varbits.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class PrinceAliRescueCacheTest {

    @Test
    fun questRowMatchesTheStagesTheScriptUses() {
        val row = QuestRow.getRow("dbrow.$QUEST_KEY".asRSCM())
        assertEquals(STAGE_COMPLETE, row.endstate)
        assertEquals(3, row.questpoints)
    }

    @Test
    fun theJailMultinpcsFollowTheStage() {
        assertShownUntil("npc.lady_keli", LADY_KELI, STAGE_JOE_DRUNK, STAGE_KELI_TIED)
        assertShownUntil("npc.prince_ali_prison", PRINCE_ALI_CELL, STAGE_KELI_TIED, STAGE_ALI_ESCAPED)
        assertShownUntil("npc.joe", JOE, STAGE_ALI_ESCAPED, STAGE_COMPLETE)
        val palace = npc("npc.prince_ali_palace")
        assertEquals(PRINCE_ALI_PALACE.asRSCM(RSCMType.NPC), palace.transforms!![STAGE_ALI_ESCAPED])
        assertEquals(-1, palace.transforms!![0])
    }

    @Test
    fun theCellGateSeparatesThePrinceFromHisGuard() {
        val gate = CoordGrid(3123, 3243, 0)
        assertLocAt(CELL_DOOR, gate)
        assertNpcAt("npc.prince_ali_prison", CoordGrid(3123, 3242, 0))
        assertNpcAt("npc.joe", CoordGrid(3123, 3245, 0))
        assertNpcAt("npc.lady_keli", CoordGrid(3128, 3244, 0))
    }

    @Test
    fun theProgressVarbitsShareOneServerVarp() {
        val varp = "varp.princeali_state".asRSCM(RSCMType.VARP)
        for (name in listOf("varbit.princeali_key", "varbit.princeali_met_leela", "varbit.princeali_keli_recruit")) {
            val varbit = checkNotNull(ServerCacheManager.getVarbit(name.asRSCM(RSCMType.VARBIT))) { name }
            assertEquals(varp, varbit.baseVar.id, name)
        }
    }

    private fun assertShownUntil(base: String, vis: String, lastShown: Int, firstHidden: Int) {
        val type = npc(base)
        assertEquals("varp.princequest".asRSCM(RSCMType.VARP), type.multiVarp)
        val transforms = checkNotNull(type.transforms)
        assertEquals(vis.asRSCM(RSCMType.NPC), transforms[0])
        assertEquals(vis.asRSCM(RSCMType.NPC), transforms[lastShown])
        assertTrue(firstHidden >= transforms.size || transforms[firstHidden] == -1, "$base at $firstHidden")
    }

    private fun npc(name: String) =
        checkNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC))) { "$name missing" }

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

    private fun assertNpcAt(npc: String, coords: CoordGrid) {
        val id = npc.asRSCM(RSCMType.NPC)
        val square = MapSquareKey.from(coords)
        val data = checkNotNull(cache.data(MAPS, square.id, 5)) { "no npcs in ${square.id}" }
        val spawns = MapNpcListDecoder.decode(InlineByteBuf(data)).packedSpawns.map(::MapNpcDefinition)
        val match =
            spawns.any {
                it.id == id && square.toCoords(it.level).translate(it.localX, it.localZ) == coords
            }
        assertTrue(match, "$npc is not at $coords")
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
