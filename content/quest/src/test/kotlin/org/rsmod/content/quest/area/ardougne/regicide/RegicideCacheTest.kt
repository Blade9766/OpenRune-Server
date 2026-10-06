package org.rsmod.content.quest.area.ardougne.regicide

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.baseVar
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.table.QuestRow
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.AGILITY_REQ
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.CRAFTING_REQ
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_DENSE_FOREST
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_LETTER
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_TRACKER_HELPING
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_TYRAS_DEAD
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Pins the cache and map facts Regicide is written against: the quest row, its vars and the
 * multis that read them, the interfaces and items, where every loc the scripts name stands, the
 * landing tiles, the restored temple's mapping onto the ruins, and the dense forest table, which
 * is re-derived here from the real collision of Isafdar.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class RegicideCacheTest {

    @Test fun `the quest row matches the stages, rewards and requirements`() {
        val row = QuestRow.getRow("dbrow.${RegicideQuest.QUEST_KEY}".asRSCM())
        assertEquals(STAGE_COMPLETE, row.endstate)
        assertEquals(3, row.questpoints)
        assertEquals(mapOf("agility" to 137_500), row.statXpAwarded.associate { it.t0.displayName to it.t1 }, "tenths of 13,750 xp")
        assertEquals(mapOf("agility" to AGILITY_REQ, "crafting" to CRAFTING_REQ), row.requirementStats.associate { it.t0.displayName to it.t1 })
        assertEquals(setOf("dbrow.quest_undergroundpass".asRSCM()), row.requirementQuests.map { it.rowId }.toSet())
    }

    @Test fun `every quest var is permanent and wide enough`() {
        assertEquals(VarpLifetime.Perm, varp("varp.regicide_quest").scope)
        for (name in listOf("varp.regicide_bits", "varp.regicide_still_total", "varp.regicide_still_settings", "varp.regicide_state", "varp.regicide_scene_return")) {
            assertEquals(VarpLifetime.Perm, varp(name).scope, name)
        }
        for (name in RegicideQuest.OWN_VARBITS + RegicideQuest.Ingredient.entries.map { it.varbit }) {
            val bit = checkNotNull(ServerCacheManager.getVarbit(name.asRSCM(RSCMType.VARBIT))) { name }
            assertEquals(VarpLifetime.Perm, ServerCacheManager.getVarp(bit.baseVar.id)!!.scope, name)
        }
        val shared = checkNotNull(ServerCacheManager.getVarbit("varbit.regicide_read_message".asRSCM(RSCMType.VARBIT)))
        assertEquals("varp.regicide_state".asRSCM(RSCMType.VARP), shared.baseVar.id)
        assertTrue("timer.regicide_still".asRSCM(RSCMType.TIMER) >= 0 && "timer.regicide_messenger".asRSCM(RSCMType.TIMER) >= 0)
        assertTrue("area.regicide_isafdar_arrival".asRSCM(RSCMType.AREA) >= 0 && "area.regicide_ardougne_castle".asRSCM(RSCMType.AREA) >= 0)
    }

    @Test fun `the multis follow the stage the way the scripts assume`() {
        val tracker = npc(RegicideQuest.TRACKER)
        assertEquals("varp.regicide_quest".asRSCM(RSCMType.VARP), tracker.multiVarp)
        val transforms = checkNotNull(tracker.transforms)
        assertEquals(RegicideQuest.TRACKER_VIS.asRSCM(RSCMType.NPC), transforms[STAGE_TYRAS_DEAD], "the tracker is still there after the explosion")
        assertTrue(STAGE_LETTER >= transforms.lastIndex, "and gone once the letter is written")

        val footprints = loc("loc.regicide_old_camp_footprints")
        val steps = checkNotNull(footprints.transforms)
        val follow = "loc.regicide_old_camp_footprints_vis_op".asRSCM(RSCMType.LOC)
        assertEquals((STAGE_TRACKER_HELPING..STAGE_DENSE_FOREST).toList(), steps.indices.filter { steps[it] == follow })
        assertEquals("Follow", loc("loc.regicide_old_camp_footprints_vis_op").actions.getOpOrNull(0))

        val guard = npc(RegicideQuest.CATAPULT_GUARD)
        assertEquals("varbit.regicide_given_rabbit".asRSCM(RSCMType.VARBIT), guard.multiVarBit)
        assertEquals(RegicideQuest.CATAPULT_GUARD_VIS.asRSCM(RSCMType.NPC), checkNotNull(guard.transforms)[0])
        assertEquals(-1, checkNotNull(guard.transforms)[1], "he is away eating once given his rabbit")

        assertEquals(RegicideQuest.IORWERTH_VIS.asRSCM(RSCMType.NPC), checkNotNull(npc(RegicideQuest.IORWERTH).transforms)[0])
        assertEquals("npc.kinglathas_vis".asRSCM(RSCMType.NPC), checkNotNull(npc(RegicideQuest.KING_LATHAS).transforms)[0])
    }

    @Test fun `the npcs, items, interface and animations are the ones the scripts use`() {
        val guard = npc(RegicideQuest.ENCOUNTER_GUARD)
        assertEquals("Tyras guard", guard.name)
        assertEquals(110, guard.combatLevel)
        assertEquals(1, guard.attackRange, "the forest guard has no halberd reach")
        assertEquals("Idris", npc(RegicideQuest.IDRIS).name)
        assertEquals("Essyllt", npc(RegicideQuest.ESSYLLT).name)
        assertEquals("Morvran", npc(RegicideQuest.MORVRAN).name)
        assertEquals("Arianwyn", npc(RegicideQuest.ARIANWYN).name)
        assertEquals("General Hining", npc(RegicideQuest.HINING).name)
        assertEquals("King's Messenger", npc(RegicideQuest.MESSENGER).name)
        for ((obj, op) in listOf(RegicideQuest.BOOK to "Read", RegicideQuest.IORWERTH_MESSAGE to "Read", RegicideQuest.KINGS_MESSAGE to "Read",
            RegicideUnlocks.IORWERTH_CAMP_SCROLL to "Teleport", RegicideUnlocks.ZUL_ANDRA_SCROLL to "Teleport")) {
            assertEquals(op, item(obj).interfaceOptions[0], obj)
        }
        assertEquals("Wear", item(RegicideQuest.PENDANT).interfaceOptions[1])
        assertEquals("Barrel bomb", item(RegicideQuest.UNFUSED_BOMB).name)
        assertEquals("Barrel bomb", item(RegicideQuest.BARREL_BOMB).name)
        assertEquals("Strip of cloth", item(RegicideQuest.STRIP_OF_CLOTH).name)
        assertEquals("Limestone", item(RegicideQuest.LIMESTONE).name)
        assertTrue(item("obj.limestonebrick").id != item(RegicideQuest.LIMESTONE).id)
        assertTrue(item("obj.cert_coal").id != item(RegicideQuest.COAL).id, "noted coal is a different obj")
        assertEquals("Roast rabbit", item(RegicideQuest.ROAST_RABBIT).name)
        for (component in listOf(FractionalStill.ADD_COAL, FractionalStill.PRESSURE_DOWN, FractionalStill.PRESSURE_UP, FractionalStill.TAR_DOWN, FractionalStill.TAR_UP)) {
            assertTrue(component.asRSCM(RSCMType.COMPONENT) ushr 16 == FractionalStill.INTERFACE.asRSCM(RSCMType.INTERFACE), component)
        }
        for (seq in listOf(IsafdarObstacles.STEP_OVER_SEQ, IsafdarObstacles.SQUEEZE_SEQ, IsafdarObstacles.JUMP_SEQ, IsafdarObstacles.BALANCE_SEQ,
            Catapult.WIND_SEQ, Catapult.TRIGGER_SEQ, Catapult.LOC_WIND_SEQ, Catapult.LOC_FIRE_SEQ, IdrisScene.SHOOT_SEQ, IdrisScene.DEATH_SEQ,
            RegicideUnlocks.SCROLL_SEQ, BarrelBomb.FURNACE_SEQ, BarrelBomb.GRIND_SEQ)) {
            assertNotNull(ServerCacheManager.getAnim(seq.asRSCM(RSCMType.SEQ)), seq)
        }
        for (spot in listOf(Catapult.FLIGHT_SPOTANIM, Catapult.EXPLOSION_SPOTANIM, RegicideUnlocks.SCROLL_SPOTANIM)) {
            assertTrue(spot.asRSCM(RSCMType.SPOTANIM) >= 0, spot)
        }
        assertEquals("Operate", loc(FractionalStill.STILL).actions.getOpOrNull(0))
        assertEquals("Enter", loc("loc.regicide_cross_over1").actions.getOpOrNull(0))
        assertEquals("Take", loc(BarrelBomb.COAL_TAR).actions.getOpOrNull(0))
        assertEquals("Climb-down", loc(WellOfVoyage.TEMPLE_WELL).actions.getOpOrNull(0))
        assertEquals("Leave", loc(WellOfVoyage.CAVE_EXIT).actions.getOpOrNull(0))
        assertEquals(215, loc("loc.furnace").category, "any furnace in the furnace category accepts limestone")
    }

    @Test fun `the locs stand where the scripts look for them`() {
        assertTrue(placed(WellOfVoyage.TEMPLE_WELL, RestoredTemple.WELL_TILE))
        assertTrue(placed(WellOfVoyage.TIRANNWN_WELL, WellOfVoyage.TIRANNWN_WELL_TILE))
        assertTrue(placed(WellOfVoyage.CAVE_EXIT, WellOfVoyage.CAVE_EXIT_TILE))
        assertTrue(placed(WellOfVoyage.CAVE_ENTRANCE, WellOfVoyage.CAVE_ENTRANCE_TILE))
        assertTrue(placed(FractionalStill.STILL, CoordGrid(2927, 3212, 0)))
        assertTrue(placed(Catapult.CATAPULT, Catapult.CATAPULT_TILE))
        assertTrue(placed(BarrelBomb.SMALL_FURNACE, CoordGrid(2193, 3146, 0)))
        assertTrue(placed("loc.regicide_old_camp_footprints", CoordGrid(2240, 3150, 0)))
        assertTrue(placed(BarrelBomb.COAL_TAR, CoordGrid(2263, 3127, 0)), "tar just south of the tracker")
        for (patch in IsafdarObstacles.LeafPatch.entries) assertTrue(placed("loc.regicide_pitfall_mid", patch.centre), patch.name)
        for (wire in IsafdarObstacles.Tripwire.entries) assertTrue(placed(IsafdarObstacles.TRIPWIRE, wire.coords), wire.name)
        for (log in IsafdarObstacles.LogBalance.entries) {
            assertTrue(placedAny(IsafdarObstacles.LOG_STARTS, log.first) && placedAny(IsafdarObstacles.LOG_STARTS, log.second), log.name)
        }
        val forests = placedLocs.filter { it.name in IsafdarObstacles.DENSE_FORESTS && inIsafdar(it.coords) }.map { it.coords }.toSet()
        assertEquals(DenseForest.PASS_SIDES.keys, forests, "every dense forest in Isafdar has a direction")
        assertTrue(placed("loc.regicide_cross_over1", DenseForest.GUARD_TRIGGER))
        assertTrue(placed("loc.overpass_gate_left", CoordGrid(2384, 3334, 0)) && placed("loc.overpass_gate_right", CoordGrid(2386, 3334, 0)))
        for (door in listOf(CoordGrid(2143, 4647, 1), CoordGrid(2143, 4648, 1))) {
            assertTrue(placedAny(listOf("loc.upass_templedoor_closed_left", "loc.upass_templedoor_closed_right"), door), "ruined temple door $door")
            val copy = door.translate(RestoredTemple.DX, RestoredTemple.DZ)
            assertTrue(placedAny(listOf("loc.upass_templedoor_closed_left", "loc.upass_templedoor_closed_right"), copy), "restored temple door $copy")
        }
        assertTrue(placed("loc.cave_temple_altar", CoordGrid(2136, 4647, 1)), "the well of the damned in the ruins")
        assertEquals(RestoredTemple.WELL_TILE, CoordGrid(2136, 4647, 1).translate(RestoredTemple.DX, RestoredTemple.DZ))
    }

    @Test fun `every landing tile can be stood on`() {
        val landings = listOf(WellOfVoyage.ISAFDAR_ARRIVAL, WellOfVoyage.TIRANNWN_CAVE_LANDING, WellOfVoyage.TIRANNWN_WELL_LANDING,
            RestoredTemple.WELL_LANDING, RegicideUnlocks.IORWERTH_CAMP, RegicideUnlocks.ZUL_ANDRA, Catapult.VANTAGE)
        for (tile in landings) assertTrue(open(tile), "$tile")
        for (z in listOf(4647, 4648)) {
            assertTrue(open(CoordGrid(2014, z + RestoredTemple.DZ, 1)), "inside the restored temple at $z")
            assertTrue(open(CoordGrid(2145, z, 1)), "outside the ruins at $z")
        }
        for (x in RegicideUnlocks.GATE_MIN_X..RegicideUnlocks.GATE_MAX_X) {
            assertTrue(open(CoordGrid(x, 3335, 0)) && open(CoordGrid(x, 3333, 0)), "both sides of the Arandar gate at $x")
        }
        for (tile in IdrisScene.SCENE_TILES.take(7)) assertTrue(open(tile), "Idris scene tile $tile")
        for (tile in IdrisScene.SCENE_TILES + listOf(WellOfVoyage.ISAFDAR_ARRIVAL)) {
            assertTrue(tile.x shr 3 in IdrisScene.ZONE_X until IdrisScene.ZONE_X + IdrisScene.BLOCK_ZONES, "$tile outside the copied clearing")
            assertTrue(tile.z shr 3 in IdrisScene.ZONE_Z until IdrisScene.ZONE_Z + IdrisScene.BLOCK_ZONES, "$tile outside the copied clearing")
        }
        for (tile in Catapult.SCENE_TILES) {
            assertTrue(tile.x shr 3 in 272..274 && tile.z shr 3 in 392..398, "$tile outside the copied camp")
        }
        for (patch in IsafdarObstacles.LeafPatch.entries) {
            val south = patch.across(patch.centre.translate(0, 5))
            val north = patch.across(patch.centre.translate(0, -5))
            val west = patch.across(patch.centre.translate(5, 0))
            val east = patch.across(patch.centre.translate(-5, 0))
            assertTrue(listOf(south, north).all(::open) || listOf(west, east).all(::open), patch.name)
        }
        for (wire in IsafdarObstacles.Tripwire.entries) {
            val sides = listOf(wire.across(wire.coords.translate(-5, -5)), wire.across(wire.coords.translate(5, 5)))
            assertTrue(sides.all(::open), "${wire.name} $sides")
        }
        for (log in IsafdarObstacles.LogBalance.entries) {
            assertTrue(open(log.across(log.first.translate(-1, -1))) && open(log.across(log.second.translate(1, 1))), log.name)
        }
    }

    @Test fun `Ardougne Castle's area takes in the king's throne room`() {
        val text = areaFile("regicide_ardougne_castle.toml")
        assertTrue(text.contains("[2568, 3283]") && text.contains("[2592, 3312]"))
        val king = CoordGrid(2578, 3293, 1)
        assertTrue(king.x in 2568..2592 && king.z in 3283..3312)
        val arrival = areaFile("regicide_isafdar_arrival.toml")
        assertTrue(arrival.contains("[2280, 3204]") && arrival.contains("[2312, 3232]"))
        assertTrue(WellOfVoyage.ISAFDAR_ARRIVAL.x in 2280..2312 && WellOfVoyage.ISAFDAR_ARRIVAL.z in 3204..3232)
    }

    /**
     * Walks Isafdar from the cave mouth. Traps and log balances are free; each dense forest costs
     * one. The tracker and Lord Iorwerth must be reachable before the tracker's lesson, Tyras Camp
     * only through dense forest, and each forest's pass side must match [DenseForest.PASS_SIDES].
     */
    @Test fun `Isafdar's crossings give the dense forest table and the quest route`() {
        val graph = IsafdarGraph()
        val arrival = graph.componentOf(WellOfVoyage.ISAFDAR_ARRIVAL)
        assertNotNull(arrival, "the cave mouth landing is in the forest")
        val noForest = graph.depths(arrival!!, forests = false)
        val withForest = graph.depths(arrival, forests = true)
        for (poi in listOf(CoordGrid(2257, 3149, 0), CoordGrid(2240, 3151, 0), CoordGrid(2205, 3252, 0), CoordGrid(2263, 3127, 0))) {
            assertTrue(graph.componentOf(poi)?.let { it in noForest } == true, "$poi reachable before learning the dense forest")
        }
        for (poi in listOf(CoordGrid(2187, 3149, 0), CoordGrid(2181, 3184, 0))) {
            val component = checkNotNull(graph.componentOf(poi)) { "$poi" }
            assertFalse(component in noForest, "$poi needs the dense forest")
            assertTrue(component in withForest, "$poi is reachable through it")
        }
        for (crossing in graph.forests) {
            val a = withForest[crossing.a]
            val b = withForest[crossing.b]
            val expected =
                when {
                    a == null || b == null || a == b -> null
                    a < b -> crossing.sideA
                    else -> crossing.sideB
                }
            assertEquals(expected, DenseForest.PASS_SIDES[crossing.coords], "dense forest at ${crossing.coords}")
        }
        val west = graph.componentOf(DenseForest.GUARD_TRIGGER.translate(-1, 1))
        assertTrue(graph.componentOf(CoordGrid(2223, 3123, 0)) == west, "the guard catches the player on the tar side of the forest")
    }

    @Test fun `the forest guard steps out where the player can reach him`() {
        val landings = listOf(CoordGrid(2231, 3150, 0), CoordGrid(2231, 3149, 0), CoordGrid(2231, 3148, 0)).filter(::open)
        assertTrue(CoordGrid(2231, 3150, 0) in landings, "the tile the forest drops the player on")
        for (landing in landings) {
            val tile = checkNotNull(TyrasGuardEncounter.spawnTile(collision, landing)) { "$landing" }
            assertTrue(tile in reach(landing), "guard tile $tile is walkable from $landing")
            assertTrue(tile.chebyshevDistance(landing) >= 2, "guard tile $tile is not on top of the player")
        }
    }

    private class Crossing(val coords: CoordGrid, val a: Int?, val b: Int?, val sideA: DenseForest.Side, val sideB: DenseForest.Side)

    private inner class IsafdarGraph {
        private val component = HashMap<CoordGrid, Int>()
        private val links = mutableListOf<Triple<Int?, Int?, Boolean>>()
        val forests = mutableListOf<Crossing>()

        init {
            var next = 0
            for (x in ISAFDAR[0]..ISAFDAR[2]) for (z in ISAFDAR[1]..ISAFDAR[3]) {
                val start = CoordGrid(x, z, 0)
                if (!open(start) || start in component) continue
                val id = next++
                for (tile in reach(start)) component[tile] = id
            }
            for (p in placedLocs.filter { inIsafdar(it.coords) && (it.name in IsafdarObstacles.DENSE_FORESTS || it.name == IsafdarObstacles.STICKS) }) {
                val (w, l) = dims(p)
                val c = p.coords
                when {
                    p.name in IsafdarObstacles.DENSE_FORESTS -> {
                        val alongX = w < l
                        val a = if (alongX) CoordGrid(c.x - 1, c.z + l / 2, 0) else CoordGrid(c.x + w / 2, c.z - 1, 0)
                        val b = if (alongX) CoordGrid(c.x + w, c.z + l / 2, 0) else CoordGrid(c.x + w / 2, c.z + l, 0)
                        val crossing =
                            Crossing(c, componentNear(a), componentNear(b),
                                if (alongX) DenseForest.Side.WEST else DenseForest.Side.SOUTH,
                                if (alongX) DenseForest.Side.EAST else DenseForest.Side.NORTH)
                        forests += crossing
                        links += Triple(crossing.a, crossing.b, true)
                    }
                    p.name == IsafdarObstacles.STICKS -> {
                        val a = if (w >= l) CoordGrid(c.x - 1, c.z, 0) else CoordGrid(c.x, c.z - 1, 0)
                        val b = if (w >= l) CoordGrid(c.x + w, c.z, 0) else CoordGrid(c.x, c.z + l, 0)
                        links += Triple(componentNear(a), componentNear(b), false)
                    }
                }
            }
            for (patch in IsafdarObstacles.LeafPatch.entries) {
                links += Triple(componentNear(patch.across(patch.centre.translate(-5, -5))), componentNear(patch.across(patch.centre.translate(5, 5))), false)
            }
            for (wire in IsafdarObstacles.Tripwire.entries) {
                links += Triple(componentNear(wire.across(wire.coords.translate(-5, -5))), componentNear(wire.across(wire.coords.translate(5, 5))), false)
            }
            for (log in IsafdarObstacles.LogBalance.entries) {
                links += Triple(componentNear(log.across(log.first.translate(-1, -1))), componentNear(log.across(log.second.translate(1, 1))), false)
            }
        }

        fun componentOf(tile: CoordGrid): Int? =
            (0..3).flatMap { r -> (-r..r).flatMap { dx -> (-r..r).map { dz -> tile.translate(dx, dz) } } }.firstNotNullOfOrNull { component[it] }

        private fun componentNear(tile: CoordGrid): Int? =
            component[tile] ?: (-1..1).flatMap { dx -> (-1..1).map { dz -> tile.translate(dx, dz) } }.firstNotNullOfOrNull { component[it] }

        fun depths(start: Int, forests: Boolean): Map<Int, Int> {
            val depth = HashMap<Int, Int>()
            depth[start] = 0
            val queue = ArrayDeque(listOf(start))
            while (queue.isNotEmpty()) {
                val c = queue.removeFirst()
                for ((a, b, forest) in links) {
                    if (forest && !forests) continue
                    val other = when (c) { a -> b; b -> a; else -> null } ?: continue
                    val d = depth.getValue(c) + if (forest) 1 else 0
                    if (other !in depth || depth.getValue(other) > d) {
                        depth[other] = d
                        if (forest) queue.addLast(other) else queue.addFirst(other)
                    }
                }
            }
            return depth
        }
    }

    private fun reach(from: CoordGrid): Set<CoordGrid> {
        val steps = StepValidator(collision)
        val seen = hashSetOf(from)
        val queue = ArrayDeque(listOf(from))
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            for (dx in -1..1) for (dz in -1..1) {
                if ((dx == 0 && dz == 0) || !steps.canTravel(c.level, c.x, c.z, dx, dz)) continue
                val n = c.translate(dx, dz)
                if (!inIsafdar(n)) continue
                if (seen.add(n)) queue += n
            }
        }
        return seen
    }

    private fun dims(p: Placed): Pair<Int, Int> {
        val type = loc(p.name)
        return if (p.angle % 2 == 1) type.length to type.width else type.width to type.length
    }

    private fun inIsafdar(c: CoordGrid) = c.level == 0 && c.x in ISAFDAR[0]..ISAFDAR[2] && c.z in ISAFDAR[1]..ISAFDAR[3]

    private fun areaFile(name: String): String =
        listOf("", "../../").map { java.io.File("$it.data/raw-cache/map/area/$name") }.first { it.isFile }.readText()

    private fun npc(name: String) = checkNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC))) { name }

    private fun loc(name: String) = checkNotNull(ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC))) { name }

    private fun item(name: String) = checkNotNull(ServerCacheManager.getItem(name.asRSCM(RSCMType.OBJ))) { name }

    private fun varp(name: String) = checkNotNull(ServerCacheManager.getVarp(name.asRSCM(RSCMType.VARP))) { name }

    private fun placed(name: String, at: CoordGrid): Boolean = placedLocs.any { it.name == name && it.coords == at }

    private fun placedAny(names: List<String>, at: CoordGrid): Boolean = placedLocs.any { it.name in names && it.coords == at }

    private fun open(tile: CoordGrid): Boolean =
        collision[tile.x, tile.z, tile.level] and (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC) == 0

    private data class Placed(val name: String, val coords: CoordGrid, val angle: Int)

    private companion object {
        val ISAFDAR = intArrayOf(2140, 3040, 2340, 3320)

        val SQUARES =
            (33..37).flatMap { x -> (47..52).map { z -> x to z } } +
                listOf(37 to 52, 36 to 150, 31 to 73, 33 to 72, 40 to 51, 45 to 50)

        val collision = CollisionFlagMap()
        val placedLocs = mutableListOf<Placed>()
        lateinit var cache: dev.openrune.filesystem.Cache

        @JvmStatic @BeforeAll fun load() {
            cache = ServerCacheManager.init(240)
            val names = HashMap<Int, String>()
            for ((sx, sz) in SQUARES.distinct()) {
                val group = (sx shl 8) or sz
                val tileData = cache.data(MAPS, group, 0) ?: continue
                val locData = cache.data(MAPS, group, 1) ?: continue
                val square = MapSquareKey(sx, sz)
                for (level in 0..3) for (x in sx * 64 until sx * 64 + 64 step 8) {
                    for (z in sz * 64 until sz * 64 + 64 step 8) collision.allocateIfAbsent(x, z, level)
                }
                val tiles = MapTileDecoder.decode(InlineByteBuf(tileData))
                val builder = GameMapBuilder()
                GameMapDecoder.putMaps(collision, square, tiles)
                GameMapDecoder.putLocs(builder, collision, square, tiles, MapLocListDecoder.decode(InlineByteBuf(locData)))
                for ((packed, zone) in builder.zoneBuilders) {
                    val base = ZoneKey(packed).toCoords()
                    for (entry in zone.build().byte2IntEntrySet()) {
                        val key = LocZoneKey(entry.byteKey)
                        val loc = LocEntity(entry.intValue)
                        val name = names.getOrPut(loc.id) { nameOf(loc.id) }
                        placedLocs += Placed(name, base.translate(key.x, key.z), loc.angle)
                    }
                }
            }
        }

        private val wanted =
            (IsafdarObstacles.DENSE_FORESTS + IsafdarObstacles.LEAVES + IsafdarObstacles.LOG_STARTS +
                listOf(IsafdarObstacles.TRIPWIRE, IsafdarObstacles.TRIPWIRE_ROCK, IsafdarObstacles.STICKS, WellOfVoyage.TEMPLE_WELL,
                    WellOfVoyage.TIRANNWN_WELL, WellOfVoyage.CAVE_EXIT, WellOfVoyage.CAVE_ENTRANCE, FractionalStill.STILL, Catapult.CATAPULT,
                    BarrelBomb.SMALL_FURNACE, BarrelBomb.COAL_TAR, "loc.regicide_old_camp_footprints", "loc.overpass_gate_left",
                    "loc.overpass_gate_right", "loc.upass_templedoor_closed_left", "loc.upass_templedoor_closed_right", "loc.cave_temple_altar"))
                .toSet()

        private val wantedIds by lazy { wanted.associateBy { it.asRSCM(RSCMType.LOC) } }

        private fun nameOf(id: Int): String = wantedIds[id] ?: "loc#$id"

        @JvmStatic @AfterAll fun close() {
            cache.close()
        }
    }
}
