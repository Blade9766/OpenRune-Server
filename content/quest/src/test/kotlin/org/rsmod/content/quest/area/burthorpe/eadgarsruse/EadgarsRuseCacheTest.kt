package org.rsmod.content.quest.area.burthorpe.eadgarsruse

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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.table.QuestRow
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.CHICKENS_NEEDED
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.GRAIN_NEEDED
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.HERBLORE_REQ
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.HERBLORE_XP
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_COMPLETE
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Pins the cache and map facts Eadgar's Ruse is written against: the quest row, its own vars,
 * where its people stand and its scenery is placed, and that the tiles the scripts move the player
 * to can be stood on.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class EadgarsRuseCacheTest {

    @Test fun `the quest row matches the stages, reward and requirements`() {
        val row = QuestRow.getRow("dbrow.${EadgarsRuseQuest.QUEST_KEY}".asRSCM())
        assertEquals(STAGE_COMPLETE, row.endstate)
        assertEquals(1, row.questpoints)
        assertEquals(mapOf("herblore" to HERBLORE_REQ), row.requirementStats.associate { it.t0.displayName to it.t1 })
        assertEquals(mapOf("herblore" to (HERBLORE_XP * 10).toInt()), row.statXpAwarded.associate { it.t0.displayName to it.t1 })
        assertEquals(
            setOf("dbrow.quest_trollstronghold", "dbrow.quest_druidicritual").map { it.asRSCM() }.toSet(),
            row.requirementQuests.map { it.rowId }.toSet(),
        )
        assertEquals(VarpLifetime.Perm, varp("varp.eadgar_quest").scope)
    }

    @Test fun `the quest's own flags are permanent, apart and wide enough`() {
        assertEquals(VarpLifetime.Perm, varp("varp.eadgar_ruse_bits").scope)
        val bits = HashSet<Pair<Int, Int>>()
        for (name in VARBITS) {
            val bit = checkNotNull(ServerCacheManager.getVarbit(name.asRSCM(RSCMType.VARBIT))) { name }
            assertEquals("varp.eadgar_ruse_bits".asRSCM(RSCMType.VARP), bit.baseVar.id, name)
            for (b in bit.startBit..bit.endBit) assertTrue(bits.add(bit.baseVar.id to b), "$name overlaps another flag")
        }
        assertTrue(width("varbit.eadgar_chickens_given") >= Integer.toBinaryString(CHICKENS_NEEDED).length)
        assertTrue(width("varbit.eadgar_grain_given") >= Integer.toBinaryString(GRAIN_NEEDED).length)
    }

    @Test fun `the people of the quest stand where the scripts expect`() {
        assertTrue(CoordGrid(2890, 10086, 2) in spawns(EadgarsRuseQuest.EADGAR), "Eadgar in his cave")
        assertEquals(listOf(CoordGrid(2844, 10057, 1)), spawns(EadgarsRuseQuest.BURNTMEAT))
        assertEquals(listOf(CoordGrid(2611, 3285, 0)), spawns(EadgarsRuseQuest.PETE))
        assertEquals(listOf(CoordGrid(2891, 3676, 0)), spawns(EadgarsRuseQuest.THISTLE_NPC))
        assertEquals(listOf(CoordGrid(2857, 10075, 0)), spawns(EadgarsRuseQuest.STOREROOM_GUARD))
        assertEquals(listOf(CoordGrid(2913, 3417, 0)), spawns(EadgarsRuseQuest.TEGID))
        assertEquals("Pick", npc(EadgarsRuseQuest.THISTLE_NPC).actions.getOpOrNull(0))
    }

    @Test fun `the scenery is placed where the scripts look for it`() {
        assertTrue(placed("loc.eadgar_rack", CoordGrid(2828, 10096, 0)))
        assertTrue(placed("loc.eadgar_kitchen_drawers", CoordGrid(2852, 10049, 1)))
        assertTrue(placed("loc.eadgar_storeroomdoor", CoordGrid(2869, 10085, 0)))
        assertTrue(placed("loc.eadgar_crate_goutweed", CoordGrid(2857, 10074, 0)))
        assertTrue(placed("loc.eadgar_aviary_wall_hatch", CoordGrid(2611, 3287, 0)))
        assertTrue(placed(EadgarsCave.ENTRANCE, CoordGrid(2892, 3672, 0)))
        assertTrue(placed(EadgarsCave.EXIT, CoordGrid(2892, 10072, 2)))
        assertTrue(placed("loc.troll_eadgar_cooking_pot", CoordGrid(2893, 10075, 2)))
        assertEquals("Search", loc("loc.eadgar_rack").actions.getOpOrNull(0))
        assertEquals("Search", loc("loc.eadgar_crate_goutweed").actions.getOpOrNull(0))
        assertEquals("Open", loc("loc.eadgar_kitchen_drawers").actions.getOpOrNull(0))
        assertEquals("Drop", item(EadgarsRuseQuest.PARROT).interfaceOptions[4])
    }

    @Test fun `every tile a script moves the player to can be stood on`() {
        for (tile in listOf(EadgarsCave.INSIDE, EadgarsCave.OUTSIDE, CoordGrid(2869, 10084, 0))) {
            assertTrue(open(tile), "$tile")
        }
        assertTrue(open(CoordGrid(2890, 10086, 2)), "Eadgar's own tile is floor")
    }

    private fun width(varbit: String): Int {
        val type = checkNotNull(ServerCacheManager.getVarbit(varbit.asRSCM(RSCMType.VARBIT)))
        return type.endBit - type.startBit + 1
    }

    private fun npc(name: String) = checkNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC))) { name }

    private fun loc(name: String) = checkNotNull(ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC))) { name }

    private fun item(name: String) = checkNotNull(ServerCacheManager.getItem(name.asRSCM(RSCMType.OBJ))) { name }

    private fun varp(name: String) = checkNotNull(ServerCacheManager.getVarp(name.asRSCM(RSCMType.VARP))) { name }

    private fun placed(name: String, at: CoordGrid): Boolean =
        placedLocs.any { it.first == name.asRSCM(RSCMType.LOC) && it.second == at }

    private fun open(tile: CoordGrid): Boolean =
        collision[tile.x, tile.z, tile.level] and (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC) == 0

    private companion object {
        val VARBITS =
            listOf(
                "varbit.eadgar_logs_given",
                "varbit.eadgar_chickens_given",
                "varbit.eadgar_grain_given",
                "varbit.eadgar_robe_given",
                "varbit.eadgar_asked_pete_water",
                "varbit.eadgar_asked_pete_food",
            )

        /** The Stronghold, Eadgar's cave, Trollheim's top, Ardougne Zoo and Taverley. */
        val SQUARES = listOf(44 to 157, 45 to 157, 45 to 57, 40 to 51, 45 to 53)

        val collision = CollisionFlagMap()
        val placedLocs = mutableListOf<Pair<Int, CoordGrid>>()
        lateinit var cache: dev.openrune.filesystem.Cache

        fun file(path: String): java.io.File =
            listOf("", "../../").map { java.io.File("$it$path") }.first { it.exists() }

        fun spawns(npc: String): List<CoordGrid> =
            file(".data/raw-cache/map/npcs").listFiles()!!.filter { it.extension == "toml" }.flatMap { spawnFile ->
                val lines = spawnFile.readLines()
                lines.indices.filter { lines[it].trim() == "npc = \"$npc\"" }.map { index ->
                    val packed = lines[index + 1].substringAfter('"').substringBefore('"').split('_').map(String::toInt)
                    CoordGrid(packed[1] * 64 + packed[3], packed[2] * 64 + packed[4], packed[0])
                }
            }

        @JvmStatic @BeforeAll fun load() {
            cache = ServerCacheManager.init(240)
            for ((sx, sz) in SQUARES) {
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
                        placedLocs += LocEntity(entry.intValue).id to base.translate(key.x, key.z)
                    }
                }
            }
        }

        @JvmStatic @AfterAll fun close() {
            cache.close()
        }
    }
}
