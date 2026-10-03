package org.rsmod.content.quest.area.falador.doricsquest

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
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey

/** Pins the cache facts Doric's Quest is written against: its quest row, Doric, and his smithy. */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class DoricsQuestCacheTest {
    @Test
    fun questRowMatchesTheStagesTheScriptUses() {
        val row = QuestRow.getRow("dbrow.${DoricsQuest.QUEST_KEY}".asRSCM())
        assertEquals(DoricsQuest.STAGE_COMPLETE, row.endstate)
        assertEquals(1, row.questpoints)
    }

    @Test
    fun doricIsSpawnedInHisSmithy() {
        val doric = "npc.doric".asRSCM(RSCMType.NPC)
        val square = MapSquareKey.from(DORIC_TILE)
        val data = checkNotNull(cache.data(MAPS, square.id, 5))
        val spawns = MapNpcListDecoder.decode(InlineByteBuf(data)).packedSpawns.map(::MapNpcDefinition)
        val tiles = spawns.filter { it.id == doric }.map { square.toCoords(it.level).translate(it.localX, it.localZ) }
        assertEquals(listOf(DORIC_TILE), tiles)
    }

    @Test
    fun theAnvilsAreAnvilsSmithingPicksUp() {
        val type = checkNotNull(ServerCacheManager.getObject(DoricsAnvils.DORICS_ANVIL.asRSCM(RSCMType.LOC)))
        assertEquals("category.anvil".asRSCM(RSCMType.CATEGORY), type.category)
        assertLocAt(DoricsAnvils.DORICS_ANVIL, CoordGrid(2950, 3451, 0))
        assertLocAt(DoricsAnvils.DORICS_ANVIL, CoordGrid(2950, 3452, 0))
    }

    @Test
    fun theWhetstoneStandsInTheSmithy() {
        val type = checkNotNull(ServerCacheManager.getObject("loc.devious_whetstone".asRSCM(RSCMType.LOC)))
        assertEquals("Use", type.actions.getOpOrNull(0))
        assertLocAt("loc.devious_whetstone", CoordGrid(2953, 3451, 0))
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
        val DORIC_TILE = CoordGrid(2952, 3451, 0)

        lateinit var cache: dev.openrune.filesystem.Cache

        @JvmStatic
        @BeforeAll
        fun loadCache() {
            cache = ServerCacheManager.init(240)
        }
    }
}
