package org.rsmod.content.quest.area.hemenster.fishingcontest

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
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.STAGE_COMPLETE
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Pins the cache and map facts Fishing Contest is written against: the quest row, the var layout,
 * the ops the scripts answer, where every loc and spawn stands, that each tile a player or npc is
 * put on is open ground, and that the gate, the railing and the tunnel connect what they should.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class FishingContestCacheTest {

    @Test fun `the quest row matches the stages and requirements`() {
        val row = QuestRow.getRow("dbrow.${FishingContestQuest.QUEST_KEY}".asRSCM())
        assertEquals(STAGE_COMPLETE, row.endstate)
        assertEquals(1, row.questpoints)
        assertEquals(mapOf("fishing" to FishingContestQuest.FISHING_LEVEL), row.requirementStats.associate { it.t0.displayName to it.t1 })
        assertTrue(row.requirementQuests.isEmpty())
    }

    @Test fun `the contest bits share the garlic pipe varp and the round clock is temporary`() {
        val pipe = "varp.garlicpipe".asRSCM(RSCMType.VARP)
        assertEquals(VarpLifetime.Perm, ServerCacheManager.getVarp(pipe)!!.scope)
        assertEquals(VarpLifetime.Perm, ServerCacheManager.getVarp("varp.fishingcompo".asRSCM(RSCMType.VARP))!!.scope)
        val bits = listOf("garlicpipe", "paid", "passed", "stranger", "round_over").map { "varbit.fishingcompo_$it" }
        val starts = bits.map { name ->
            val type = ServerCacheManager.getVarbit(name.asRSCM(RSCMType.VARBIT))!!
            assertEquals(pipe, type.baseVar.id, name)
            type.startBit
        }
        assertEquals(starts.size, starts.distinct().size)
        val round = ServerCacheManager.getVarbit("varbit.fishingcontest_round_left".asRSCM(RSCMType.VARBIT))!!
        assertEquals(VarpLifetime.Temp, ServerCacheManager.getVarp(round.baseVar.id)!!.scope)
        assertTrue((1 shl (round.endBit - round.startBit + 1)) > FishingContestQuest.ROUND_STEPS)
    }

    @Test fun `npcs, locs and items carry the ops the scripts answer`() {
        assertEquals("Talk-to", npc(FishingContestQuest.AUSTRI).actions.getOpOrNull(0))
        assertEquals("Talk-to", npc(FishingContestQuest.VESTRI).actions.getOpOrNull(0))
        assertEquals("Pay", npc(FishingContestQuest.BONZO).actions.getOpOrNull(2))
        assertEquals("Bait", npc(FishingCompetition.PIPE_SPOT).actions.getOpOrNull(0))
        assertEquals("Bait", npc(FishingCompetition.OPEN_SPOT).actions.getOpOrNull(0))
        assertEquals("Talk-to", npc(FishingContestQuest.STRANGER_AT_SPOT).actions.getOpOrNull(0))
        assertEquals("Talk-to", npc(FishingContestQuest.STRANGER_MOVED).actions.getOpOrNull(0))
        assertEquals("Search", loc(FishingCompetition.PIPE).actions.getOpOrNull(0))
        assertEquals("Squeeze-through", loc(McGruborsWood.LOOSE_RAILING).actions.getOpOrNull(0))
        assertTrue(loc(McGruborsWood.LOOSE_RAILING).desc!!.contains("loose"))
        for (vine in McGruborsWood.VINES) assertEquals("Check", loc(vine).actions.getOpOrNull(0), vine)
        assertEquals("Climb-down", loc(WhiteWolfTunnel.WEST_DOWN).actions.getOpOrNull(0))
        assertEquals("Climb-down", loc(WhiteWolfTunnel.EAST_DOWN).actions.getOpOrNull(0))
        assertEquals("Climb-up", loc(WhiteWolfTunnel.WEST_UP).actions.getOpOrNull(0))
        assertEquals("Climb-up", loc(WhiteWolfTunnel.EAST_UP).actions.getOpOrNull(0))
        assertTrue(item(FishingContestQuest.WORMS).stackable)
        assertFalse(item(FishingContestQuest.TROPHY).stackable)
        assertNotNull(item(FishingContestQuest.PASS))
        assertNotNull(item(FishingContestQuest.CARP))
        for (name in listOf(FishingCompetition.PIPE_SPOT, FishingCompetition.OPEN_SPOT, FishingCompetition.BIG_DAVE_SPOT,
            FishingCompetition.JOSHUA_SPOT, FishingContestQuest.BIG_DAVE, FishingContestQuest.JOSHUA, FishingContestQuest.STRANGER,
        )) assertEquals(0, npc(name).wanderRange, "$name wanders")
    }

    @Test fun `every quest loc stands where the scripts expect`() {
        for (x in 2636..2638) assertLoc(FishingCompetition.PIPE, CoordGrid(x, 3446, 0))
        assertLoc(HemensterGate.GATE_RIGHT, CoordGrid(HemensterGate.INSIDE_X, 3441, 0))
        assertLoc(HemensterGate.GATE_LEFT, CoordGrid(HemensterGate.INSIDE_X, 3442, 0))
        assertLoc(McGruborsWood.LOOSE_RAILING, McGruborsWood.INSIDE)
        assertLoc(McGruborsWood.GATE_LEFT, CoordGrid(2650, 3470, 0))
        assertLoc("loc.red_worm_vine", RED_VINE)
        assertLoc(WhiteWolfTunnel.WEST_DOWN, CoordGrid(2820, 3484, 0))
        assertLoc(WhiteWolfTunnel.EAST_DOWN, CoordGrid(2876, 3480, 0))
        assertLoc(WhiteWolfTunnel.WEST_UP, CoordGrid(2820, 9883, 0))
        assertLoc(WhiteWolfTunnel.EAST_UP, CoordGrid(2876, 9880, 0))
    }

    @Test fun `each quest npc and the garlic are spawned where the scripts expect`() {
        val spawns = rawSpawns("npcs")
        val counts = spawns.groupingBy { it.first }.eachCount()
        for (name in listOf(
            FishingContestQuest.AUSTRI, FishingContestQuest.VESTRI, FishingContestQuest.GRANDPA_JACK,
            FishingContestQuest.MORRIS, FishingContestQuest.BONZO, FishingContestQuest.STRANGER,
            FishingContestQuest.BIG_DAVE, FishingContestQuest.JOSHUA, FishingContestQuest.FORESTER,
            FishingCompetition.PIPE_SPOT, FishingCompetition.OPEN_SPOT,
        )) assertEquals(1, counts[name] ?: 0, name)
        assertTrue(FishingContestQuest.STRANGER to FishingCompetition.STRANGER_POST in spawns)
        assertTrue(FishingCompetition.PIPE_SPOT to FishingCompetition.PIPE_SPOT_TILE in spawns)
        val dogs = spawns.filter { it.first == "npc.guarddog" && walks(McGruborsWood.INSIDE, it.second) }
        assertTrue(dogs.size >= 2, "the wood has no guard dogs")
        assertTrue("obj.garlic" to GARLIC in rawSpawns("objs"))
    }

    @Test fun `every tile a player or npc is put on is open ground`() {
        for (tile in listOf(
            WhiteWolfTunnel.WEST_TOP, WhiteWolfTunnel.EAST_TOP, WhiteWolfTunnel.WEST_BOTTOM,
            WhiteWolfTunnel.EAST_BOTTOM, McGruborsWood.INSIDE, McGruborsWood.OUTSIDE,
            FishingCompetition.STRANGER_POST, FishingCompetition.STRANGER_AWAY_TILE,
            CoordGrid(HemensterGate.INSIDE_X, 3441, 0), CoordGrid(HemensterGate.OUTSIDE_X, 3441, 0),
            TUNNEL_MIDDLE,
        )) assertTrue(open(tile), "$tile is blocked")
        assertFalse(open(FishingCompetition.PIPE_SPOT_TILE), "the carp spot should be water")
    }

    @Test fun `the gate is the only way into the competition grounds`() {
        val inside = CoordGrid(HemensterGate.INSIDE_X, 3441, 0)
        val outside = CoordGrid(HemensterGate.OUTSIDE_X, 3441, 0)
        assertFalse(walks(outside, PIPE_STAND), "the grounds can be walked into")
        assertTrue(walks(inside, PIPE_STAND))
        assertTrue(walks(inside, BONZO_STAND))
        assertTrue(walks(FishingCompetition.STRANGER_POST, FishingCompetition.STRANGER_AWAY_TILE))
        assertTrue(walks(outside, FORESTER_TILE))
        assertTrue(HemensterGate.isInside(inside) && !HemensterGate.isInside(outside))
    }

    @Test fun `the loose railing is the only way to the red vines`() {
        assertTrue(walks(McGruborsWood.INSIDE, VINE_STAND))
        assertFalse(walks(McGruborsWood.OUTSIDE, VINE_STAND), "the vines can be reached without the railing")
        assertTrue(walks(McGruborsWood.OUTSIDE, FORESTER_TILE), "the railing's outer side is cut off")
        assertFalse(walks(FORESTER_TILE, VINE_STAND), "the locked gate is open")
        assertTrue(McGruborsWood.isInside(McGruborsWood.INSIDE) && !McGruborsWood.isInside(McGruborsWood.OUTSIDE))
    }

    @Test fun `the tunnel runs from Vestri's stairs to Austri's`() {
        assertTrue(walks(WhiteWolfTunnel.WEST_BOTTOM, WhiteWolfTunnel.EAST_BOTTOM))
        assertTrue(walks(WhiteWolfTunnel.WEST_BOTTOM, TUNNEL_MIDDLE))
        assertFalse(walks(WhiteWolfTunnel.WEST_TOP, WhiteWolfTunnel.EAST_TOP), "the mountain can be crossed on foot nearby")
        assertTrue(walks(WhiteWolfTunnel.WEST_TOP, VESTRI_TILE), "the west arrival is cut off from Vestri")
        assertTrue(walks(WhiteWolfTunnel.EAST_TOP, AUSTRI_TILE), "the east arrival is cut off from Austri")
    }

    @Test fun `every symbol the scripts use resolves`() {
        for (seq in listOf(
            FishingCompetition.CAST_SEQ, FishingCompetition.PLACE_SEQ, FishingCompetition.GAG_SEQ,
            McGruborsWood.SQUEEZE_SEQ, McGruborsWood.DIG_SEQ, WhiteWolfTunnel.CLIMB_SEQ,
        )) assertNotNull(ServerCacheManager.getAnim(seq.asRSCM(RSCMType.SEQ)), seq)
        assertTrue(FishingCompetition.SMELL_SPOTANIM.asRSCM(RSCMType.SPOTANIM) >= 0)
        for (synth in listOf(HemensterGate.OPEN_SOUND, McGruborsWood.SQUEEZE_SOUND, McGruborsWood.DIG_SOUND)) {
            assertTrue(synth.asRSCM(RSCMType.SYNTH) >= 0, synth)
        }
        for (timer in listOf(FishingCompetition.ROUND_TIMER, FishingCompetition.STRANGER_RETURN_TIMER)) {
            assertTrue(timer.asRSCM(RSCMType.TIMER) >= 0, timer)
        }
        assertTrue(FishingCompetition.CATCH_QUEUE.asRSCM(RSCMType.QUEUE) >= 0)
        for (obj in listOf(FishingContestQuest.ROD, FishingContestQuest.FLY_ROD, FishingContestQuest.OILY_ROD,
            FishingContestQuest.SPADE, FishingContestQuest.GARLIC, FishingCompetition.SARDINE, "obj.trout")) {
            assertNotNull(item(obj), obj)
        }
    }

    private fun open(tile: CoordGrid): Boolean =
        collision[tile.x, tile.z, tile.level] and (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC) == 0

    private fun walks(from: CoordGrid, to: CoordGrid): Boolean {
        val steps = StepValidator(collision)
        val seen = hashSetOf(from)
        val queue = ArrayDeque(listOf(from))
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            if (c == to) return true
            for (dx in -1..1) for (dz in -1..1) {
                if ((dx == 0 && dz == 0) || !steps.canTravel(c.level, c.x, c.z, dx, dz)) continue
                val n = c.translate(dx, dz)
                if (MapSquareKey(n.x / 64, n.z / 64) in LOADED && seen.add(n)) queue += n
            }
        }
        return false
    }

    private fun npc(name: String) = checkNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC))) { name }

    private fun loc(name: String) = checkNotNull(ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC))) { name }

    private fun item(name: String) = checkNotNull(ServerCacheManager.getItem(name.asRSCM(RSCMType.OBJ))) { name }

    private fun assertLoc(name: String, at: CoordGrid) {
        val id = name.asRSCM(RSCMType.LOC)
        assertTrue(placed.any { it.first == id && it.second == at }, "$name is not at $at")
    }

    private fun rawSpawns(kind: String): List<Pair<String, CoordGrid>> {
        val dir = listOf("", "../../").map { java.io.File("$it.data/raw-cache/map/$kind") }.first { it.isDirectory }
        val key = if (kind == "npcs") "npc" else "obj"
        val pattern = Regex("$key = \"($key[.][a-z0-9_]+)\"\\s*\\r?\\ncoords = \"(\\d+)_(\\d+)_(\\d+)_(\\d+)_(\\d+)\"")
        return dir.listFiles { f -> f.name.endsWith(".toml") }!!.flatMap { file ->
            pattern.findAll(file.readText()).map {
                val (name, level, mx, mz, lx, lz) = it.destructured
                name to CoordGrid(mx.toInt() * 64 + lx.toInt(), mz.toInt() * 64 + lz.toInt(), level.toInt())
            }.toList()
        }
    }

    private companion object {
        val RED_VINE = CoordGrid(2631, 3498, 0)
        val VINE_STAND = CoordGrid(2632, 3497, 0)
        val GARLIC = CoordGrid(2714, 3478, 0)
        val PIPE_STAND = CoordGrid(2638, 3444, 0)
        val BONZO_STAND = CoordGrid(2642, 3438, 0)
        val FORESTER_TILE = CoordGrid(2650, 3468, 0)
        val TUNNEL_MIDDLE = CoordGrid(2848, 9880, 0)
        val VESTRI_TILE = CoordGrid(2820, 3487, 0)
        val AUSTRI_TILE = CoordGrid(2877, 3483, 0)

        val LOADED = listOf(41 to 53, 41 to 54, 42 to 53, 42 to 54, 43 to 54, 44 to 54, 43 to 154, 44 to 154)
            .map { (x, z) -> MapSquareKey(x, z) }.toSet()

        val collision = CollisionFlagMap()
        val placed = mutableListOf<Pair<Int, CoordGrid>>()
        lateinit var cache: dev.openrune.filesystem.Cache

        @JvmStatic @BeforeAll fun load() {
            cache = ServerCacheManager.init(240)
            for (square in LOADED) {
                val group = (square.x shl 8) or square.z
                val tiles = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 0))))
                val spawns = MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 1))))
                for (level in 0..3) for (x in square.x * 64 until square.x * 64 + 64 step 8) {
                    for (z in square.z * 64 until square.z * 64 + 64 step 8) collision.allocateIfAbsent(x, z, level)
                }
                val builder = GameMapBuilder()
                GameMapDecoder.putMaps(collision, square, tiles)
                GameMapDecoder.putLocs(builder, collision, square, tiles, spawns)
                for ((packed, zone) in builder.zoneBuilders) {
                    val base = ZoneKey(packed).toCoords()
                    for (entry in zone.build().byte2IntEntrySet()) {
                        val key = LocZoneKey(entry.byteKey)
                        placed += LocEntity(entry.intValue).id to base.translate(key.x, key.z)
                    }
                }
            }
        }

        @JvmStatic @AfterAll fun close() {
            cache.close()
        }
    }
}
