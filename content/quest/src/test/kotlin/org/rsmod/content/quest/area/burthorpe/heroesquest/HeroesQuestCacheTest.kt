package org.rsmod.content.quest.area.burthorpe.heroesquest

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
import org.junit.jupiter.api.Assertions.assertFalse
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
 * Pins the cache facts Heroes' Quest is written against: the quest row, the sides the guarded
 * doors are locked from, the ops the scripts bind to, and where the heist's scenery and people
 * stand in Scarface Pete's mansion.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class HeroesQuestCacheTest {
    @Test
    fun questRowMatchesTheStagesTheScriptUses() {
        val row = QuestRow.getRow("dbrow.${HeroesQuest.QUEST_KEY}".asRSCM())
        assertEquals(HeroesQuest.STAGE_COMPLETE, row.endstate)
        assertEquals(1, row.questpoints)
    }

    /** Each door's own tile is on the side the scripts treat as locked or as the inside. */
    @Test
    fun theGuardedDoorsFaceTheWayTheScriptsExpect() {
        assertLocAt("loc.herodoor_l", CoordGrid(2902, 3510, 0), shape = 0, angle = WEST)
        assertLocAt("loc.herodoor_r", CoordGrid(2902, 3511, 0), shape = 0, angle = WEST)
        assertLocAt("loc.garvdoor", CoordGrid(2774, 3187, 0), shape = 0, angle = NORTH)
        assertLocAt("loc.pete_sidedoor", CoordGrid(2781, 3197, 0), shape = 0, angle = SOUTH)
        assertLocAt("loc.pete_treasuredoor", CoordGrid(2764, 3197, 0), shape = 0, angle = WEST)
        assertLocAt("loc.herokitchendoor", CoordGrid(2788, 3189, 0), shape = 0, angle = NORTH)
        assertLocAt("loc.herokitchenpanel", CoordGrid(2787, 3190, 0), shape = 0, angle = WEST)
        assertLocAt("loc.grubordoor", CoordGrid(2811, 3170, 0), shape = 0, angle = WEST)
        assertLocAt("loc.dungeonjail", CoordGrid(2931, 9690, 0), shape = 0, angle = SOUTH)
        assertLocAt("loc.dungeonjail", CoordGrid(2931, 9694, 0), shape = 0, angle = NORTH)
        assertLocAt("loc.deepdungeondoor", CoordGrid(2924, 9803, 0), shape = 0, angle = WEST)
    }

    @Test
    fun theHeistSceneryStandsWhereTheScriptsExpectIt() {
        assertLocAt("loc.gripcbshut", CoordGrid(2775, 3196, 0))
        assertLocAt("loc.shutcandlechest", CoordGrid(2766, 3199, 0))
        assertLocAt("loc.snipable_wall", CoordGrid(2780, 3198, 0), shape = 0, angle = WEST)
        assertLocAt("loc.herorockslide", CoordGrid(2838, 3517, 0))
    }

    /** Grip is shot through the arrow slit, so the wall must let projectiles through. */
    @Test
    fun theArrowSlitLetsProjectilesThrough() {
        assertFalse(loc("loc.snipable_wall").blockRange)
    }

    @Test
    fun theSceneryCarriesTheOpsTheScriptBindsTo() {
        assertLocOps("loc.garvdoor", "Open")
        assertLocOps("loc.pete_sidedoor", "Open")
        assertLocOps("loc.pete_treasuredoor", "Open")
        assertLocOps("loc.herokitchendoor", "Open")
        assertLocOps("loc.herokitchenpanel", "Push")
        assertLocOps("loc.grubordoor", "Open")
        assertLocOps("loc.gripcbshut", "Open")
        assertLocOps("loc.gripcbopen", "Search", "Shut")
        assertLocOps("loc.shutcandlechest", "Open")
        assertLocOps("loc.opencandlechest", "Search", "Close")
        assertLocOps("loc.herorockslide", "Investigate", "Mine")
        assertLocOps("loc.dungeonjail", "Open")
        assertLocOps("loc.deepdungeondoor", "Open")
    }

    @Test
    fun theNpcsCarryTheOpsTheScriptsBindTo() {
        assertNpcOp("npc.achietties", 0, "Talk-to")
        assertNpcOp("npc.grip", 0, "Talk-to")
        assertNpcOp("npc.grip", 1, "Attack")
        assertNpcOp("npc.garv", 0, "Talk-to")
        assertNpcOp("npc.grubor", 0, "Talk-to")
        assertNpcOp("npc.trobert", 0, "Talk-to")
        assertNpcOp("npc.alfonse_the_waiter", 0, "Talk-to")
        assertNpcOp("npc.alfonse_the_waiter", 2, "Trade")
        assertNpcOp("npc.charlie_the_cook", 0, "Talk-to")
        assertNpcOp("npc.pirate_guard", 0, "Talk-to")
        assertNpcOp("npc.velrak_the_explorer", 0, "Talk-to")
        assertNpcOp("npc.entrana_monk", 0, "Talk-to")
        assertNpcOp("npc.high_priest_of_entrana", 0, "Talk-to")
    }

    @Test
    fun theQuestItemsCarryTheOpsTheScriptBindsTo() {
        val oil = checkNotNull(ServerCacheManager.getItem("obj.blamish_oil".asRSCM(RSCMType.OBJ)))
        assertEquals("Drink", oil.interfaceOptions.getOrNull(0))
    }

    @Test
    fun theMansionIsStaffedWhereTheScriptsExpect() {
        assertNpcAt("npc.grip", CoordGrid(2774, 3192, 0))
        assertNpcAt("npc.garv", CoordGrid(2776, 3186, 0))
        assertNpcAt("npc.achietties", CoordGrid(2903, 3510, 0))
        assertNpcAt("npc.grubor", CoordGrid(2812, 3171, 0))
    }

    private fun loc(name: String) =
        checkNotNull(ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC))) { "$name missing" }

    private fun assertLocOps(name: String, vararg ops: String) {
        val type = loc(name)
        ops.forEachIndexed { index, op ->
            assertEquals(op, type.actions.getOpOrNull(index), "$name lost its $op op")
        }
    }

    private fun assertNpcOp(name: String, index: Int, op: String) {
        val type = checkNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC))) { "$name missing" }
        assertEquals(op, type.actions.getOpOrNull(index), "$name lost its $op op")
    }

    private fun assertLocAt(name: String, coords: CoordGrid, shape: Int? = null, angle: Int? = null) {
        val id = name.asRSCM(RSCMType.LOC)
        val square = MapSquareKey.from(coords)
        val data = checkNotNull(cache.data(MAPS, square.id, 1)) { "no locs in ${square.id}" }
        val spawns = MapLocListDecoder.decode(InlineByteBuf(data)).spawns.map(::MapLocDefinition)
        val match =
            spawns.any {
                it.id == id &&
                    square.toCoords(it.level).translate(it.localX, it.localZ) == coords &&
                    (shape == null || it.shape == shape) &&
                    (angle == null || it.angle == angle)
            }
        assertTrue(match, "$name is not at $coords (shape=$shape angle=$angle)")
    }

    private fun assertNpcAt(name: String, coords: CoordGrid) {
        val id = name.asRSCM(RSCMType.NPC)
        val square = MapSquareKey.from(coords)
        val data = checkNotNull(cache.data(MAPS, square.id, 5)) { "no npcs in ${square.id}" }
        val spawns = MapNpcListDecoder.decode(InlineByteBuf(data)).packedSpawns.map(::MapNpcDefinition)
        val match =
            spawns.any {
                it.id == id && square.toCoords(it.level).translate(it.localX, it.localZ) == coords
            }
        assertTrue(match, "$name is not spawned at $coords")
    }

    private companion object {
        const val WEST = 0
        const val NORTH = 1
        const val SOUTH = 3

        lateinit var cache: dev.openrune.filesystem.Cache

        @JvmStatic
        @BeforeAll
        fun loadCache() {
            cache = ServerCacheManager.init(240)
        }
    }
}
