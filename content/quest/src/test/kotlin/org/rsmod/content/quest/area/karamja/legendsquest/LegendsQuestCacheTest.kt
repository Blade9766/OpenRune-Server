package org.rsmod.content.quest.area.karamja.legendsquest

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

/**
 * Pins the cache facts Legends' Quest is written against: the stage the Radimus multinpcs switch
 * at, the ops of the scenery the scripts bind to, where that scenery stands and which quest npcs
 * the map spawns.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class LegendsQuestCacheTest {
    @Test
    fun questRowMatchesTheStagesAndRewards() {
        val row = QuestRow.getRow("dbrow.${LegendsQuest.QUEST_KEY}".asRSCM())
        assertEquals(LegendsQuest.STAGE_COMPLETE, row.endstate)
        assertEquals(4, row.questpoints)
    }

    /** Radimus leaves his study for the main hall when the totem is handed in. */
    @Test
    fun radimusMovesToTheHallAtTheReturnedStage() {
        val hut = checkNotNull(ServerCacheManager.getNpc("npc.radimus_erkle_hut".asRSCM(RSCMType.NPC)))
        val guild = checkNotNull(ServerCacheManager.getNpc("npc.radimus_erkle_guild".asRSCM(RSCMType.NPC)))
        val radimus = "npc.radimus_erkle".asRSCM(RSCMType.NPC)
        val varp = "varp.legendsquest".asRSCM(RSCMType.VARP)
        assertEquals(varp, hut.multiVarp)
        assertEquals(varp, guild.multiVarp)
        val hutForms = checkNotNull(hut.transforms)
        val guildForms = checkNotNull(guild.transforms)
        assertEquals(radimus, hutForms[LegendsQuest.STAGE_RETURNED - 1])
        assertEquals(-1, hutForms.getOrElse(LegendsQuest.STAGE_RETURNED) { -1 })
        assertEquals(-1, guildForms[LegendsQuest.STAGE_RETURNED - 1])
        assertEquals(radimus, guildForms[LegendsQuest.STAGE_COMPLETE])
    }

    @Test
    fun theSceneryCarriesTheOpsTheScriptsBindTo() {
        assertOp("loc.legendsguildgatel", 1, "Open")
        assertOp("loc.legends_cupboardopen", 2, "Search")
        assertOp("loc.kharazi_jungle_tree1", 1, "Chop-down")
        assertOp("loc.kharazi_jungle_plant1", 1, "Chop-down")
        assertOp("loc.lgshamancaverock1", 1, "Search")
        assertOp("loc.lqfirewall_straight", 2, "Investigate")
        assertOp("loc.lglockpickgatebottoml", 2, "Search")
        assertOp("loc.mine_test_boulder1", 1, "Smash-to-bits")
        assertOp("loc.crumbled_wall", 1, "Jump-over")
        assertOp("loc.lgancientwalldoor", 2, "Search")
        assertOp("loc.lg_gemplacerock", 2, "Search")
        assertOp("loc.lg_winchdown_rope", 1, "Climb-down")
        assertOp("loc.furnace_legendsquest", 1, "Search")
        assertOp("loc.legendsquest_force_barrier", 1, "Walk-through")
        assertOp("loc.yommitree_totem", 1, "Lift")
        assertOp("loc.kharazi_bamboo_tree_base_leafy", 1, "Shake")
        val notes = checkNotNull(ServerCacheManager.getItem(LegendsQuest.NOTES.asRSCM(RSCMType.OBJ)))
        assertEquals("Complete", notes.interfaceOptions?.get(1))
        val bowl = checkNotNull(ServerCacheManager.getItem(LegendsQuest.BLESSED_BOWL_PURE.asRSCM(RSCMType.OBJ)))
        assertEquals("Empty", bowl.interfaceOptions?.get(3))
    }

    @Test
    fun theQuestSceneryStandsWhereTheScriptsExpectIt() {
        assertLocAt("loc.legendsguildgatel", LegendsCoords.GATE_WEST)
        assertLocAt("loc.lgancientwalldoor", LegendsCoords.MARKED_WALL_WEST)
        assertLocAt("loc.dragons_eye_rock", LegendsCoords.DRAGONS_EYE)
        assertLocAt("loc.legendsquest_force_barrier", LegendsCoords.BARRIER_SOUTH)
        assertLocAt("loc.lg_gemplacerock", CoordGrid(2764, 9309, 0))
        assertLocAt("loc.lg_gemplacerock", CoordGrid(2757, 9297, 0))
        assertLocAt("loc.lgclimbrope_viyeldicaves", CoordGrid(2377, 4711, 0))
        assertLocAt("loc.sacred_water", CoordGrid(2837, 2915, 0))
        assertLocAt("loc.fertilesoil", CoordGrid(2778, 2916, 0))
    }

    @Test
    fun ungaduluStandsInsideHisOctagram() {
        val spawn = npcSpawns(LegendsCoords.OCTAGRAM_CENTRE).single { it.id == "npc.ungadulu_good".asRSCM(RSCMType.NPC) }
        val square = MapSquareKey.from(LegendsCoords.OCTAGRAM_CENTRE)
        val coords = square.toCoords(spawn.level).translate(spawn.localX, spawn.localZ)
        assertTrue(LegendsCoords.inOctagram(coords), "Ungadulu spawns outside the octagram at $coords")
    }

    /** Gujuo, Echned Zekin, Viyeldi and Nezikchened only appear when the quest summons them. */
    @Test
    fun theSummonedNpcsAreNotSpawnedByTheMap() {
        val summoned = listOf("npc.gujuo", "npc.echned_zekin", "npc.viyeldi", "npc.nezikchened").map { it.asRSCM(RSCMType.NPC) }
        for (at in listOf(CoordGrid(2800, 2910, 0), CoordGrid(2400, 4700, 0), LegendsCoords.OCTAGRAM_CENTRE)) {
            val spawned = npcSpawns(at).filter { it.id in summoned }
            assertTrue(spawned.isEmpty(), "summoned npc spawned by the map near $at: $spawned")
        }
    }

    /**
     * The generated spawn file stood a plain Radimus beside the study multinpc, which would have
     * stayed in the study after the hand-in; only the two multinpcs belong there.
     */
    @Test
    fun radimusIsOnlySpawnedAsHisTwoMultinpcs() {
        val spawns = npcSpawns(CoordGrid(2724, 3370, 0)).map { it.id }
        assertTrue("npc.radimus_erkle".asRSCM(RSCMType.NPC) !in spawns, "a plain Radimus is spawned")
        assertEquals(1, spawns.count { it == "npc.radimus_erkle_hut".asRSCM(RSCMType.NPC) })
        assertEquals(1, spawns.count { it == "npc.radimus_erkle_guild".asRSCM(RSCMType.NPC) })
    }

    /** Holy water is thrown like a dart, so wielding it must give the Thrown combat styles. */
    @Test
    fun holyWaterIsAThrownWeapon() {
        val water = checkNotNull(ServerCacheManager.getItem(LegendsQuest.HOLY_WATER.asRSCM(RSCMType.OBJ)))
        val dart = checkNotNull(ServerCacheManager.getItem("obj.bronze_dart".asRSCM(RSCMType.OBJ)))
        assertEquals(dart.weaponCategory?.id, water.weaponCategory?.id)
        assertEquals("Wield", water.interfaceOptions?.get(1))
    }

    private fun assertOp(loc: String, op: Int, action: String) {
        val type = checkNotNull(ServerCacheManager.getObject(loc.asRSCM(RSCMType.LOC))) { "$loc missing" }
        assertEquals(action, type.actions.getOpOrNull(op - 1), "$loc lost its $action op")
    }

    private fun assertLocAt(loc: String, coords: CoordGrid) {
        val id = loc.asRSCM(RSCMType.LOC)
        val square = MapSquareKey.from(coords)
        val data = checkNotNull(cache.data(MAPS, square.id, 1)) { "no locs in ${square.id}" }
        val spawns = MapLocListDecoder.decode(InlineByteBuf(data)).spawns.map(::MapLocDefinition)
        val match =
            spawns.any { it.id == id && square.toCoords(it.level).translate(it.localX, it.localZ) == coords }
        assertTrue(match, "$loc is not at $coords")
    }

    private fun npcSpawns(at: CoordGrid): List<MapNpcDefinition> {
        val square = MapSquareKey.from(at)
        val data = cache.data(MAPS, square.id, 5) ?: return emptyList()
        return MapNpcListDecoder.decode(InlineByteBuf(data)).packedSpawns.map(::MapNpcDefinition)
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
