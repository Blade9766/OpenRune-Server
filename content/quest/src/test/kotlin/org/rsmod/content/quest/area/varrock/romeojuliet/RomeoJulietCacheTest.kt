package org.rsmod.content.quest.area.varrock.romeojuliet

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.npc.MapNpcDefinition
import dev.openrune.map.npc.MapNpcListDecoder
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
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.JULIET_HIDDEN
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.JULIET_VISIBLE
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey

/**
 * Pins the cache facts Romeo & Juliet is written against: the quest row, Juliet's multinpc, the
 * op slots of the quest items and the Apothecary, and the scenery the two cutscenes stage around.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class RomeoJulietCacheTest {
    @Test
    fun questRowMatchesTheStagesTheScriptUses() {
        val row = QuestRow.getRow("dbrow.${RomeoJulietQuest.QUEST_KEY}".asRSCM())
        assertEquals(RomeoJulietQuest.STAGE_COMPLETE, row.endstate)
        assertEquals(QUEST_POINTS, row.questpoints)
    }

    @Test
    fun julietHidesWhileSheLiesInTheCrypt() {
        val juliet = npc("npc.juliet_multi_visible")
        assertEquals("varbit.romjul_juliet_visible".asRSCM(RSCMType.VARBIT), juliet.multiVarBit)
        val transforms = checkNotNull(juliet.transforms)
        assertEquals("npc.juliet".asRSCM(RSCMType.NPC), transforms[JULIET_VISIBLE])
        assertEquals(-1, transforms[JULIET_HIDDEN])
    }

    @Test
    fun julietIsSpawnedOnceOnHerBalcony() {
        val balcony = CoordGrid(3158, 3425, 1)
        val ids = setOf("npc.juliet", "npc.juliet_multi_visible").map { it.asRSCM(RSCMType.NPC) }
        val count = npcSpawns(balcony).count { it.id in ids && it.level == balcony.level }
        assertEquals(1, count, "Juliet is spawned $count times")
    }

    @Test
    fun theQuestItemsCarryTheOpsTheScriptBindsTo() {
        assertHeldOp("obj.cadava", 1, "Look-at")
        assertHeldOp("obj.cadava", 2, "Drink")
    }

    @Test
    fun theApothecaryOffersHisPotions() {
        assertEquals("Potions", npc("npc.apothecary").actions.getOpOrNull(2))
    }

    @Test
    fun theCryptScenesStandWhereTheCutsceneExpectsThem() {
        assertLocAt("loc.romeo_juliet_crypt_tomb_no_headstone", CoordGrid(2322, 4642, 0))
        assertLocAt("loc.romeo_juliet_stairs_up", CoordGrid(2334, 4645, 0))
        assertLocAt("loc.fai_varrock_castle_door", CoordGrid(3862, 4898, 1))
        assertLocAt("loc.fai_varrock_juliet_chair", CoordGrid(3858, 4902, 1))
    }

    private fun npc(name: String) =
        checkNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC))) { "$name missing" }

    private fun assertHeldOp(obj: String, op: Int, action: String) {
        val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))) { "$obj missing" }
        assertEquals(action, type.interfaceOptions.getOrNull(op - 1), "$obj lost its $action op")
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

    private fun npcSpawns(coords: CoordGrid): List<MapNpcDefinition> {
        val square = MapSquareKey.from(coords)
        val data = checkNotNull(cache.data(MAPS, square.id, 5)) { "no npcs in ${square.id}" }
        return MapNpcListDecoder.decode(InlineByteBuf(data)).packedSpawns.map(::MapNpcDefinition)
    }

    private companion object {
        const val QUEST_POINTS = 5

        lateinit var cache: dev.openrune.filesystem.Cache

        @JvmStatic
        @BeforeAll
        fun loadCache() {
            cache = ServerCacheManager.init(240)
        }
    }
}
