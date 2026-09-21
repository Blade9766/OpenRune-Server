package org.rsmod.content.quest.area.ardougne.undergroundpass

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
import org.junit.jupiter.api.Assertions.assertNull
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
 * Pins the cache facts Underground Pass is written against.
 *
 * Two of the quest's twelve stages are not free choices: the caged unicorn and everything living
 * in Iban's lair are multinpcs that index `varp.upass` directly, so the value the script advances
 * to is the value the client uses to decide whether to draw them. Everything else the quest
 * remembers is a varbit on `varp.ibanmulti`, which must stay clear of the stage varp.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class UndergroundPassCacheTest {
    @Test
    fun questRowMatchesTheStagesAndRequirementsTheScriptsUse() {
        val row = QuestRow.getRow("dbrow.${UndergroundPassQuest.QUEST_KEY}".asRSCM())
        assertEquals(UndergroundPassQuest.STAGE_COMPLETE, row.endstate)
        assertEquals(5, row.questpoints)
        assertEquals("npc.kinglathas".asRSCM(RSCMType.NPC), row.startnpc.single().id)
        assertEquals(
            "dbrow.${UndergroundPassQuest.BIOHAZARD_QUEST}".asRSCM(RSCMType.DBROW),
            row.requirementQuests.single().rowId,
        )
    }

    @Test
    fun theUnicornLeavesItsCageOnTheStageTheBoulderAdvancesTo() {
        val unicorn =
            checkNotNull(
                ServerCacheManager.getNpc(UndergroundPassQuest.UNICORN.asRSCM(RSCMType.NPC))
            )
        assertEquals("varp.upass".asRSCM(RSCMType.VARP), unicorn.multiVarp)
        val transforms = checkNotNull(unicorn.transforms)
        val visible = "npc.unicorn_upass_vis".asRSCM(RSCMType.NPC)
        for (stage in 0 until UndergroundPassQuest.STAGE_UNICORN) {
            assertEquals(visible, transforms[stage], "the unicorn should still be caged at $stage")
        }
        assertEquals(-1, transforms[UndergroundPassQuest.STAGE_UNICORN])
    }

    @Test
    fun ibanAndHisHouseholdLeaveOnTheStageTheTempleCollapsesAt() {
        val household =
            listOf(
                UndergroundPassQuest.IBAN,
                UndergroundPassQuest.DISCIPLE,
                UndergroundPassQuest.KARDIA,
                UndergroundPassQuest.DOOMION,
                UndergroundPassQuest.OTHAINIAN,
                UndergroundPassQuest.HOLTHION,
                UndergroundPassQuest.HALF_SOULLESS,
            ) + UndergroundPassQuest.SLAVES

        for (name in household) {
            val npc = checkNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC))) { name }
            assertEquals("varp.upass".asRSCM(RSCMType.VARP), npc.multiVarp, name)
            val transforms = checkNotNull(npc.transforms) { name }
            assertEquals(
                -1,
                transforms[UndergroundPassQuest.STAGE_IBAN_DEAD],
                "$name should be gone once Iban is dead",
            )
            assertTrue(
                transforms[UndergroundPassQuest.STAGE_IBAN_DEAD - 1] != -1,
                "$name should still be there the stage before",
            )
        }
    }

    @Test
    fun theDarkMageOnlySellsStavesOnceTheQuestIsFinished() {
        val mage = checkNotNull(ServerCacheManager.getNpc("npc.upassmage".asRSCM(RSCMType.NPC)))
        val transforms = checkNotNull(mage.transforms)
        assertEquals(
            "npc.upassmage_2ops".asRSCM(RSCMType.NPC),
            transforms[UndergroundPassQuest.STAGE_COMPLETE],
        )
        assertEquals(
            "npc.upassmage_1op".asRSCM(RSCMType.NPC),
            transforms[UndergroundPassQuest.STAGE_IBAN_DEAD],
        )
    }

    @Test
    fun theQuestsOwnFlagsStayOffTheStageVarp() {
        val stageVarp = "varp.upass".asRSCM(RSCMType.VARP)
        val flagVarp = "varp.ibanmulti".asRSCM(RSCMType.VARP)
        for (name in UndergroundPassQuest.SUB_STATE_VARBITS) {
            val varbit = checkNotNull(ServerCacheManager.getVarbits()[name.asRSCM(RSCMType.VARBIT)])
            assertEquals(flagVarp, varbit.varp, "$name is not on varp.ibanmulti")
        }
        val onStageVarp =
            ServerCacheManager.getVarbits().values.filter { it.varp == stageVarp }
        assertTrue(onStageVarp.isEmpty(), "varp.upass carries varbits: $onStageVarp")
    }

    @Test
    fun eachKoftikHidesBehindAVarbitOfHisOwn() {
        val koftiks =
            listOf(
                UndergroundPassQuest.KOFTIK_OUTSIDE,
                UndergroundPassQuest.KOFTIK_BRIDGE,
                UndergroundPassQuest.KOFTIK_GRID,
                UndergroundPassQuest.KOFTIK_MAZE,
                UndergroundPassQuest.KOFTIK_TEMPLE,
                UndergroundPassQuest.KOFTIK_END,
            )
        val varbits = UndergroundPassQuest.KOFTIK_STAGES.map { it.first.asRSCM(RSCMType.VARBIT) }
        for ((name, varbit) in koftiks.zip(varbits)) {
            val npc = checkNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC))) { name }
            assertEquals(varbit, npc.multiVarBit, name)
            val transforms = checkNotNull(npc.transforms) { name }
            assertEquals(
                "npc.caveguide_vis".asRSCM(RSCMType.NPC),
                transforms[UndergroundPassQuest.KOFTIK_HERE],
                name,
            )
            assertEquals(-1, transforms[UndergroundPassQuest.KOFTIK_GONE], name)
        }
    }

    @Test
    fun theFourOrbsEachHaveTheirOwnVarbit() {
        val orbLocs = listOf("loc.caveorb", "loc.caveorb2", "loc.caveorb3", "loc.caveorb4")
        for ((index, name) in orbLocs.withIndex()) {
            val loc = checkNotNull(ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC))) { name }
            assertEquals(orbTakenVarbit(index).asRSCM(RSCMType.VARBIT), loc.multiVarBit, name)
            val transforms = checkNotNull(loc.transforms) { name }
            assertEquals("loc.caveorb_vis".asRSCM(RSCMType.LOC), transforms[0], name)
            assertEquals(-1, transforms[1], name)
        }
    }

    @Test
    fun theSceneryOfThePassStandsWhereTheScriptsExpectIt() {
        assertLocAt("loc.upass_caveentrance2", UpassCoords.CAVE_ENTRANCE)
        assertLocAt("loc.cave_exit_upass", UpassCoords.CAVE_EXIT)
        assertLocAt("loc.oldbridge_guiderope", UpassCoords.GUIDE_ROPE)
        assertLocAt("loc.old_bridge_up", UpassCoords.BRIDGE)
        assertLocAt("loc.upass_lever_up", UpassCoords.BRIDGE_LEVER)
        assertLocAt("loc.portcullis_lever_up", UpassCoords.PORTCULLIS_LEVER)
        assertLocAt("loc.furnace_upass", UpassCoords.FURNACE)
        assertLocAt("loc.cave_well", UpassCoords.WELL_OF_IBAN)
        assertLocAt("loc.upass_mud", UpassCoords.LOOSE_MUD)
        assertLocAt("loc.cavewalltunnel_upass_tocells", UpassCoords.CELL_TUNNEL)
        assertLocAt("loc.unicorncage_destroyed_upass", UpassCoords.UNICORN_CAGE)
        assertLocAt("loc.bloodwell_upass", UpassCoords.WELL_OF_DOORS)
        assertLocAt("loc.cave_temple_altar", UpassCoords.WELL_OF_THE_DAMNED)
        assertLocAt("loc.upassdwarfbrewbarrel", UpassCoords.BREW_BARREL)
        assertLocAt("loc.ibantomb_left", UpassCoords.IBAN_TOMB_LEFT)
        assertLocAt("loc.ibantomb_right", UpassCoords.IBAN_TOMB_RIGHT)
        assertLocAt("loc.cavewitchchest", UpassCoords.WITCH_CHEST)
        assertLocAt("loc.upassshutchest1", UpassCoords.SHADOW_CHEST)
        assertLocAt("loc.upass_cage_dummy", UpassCoords.DOVE_CAGE)
        assertLocAt("loc.upass_last_out", UpassCoords.PASS_EXIT_CAVE)
        for (coords in UpassCoords.PORTCULLIS) {
            assertLocAt("loc.portcullis_upass", coords)
        }
        for (coords in UpassCoords.DOORS_OF_IBAN) {
            assertTrue(
                locAt("loc.cavetempledoor2l", coords) || locAt("loc.cavetempledoor2r", coords),
                "no Door of Iban at $coords",
            )
        }
    }

    /** The grid is a ten-by-ten checkerboard of the two grille types, and nothing else. */
    @Test
    fun theGridIsTheSizeThePathGeneratorAssumes() {
        val left = "loc.gill_trapl".asRSCM(RSCMType.LOC)
        val right = "loc.gill_trapr".asRSCM(RSCMType.LOC)
        val square = MapSquareKey.from(CoordGrid(UpassCoords.GRID_WEST_X, UpassCoords.GRID_SOUTH_Z, 0))
        val data = checkNotNull(cache.data(MAPS, square.id, 1))
        val grilles =
            MapLocListDecoder.decode(InlineByteBuf(data))
                .spawns
                .map(::MapLocDefinition)
                .filter { it.id == left || it.id == right }
                .map { square.toCoords(it.level).translate(it.localX, it.localZ) }
                .toSet()
        assertEquals(UpassCoords.GRID_SIZE * UpassCoords.GRID_SIZE, grilles.size)
        for (x in UpassCoords.GRID_WEST_X..UpassCoords.GRID_EAST_X) {
            for (z in UpassCoords.GRID_SOUTH_Z..UpassCoords.GRID_NORTH_Z) {
                assertTrue(CoordGrid(x, z, 0) in grilles, "no grille at $x,$z")
            }
        }
    }

    @Test
    fun theQuestNpcsAreSpawnedWhereTheQuestNeedsThem() {
        assertNpcAt(UndergroundPassQuest.KOFTIK_OUTSIDE, CoordGrid(2436, 3315, 0))
        assertNpcAt(UndergroundPassQuest.KOFTIK_BRIDGE, CoordGrid(2449, 9716, 0))
        assertNpcAt(UndergroundPassQuest.KOFTIK_GRID, CoordGrid(2479, 9679, 0))
        assertNpcAt(UndergroundPassQuest.KOFTIK_MAZE, CoordGrid(2423, 9609, 0))
        assertNpcAt(UndergroundPassQuest.KOFTIK_END, CoordGrid(2443, 9607, 0))
        assertNpcAt(UndergroundPassQuest.NILOOF, CoordGrid(2315, 9806, 0))
        assertNpcAt(UndergroundPassQuest.KLANK, CoordGrid(2323, 9804, 0))
        assertNpcAt(UndergroundPassQuest.KAMEN, CoordGrid(2325, 9799, 0))
        assertNpcAt(UndergroundPassQuest.UNICORN, UpassCoords.UNICORN_SPAWN)
        assertNpcAt(UndergroundPassQuest.BOULDER, UpassCoords.BOULDER_SPAWN)
        assertNpcAt(UndergroundPassQuest.KALRAG, CoordGrid(2356, 9911, 0))
        assertNpcAt(UndergroundPassQuest.PALADIN_JERRO, CoordGrid(2424, 9721, 0))
        assertNpcAt(UndergroundPassQuest.PALADIN_CARL, CoordGrid(2422, 9718, 0))
        assertNpcAt(UndergroundPassQuest.PALADIN_HARRY, CoordGrid(2426, 9718, 0))
    }

    /**
     * The map data spawns a plain copy of several of these npcs on the same tile as the multinpc
     * the quest drives, which would leave two of everything standing in the pass.
     */
    @Test
    fun noQuestNpcIsSpawnedTwiceOnTheSameTile() {
        val doubled =
            listOf(
                UndergroundPassQuest.PALADIN_JERRO,
                UndergroundPassQuest.PALADIN_CARL,
                UndergroundPassQuest.PALADIN_HARRY,
                UndergroundPassQuest.KALRAG,
                UndergroundPassQuest.UNICORN,
            )
        for (name in doubled) {
            val twin = UndergroundPassQuest.visibleTwin(name).asRSCM(RSCMType.NPC)
            val base = name.asRSCM(RSCMType.NPC)
            for (square in PASS_SQUARES) {
                val spawns = npcSpawns(square)
                val baseTiles = spawns.filter { it.id == base }.map { it.localX to it.localZ }
                val twinTiles = spawns.filter { it.id == twin }.map { it.localX to it.localZ }
                assertTrue(
                    baseTiles.none { it in twinTiles },
                    "$name and its visible twin share a tile in $square",
                )
            }
        }
    }

    @Test
    fun theDisciplesStillDropTheRobesTheTempleDoorsWant() {
        val disciple =
            checkNotNull(
                ServerCacheManager.getNpc(
                    UndergroundPassQuest.visibleTwin(UndergroundPassQuest.DISCIPLE)
                        .asRSCM(RSCMType.NPC)
                )
            )
        assertNull(disciple.transforms, "the visible disciple should be a plain npc")
        for (robe in listOf(UndergroundPassQuest.ZAMORAK_TOP, UndergroundPassQuest.ZAMORAK_BOTTOM)) {
            assertTrue(
                ServerCacheManager.getItem(robe.asRSCM(RSCMType.OBJ))?.wearpos1 != -1,
                "$robe is not wearable",
            )
        }
    }

    private fun locAt(loc: String, coords: CoordGrid): Boolean {
        val id = loc.asRSCM(RSCMType.LOC)
        val square = MapSquareKey.from(coords)
        val data = cache.data(MAPS, square.id, 1) ?: return false
        return MapLocListDecoder.decode(InlineByteBuf(data)).spawns.map(::MapLocDefinition).any {
            it.id == id && square.toCoords(it.level).translate(it.localX, it.localZ) == coords
        }
    }

    private fun assertLocAt(loc: String, coords: CoordGrid) {
        assertTrue(locAt(loc, coords), "$loc is not at $coords")
    }

    private fun npcSpawns(square: MapSquareKey): List<MapNpcDefinition> {
        val data = cache.data(MAPS, square.id, 5) ?: return emptyList()
        return MapNpcListDecoder.decode(InlineByteBuf(data)).packedSpawns.map(::MapNpcDefinition)
    }

    private fun assertNpcAt(npc: String, coords: CoordGrid) {
        val id = npc.asRSCM(RSCMType.NPC)
        val square = MapSquareKey.from(coords)
        val match =
            npcSpawns(square).any {
                it.id == id && square.toCoords(it.level).translate(it.localX, it.localZ) == coords
            }
        assertTrue(match, "$npc is not spawned at $coords")
    }

    private companion object {
        /** Every map square the quest's npcs stand in. */
        val PASS_SQUARES =
            listOf(
                CoordGrid(2424, 9721, 0),
                CoordGrid(2356, 9911, 0),
                CoordGrid(2372, 9604, 0),
                CoordGrid(2315, 9806, 0),
                CoordGrid(2136, 4647, 1),
            ).map(MapSquareKey::from)

        lateinit var cache: dev.openrune.filesystem.Cache

        @JvmStatic
        @BeforeAll
        fun loadCache() {
            cache = ServerCacheManager.init(240)
        }
    }
}
