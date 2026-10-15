package org.rsmod.content.quest.area.mortton.myreque

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
import dev.openrune.types.varp.bits
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
import org.rsmod.api.route.StepFactory
import org.rsmod.api.table.QuestRow
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_BETRAYED
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Member
import org.rsmod.content.quest.area.mortton.myreque.npcs.CurpileFyod
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.LineValidator
import org.rsmod.routefinder.RouteFinding
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Pins the cache and map facts In Search of the Myreque is written against: the quest row and
 * its requirements, which var drives which multinpc and multiloc, where every loc the scripts
 * touch stands, that each tile a player is moved to is walkable and leads where the quest says,
 * and that the hellhound really does get stuck short of the safespot.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class InSearchOfTheMyrequeCacheTest {

    @Test fun `the quest row matches the stages and requirements`() {
        val row = QuestRow.getRow("dbrow.${InSearchOfTheMyrequeQuest.QUEST_KEY}".asRSCM())
        assertEquals(STAGE_COMPLETE, row.endstate)
        assertEquals(2, row.questpoints)
        assertEquals(mapOf("agility" to 25), row.requirementStats.associate { it.t0.displayName to it.t1 })
        assertEquals(listOf("Nature Spirit"), row.requirementQuests.map { it.displayname })
        assertEquals(listOf("npc.multi_vanstrom_stranger_entity".asRSCM(RSCMType.NPC)), row.startnpc.map { it.id })
    }

    @Test fun `sani and harold leave the hideout at the betrayal stage`() {
        for (name in listOf("npc.route_sani_piliu", "npc.route_harold_evans")) {
            val type = npc(name)
            assertEquals("varp.routequest".asRSCM(RSCMType.VARP), type.multiVarp)
            val transforms = type.transforms!!
            assertTrue(transforms.subList(0, STAGE_BETRAYED).all { it != -1 }, name)
            assertEquals(-1, transforms[STAGE_BETRAYED], name)
        }
    }

    @Test fun `the tavern seat and the tree bases follow their varbits`() {
        val seat = npc("npc.multi_vanstrom_stranger_entity")
        assertEquals("varbit.thsfm_vanstrom_hide".asRSCM(RSCMType.VARBIT), seat.multiVarBit)
        assertEquals(InSearchOfTheMyrequeQuest.VANSTROM_SITTING.asRSCM(RSCMType.NPC), seat.transforms!![0])
        assertEquals(InSearchOfTheMyrequeQuest.STRANGER_NPC.asRSCM(RSCMType.NPC), seat.transforms!![1])
        val tree = loc("loc.spooky_tree_base_forbridge")
        assertEquals("varbit.route_bridgecomplete".asRSCM(RSCMType.VARBIT), tree.multiVarBit)
        assertEquals("loc.route_treebase_1op".asRSCM(RSCMType.LOC), tree.transforms!![6])
        assertEquals("loc.route_treebase_2ops".asRSCM(RSCMType.LOC), tree.transforms!![7])
        assertEquals("Climb", loc("loc.route_treebase_1op").actions.getOpOrNull(1))
        assertEquals("Cross-bridge", loc("loc.route_treebase_2ops").actions.getOpOrNull(0))
        assertEquals("Squeeze-past", loc(MyrequeTunnels.STALAGMITE).actions.getOpOrNull(1))
    }

    @Test fun `the member flags use free bits of routequestmulti and both quest varps persist`() {
        val multi = "varp.routequestmulti".asRSCM(RSCMType.VARP)
        val ours = Member.entries.map { it.varbit.asRSCM(RSCMType.VARBIT) }.toSet()
        val taken = HashSet<Int>()
        for ((id, varbit) in ServerCacheManager.getVarbits()) {
            if (varbit.baseVar.id == multi && id !in ours) taken += varbit.bits
        }
        for (flag in Member.entries) {
            val varbit = checkNotNull(ServerCacheManager.getVarbit(flag.varbit.asRSCM(RSCMType.VARBIT)))
            assertEquals(multi, varbit.baseVar.id)
            assertFalse(varbit.bits.any { it in taken }, flag.varbit)
        }
        for (varp in listOf("varp.routequest", "varp.routequestmulti")) {
            assertEquals(VarpLifetime.Perm, ServerCacheManager.getVarp(varp.asRSCM(RSCMType.VARP))!!.scope, varp)
        }
    }

    @Test fun `the hellhound keeps its osrs statistics`() {
        val hound = npc(InSearchOfTheMyrequeQuest.HELLHOUND)
        assertEquals(97, hound.combatLevel)
        assertEquals(55, hound.hitpoints)
        assertEquals(2, hound.size)
    }

    @Test fun `every quest loc stands where the scripts expect`() {
        assertLoc("loc.spooky_tree_base_forbridge", MyrequeCoords.SOUTH_TREE)
        assertLoc("loc.spooky_tree_base_forbridge", MyrequeCoords.NORTH_TREE)
        assertLoc("loc.route_rowboat_hollows", CoordGrid(3498, 3377, 0))
        assertLoc("loc.route_rowboat_mortton", CoordGrid(3523, 3284, 0))
        assertLoc(CurpileFyod.SURFACE_DOOR_LEFT, CoordGrid(3510, 3447, 0))
        assertLoc(CurpileFyod.TUNNEL_DOOR_LEFT, CoordGrid(3500, 9812, 0))
        assertLoc(MyrequeTunnels.CAVE_ENTRANCE, MyrequeCoords.POCKET_CAVE)
        assertLoc(MyrequeTunnels.CAVE_ENTRANCE, MyrequeCoords.HIDEOUT_CAVE)
        assertLoc(MyrequeTunnels.FALSE_WALL, MyrequeCoords.FALSE_WALL)
        assertLoc(MyrequeTunnels.BASEMENT_LADDER, MyrequeCoords.BASEMENT_LADDER)
        assertLoc(MyrequeTunnels.CANIFIS_TRAPDOOR, MyrequeCoords.CANIFIS_TRAPDOOR)
    }

    @Test fun `every tile a player is moved to is open ground`() {
        for (tile in listOf(
            MyrequeCoords.MORTTON_LANDING, MyrequeCoords.HOLLOWS_LANDING, MyrequeCoords.BRIDGE_SOUTH_END,
            MyrequeCoords.BRIDGE_NORTH_END, MyrequeCoords.SURFACE_DOORS_OUTSIDE, MyrequeCoords.TUNNEL_DOORS_INSIDE,
            MyrequeCoords.POCKET_OUTSIDE, MyrequeCoords.POCKET_INSIDE, MyrequeCoords.HIDEOUT_ARRIVAL,
            MyrequeCoords.FALSE_WALL_NORTH, MyrequeCoords.FALSE_WALL_SOUTH, MyrequeCoords.BASEMENT_FOOT,
            MyrequeCoords.CANIFIS_EXIT, SAFESPOT,
        )) {
            assertEquals(0, collision[tile.x, tile.z, 0] and (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC), "$tile")
        }
    }

    @Test fun `the bridge is the only way across and the tunnels join up as described`() {
        assertTrue(walks(MyrequeCoords.HOLLOWS_LANDING, MyrequeCoords.BRIDGE_SOUTH_END))
        assertFalse(walks(MyrequeCoords.BRIDGE_SOUTH_END, MyrequeCoords.BRIDGE_NORTH_END, limitTo = HOLLOWS))
        assertTrue(walks(MyrequeCoords.BRIDGE_NORTH_END, MyrequeCoords.SURFACE_DOORS_OUTSIDE))
        assertTrue(walks(MyrequeCoords.TUNNEL_DOORS_INSIDE, MyrequeCoords.POCKET_OUTSIDE))
        assertTrue(walks(MyrequeCoords.TUNNEL_DOORS_INSIDE, MyrequeCoords.FALSE_WALL_SOUTH))
        assertTrue(walks(MyrequeCoords.POCKET_INSIDE, MyrequeCoords.POCKET_CAVE.translateZ(1)))
        collision.add(MyrequeCoords.STALAGMITE.x, MyrequeCoords.STALAGMITE.z, 0, CollisionFlag.LOC)
        try {
            assertFalse(walks(MyrequeCoords.POCKET_OUTSIDE, MyrequeCoords.POCKET_INSIDE), "the stalagmite seals the pocket")
        } finally {
            collision.remove(MyrequeCoords.STALAGMITE.x, MyrequeCoords.STALAGMITE.z, 0, CollisionFlag.LOC)
        }
        assertFalse(walks(MyrequeCoords.TUNNEL_DOORS_INSIDE, MyrequeCoords.HIDEOUT_ARRIVAL), "the hideout is only reached by its cave")
        assertTrue(walks(MyrequeCoords.HIDEOUT_ARRIVAL, MyrequeCoords.VELIAF.translateX(-1)))
        assertTrue(walks(MyrequeCoords.FALSE_WALL_NORTH, MyrequeCoords.BASEMENT_FOOT))
    }

    @Test fun `the hellhound chasing from its spawn is stuck short of the safespot but in sight of it`() {
        val factory = StepFactory(collision)
        val steps = StepValidator(collision)
        var hound = MyrequeCoords.HOUND_SPAWN
        repeat(60) {
            val d = RouteFinding.naiveDestination(hound.x, hound.z, 2, 2, SAFESPOT.x, SAFESPOT.z, 1, 1)
            val dest = CoordGrid(d.x, d.z, 0)
            if (dest == hound) return@repeat
            val next = factory.validated(hound, dest, size = 2)
            if (next == CoordGrid.NULL) return@repeat
            hound = next
        }
        val foot = listOf(hound, hound.translate(1, 0), hound.translate(0, 1), hound.translate(1, 1))
        val inMelee = foot.any { f ->
            val dx = f.x - SAFESPOT.x
            val dz = f.z - SAFESPOT.z
            kotlin.math.abs(dx) + kotlin.math.abs(dz) == 1 && steps.canTravel(0, SAFESPOT.x, SAFESPOT.z, dx, dz)
        }
        assertFalse(inMelee, "hound reached melee range at $hound")
        assertTrue(LineValidator(collision).hasLineOfSight(0, SAFESPOT.x, SAFESPOT.z, hound.x, hound.z, destWidth = 2, destLength = 2))
        assertTrue(walks(MyrequeCoords.HIDEOUT_ARRIVAL, SAFESPOT))
    }

    @Test fun `each quest npc is spawned exactly once`() {
        val dir = listOf("", "../../").map { java.io.File("${it}.data/raw-cache/map/npcs") }.first { it.isDirectory }
        val spawns = dir.listFiles { f -> f.name.endsWith(".toml") }!!
            .flatMap { file -> SPAWN.findAll(file.readText()).map { it.groupValues[1] }.toList() }
        val counts = spawns.groupingBy { it }.eachCount()
        val expected = mapOf(
            "npc.multi_vanstrom_stranger_entity" to 1, "npc.route_cyreg_paddlehorn" to 1,
            "npc.route_curpile_fyod" to 1, "npc.route_veliaf_hurtz_parent" to 1,
            "npc.route_sani_piliu" to 1, "npc.route_harold_evans" to 1, "npc.route_ivan_strom_parent" to 1,
            "npc.route_polmafi_ferdygris_parent" to 1, "npc.route_radigad_ponfit_parent" to 1,
        )
        for ((npc, n) in expected) assertEquals(n, counts[npc] ?: 0, npc)
        for (plain in listOf("npc.canafis_stranger", "npc.route_vanstrom_klause_sitting", "npc.route_polmafi_ferdygris",
            "npc.route_radigad_ponfit", "npc.route_ivan_strom", "npc.route_veliaf_hurtz")) {
            assertEquals(0, counts[plain] ?: 0, "$plain would stand beside its multinpc")
        }
    }

    @Test fun `every guard question is answered somewhere in the dialogue`() {
        val root = listOf("content/quest/", "")
            .map { java.io.File("${it}src/main/kotlin/org/rsmod/content/quest/area/mortton/myreque") }
            .first { it.isDirectory }
        val sources = listOf("npcs/CyregPaddlehorn.kt", "npcs/CanifisTavern.kt").joinToString("\n") {
            root.resolve(it).readText()
        }
        for (question in CurpileFyod.QUESTIONS) {
            assertTrue(question.answer in question.options, question.text)
            val clue = if (question.answer == "Drakan") "Drakans" else question.answer
            assertTrue(sources.contains(clue), "nobody tells the player '${question.answer}'")
        }
    }

    @Test fun `every symbol the scripts use resolves`() {
        for (seq in listOf(HollowsBridge::class, Betrayal::class)) assertNotNull(seq)
        for (name in listOf(
            "seq.human_climbing", "seq.human_climbing_down", "seq.human_hammer_hit", "seq.human_squeeze",
            "seq.human_pickupfloor", "seq.human_reachforladder", "seq.human_reachforladdertop",
            "seq.human_unarmedpunch", "seq.human_death_backwards", "seq.human_death", Betrayal.MIST_CLEAR_SEQ,
            Betrayal.REVEAL_SEQ, Betrayal.CAST_SEQ, Betrayal.SUMMON_SEQ, Betrayal.DEPART_SEQ,
        )) assertNotNull(ServerCacheManager.getAnim(name.asRSCM(RSCMType.SEQ)), name)
        for (name in listOf("spotanim.misty", "spotanim.myq3_head_vampyre_transform", "spotanim.spell_blood_burst_impact")) {
            assertTrue(name.asRSCM(RSCMType.SPOTANIM) >= 0, name)
        }
        for (name in listOf(
            "synth.climb_wall", "synth.hammer_and_build", "synth.squeeze_thru_crack", "synth.climb_under",
            "synth.trapdoor_open", "synth.big_wooden_door_open", "synth.vampire_arrives", "synth.blood_cast",
            "synth.blood_burst_impact", "synth.ghast_attack",
        )) assertTrue(name.asRSCM(RSCMType.SYNTH) >= 0, name)
        for (name in listOf(Betrayal.SANI_ACTOR, Betrayal.HAROLD_ACTOR, Betrayal.MIST_ACTOR, Betrayal.VANSTROM_ACTOR, InSearchOfTheMyrequeQuest.CYREG, InSearchOfTheMyrequeQuest.CURPILE, InSearchOfTheMyrequeQuest.VELIAF)) {
            assertNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC)), name)
        }
        for (member in Member.entries) assertEquals("Talk-to", npc(member.npc).actions.getOpOrNull(0), member.npc)
    }

    private fun walks(from: CoordGrid, to: CoordGrid, limitTo: IntArray? = null): Boolean {
        val steps = StepValidator(collision)
        val seen = hashSetOf(from)
        val queue = ArrayDeque(listOf(from))
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            if (c == to) return true
            for (dx in -1..1) for (dz in -1..1) {
                if ((dx == 0 && dz == 0) || !steps.canTravel(0, c.x, c.z, dx, dz)) continue
                val n = c.translate(dx, dz)
                if (limitTo != null && (n.x !in limitTo[0]..limitTo[2] || n.z !in limitTo[1]..limitTo[3])) continue
                if (n.x / 64 in LOADED_X && seen.add(n)) queue += n
            }
        }
        return false
    }

    private fun npc(name: String) = checkNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC))) { name }

    private fun loc(name: String) = checkNotNull(ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC))) { name }

    private fun assertLoc(name: String, at: CoordGrid) {
        val id = name.asRSCM(RSCMType.LOC)
        assertTrue(placed.any { it.first == id && it.second == at }, "$name is not at $at")
    }

    private companion object {
        val SAFESPOT = CoordGrid(3513, 9844, 0)

        val SPAWN = Regex("npc = \"(npc[.][a-z0-9_]+)\"")

        /** The Hollows between the boat and Curpile's door, for proving the gap can't be walked round. */
        val HOLLOWS = intArrayOf(3480, 3370, 3519, 3455)
        val LOADED_X = 53..55

        val collision = CollisionFlagMap()
        val placed = mutableListOf<Pair<Int, CoordGrid>>()
        lateinit var cache: dev.openrune.filesystem.Cache

        @JvmStatic @BeforeAll fun load() {
            cache = ServerCacheManager.init(240)
            for ((mx, mz) in listOf(54 to 52, 54 to 53, 54 to 54, 55 to 51, 54 to 153, 54 to 154, 53 to 153)) {
                val square = MapSquareKey(mx, mz)
                val group = (mx shl 8) or mz
                val tiles = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 0))))
                val spawns = MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 1))))
                for (level in 0..3) for (x in mx * 64 until mx * 64 + 64 step 8) for (z in mz * 64 until mz * 64 + 64 step 8) {
                    collision.allocateIfAbsent(x, z, level)
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
