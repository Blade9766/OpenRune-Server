package org.rsmod.content.quest.area.falador.recruitmentdrive

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM
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
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.api.table.QuestRow
import org.rsmod.content.quest.area.falador.recruitmentdrive.rooms.AcrosticRoom
import org.rsmod.content.quest.area.falador.recruitmentdrive.rooms.Alchemy
import org.rsmod.content.quest.area.falador.recruitmentdrive.rooms.CombatRoom
import org.rsmod.content.quest.area.falador.recruitmentdrive.rooms.CrossingRoom
import org.rsmod.content.quest.area.falador.recruitmentdrive.rooms.ImprovisationRoom
import org.rsmod.content.quest.area.falador.recruitmentdrive.rooms.PatienceRoom
import org.rsmod.content.quest.area.falador.recruitmentdrive.rooms.StatueRoom
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Pins Recruitment Drive to the cache and the map: the quest row, the stage and attempt vars, every
 * room's portals, doors and observer where [TestRoom] puts them, the tiles scripts move the player
 * to, the statue layouts, the crossing multilocs, the improvisation room's locs, and the items,
 * interface, timers and shop the scripts name.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class RecruitmentDriveCacheTest {

    @Test fun `the quest row gives one quest point, 1,000-point-5 xp in three skills and the two requirements`() {
        val row = QuestRow.getRow("dbrow.${RecruitmentDriveQuest.QUEST_KEY}".asRSCM())
        assertEquals(RecruitmentDriveQuest.STAGE_COMPLETE, row.endstate)
        assertEquals(1, row.questpoints)
        assertEquals(
            mapOf("prayer" to 10005, "herblore" to 10005, "agility" to 10005),
            row.statXpAwarded.associate { it.t0.displayName.lowercase() to it.t1 },
            "tenths of 1,000.5",
        )
        assertEquals(
            setOf("dbrow.quest_blackknightsfortress", "dbrow.quest_druidicritual").map { it.asRSCM() }.toSet(),
            row.requirementQuests.map { it.rowId }.toSet(),
        )
        assertEquals((RecruitmentDriveQuest.REWARD_XP * 10).toInt(), 10005)
    }

    @Test fun `the stage is rd_main on the recruitment drive varp, and the attempt vars are where expected`() {
        assertEquals("varp.recruitmentdrive".asRSCM(RSCMType.VARP), varbit("varbit.rd_main").baseVar.id)
        assertEquals(VarpLifetime.Perm, varp("varp.recruitmentdrive").scope)
        assertEquals(VarpLifetime.Perm, varp("varp.rd_session").scope, "an attempt survives logout")
        assertEquals(VarpLifetime.Temp, varp("varp.rd_lock").scope)
        assertEquals(VarpLifetime.Perm, varp("varp.respawn_point_state").scope)
        assertTrue(width("varbit.rd_order") >= 11, "room for 1,800 orders")
        assertTrue((1 shl width("varbit.rd_order")) > RoomOrder.all.size)
        assertTrue((1 shl width("varbit.rd_statue_answer")) > StatueRoom.LAYOUTS)
        assertTrue((1 shl width("varbit.rd_patience_ticks")) > PatienceRoom.WAIT_TICKS)
        for (wheel in AcrosticRoom.WHEEL_VARBITS) assertTrue((1 shl width(wheel)) >= AcrosticRoom.ALPHABET)
        for (room in TestRoom.entries) assertEquals(1, width(room.completeVarbit), room.name)
        assertEquals(VarpLifetime.Perm, varp(varbit("varbit.rd_room1_complete").baseVar.id).scope)
        for (name in RecruitmentState.ROOM_VARBITS) assertNotNull(ServerCacheManager.getVarbit(name.asRSCM(RSCMType.VARBIT)), name)
    }

    @Test fun `every room's portals and exit door stand where the room says, and its observer spawns there`() {
        for (room in TestRoom.entries) {
            assertTrue(placed(room.entrancePortal, room.entranceTile), "${room.name} entrance")
            assertTrue(placed(room.exitPortal, room.exitPortalTile), "${room.name} exit portal")
            assertTrue(placed(room.exitDoor, room.exitDoorTile), "${room.name} exit door")
            assertTrue(room.observerTile in spawns(room.observer), "${room.name} observer at ${room.observerTile}")
            assertEquals(room, TestRoom.at(room.arrival))
            assertEquals(room, TestRoom.at(room.observerTile))
            assertEquals(room, TestRoom.at(room.exitDoorTile))
            assertTrue(open(room.arrival), "${room.name} arrival ${room.arrival} is walkable")
        }
        assertEquals(TestRoom.entries.size, TestRoom.entries.map { it.minX to it.minZ }.distinct().size)
    }

    @Test fun `scripted tiles are walkable floor`() {
        assertTrue(open(CombatRoom.LEYE_TILE))
        assertTrue(open(ImprovisationRoom.BEFORE_STONE_DOOR))
        assertTrue(open(ImprovisationRoom.BETWEEN_DOORS))
        assertTrue(open(CrossingRoom.EAST_TO_WEST.first()))
        assertTrue(open(CrossingRoom.EAST_TO_WEST.last()))
        assertTrue(open(TestingGrounds.FALADOR_PARK), "Falador Park return tile")
        assertTrue(open(GazeOfSaradomin.FALADOR_RESPAWN), "Falador respawn")
        assertEquals(CoordGrid(2997, 3373, 0), spawns(RecruitmentDriveQuest.SIR_TIFFY).single(), "Sir Tiffy's bench")
    }

    @Test fun `the room items and tables are where the scripts put them`() {
        for ((_, tile) in CombatRoom.WEAPONS) assertTrue(placedAny("loc.rd_wooden_table", tile.x - 1..tile.x, tile.z), "table under $tile")
        assertTrue(spawnsObj("obj.rd_hourglass").contains(PatienceRoom.HOURGLASS_TILE))
        assertTrue(spawnsObj(ImprovisationRoom.SPADE).contains(ImprovisationRoom.SPADE_TILE))
        assertTrue(spawnsObj(Alchemy.EMPTY_VIAL).contains(ImprovisationRoom.VIAL_TILE))
        for ((crate, _) in ImprovisationRoom.CRATE_CONTENTS) {
            assertTrue(ImprovisationRoom.CRATES.any { placed(it, crate) }, "a crate at $crate")
        }
        for ((bookcase, _) in ImprovisationRoom.BOOKCASES) assertTrue(placedLocs.any { it.id == bookcase.asRSCM(RSCMType.LOC) }, bookcase)
        for (shelf in ImprovisationRoom.Shelf.entries) assertTrue(placedLocs.any { it.id == shelf.loc.asRSCM(RSCMType.LOC) }, shelf.name)
        assertTrue(placedLocs.any { it.id == "loc.rd_stone_door".asRSCM(RSCMType.LOC) && it.coords.x == ImprovisationRoom.STONE_DOOR_X })
        assertEquals("varbit.rd_room6_stone_door".asRSCM(RSCMType.VARBIT), loc("loc.rd_stone_door").multiVarBit)
        val door = checkNotNull(loc("loc.rd_stone_door").transforms)
        assertEquals(
            listOf(ImprovisationRoom.STONE_DOOR_BASIC, ImprovisationRoom.STONE_DOOR_SPADE, ImprovisationRoom.STONE_DOOR_SPADE, ImprovisationRoom.STONE_DOOR_OPEN),
            (0..3).map { RSCM.getReverseMapping(RSCMType.LOC, door[it]) },
        )
    }

    @Test fun `each statue layout hides a different statue, and the full set is layout zero`() {
        val missing = (1..StatueRoom.LAYOUTS).map { checkNotNull(StatueRoom.missingStatue(it)) }
        assertEquals(StatueRoom.STATUES.map { it.asRSCM(RSCMType.LOC) }.toSet(), missing.toSet())
        for (multiloc in StatueRoom.MULTILOCS) {
            val type = loc(multiloc)
            assertEquals("varbit.rd_room_order".asRSCM(RSCMType.VARBIT), type.multiVarBit)
            assertTrue(checkNotNull(type.transforms)[StatueRoom.FULL_SET] in StatueRoom.STATUES.map { it.asRSCM(RSCMType.LOC) })
        }
        assertEquals(12, StatueRoom.MULTILOCS.count { name -> placedLocs.any { it.id == name.asRSCM(RSCMType.LOC) && TestRoom.at(it.coords) == TestRoom.STATUES } })
    }

    @Test fun `the crossing pieces show on the banks the varbit values say`() {
        for (cargo in org.rsmod.content.quest.area.falador.recruitmentdrive.rooms.Cargo.entries) {
            val east = locs(CrossingRoom.eastVarbit(cargo))
            val west = locs(CrossingRoom.westVarbit(cargo))
            val shown = CrossingRoom.pieceLocs(cargo).map { it.asRSCM(RSCMType.LOC) }
            assertTrue(east.transforms!![CrossingRoom.EAST_PRESENT] in shown, "$cargo east present")
            assertEquals(-1, east.transforms!![CrossingRoom.EAST_ABSENT], "$cargo east absent")
            assertTrue(west.transforms!![CrossingRoom.WEST_PRESENT] in shown, "$cargo west present")
            assertEquals(-1, west.transforms!![CrossingRoom.WEST_ABSENT], "$cargo west absent")
            assertTrue(placedLocs.any { it.id == east.id && it.coords.x >= CrossingRoom.EAST_BANK_MIN_X })
            assertTrue(placedLocs.any { it.id == west.id && it.coords.x <= CrossingRoom.WEST_BANK_MAX_X })
            val item = checkNotNull(ServerCacheManager.getItem(CrossingRoom.obj(cargo).asRSCM(RSCMType.OBJ)))
            assertEquals(5000.0, item.weight.toDouble(), "$cargo weighs 5 kg")
        }
        assertEquals(setOf(1, 3, 5), org.rsmod.content.quest.area.falador.recruitmentdrive.rooms.Cargo.entries.map { CrossingRoom.wearSlot(it) }.toSet(), "cape, weapon and shield slots")
        assertEquals("loc.rd_fox_fat", RSCM.getReverseMapping(RSCMType.LOC, locs("varbit.rd_foxleft").transforms!![CrossingRoom.EAST_EATEN]))
        assertEquals("loc.rd_sack_empty", RSCM.getReverseMapping(RSCMType.LOC, locs("varbit.rd_grainright").transforms!![CrossingRoom.WEST_EATEN]))
        assertTrue(placed(CrossingRoom.BRIDGE_EAST, CoordGrid(2483, 4972, 0)))
        assertTrue(placed(CrossingRoom.BRIDGE_WEST, CoordGrid(2477, 4972, 0)))
    }

    @Test fun `the named items, interface, jingles, timers and shop exist`() {
        for (name in SessionItems.TYPES + listOf(RecruitmentDriveQuest.SALLET, RecruitmentDriveQuest.HAUBERK, RecruitmentDriveQuest.CUISSE, RecruitmentDriveQuest.HARNESS)) {
            assertNotNull(ServerCacheManager.getItem(name.asRSCM(RSCMType.OBJ)), name)
        }
        for (component in listOf(AcrosticRoom.LOCK_ENTER) + AcrosticRoom.WHEEL_NAMES.flatMap { listOf("rd$it", "rd${it}_left", "rd${it}_right") }.map { "component.rd_combolock:$it" }) {
            assertTrue(component.asRSCM(RSCMType.COMPONENT) shr 16 == "interface.rd_combolock".asRSCM(RSCMType.INTERFACE), component)
        }
        for (name in listOf(StatueRoom.TIMER, PatienceRoom.TIMER)) assertTrue(name.asRSCM(RSCMType.TIMER) >= 0)
        assertTrue(PatienceRoom.WATCH_QUEUE.asRSCM(RSCMType.QUEUE) >= 0)
        assertNotNull(ServerCacheManager.getInventory(RecruitmentDriveQuest.SHOP_INV.asRSCM(RSCMType.INV)))
        val sallet = checkNotNull(ServerCacheManager.getItem(RecruitmentDriveQuest.SALLET.asRSCM(RSCMType.OBJ)))
        assertEquals(6000, sallet.cost)
        assertEquals(20000, checkNotNull(ServerCacheManager.getItem(RecruitmentDriveQuest.HARNESS.asRSCM(RSCMType.OBJ))).cost)
        assertEquals(20, sallet.paramOrNull(BaseParams.statreq1_level), "20 Defence to wear")
        assertEquals(10, sallet.paramOrNull(BaseParams.statreq2_level), "10 Prayer to wear")
        val leye = checkNotNull(ServerCacheManager.getNpc(CombatRoom.SIR_LEYE.asRSCM(RSCMType.NPC)))
        assertEquals(20, leye.combatLevel)
        assertEquals("Sir Leye", leye.name)
        assertFalse(TestRoom.entries.map { it.jingle }.toSet().size < TestRoom.entries.size, "seven jingles")
    }

    private fun locs(varbit: String) =
        checkNotNull(StatueRoom.MULTILOCS.plus(listOf("loc.rd_room2_fox_multi", "loc.rd_room2_fox_multi_right", "loc.rd_room2_chicken_multi", "loc.rd_room2_chicken_multi_right", "loc.rd_room2_grain_multi", "loc.rd_room2_grain_multi_right")).map(::loc).firstOrNull { it.multiVarBit == varbit.asRSCM(RSCMType.VARBIT) }) { varbit }

    private fun width(name: String): Int = varbit(name).let { it.endBit - it.startBit + 1 }

    private fun varbit(name: String) = checkNotNull(ServerCacheManager.getVarbit(name.asRSCM(RSCMType.VARBIT))) { name }

    private fun varp(name: String) = checkNotNull(ServerCacheManager.getVarp(name.asRSCM(RSCMType.VARP))) { name }

    private fun varp(id: Int) = checkNotNull(ServerCacheManager.getVarp(id)) { "varp $id" }

    private fun loc(name: String) = checkNotNull(ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC))) { name }

    private fun placed(name: String, at: CoordGrid): Boolean = placedLocs.any { it.id == name.asRSCM(RSCMType.LOC) && it.coords == at }

    private fun placedAny(name: String, xs: IntRange, z: Int): Boolean = xs.any { placed(name, CoordGrid(it, z, 0)) }

    private fun open(tile: CoordGrid): Boolean =
        collision[tile.x, tile.z, tile.level] and (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC) == 0

    private data class Placed(val id: Int, val coords: CoordGrid, val angle: Int)

    private companion object {
        val SQUARES = listOf(38 to 77, 46 to 52)

        val collision = CollisionFlagMap()
        val placedLocs = mutableListOf<Placed>()
        lateinit var cache: dev.openrune.filesystem.Cache

        fun file(path: String): java.io.File = listOf("", "../../").map { java.io.File("$it$path") }.first { it.exists() }

        fun spawns(npc: String): List<CoordGrid> = spawnsIn(".data/raw-cache/map/npcs", "npc = \"$npc\"")

        fun spawnsObj(obj: String): List<CoordGrid> = spawnsIn(".data/raw-cache/map/objs", "obj = \"$obj\"")

        fun spawnsIn(dir: String, key: String): List<CoordGrid> =
            file(dir).listFiles()!!.filter { it.extension == "toml" }.flatMap { spawnFile ->
                val lines = spawnFile.readLines()
                lines.indices.filter { lines[it].trim() == key }.map { index ->
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
                        val loc = LocEntity(entry.intValue)
                        placedLocs += Placed(loc.id, base.translate(key.x, key.z), loc.angle)
                    }
                }
            }
        }

        @JvmStatic @AfterAll fun close() {
            cache.close()
        }
    }
}
