package org.rsmod.content.quest.area.seers.murdermystery

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
import org.junit.jupiter.api.Assertions.assertNotNull
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
 * Pins the cache facts Murder Mystery is written against: the ops the mansion's scenery carries,
 * where that scenery stands, and that each of Lord Sinclair's children is spawned exactly once.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class MurderMysteryCacheTest {
    @Test
    fun questRowMatchesTheStagesAndRewardsTheScriptAwards() {
        val row = QuestRow.getRow("dbrow.${MurderMysteryQuest.QUEST_KEY}".asRSCM())
        assertEquals(MurderMysteryQuest.STAGE_COMPLETE, row.endstate)
        assertEquals(3, row.questpoints)
    }

    @Test
    fun everySuspectHasTheirOwnSilverware() {
        for (suspect in Suspect.entries) {
            for (obj in listOf(suspect.silverItem, suspect.dustedItem, suspect.print, suspect.thread)) {
                assertNotNull(
                    ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)),
                    "$obj is missing from the cache",
                )
            }
        }
        val prints = Suspect.entries.map { it.print }
        assertEquals(prints.size, prints.toSet().size, "two suspects share a fingerprint")
    }

    @Test
    fun theSceneryCarriesTheOpsTheScriptBindsTo() {
        for (suspect in Suspect.entries) {
            assertOp(suspect.barrel, op = 2, action = "Search")
            assertOp(suspect.poisonLoc, op = 2, action = "Investigate")
        }
        assertOp("loc.flourbarrel", op = 2, action = "Take-from")
        assertOp("loc.murdersacks", op = 2, action = "Investigate")
        assertOp("loc.murderdoggatel", op = 1, action = "Investigate")
        assertOp("loc.murderdoggater", op = 1, action = "Investigate")
    }

    /**
     * The study window is a multiloc on the King's Ransom varbit; the op the quest binds to lives
     * on its transforms, so the handler has to be registered against the base loc.
     */
    @Test
    fun theStudyWindowIsAMultilocShowingTheSmashedWindow() {
        val window = checkNotNull(ServerCacheManager.getObject("loc.murderwindow".asRSCM(RSCMType.LOC)))
        assertEquals("varbit.kr_window".asRSCM(RSCMType.VARBIT), window.multiVarBit)
        val visible = checkNotNull(window.transforms)[0]
        val smashed = checkNotNull(ServerCacheManager.getObject(visible))
        assertEquals("Investigate", smashed.actions.getOpOrNull(1))
    }

    @Test
    fun theCrimeSceneStandsWhereTheScriptsExpectIt() {
        assertLocAt("loc.murderwindow", CoordGrid(2748, 3577, 0))
        assertLocAt("loc.flourbarrel", CoordGrid(2733, 3582, 0))
        assertLocAt("loc.murdersacks", CoordGrid(2731, 3582, 0))
        assertLocAt("loc.murderdoggatel", CoordGrid(2750, 3578, 0))
        assertLocAt("loc.murderdoggater", CoordGrid(2749, 3578, 0))

        assertLocAt(Suspect.Anna.poisonLoc, CoordGrid(2730, 3572, 0))
        assertLocAt(Suspect.Bob.poisonLoc, CoordGrid(2730, 3559, 0))
        assertLocAt(Suspect.Carol.poisonLoc, CoordGrid(2736, 3573, 0))
        assertLocAt(Suspect.David.poisonLoc, CoordGrid(2740, 3574, 1))
        assertLocAt(Suspect.Elizabeth.poisonLoc, CoordGrid(2747, 3563, 0))
        assertLocAt(Suspect.Frank.poisonLoc, CoordGrid(2746, 3573, 0))

        assertLocAt(Suspect.Anna.barrel, CoordGrid(2733, 3575, 0))
        assertLocAt(Suspect.Bob.barrel, CoordGrid(2735, 3579, 0))
        assertLocAt(Suspect.Carol.barrel, CoordGrid(2733, 3580, 1))
        assertLocAt(Suspect.David.barrel, CoordGrid(2733, 3577, 1))
        assertLocAt(Suspect.Elizabeth.barrel, CoordGrid(2747, 3581, 1))
        assertLocAt(Suspect.Frank.barrel, CoordGrid(2747, 3577, 1))
    }

    @Test
    fun theMurderWeaponAndPoisonedPotLieInTheStudy() {
        for (obj in listOf(MurderMysteryQuest.DAGGER, MurderMysteryQuest.PUNGENT_POT)) {
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)))
            assertEquals("Take", type.options.getOpOrNull(2), "$obj cannot be taken from the ground")
        }
    }

    /**
     * Each child is spawned as the King's Ransom multinpc, which shows the plain type while that
     * quest has not started. The generated spawn file also carried the plain types, leaving two of
     * several of them standing in the mansion.
     */
    @Test
    fun eachSuspectIsSpawnedExactlyOnce() {
        val spawns = mansionNpcSpawns()
        for (suspect in Suspect.entries) {
            val ids = suspect.npcs.map { it.asRSCM(RSCMType.NPC) }.toSet()
            val count = spawns.count { it.id in ids }
            assertEquals(1, count, "${suspect.displayName} is spawned $count times")
        }
    }

    @Test
    fun theSuspectMultisShowThePlainTypeBeforeKingsRansom() {
        for (suspect in Suspect.entries) {
            val multi = checkNotNull(ServerCacheManager.getNpc(suspect.npcs[1].asRSCM(RSCMType.NPC)))
            assertEquals("varbit.kr_quest".asRSCM(RSCMType.VARBIT), multi.multiVarBit)
            assertEquals(
                suspect.npcs[0].asRSCM(RSCMType.NPC),
                checkNotNull(multi.transforms)[0],
                "${suspect.displayName}'s multi does not show her plain type at kr_quest 0",
            )
        }
    }

    @Test
    fun theGuardDogRunsAnAiTimerSoItCanBark() {
        val dog = checkNotNull(ServerCacheManager.getNpc("npc.murder_mystery_guarddog".asRSCM(RSCMType.NPC)))
        assertEquals(1, dog.timer, "the guard dog needs `timer = 1` for its ai timer to fire")
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
            spawns.any {
                it.id == id && square.toCoords(it.level).translate(it.localX, it.localZ) == coords
            }
        assertTrue(match, "$loc is not at $coords")
    }

    private fun mansionNpcSpawns(): List<MapNpcDefinition> {
        val square = MapSquareKey.from(CoordGrid(2740, 3570, 0))
        val data = checkNotNull(cache.data(MAPS, square.id, 5)) { "no npcs in ${square.id}" }
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
