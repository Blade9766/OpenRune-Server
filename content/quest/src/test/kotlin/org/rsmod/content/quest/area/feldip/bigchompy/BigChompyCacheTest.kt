package org.rsmod.content.quest.area.feldip.bigchompy

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
 * Pins the cache facts Big Chompy Bird Hunting is written against. The quest stores its stage in
 * the whole of `varp.chompybird`, which the client reads directly to pick Rantz's appearance and
 * the state of the spit, so a cache rebuild that moved any of these would break the quest silently.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class BigChompyCacheTest {
    @Test
    fun questRowMatchesTheStagesAndRewardsTheScriptAwards() {
        val row = QuestRow.getRow("dbrow.${BigChompyBirdHuntingQuest.QUEST_KEY}".asRSCM())
        assertEquals(BigChompyBirdHuntingQuest.STAGE_COMPLETE, row.endstate)
        assertEquals(2, row.questpoints)
        assertEquals(RANTZ_ID, row.startnpc.single().id)
    }

    @Test
    fun rantzOnlyChangesAppearanceOnTheFinalStage() {
        val rantz = checkNotNull(ServerCacheManager.getNpc(RANTZ_ID))
        assertEquals("varp.chompybird".asRSCM(RSCMType.VARP), rantz.multiVarp)

        val transforms = checkNotNull(rantz.transforms)
        val preQuest = "npc.rantz_pre_quest".asRSCM(RSCMType.NPC)
        val postQuest = "npc.rantz_post_quest".asRSCM(RSCMType.NPC)
        assertEquals(postQuest, transforms[BigChompyBirdHuntingQuest.STAGE_COMPLETE])
        val stages =
            listOf(
                BigChompyBirdHuntingQuest.STAGE_STARTED,
                BigChompyBirdHuntingQuest.STAGE_GAVE_ARROWS,
                BigChompyBirdHuntingQuest.STAGE_ASKED_ABOUT_TOADS,
                BigChompyBirdHuntingQuest.STAGE_OPENED_CHEST,
                BigChompyBirdHuntingQuest.STAGE_SHOWN_TOAD,
                BigChompyBirdHuntingQuest.STAGE_DROPPED_TOAD,
                BigChompyBirdHuntingQuest.STAGE_CHOMPY_ATE_TOAD,
                BigChompyBirdHuntingQuest.STAGE_RANTZ_MISSED,
                BigChompyBirdHuntingQuest.STAGE_GOT_BOW,
                BigChompyBirdHuntingQuest.STAGE_KILLED_CHOMPY,
                BigChompyBirdHuntingQuest.STAGE_TOLD_TO_COOK,
                BigChompyBirdHuntingQuest.STAGE_COOKED,
            )
        for (stage in stages) {
            assertEquals(preQuest, transforms[stage], "stage $stage still shows the pre-quest Rantz")
        }
    }

    @Test
    fun spitRoastTransformsInTheOrderTheCookingScriptWrites() {
        val spit =
            checkNotNull(
                ServerCacheManager.getObject(
                    BigChompyBirdHuntingQuest.SPIT_ROAST.asRSCM(RSCMType.LOC)
                )
            )
        assertEquals("varbit.ogre_spit_roaster".asRSCM(RSCMType.VARBIT), spit.multiVarBit)
        val transforms = checkNotNull(spit.transforms)
        assertEquals("loc.chompybird_spitroast_empty".asRSCM(RSCMType.LOC), transforms[0])
        assertEquals("loc.chompybird_spitroast".asRSCM(RSCMType.LOC), transforms[1])
        assertEquals("loc.chompybird_spitroast_cooked".asRSCM(RSCMType.LOC), transforms[2])
        assertEquals("loc.chompybird_spitroast_ruined".asRSCM(RSCMType.LOC), transforms[3])
    }

    @Test
    fun theQuestSceneryStandsWhereTheScriptsExpectIt() {
        assertLocAt(BigChompyBirdHuntingQuest.SPIT_ROAST, CoordGrid(2630, 2990, 0))
        assertLocAt(BigChompyBirdHuntingQuest.CAVE_ENTRANCE, CoordGrid(2629, 2998, 0))
        assertLocAt(BigChompyBirdHuntingQuest.OGRE_CHEST, CoordGrid(2637, 9398, 0))
        assertLocAt(BigChompyBirdHuntingQuest.CAVE_EXIT_RIGHT, CoordGrid(2646, 9377, 0))
        assertLocAt(BigChompyBirdHuntingQuest.CAVE_EXIT_LEFT, CoordGrid(2647, 9377, 0))
    }

    @Test
    fun rantzRunsAnAiTimerSoHeCanSpotChompies() {
        val rantz = checkNotNull(ServerCacheManager.getNpc(RANTZ_ID))
        assertEquals(1, rantz.timer, "npc.rantz needs `timer = 1` for its ai timer to fire")
    }

    @Test
    fun rantzChildrenAreSpawnedInHisCave() {
        val square = MapSquareKey.from(CoordGrid(2642, 9394, 0))
        val data = checkNotNull(cache.data(MAPS, square.id, 5)) { "no npcs in ${square.id}" }
        val spawns = MapNpcListDecoder.decode(InlineByteBuf(data)).packedSpawns.map(::MapNpcDefinition)
        for (child in listOf("npc.fycie", "npc.bugs")) {
            val id = child.asRSCM(RSCMType.NPC)
            assertTrue(spawns.any { it.id == id }, "$child is not spawned in Rantz's cave")
        }
    }

    @Test
    fun rantzCanSeeTheClearingHeSendsThePlayerTo() {
        val clearing = BigChompyBirdHuntingQuest.BAIT_CLEARING
        val southWest = BigChompyBirdHuntingQuest.BAIT_ZONE_SOUTH_WEST
        val northEast = BigChompyBirdHuntingQuest.BAIT_ZONE_NORTH_EAST
        assertTrue(clearing.x in southWest.x..northEast.x)
        assertTrue(clearing.z in southWest.z..northEast.z)
        assertTrue(RANTZ_COORDS.chebyshevDistance(clearing) <= RANTZ_SIGHT)
    }

    private fun assertLocAt(loc: String, coords: CoordGrid) {
        val id = loc.asRSCM(RSCMType.LOC)
        val square = MapSquareKey.from(coords)
        val data = checkNotNull(cache.data(MAPS, square.id, 1)) { "no locs in ${square.id}" }
        val spawns = MapLocListDecoder.decode(InlineByteBuf(data)).spawns.map(::MapLocDefinition)
        val match =
            spawns.any {
                it.id == id &&
                    square.toCoords(it.level).translate(it.localX, it.localZ) == coords
            }
        assertTrue(match, "$loc is not at $coords")
    }

    private companion object {
        val RANTZ_ID by lazy { "npc.rantz".asRSCM(RSCMType.NPC) }

        /** Rantz's spawn in `map/npcs/feldip_hills.toml`. */
        val RANTZ_COORDS = CoordGrid(2630, 2981, 0)

        /** How far his AI timer looks for a chompy. */
        const val RANTZ_SIGHT = 16

        lateinit var cache: dev.openrune.filesystem.Cache

        @JvmStatic
        @BeforeAll
        fun loadCache() {
            cache = ServerCacheManager.init(240)
        }
    }
}
