package org.rsmod.content.quest.area.varrock.familycrest

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
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
import org.rsmod.content.quest.area.varrock.familycrest.WitchavenDungeon.Lever
import org.rsmod.content.quest.area.varrock.familycrest.WitchavenDungeon.PuzzleDoor
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey

/**
 * Pins the cache facts Family Crest is written against. The quest keeps its stage in the whole of
 * `varp.crestquest`, which the three sons' multinpcs read directly, and the Witchaven doors are
 * named after the lever positions that open them - so a cache rebuild that moved any of these
 * would break the quest silently.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class FamilyCrestCacheTest {
    @Test
    fun questRowMatchesTheStagesAndRequirementsTheScriptAssumes() {
        val row = QuestRow.getRow("dbrow.${FamilyCrestQuest.QUEST_KEY}".asRSCM())
        assertEquals(FamilyCrestQuest.STAGE_COMPLETE, row.endstate)
        assertEquals(1, row.questpoints)
        assertEquals("npc.dimintheis".asRSCM(RSCMType.NPC), row.startnpc.single().id)
        assertEquals(FamilyCrestQuest.RECOMMENDED_COMBAT, row.recommendedCombat)

        val required = row.requirementStats.associate { it.t0.internalName to it.t1 }
        assertEquals(FamilyCrestQuest.MAGIC_REQ, required["stat.magic"])
        assertEquals(FamilyCrestQuest.MINING_REQ, required["stat.mining"])
        assertEquals(FamilyCrestQuest.SMITHING_REQ, required["stat.smithing"])
        assertEquals(FamilyCrestQuest.CRAFTING_REQ, required["stat.crafting"])
    }

    @Test
    fun avanIsAnAnonymousMinerUntilTheGemTraderNamesHim() {
        val avan = checkNotNull(ServerCacheManager.getNpc(FamilyCrestQuest.AVAN.asRSCM(RSCMType.NPC)))
        assertEquals("varp.crestquest".asRSCM(RSCMType.VARP), avan.multiVarp)

        val transforms = checkNotNull(avan.transforms)
        val stranger = "npc.avan_fitzharmon_man".asRSCM(RSCMType.NPC)
        val named = "npc.avan_fitzharmon_avan_1op".asRSCM(RSCMType.NPC)
        for (stage in 0 until FamilyCrestQuest.STAGE_AVAN_FOUND) {
            assertEquals(stranger, transforms[stage], "stage $stage should still hide Avan")
        }
        assertEquals(named, transforms[FamilyCrestQuest.STAGE_AVAN_FOUND])
    }

    @Test
    fun everyBrotherGainsTheGauntletsOptionOnlyOnceTheQuestIsDone() {
        val sons =
            mapOf(
                FamilyCrestQuest.CALEB to
                    ("npc.caleb_fitzharmon_1op" to "npc.caleb_fitzharmon_2ops"),
                FamilyCrestQuest.JOHNATHON to
                    ("npc.johnathon_fitzharmon_1op" to "npc.johnathon_fitzharmon_2ops"),
                FamilyCrestQuest.AVAN to
                    ("npc.avan_fitzharmon_avan_1op" to "npc.avan_fitzharmon_avan_2ops"),
            )
        for ((son, forms) in sons) {
            val type = checkNotNull(ServerCacheManager.getNpc(son.asRSCM(RSCMType.NPC)))
            val transforms = checkNotNull(type.transforms)
            val (plain, withGauntlets) = forms
            assertEquals(
                plain.asRSCM(RSCMType.NPC),
                transforms[FamilyCrestQuest.STAGE_COMPLETE - 1],
                "$son should not offer gauntlets before the quest ends",
            )
            assertEquals(
                withGauntlets.asRSCM(RSCMType.NPC),
                transforms[FamilyCrestQuest.STAGE_COMPLETE],
                "$son should offer gauntlets once the quest ends",
            )
            val gauntletForm = checkNotNull(ServerCacheManager.getNpc(withGauntlets.asRSCM(RSCMType.NPC)))
            assertEquals("Gauntlets", gauntletForm.actions.getOpOrNull(2))
        }
    }

    @Test
    fun theDungeonLeversAndDoorsStandWhereThePuzzleExpectsThem() {
        for (lever in Lever.entries) {
            assertLocAt(lever.lowered, lever.coords)
        }
        assertLocAt(PuzzleDoor.H2.loc, CoordGrid(2719, 9671, 0))
        assertLocAt(PuzzleDoor.G2H1.loc, CoordGrid(2722, 9671, 0))
        assertLocAt(PuzzleDoor.I2H1.loc, CoordGrid(2727, 9690, 0))
        assertLocAt(PuzzleDoor.H2G1.loc, CoordGrid(2723, 9711, 0))
    }

    @Test
    fun eachDoorNameSpellsOutTheLeverPositionsThatOpenIt() {
        for (door in PuzzleDoor.entries) {
            val expected =
                door.raisedLevers.joinToString("") { "${it.name.lowercase()}2" } +
                    door.loweredLevers.joinToString("") { "${it.name.lowercase()}1" }
            assertEquals("loc.famcrest_door$expected", door.loc)
        }
    }

    @Test
    fun theWikiLeverSequenceIsTheOnlyWayIntoTheGoldRoom() {
        val raised = Lever.entries.associateWith { false }.toMutableMap()
        fun opens(door: PuzzleDoor) = door.opensNow { raised.getValue(it) }

        assertFalse(opens(PuzzleDoor.I2H1), "the gold room starts locked")
        assertFalse(opens(PuzzleDoor.H2G1), "lever I starts out of reach")

        raised[Lever.G] = true
        assertTrue(opens(PuzzleDoor.G2H1), "raising G opens the way to lever H")
        raised[Lever.H] = true
        assertTrue(opens(PuzzleDoor.H2), "raising H opens the way back out")
        raised[Lever.G] = false
        assertTrue(opens(PuzzleDoor.H2G1), "lowering G opens the north room")
        raised[Lever.I] = true
        assertTrue(opens(PuzzleDoor.H2G1), "the north room is still open to leave by")
        raised[Lever.G] = true
        assertTrue(opens(PuzzleDoor.H2), "raising G again leaves H2 open to reach lever H")
        raised[Lever.H] = false
        assertTrue(opens(PuzzleDoor.I2H1), "the gold room is open")
        assertTrue(opens(PuzzleDoor.G2H1), "and the entrance hall can still be left")
    }

    @Test
    fun theGoldSeamsBehindTheDoorAreOrdinaryGoldRocks() {
        val goldRock = "loc.goldrock2".asRSCM(RSCMType.LOC)
        for (seam in PERFECT_SEAMS) {
            val square = MapSquareKey.from(seam)
            val data = checkNotNull(cache.data(MAPS, square.id, 1)) { "no locs in ${square.id}" }
            val spawns = MapLocListDecoder.decode(InlineByteBuf(data)).spawns.map(::MapLocDefinition)
            val match =
                spawns.any {
                    it.id == goldRock &&
                        square.toCoords(it.level).translate(it.localX, it.localZ) == seam
                }
            assertTrue(match, "no gold rock at $seam")
        }
    }

    @Test
    fun chronozonIsStrongEnoughToNeedTheBlastSpells() {
        val demon = checkNotNull(ServerCacheManager.getNpc(FamilyCrestQuest.CHRONOZON.asRSCM(RSCMType.NPC)))
        assertEquals("Attack", demon.actions.getOpOrNull(1))
        assertEquals(60, demon.hitpoints)
        assertEquals(170, demon.combatLevel)
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
        /** The three `loc.goldrock2` spawns inside the hellhound room. */
        val PERFECT_SEAMS =
            listOf(
                CoordGrid(2732, 9680, 0),
                CoordGrid(2740, 9700, 0),
                CoordGrid(2743, 9699, 0),
            )

        lateinit var cache: dev.openrune.filesystem.Cache

        @JvmStatic
        @BeforeAll
        fun loadCache() {
            cache = ServerCacheManager.init(240)
        }
    }
}
