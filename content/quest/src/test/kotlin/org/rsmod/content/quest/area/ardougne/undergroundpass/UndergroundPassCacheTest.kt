package org.rsmod.content.quest.area.ardougne.undergroundpass

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.npc.MapNpcDefinition
import dev.openrune.map.npc.MapNpcListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.tile.MapTileSimpleDefinition
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
        assertLocAt("loc.upass_caveentrance2", CoordGrid(0, 38, 51, 1, 49))
        assertLocAt("loc.cave_exit_upass", CoordGrid(0, 39, 151, 0, 49))
        assertLocAt("loc.oldbridge_guiderope", UpassCoords.GUIDE_ROPE)
        assertLocAt("loc.old_bridge_up", UpassCoords.BRIDGE)
        assertLocAt("loc.upass_lever_up", CoordGrid(0, 38, 151, 4, 52))
        assertLocAt("loc.portcullis_lever_up", CoordGrid(0, 38, 151, 34, 8))
        assertLocAt("loc.obstical_rockswing_norope", UpassCoords.SWING_ROCK)
        assertLocAt("loc.upass_logtrap", UpassCoords.ORB_LOG_TRAP)
        assertLocAt("loc.cave_well", UpassCoords.WELL_OF_IBAN)
        assertLocAt("loc.bloodwell_upass", UpassCoords.WELL_OF_DOORS)
        assertLocAt("loc.cave_temple_altar", UpassCoords.WELL_OF_THE_DAMNED)
        assertLocAt("loc.iban_temple_throne", UpassCoords.IBAN_THRONE)
        assertLocAt("loc.ibantomb_left", UpassCoords.IBAN_TOMB_LEFT)
        assertLocAt("loc.ibantomb_right", UpassCoords.IBAN_TOMB_RIGHT)
        assertLocAt("loc.cavewalltunnel_upass_down", UpassCoords.LAIR_SHAFT_SOUTH)
        assertLocAt("loc.cavewalltunnel_upass_up", UpassCoords.CAMP_SHAFT_SOUTH)
        assertLocAt("loc.cavewalltunnel_upass_down", UpassCoords.LAIR_SHAFT_NORTH)
        assertLocAt("loc.cavewalltunnel_upass_up", UpassCoords.CAMP_SHAFT_NORTH)
        for (coords in UpassCoords.PORTCULLIS) {
            assertLocAt("loc.portcullis_upass", coords)
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
        assertNpcAt(UndergroundPassQuest.UNICORN, CoordGrid(0, 37, 150, 29, 3))
        assertNpcAt(UndergroundPassQuest.BOULDER, CoordGrid(0, 37, 149, 28, 59))
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

    /**
     * Every tile a script puts the player on. The obstacles are crossed by gliding onto these, so
     * one that is a wall or under a blocking loc strands the player inside the scenery.
     */
    @Test
    fun everyLandingTileCanBeStoodOn() {
        val landings =
            mapOf(
                "PASS_ARRIVAL" to UpassCoords.PASS_ARRIVAL,
                "CAVE_EXIT_LANDING" to UpassCoords.CAVE_EXIT_LANDING,
                "CREVASSE_FLOOR" to UpassCoords.CREVASSE_FLOOR,
                "ROCKPILE_TOP" to UpassCoords.ROCKPILE_TOP,
                "BRIDGE_WEST" to UpassCoords.BRIDGE_WEST,
                "BRIDGE_EAST" to UpassCoords.BRIDGE_EAST,
                "BRIDGE_LEVER_STAND" to UpassCoords.BRIDGE_LEVER_STAND,
                "SWING_START" to UpassCoords.SWING_START,
                "SWING_LANDING" to UpassCoords.SWING_LANDING,
                "SWING_BACK_START" to UpassCoords.SWING_BACK_START,
                "SWING_BACK_LANDING" to UpassCoords.SWING_BACK_LANDING,
                "GRID_CLIMB_OUT" to UpassCoords.GRID_CLIMB_OUT,
                "WELL_BOTTOM" to UpassCoords.WELL_BOTTOM,
                "MUDPILE_TOP" to UpassCoords.MUDPILE_TOP,
                "MUD_TUNNEL_EXIT" to UpassCoords.MUD_TUNNEL_EXIT,
                "CELL_TUNNEL_INSIDE" to UpassCoords.CELL_TUNNEL_INSIDE,
                "LEDGE_SOUTH_END" to UpassCoords.LEDGE_SOUTH_END,
                "UNICORN_TUNNEL_NORTH" to UpassCoords.UNICORN_TUNNEL_NORTH,
                "UNICORN_CAVE_ALIVE" to UpassCoords.UNICORN_CAVE_ALIVE,
                "UNICORN_CAVE_DEAD" to UpassCoords.UNICORN_CAVE_DEAD,
                "DOORS_PASS_SIDE" to UpassCoords.DOORS_PASS_SIDE,
                "DOORS_LAIR_SIDE" to UpassCoords.DOORS_LAIR_SIDE,
                "LAIR_SHAFT_SOUTH_LANDING" to UpassCoords.LAIR_SHAFT_SOUTH_LANDING,
                "CAMP_SHAFT_SOUTH_LANDING" to UpassCoords.CAMP_SHAFT_SOUTH_LANDING,
                "LAIR_SHAFT_NORTH_LANDING" to UpassCoords.LAIR_SHAFT_NORTH_LANDING,
                "CAMP_SHAFT_NORTH_LANDING" to UpassCoords.CAMP_SHAFT_NORTH_LANDING,
                "LAIR_FALL_0" to UpassCoords.LAIR_FALLS[0],
                "LAIR_FALL_1" to UpassCoords.LAIR_FALLS[1],
                "WITCH_HIDING_SPOT" to UpassCoords.WITCH_HIDING_SPOT,
                "TEMPLE_ESCAPE_LANDING" to UpassCoords.TEMPLE_ESCAPE_LANDING,
                "KOFTIK_LEADS_OUT" to UpassCoords.KOFTIK_LEADS_OUT,
            ) + UpassCoords.ROPE_SHOT_WALK.withIndex().associate { "ROPE_SHOT_WALK_${it.index}" to it.value }
        for ((name, coords) in landings) {
            assertTrue(standable(coords), "$name at $coords cannot be stood on")
        }
    }

    /** Floor that is neither flagged solid nor under a blocking loc, allowing for bridged tiles. */
    private fun standable(coords: CoordGrid): Boolean {
        val square = MapSquareKey.from(coords)
        val tiles = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, square.id, 0))))
        val localX = coords.x and (MAP_SQUARE_SIZE - 1)
        val localZ = coords.z and (MAP_SQUARE_SIZE - 1)
        if (tiles[localX, localZ, coords.level].toInt() and MapTileSimpleDefinition.BLOCK_MAP_SQUARE != 0) {
            return false
        }
        val data = cache.data(MAPS, square.id, 1) ?: return true
        return MapLocListDecoder.decode(InlineByteBuf(data)).spawns.map(::MapLocDefinition).none {
            val bridged =
                tiles[it.localX, it.localZ, 1].toInt() and MapTileSimpleDefinition.LINK_BELOW != 0
            val level = if (bridged) it.level - 1 else it.level
            val type = ServerCacheManager.getObject(it.id)
            if (level != coords.level || type == null || type.blockWalk == 0 || it.shape !in 10..11) {
                return@none false
            }
            val width = if (it.angle % 2 == 1) type.length else type.width
            val length = if (it.angle % 2 == 1) type.width else type.length
            localX in it.localX until it.localX + width && localZ in it.localZ until it.localZ + length
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
        const val MAP_SQUARE_SIZE = 64

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
