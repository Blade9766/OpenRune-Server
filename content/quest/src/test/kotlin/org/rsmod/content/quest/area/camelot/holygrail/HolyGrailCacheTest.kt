package org.rsmod.content.quest.area.camelot.holygrail

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
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.DEAD_ARRIVAL
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.RESTORED_ARRIVAL
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.SUB_STATE_VARBITS
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.TOWER
import org.rsmod.content.quest.area.camelot.holygrail.npcs.EntranaGrailLore
import org.rsmod.content.quest.area.camelot.holygrail.npcs.SirPercival
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Pins the cache and map facts Holy Grail is written against: the quest row, the state varbits,
 * where every loc and spawn the scripts touch stands, that every tile a player or npc is put on
 * is open ground, that the Titan's bridge is the only way west in the dying realm, and that the
 * rooms and boxes the scripts watch match the map.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class HolyGrailCacheTest {

    @Test fun `the quest row matches the stages and requirements`() {
        val row = QuestRow.getRow("dbrow.${HolyGrailQuest.QUEST_KEY}".asRSCM())
        assertEquals(STAGE_COMPLETE, row.endstate)
        assertEquals(2, row.questpoints)
        assertEquals(mapOf("attack" to 20), row.requirementStats.associate { it.t0.displayName to it.t1 })
        assertEquals(listOf("Merlin's Crystal"), row.requirementQuests.map { it.displayname })
    }

    @Test fun `the state varbits sit on their own persistent server varp`() {
        val varp = "varp.holygrail_state".asRSCM(RSCMType.VARP)
        assertEquals(VarpLifetime.Perm, ServerCacheManager.getVarp(varp)!!.scope)
        assertEquals(VarpLifetime.Perm, ServerCacheManager.getVarp("varp.grail".asRSCM(RSCMType.VARP))!!.scope)
        for (name in SUB_STATE_VARBITS) {
            assertEquals(varp, ServerCacheManager.getVarbit(name.asRSCM(RSCMType.VARBIT))!!.baseVar.id, name)
        }
    }

    @Test fun `quest items and locs carry the ops the scripts answer`() {
        assertEquals("Blow", item(HolyGrailQuest.WHISTLE).interfaceOptions[0])
        assertEquals("Ring", item(HolyGrailQuest.BELL).interfaceOptions[0])
        assertEquals("Blow-on", item(HolyGrailQuest.FEATHER).interfaceOptions[0])
        assertEquals("Look-at", item(HolyGrailQuest.NAPKIN).interfaceOptions[0])
        assertEquals("Prod", loc(HolyGrailQuest.PERCIVAL_SACKS).actions.getOpOrNull(0))
        assertEquals("Open", loc(HolyGrailQuest.PERCIVAL_SACKS).actions.getOpOrNull(1))
        assertEquals("Open", loc(HolyGrailQuest.WHISTLE_DOOR).actions.getOpOrNull(0))
        assertEquals("Attack", npc(HolyGrailQuest.TITAN).actions.getOpOrNull(1))
        assertEquals(120, npc(HolyGrailQuest.TITAN).combatLevel)
    }

    @Test fun `every quest loc stands where the scripts expect`() {
        assertLoc(HolyGrailQuest.WHISTLE_DOOR, CoordGrid(3106, 3361, 2))
        assertLoc("loc.percy_sacks", SirPercival.SACKS)
        assertLoc(HolyGrailQuest.WORKSHOP_DOOR, CoordGrid(2764, 3503, 1))
        assertTrue(placed.any { it.second == WhistleRoom.TABLE }, "no table under the whistles")
        assertTrue(placed.any { it.second == GRAIL_TABLE }, "no table under the Grail")
        assertLoc("loc.laddertop", CoordGrid(2779, 4684, 2))
        assertLoc("loc.laddertop", CoordGrid(2651, 4684, 2))
    }

    @Test fun `the grail bell and the grail are spawned where the scripts expect`() {
        val objs = rawSpawns("objs")
        assertTrue("obj.grail_bell" to FisherRealm.BELL_SPOT in objs)
        assertTrue("obj.holy_grail" to GRAIL_TABLE in objs)
    }

    @Test fun `each quest npc is spawned exactly as often as the scripts expect`() {
        val counts = rawSpawns("npcs").groupingBy { it.first }.eachCount()
        for ((name, n) in mapOf(
            HolyGrailQuest.MERLIN to 1, HolyGrailQuest.KING_ARTHUR to 1, HolyGrailQuest.HIGH_PRIEST to 1,
            HolyGrailQuest.GALAHAD to 1, HolyGrailQuest.TITAN to 1, HolyGrailQuest.FISHERMAN to 1,
            HolyGrailQuest.FISHER_KING to 1, HolyGrailQuest.KING_PERCIVAL to 1, HolyGrailQuest.CRONE to 0,
            HolyGrailQuest.SIR_PERCIVAL to 0,
        )) assertEquals(n, counts[name] ?: 0, name)
        val spawns = rawSpawns("npcs")
        assertTrue(HolyGrailQuest.MERLIN to MERLIN_TILE in spawns)
        assertTrue(spawns.any { it.first == HolyGrailQuest.TITAN && it.second == FisherRealm.BRIDGE_TILE })
        assertTrue(spawns.all { it.first != HolyGrailQuest.TITAN || FisherRealm.isInDyingRealm(it.second) })
        assertTrue(spawns.all { it.first != HolyGrailQuest.KING_PERCIVAL || FisherRealm.isInHealedRealm(it.second) })
    }

    @Test fun `every tile a player or npc is put on is open ground`() {
        for (tile in listOf(
            TOWER, DEAD_ARRIVAL, RESTORED_ARRIVAL, FisherRealm.BRIDGE_EAST, FisherRealm.BRIDGE_WEST,
            FisherRealm.CASTLE_ENTRY, EntranaGrailLore.CRONE_TILE, SirPercival.PERCIVAL_TILE, MERLIN_TILE,
        )) assertTrue(open(tile), "$tile is blocked")
        assertFalse(open(FisherRealm.BRIDGE_TILE), "the Titan's tile should be solid")
    }

    @Test fun `the bridge is the only way west and leads on to the fisherman and the bell`() {
        assertTrue(walks(DEAD_ARRIVAL, FisherRealm.BRIDGE_EAST))
        assertFalse(walks(DEAD_ARRIVAL, FisherRealm.BRIDGE_WEST), "the river can be walked round")
        assertTrue(walks(FisherRealm.BRIDGE_WEST, FISHERMAN_TILE))
        assertTrue(walks(FisherRealm.BRIDGE_WEST, FisherRealm.BELL_SPOT))
        assertTrue(walks(RESTORED_ARRIVAL, CoordGrid(2661, 4722, 0)), "the healed realm needs no titan")
    }

    @Test fun `merlin stands in the workshop behind its door`() {
        assertTrue(walks(CoordGrid(2765, 3503, 1), MERLIN_TILE))
        assertFalse(walks(CoordGrid(2763, 3503, 1), MERLIN_TILE), "the workshop is open without its door")
    }

    @Test fun `the castle entry reaches the stairs to the Fisher King`() {
        assertTrue(walks(FisherRealm.CASTLE_ENTRY, CoordGrid(2762, 4682, 0)) || walks(FisherRealm.CASTLE_ENTRY, CoordGrid(2760, 4682, 0)))
    }

    @Test fun `the whistle room box is the room behind the whistle door`() {
        val inside = CoordGrid(3107, 3360, 2)
        assertTrue(WhistleRoom.isInRoom(inside))
        for (x in 3104..3111) for (z in 3357..3360) {
            val tile = CoordGrid(x, z, 2)
            if (open(tile)) assertTrue(walks(inside, tile), "$tile is boxed in")
        }
        assertFalse(walks(inside, CoordGrid(3106, 3362, 2)), "the room is open without the door")
        assertTrue(open(CoordGrid(3106, 3362, 2)))
    }

    @Test fun `the tower radius covers open ground beneath the watchtower`() {
        assertTrue(TOWER.level == 0)
        assertTrue(walks(TOWER, CoordGrid(TOWER.x + 3, TOWER.z, 0)) || walks(TOWER, CoordGrid(TOWER.x, TOWER.z - 3, 0)))
    }

    @Test fun `the empty tower box is the dying castle's tower top`() {
        assertTrue(FisherRealm.isEmptyTower(CoordGrid(2778, 4684, 2)))
        assertTrue(open(CoordGrid(2778, 4684, 2)))
        assertFalse(FisherRealm.isEmptyTower(GRAIL_TABLE))
    }

    @Test fun `every symbol the scripts use resolves`() {
        for (seq in listOf(
            MagicWhistle.BLOW_SEQ, FisherRealm.RING_SEQ, FisherRealm.TAKE_SEQ, SirPercival.OPEN_SEQ,
        )) assertNotNull(ServerCacheManager.getAnim(seq.asRSCM(RSCMType.SEQ)), seq)
        for (spot in listOf(MagicWhistle.TRAVEL_SPOTANIM, FisherRealm.GRAIL_SPOTANIM, FisherRealm.ENTRY_SPOTANIM)) {
            assertTrue(spot.asRSCM(RSCMType.SPOTANIM) >= 0, spot)
        }
        assertTrue(EntranaGrailLore.ARRIVE_SOUND.asRSCM(RSCMType.SYNTH) >= 0)
        for (obj in listOf("obj.swordfish", "obj.rune_full_helm", "obj.rune_platebody", "obj.rune_platelegs",
            "obj.rune_kiteshield", "obj.rune_longsword", "obj.excalibur")) assertNotNull(item(obj), obj)
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
        val GRAIL_TABLE = CoordGrid(2649, 4684, 2)
        val MERLIN_TILE = CoordGrid(2767, 3500, 1)
        val FISHERMAN_TILE = CoordGrid(2802, 4706, 0)

        val LOADED = listOf(41 to 73, 43 to 73, 42 to 50, 46 to 54, 48 to 52, 43 to 54, 44 to 52, 40 to 54)
            .map { (x, z) -> MapSquareKey(x, z) }.toSet()

        val collision = CollisionFlagMap()
        val placed = mutableListOf<Pair<Int, CoordGrid>>()
        lateinit var cache: dev.openrune.filesystem.Cache

        @JvmStatic @BeforeAll fun load() {
            cache = ServerCacheManager.init(240)
            for (square in LOADED) {
                val mx = square.x
                val mz = square.z
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
