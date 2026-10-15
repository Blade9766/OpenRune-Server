package org.rsmod.content.quest.area.karamja.piratestreasure

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
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.BANANA_CRATE
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.GROCERY_CRATE
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.HECTORS_CHEST
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.WYDIN_DOOR
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey

/** Pins the cache facts Pirate's Treasure is written against: the quest row, ops and scenery. */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class PiratesTreasureCacheTest {
    @Test
    fun questRowMatchesTheStagesTheScriptUses() {
        val row = QuestRow.getRow("dbrow.${PiratesTreasureQuest.QUEST_KEY}".asRSCM())
        assertEquals(PiratesTreasureQuest.STAGE_COMPLETE, row.endstate)
        assertEquals(2, row.questpoints)
    }

    @Test
    fun theSceneryOffersTheOpsTheScriptBindsTo() {
        assertLocOp(BANANA_CRATE, 1, "Search")
        assertLocOp(BANANA_CRATE, 2, "Fill")
        assertLocOp(GROCERY_CRATE, 1, "Search")
        assertLocOp(WYDIN_DOOR, 1, "Open")
        assertLocOp(HECTORS_CHEST, 1, "Open")
    }

    @Test
    fun theQuestItemsCarryTheOpsTheScriptBindsTo() {
        assertHeldOp(PiratesTreasureQuest.PIRATE_MESSAGE, 1, "Read")
        assertHeldOp(PiratesTreasureQuest.CASKET, 1, "Open")
        assertHeldOp(PiratesTreasureQuest.SPADE, 1, "Dig")
    }

    @Test
    fun theSceneryStandsWhereTheScriptExpectsIt() {
        assertLocAt(BANANA_CRATE, CoordGrid(2939, 3149, 0))
        assertLocAt(GROCERY_CRATE, CoordGrid(3009, 3207, 0))
        assertLocAt(WYDIN_DOOR, CoordGrid(3012, 3204, 0))
        assertLocAt(HECTORS_CHEST, CoordGrid(3219, 3396, 1))
    }

    private fun assertLocOp(loc: String, op: Int, action: String) {
        val type = checkNotNull(ServerCacheManager.getObject(loc.asRSCM(RSCMType.LOC))) { "$loc missing" }
        assertEquals(action, type.actions.getOpOrNull(op - 1), "$loc lost its $action op")
    }

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

    private companion object {
        lateinit var cache: dev.openrune.filesystem.Cache

        @JvmStatic
        @BeforeAll
        fun loadCache() {
            cache = ServerCacheManager.init(240)
        }
    }
}
