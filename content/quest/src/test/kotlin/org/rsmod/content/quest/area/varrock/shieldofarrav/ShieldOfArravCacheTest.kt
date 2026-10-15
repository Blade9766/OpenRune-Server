package org.rsmod.content.quest.area.varrock.shieldofarrav

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
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.PHOENIX_JOINED
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.PHOENIX_TASKED
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey

/**
 * Pins the cache facts Shield of Arrav is written against: the quest varbit, the two multinpcs
 * whose transforms fix gang stages, the sides of the gang doors, the item op slots, and where the
 * hideouts' scenery and guards stand.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class ShieldOfArravCacheTest {
    @Test
    fun questRowMatchesTheStagesTheScriptUses() {
        val row = QuestRow.getRow("dbrow.${ShieldOfArravQuest.QUEST_KEY}".asRSCM())
        assertEquals(ShieldOfArravQuest.STAGE_COMPLETE, row.endstate)
        assertEquals(1, row.questpoints)
        val varbit = checkNotNull(ServerCacheManager.getVarbit("varbit.shieldofarrav".asRSCM(RSCMType.VARBIT)))
        assertEquals("varp.scorpcatcher_secondary".asRSCM(RSCMType.VARP), varbit.varp)
    }

    /** Jonny is attackable at exactly Straven's stage and vanishes once the player has joined. */
    @Test
    fun jonnyTheBeardFollowsThePhoenixGangStage() {
        val jonny = npc("npc.jonny_the_beard")
        assertEquals("varp.phoenixgang".asRSCM(RSCMType.VARP), jonny.multiVarp)
        val transforms = checkNotNull(jonny.transforms)
        assertEquals("npc.jonny_the_beard_1op".asRSCM(RSCMType.NPC), transforms[PHOENIX_TASKED - 1])
        assertEquals("npc.jonny_the_beard_2op".asRSCM(RSCMType.NPC), transforms[PHOENIX_TASKED])
        assertEquals(-1, transforms[PHOENIX_JOINED])
        assertEquals("Attack", npc("npc.jonny_the_beard_2op").actions.getOpOrNull(1))
    }

    @Test
    fun theWeaponsmasterHidesOnceKilled() {
        val weaponsmaster = npc("npc.weaponsmaster")
        assertEquals("varbit.soa_weaponmaster_dead".asRSCM(RSCMType.VARBIT), weaponsmaster.multiVarBit)
        val transforms = checkNotNull(weaponsmaster.transforms)
        assertEquals("npc.weaponsmaster_vis".asRSCM(RSCMType.NPC), transforms[0])
        assertEquals(-1, transforms[1])
    }

    @Test
    fun theWeaponsmasterIsSpawnedOnce() {
        val store = CoordGrid(3246, 3384, 1)
        val ids = setOf("npc.weaponsmaster", "npc.weaponsmaster_vis").map { it.asRSCM(RSCMType.NPC) }
        val count = npcSpawns(store).count { it.id in ids && it.level == store.level }
        assertEquals(1, count, "the Weaponsmaster is spawned $count times")
    }

    @Test
    fun theHideoutsStandWhereTheScriptsExpectThem() {
        assertLocAt("loc.phoenixdoor", CoordGrid(3247, 9779, 0), shape = 0, angle = NORTH)
        assertLocAt("loc.phoenixdoor2", CoordGrid(3251, 3386, 0), shape = 0, angle = SOUTH)
        assertLocAt("loc.blackarmdoor", CoordGrid(3185, 3388, 0), shape = 0, angle = SOUTH)
        assertLocAt("loc.phoenixshutchest", CoordGrid(3235, 9761, 0))
        assertLocAt("loc.blackarmcupboardshut", CoordGrid(3189, 3385, 1))
        assertLocAt("loc.questbookcase", CoordGrid(3212, 3493, 0))
        assertLocAt("loc.qip_soa_vtam_corporation_sign", CoordGrid(3241, 3383, 0))
    }

    @Test
    fun theSceneryCarriesTheOpsTheScriptBindsTo() {
        assertLocOp("loc.phoenixdoor", "Open")
        assertLocOp("loc.phoenixdoor2", "Open")
        assertLocOp("loc.blackarmdoor", "Open")
        assertLocOp("loc.phoenixshutchest", "Open")
        assertLocOp("loc.blackarmcupboardshut", "Open")
        assertLocOp("loc.questbookcase", "Search")
        assertLocOp("loc.qip_soa_vtam_corporation_sign", "Read")
    }

    @Test
    fun theQuestItemsCarryTheOpsTheScriptBindsTo() {
        assertHeldOp("obj.the_shield_of_arrav", 1, "Read")
        assertHeldOp("obj.intelligence_report", 1, "Read")
        assertHeldOp("obj.arravcertificate", 1, "Read")
        assertHeldOp("obj.arravcertificate_lft", 1, "Inspect")
        assertHeldOp("obj.arravcertificate_rht", 1, "Inspect")
        assertHeldOp("obj.arravshield1", 1, "Inspect")
        assertHeldOp("obj.arravshield2", 1, "Inspect")
        assertHeldOp("obj.phoenixkey2", 4, "Inspect")
        assertHeldOp("obj.phoenix_crossbow", 4, "Inspect")
        assertHeldOp("obj.qip_soa_newspaper1", 1, "Read")
        assertHeldOp("obj.qip_soa_newspaper2", 1, "Read")
    }

    @Test
    fun bennyRunsAnAiTimerSoHeCanShout() {
        assertEquals(1, npc("npc.qip_soa_newspaperseller").timer)
    }

    private fun npc(name: String) =
        checkNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC))) { "$name missing" }

    private fun assertLocOp(loc: String, action: String) {
        val type = checkNotNull(ServerCacheManager.getObject(loc.asRSCM(RSCMType.LOC))) { "$loc missing" }
        assertEquals(action, type.actions.getOpOrNull(0), "$loc lost its $action op")
    }

    private fun assertHeldOp(obj: String, op: Int, action: String) {
        val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))) { "$obj missing" }
        assertEquals(action, type.interfaceOptions.getOrNull(op - 1), "$obj lost its $action op")
    }

    private fun assertLocAt(loc: String, coords: CoordGrid, shape: Int? = null, angle: Int? = null) {
        val id = loc.asRSCM(RSCMType.LOC)
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
        assertTrue(match, "$loc is not at $coords (shape=$shape angle=$angle)")
    }

    private fun npcSpawns(coords: CoordGrid): List<MapNpcDefinition> {
        val square = MapSquareKey.from(coords)
        val data = checkNotNull(cache.data(MAPS, square.id, 5)) { "no npcs in ${square.id}" }
        return MapNpcListDecoder.decode(InlineByteBuf(data)).packedSpawns.map(::MapNpcDefinition)
    }

    private companion object {
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
