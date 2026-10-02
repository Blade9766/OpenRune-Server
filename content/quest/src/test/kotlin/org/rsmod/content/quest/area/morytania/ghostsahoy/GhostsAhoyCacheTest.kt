package org.rsmod.content.quest.area.morytania.ghostsahoy

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
import org.rsmod.api.table.cooking.CookingFoodsRow
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.AGILITY_REQ
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.COOKING_REQ
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.STAGE_GATHER
import org.rsmod.content.quest.area.morytania.ghostsahoy.npcs.Robin
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Pins the cache and map facts Ghosts Ahoy is written against: the quest row's stages, rewards
 * and requirements, the vars and their widths, where every loc and npc the scripts name stands,
 * which side of each toll barrier is the town, the gangplank and ectophial landings, the rock
 * route to the third chest, the lobster's room in the hold, and the dig tile the treasure map's
 * directions lead to from the statue on Dragontooth Island.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class GhostsAhoyCacheTest {

    @Test fun `the quest row matches the stages, rewards and requirements`() {
        val row = QuestRow.getRow("dbrow.${GhostsAhoyQuest.QUEST_KEY}".asRSCM())
        assertEquals(STAGE_COMPLETE, row.endstate)
        assertEquals(2, row.questpoints)
        assertEquals(mapOf("prayer" to 24000), row.statXpAwarded.associate { it.t0.displayName to it.t1 }, "tenths of 2,400 xp")
        assertEquals(mapOf("agility" to AGILITY_REQ, "cooking" to COOKING_REQ), row.requirementStats.associate { it.t0.displayName to it.t1 })
        val prerequisites = row.requirementQuests.map { it.rowId }.toSet()
        assertEquals(setOf("dbrow.quest_priestinperil".asRSCM(), "dbrow.quest_restlessghost".asRSCM()), prerequisites)
        assertEquals("Velorina", row.startnpc.single().name)
    }

    @Test fun `every quest var is permanent and wide enough`() {
        val widths = mapOf(
            "varbit.ahoy_questvar" to STAGE_COMPLETE,
            "varbit.ahoy_signaturecounter" to GhostsAhoyQuest.SIGNATURES_NEEDED,
            "varbit.ahoy_subquest_bow" to GhostsAhoyQuest.BOW_TRADED,
            "varbit.ahoy_subquest_toyboat" to GhostsAhoyQuest.BOAT_RETURNED,
            "varbit.ahoy_subquest_nettletea" to GhostsAhoyQuest.TEA_DRUNK,
            "varbit.ahoy_windspeed" to 1,
            "varbit.ahoy_killed_lobster" to 1,
            "varbit.ahoy_requested_sheet" to 1,
            "varbit.ahoy_templedoor_unlocked" to 1,
            "varbit.ahoy_given_book" to 1,
            "varbit.ahoy_given_manual" to 1,
            "varbit.ahoy_given_robes" to 1,
            "varbit.ahoy_flag_seen" to 7,
            "varbit.ahoy_petition_approved" to 1,
            "varbit.ahoy_petition_presented" to 1,
        ) + FlagPart.entries.flatMap { listOf(it.targetVarbit to 6, it.appliedVarbit to 6) }
        for ((name, max) in widths) {
            val bit = checkNotNull(ServerCacheManager.getVarbit(name.asRSCM(RSCMType.VARBIT))) { name }
            assertTrue((1 shl (bit.endBit - bit.startBit + 1)) > max, name)
            assertEquals(VarpLifetime.Perm, ServerCacheManager.getVarp(bit.baseVar.id)!!.scope, name)
        }
        assertEquals(VarpLifetime.Perm, ServerCacheManager.getVarp("varp.ahoy_last_signer".asRSCM(RSCMType.VARP))!!.scope)
        val questVar = ServerCacheManager.getVarbit("varbit.ahoy_questvar".asRSCM(RSCMType.VARBIT))!!
        assertEquals("varp.ahoy_varbits_1".asRSCM(RSCMType.VARP), questVar.baseVar.id)
        assertTrue("timer.ahoy_wind".asRSCM(RSCMType.TIMER) >= 0)
    }

    @Test fun `the barriers and the akharanu multinpc follow the quest stage`() {
        val barrier = loc("loc.ahoy_town_barrier_multi")
        assertEquals("varbit.ahoy_questvar".asRSCM(RSCMType.VARBIT), barrier.multiVarBit)
        val transforms = checkNotNull(barrier.transforms)
        assertEquals(PhasmatysBarrier.BARRIER.asRSCM(RSCMType.LOC), transforms[STAGE_COMPLETE - 1])
        assertEquals(PhasmatysBarrier.BARRIER_FREE.asRSCM(RSCMType.LOC), transforms[STAGE_COMPLETE])
        assertEquals("Pass", loc(PhasmatysBarrier.BARRIER).actions.getOpOrNull(0))
        assertEquals("Pay-toll(2-Ecto)", loc(PhasmatysBarrier.BARRIER).actions.getOpOrNull(3))
        assertEquals("Pass", loc(PhasmatysBarrier.BARRIER_FREE).actions.getOpOrNull(3))
        for (gate in PhasmatysBarrier.Gate.entries) {
            assertTrue(placed("loc.ahoy_town_barrier_multi", gate.origin), "no barrier at ${gate.origin}")
        }
        val akharanu = npc("npc.ahoy_akharanu_multi")
        assertEquals("npc.ahoy_akharanu".asRSCM(RSCMType.NPC), checkNotNull(akharanu.transforms)[STAGE_GATHER])
    }

    @Test fun `each barrier separates the town from the outside`() {
        val town = reachable(PhasmatysBarrier.Gate.WEST.across(PhasmatysBarrier.Gate.WEST.outsideTile()), TOWN_BOUNDS)
        for (gate in PhasmatysBarrier.Gate.entries) {
            val inside = gate.across(gate.outsideTile())
            val outside = gate.across(inside)
            assertTrue(gate.isInside(inside) && !gate.isInside(outside), gate.name)
            assertTrue(open(inside) && open(outside), "${gate.name} landing blocked")
            assertTrue(inside in town, "${gate.name}: $inside is not in town")
            assertFalse(outside in town, "${gate.name}: $outside is in town")
        }
        assertTrue(CoordGrid(3688, 3470, 0) in town, "the bank is in town")
        assertFalse(Ectophial.ARRIVAL in town, "the Ectofuntus stands outside the walls")
        assertTrue(open(Ectophial.ARRIVAL))
    }

    @Test fun `the wreck's locs stand where the scripts look for them`() {
        assertTrue(placed(Shipwreck.LOCKED_CHEST, Shipwreck.CAPTAINS_CHEST))
        assertTrue(placed(Shipwreck.CLOSED_CHEST, Shipwreck.ROCK_CHEST))
        assertTrue(placed(GiantLobster.CHEST, GiantLobster.CHEST_TILE))
        assertTrue(placed(Shipwreck.MAST, Shipwreck.MAST_TILE))
        assertTrue(Shipwreck.onQuarterdeck(Shipwreck.MAST_TILE))
        assertTrue(placed(Shipwreck.GANGPLANK_ON_SHORE, CoordGrid(3605, 3547, 0)))
        assertTrue(placed(Shipwreck.GANGPLANK_ON_DECK, CoordGrid(3605, 3546, 1)))
        assertTrue(open(Shipwreck.DECK_LANDING) && open(Shipwreck.SHORE_LANDING))
        assertTrue(placed(Shipwreck.LADDER_UP, CoordGrid(3615, 3545, 1)), "ladder to the quarterdeck")
        assertTrue(placed(TempleRobes.DOOR, TempleRobes.TEMPLE_DOOR))
        assertTrue(placed(TempleRobes.COFFIN, CoordGrid(3659, 3513, 1)))
        assertTrue(placed(Ectophial.ECTOFUNTUS, Ectophial.ECTOFUNTUS_TILE))
        val oldMan = rawSpawns().single { it.first == GhostsAhoyQuest.OLD_MAN }.second
        assertTrue(Shipwreck.onWreck(oldMan) && oldMan.level == 1)
    }

    @Test fun `the lobster fits beside the chest in the hold`() {
        val lobster = npc(GhostsAhoyQuest.GIANT_LOBSTER)
        assertEquals("Giant lobster", lobster.name)
        assertEquals(32, lobster.combatLevel)
        assertEquals(2, lobster.size)
        for (dx in 0 until lobster.size) for (dz in 0 until lobster.size) {
            assertTrue(open(GiantLobster.SPAWN_TILE.translate(dx, dz)), "lobster tile $dx,$dz blocked")
        }
        assertTrue(GiantLobster.SPAWN_TILE.chebyshevDistance(GiantLobster.CHEST_TILE) <= 3)
    }

    @Test fun `the rocks lead from the shore to the third chest in jumps of three or less`() {
        val rocks = ROCK_ROUTE
        for (rock in rocks) {
            assertTrue(placed(Shipwreck.ROCK, rock), "no rock at $rock")
            assertTrue(open(rock), "rock $rock can't be stood on")
        }
        assertTrue(reachable(Shipwreck.SHORE_LANDING, WRECK_BOUNDS).contains(rocks.first().translate(1, 0)) ||
            reachable(Shipwreck.SHORE_LANDING, WRECK_BOUNDS).any { it.chebyshevDistance(rocks.first()) <= 1 })
        for ((a, b) in rocks.zipWithNext()) {
            assertTrue(a.chebyshevDistance(b) in 1..3, "$a to $b")
        }
        val island = reachable(rocks.last(), WRECK_BOUNDS)
        assertTrue(island.any { it.chebyshevDistance(Shipwreck.ROCK_CHEST) == 1 }, "the chest can't be reached from the last rock")
        assertFalse(Shipwreck.ROCK_CHEST.translate(-1, 0) in reachable(Shipwreck.SHORE_LANDING, WRECK_BOUNDS), "the chest is reachable on foot")
    }

    @Test fun `the treasure map's directions end on open ground on Dragontooth Island`() {
        assertTrue(placed("loc.statue_saradomin", CoordGrid(3792, 3556, 0)))
        assertTrue(placed("loc.ahoy_x_start", TreasureMap.STATUE_START))
        var walk = TreasureMap.STATUE_START
        for ((dx, dz) in listOf(0 to -6, 8 to 0, 0 to 2, 4 to 0, 0 to -22)) walk = walk.translate(dx, dz)
        assertEquals(TreasureMap.DIG_TILE, walk)
        assertEquals(CoordGrid(3803, 3530, 0), TreasureMap.DIG_TILE)
        assertTrue(open(TreasureMap.DIG_TILE))
        val captain = rawSpawns().filter { it.first == GhostsAhoyQuest.CAPTAIN }.map { it.second }
        assertTrue(captain.any { it.x > 3750 } && captain.any { it.x < 3750 }, "a captain on each shore")
        assertTrue(TreasureMap.DIG_TILE in reachable(CoordGrid(3793, 3559, 0), ISLAND_BOUNDS))
    }

    @Test fun `the interfaces and items carry the components and ops the scripts use`() {
        for (component in listOf(Robin.DRAW_BUTTON, Robin.HOLD_BUTTON, Shipwreck.WIND_TEXT, "component.ahoy_runedraw:runedraw_slot_20")) {
            assertTrue(component.asRSCM(RSCMType.COMPONENT) > 0, component)
        }
        assertEquals("Empty", item(GhostsAhoyQuest.ECTOPHIAL).interfaceOptions[0])
        assertEquals("Repair", item(GhostsAhoyQuest.TOY_BOAT).interfaceOptions[0])
        assertEquals("Read", item(GhostsAhoyQuest.MAP).interfaceOptions[0])
        assertEquals("Count", item(GhostsAhoyQuest.PETITION).interfaceOptions[0])
        assertEquals("Wear", item(GhostsAhoyQuest.BEDSHEET_SLIMED).interfaceOptions[1])
        assertEquals("Travel", npc(GhostsAhoyQuest.CAPTAIN).actions.getOpOrNull(2))
        for (rune in RuneDrawGame.Rune.entries) assertNotNull(ServerCacheManager.getItem(rune.obj.asRSCM()), rune.obj)
        for (colour in FlagColour.entries) assertNotNull(ServerCacheManager.getItem(colour.dye.asRSCM()), colour.dye)
        for (seq in listOf("seq.ahoy_ecto_teleport", "seq.quest_ahoy_human_filling_bucket", "seq.human_enchantamuletlvl1", "seq.human_longjump")) {
            assertNotNull(ServerCacheManager.getAnim(seq.asRSCM(RSCMType.SEQ)), seq)
        }
        assertTrue("spotanim.ectophial_pour_spotanim".asRSCM(RSCMType.SPOTANIM) >= 0)
        assertTrue("spotanim.enchant_amulet_lvl1".asRSCM(RSCMType.SPOTANIM) >= 0)
    }

    @Test fun `nettle-water boils into nettle tea on any fire or range from level 20`() {
        val row = CookingFoodsRow.all().single { it.input.id == GhostsAhoyQuest.BOWL_OF_NETTLE_WATER.asRSCM() }
        assertEquals(GhostsAhoyQuest.BOWL_OF_TEA.asRSCM(), row.output.id)
        assertEquals(COOKING_REQ, row.statReq.single().t1)
        assertEquals(52, row.xp)
        assertEquals(GhostsAhoyQuest.BOWL.asRSCM(), row.burnt.id, "boiling over leaves an empty bowl")
        assertEquals(54, row.stopBurnFire)
        assertEquals("content.cooking_range_standard".asRSCM(RSCMType.CONTENT), loc("loc.ahoy_range").contentGroup)
    }

    private fun PhasmatysBarrier.Gate.outsideTile(): CoordGrid =
        if (alongX) CoordGrid(outside, origin.z, origin.level) else CoordGrid(origin.x, outside, origin.level)

    private fun npc(name: String) = checkNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC))) { name }

    private fun loc(name: String) = checkNotNull(ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC))) { name }

    private fun item(name: String) = checkNotNull(ServerCacheManager.getItem(name.asRSCM(RSCMType.OBJ))) { name }

    private fun placed(name: String, at: CoordGrid): Boolean = placed.any { it.first == name.asRSCM(RSCMType.LOC) && it.second == at }

    private fun open(tile: CoordGrid): Boolean =
        collision[tile.x, tile.z, tile.level] and (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC) == 0

    private fun reachable(from: CoordGrid, bounds: IntArray): Set<CoordGrid> {
        val steps = StepValidator(collision)
        val seen = hashSetOf(from)
        val queue = ArrayDeque(listOf(from))
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            for (dx in -1..1) for (dz in -1..1) {
                if ((dx == 0 && dz == 0) || !steps.canTravel(c.level, c.x, c.z, dx, dz)) continue
                val n = c.translate(dx, dz)
                if (n.x !in bounds[0]..bounds[2] || n.z !in bounds[1]..bounds[3]) continue
                if (seen.add(n)) queue += n
            }
        }
        return seen
    }

    private fun rawSpawns(): List<Pair<String, CoordGrid>> {
        val dir = listOf("", "../../").map { java.io.File("$it.data/raw-cache/map/npcs") }.first { it.isDirectory }
        val pattern = Regex("npc = \"(npc[.][a-z0-9_]+)\"\\s*\\r?\\ncoords = \"(\\d+)_(\\d+)_(\\d+)_(\\d+)_(\\d+)\"")
        return dir.listFiles { f -> f.name.endsWith(".toml") }!!.flatMap { file ->
            pattern.findAll(file.readText()).map {
                val (name, level, mx, mz, lx, lz) = it.destructured
                name to CoordGrid(mx.toInt() * 64 + lx.toInt(), mz.toInt() * 64 + lz.toInt(), level.toInt())
            }.toList()
        }
    }

    private companion object {
        val TOWN_BOUNDS = intArrayOf(3600, 3440, 3720, 3540)
        val WRECK_BOUNDS = intArrayOf(3585, 3535, 3630, 3575)
        val ISLAND_BOUNDS = intArrayOf(3770, 3510, 3840, 3580)

        val ROCK_ROUTE = listOf(
            CoordGrid(3604, 3550, 0), CoordGrid(3602, 3550, 0), CoordGrid(3599, 3552, 0), CoordGrid(3597, 3552, 0),
            CoordGrid(3595, 3554, 0), CoordGrid(3595, 3556, 0), CoordGrid(3597, 3559, 0), CoordGrid(3597, 3561, 0),
            CoordGrid(3599, 3564, 0), CoordGrid(3601, 3564, 0),
        )

        val collision = CollisionFlagMap()
        val placed = mutableListOf<Pair<Int, CoordGrid>>()
        lateinit var cache: dev.openrune.filesystem.Cache

        @JvmStatic @BeforeAll fun load() {
            cache = ServerCacheManager.init(240)
            for (sx in 54..59) for (sz in 53..56) {
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
