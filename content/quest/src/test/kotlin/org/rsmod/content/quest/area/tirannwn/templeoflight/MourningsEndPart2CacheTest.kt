package org.rsmod.content.quest.area.tirannwn.templeoflight

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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.table.QuestRow
import org.rsmod.content.quest.area.tirannwn.templeoflight.npcs.Thorgel
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Pins Mourning's End Part II to the cache and the map: the quest row and its vars, every pillar
 * where [TempleGeometry] says it stands with the openings its model and rotation give it, every
 * beam path's multiloc on the varbit named for it and laid out between the right pillars, the
 * doors and their colours, the cross locs and shafts, the chests, the dispenser, every tile a
 * script moves the player to, each blade trap's wall, and the items and interfaces the scripts
 * name.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class MourningsEndPart2CacheTest {

    @Test fun `the quest row has the current rewards and Part I as its requirement`() {
        val row = QuestRow.getRow("dbrow.${MourningsEndPart2Quest.QUEST_KEY}".asRSCM())
        assertEquals(MourningsEndPart2Quest.STAGE_COMPLETE, row.endstate)
        assertEquals(2, row.questpoints)
        assertEquals(mapOf("agility" to (MourningsEndPart2Quest.AGILITY_XP * 10).toInt()), row.statXpAwarded.associate { it.t0.displayName to it.t1 }, "tenths of 60,000, not the old 20,000")
        assertEquals(listOf("dbrow.quest_mourningsendpart1".asRSCM()), row.requirementQuests.map { it.rowId })
        assertTrue(row.requirementStats.isEmpty(), "no skill requirement of its own")
    }

    @Test fun `the quest's vars are permanent and the derived and server-only ones where expected`() {
        val stage = varbit("varbit.mourning_quest_main")
        assertEquals("varp.mourning_quest_part2".asRSCM(RSCMType.VARP), stage.baseVar.id)
        assertTrue(stage.endBit - stage.startBit + 1 >= 6, "room for stage 60")
        for (varp in TemplePuzzleStore.VARPS + ThorgelList.DELIVERED_VARPS + "varp.mourning2_state") {
            assertEquals(VarpLifetime.Perm, varp(varp).scope, varp)
        }
        assertEquals(VarpLifetime.Temp, varp(TempleShadows.PEACE_VARP).scope)
        for (item in TempleItem.entries) assertNotNull(ServerCacheManager.getVarbit(TemplePuzzle.trayVarbit(item).asRSCM(RSCMType.VARBIT)), item.name)
        assertTrue(width(TemplePuzzle.trayVarbit(TempleItem.MIRROR)) >= 4, "room for 13 mirrors")
        for (chest in TempleChest.entries) assertEquals(1, width(chest.varbit))
        for (name in listOf(ThorgelList.TICKET_VARBIT, ThorgelList.BOOK_VARBIT, ThorgelList.KEY_VARBIT)) assertEquals(2, width(name))
        assertEquals(2, width("varbit.mourning_dwarf_startedtask"))
        val hunt = checkNotNull(ServerCacheManager.getHunt(TempleShadows.HUNT_SHADOW))
        assertEquals(TempleShadows.PEACE_VARP.asRSCM(RSCMType.VARP), checkNotNull(hunt.checkVar1).varp)
    }

    @Test fun `every pillar stands where the geometry says, open on the sides its model shows`() {
        for (pillar in TempleGeometry.pillars) {
            assertTrue(placed(pillar.loc, pillar.coords), "${pillar.id} at ${pillar.coords}")
            pillar.column?.let { assertEquals(TempleGeometry.columns[it], pillar.coords.x to pillar.coords.z, "${pillar.id} stands in column $it") }
            if (pillar.kind == PillarKind.FINAL) continue
            val type = loc(pillar.loc)
            val shown = loc(checkNotNull(type.transforms)[0])
            val angle = placedAngle(pillar.loc, pillar.coords)
            assertEquals(pillar.holes, holes(RSCM.getReverseMapping(RSCMType.LOC, shown.id), angle), "${pillar.id}'s openings")
            val expected =
                when (pillar.id) {
                    "1_b" -> "varbit.mourning_light_temple_3_8_east"
                    else -> TempleGeometry.gapVarbits[pillar.floor to pillar.column!!]
                }
            assertEquals(expected!!.asRSCM(RSCMType.VARBIT), type.multiVarBit, "${pillar.id} shows the beam above it")
        }
        val bends = listOf(CoordGrid(1869, 4650, 2), CoordGrid(1881, 4639, 2), CoordGrid(1869, 4628, 2), CoordGrid(1869, 4650, 0), CoordGrid(1869, 4628, 0))
        for (bend in bends) assertTrue(placed(TemplePillars.BEND, bend), "fixed bend at $bend")
    }

    @Test fun `every beam path is drawn by its varbit between the pillars it joins`() {
        for (link in TempleGeometry.links) {
            val tiles = segmentTiles(link.varbit)
            assertTrue(tiles.isNotEmpty(), "${link.varbit} has beam tiles")
            for (port in listOfNotNull(link.from, (link.to as? LinkEnd.ToPillar)?.port)) {
                val pillar = TempleGeometry.pillarsById.getValue(port.pillar)
                if (port.side.vertical) continue
                assertTrue(port.side in pillar.holes, "${link.varbit}: ${pillar.id} is open to the ${port.side}")
                val next = step(pillar.coords, port.side)
                assertTrue(next in tiles, "${link.varbit} starts beside ${pillar.id} on its ${port.side} side: $tiles")
            }
            when (val end = link.to) {
                is LinkEnd.ToDoor -> {
                    val door = TempleGeometry.doorsById.getValue(end.door)
                    assertTrue(door.tiles.any { tile -> Facing.entries.filter { !it.vertical }.any { step(tile, it) in tiles } }, "${link.varbit} reaches ${door.id}")
                }
                is LinkEnd.ToPillar -> {
                    if (link.from.side.vertical || end.port.side.vertical) continue
                    val a = TempleGeometry.pillarsById.getValue(link.from.pillar).coords
                    val b = TempleGeometry.pillarsById.getValue(end.port.pillar).coords
                    if (a.level == b.level && (a.x == b.x || a.z == b.z)) {
                        assertTrue(straight(a, b).containsAll(tiles), "${link.varbit} runs straight from ${link.from.pillar} to ${end.port.pillar}")
                    }
                }
                LinkEnd.Wall -> Unit
            }
            for (cross in link.crosses) {
                val at = crossCoords(cross.cross)
                assertTrue(Facing.entries.filter { !it.vertical }.any { step(at, it) in tiles }, "${link.varbit} passes ${cross.cross}")
            }
        }
        val pipes = mapOf("3_5_west" to CoordGrid(1869, 4650, 1), "3_8_east" to CoordGrid(1881, 4639, 1), "3_10_west" to CoordGrid(1869, 4628, 1))
        for ((name, shaft) in pipes) {
            val vertical = placedLocs.filter { it.coords == shaft }.map { loc(it.id) }
            assertTrue(vertical.any { it.multiVarBit == "varbit.mourning_light_temple_$name".asRSCM(RSCMType.VARBIT) }, "$name drops through the middle floor")
        }
    }

    @Test fun `the doors are where the geometry says, in their colours`() {
        for (door in TempleGeometry.doors) {
            val type = loc(door.loc)
            assertEquals(door.varbit.asRSCM(RSCMType.VARBIT), type.multiVarBit, door.id)
            val shut = RSCM.getReverseMapping(RSCMType.LOC, checkNotNull(type.transforms)[0])
            val colour = door.colour?.label ?: "black"
            assertTrue(shut == "loc.mourning_door_of_light_$colour" || (colour == "magenta" && shut == "loc.mourning_crystal_magenta"), "${door.id} is $colour: $shut")
            assertEquals("loc.mourning_door_of_light_white", RSCM.getReverseMapping(RSCMType.LOC, type.transforms!![1]), "${door.id} turns white")
            for (tile in door.tiles) assertTrue(placed(door.loc, tile), "${door.id} at $tile")
        }
        assertEquals("Pass-through", loc("loc.mourning_door_of_light_white").actions.getOpOrNull(0))
    }

    @Test fun `cross locs and shafts carry the varbits the light writes`() {
        for ((varbit, place) in TempleGeometry.crosses) {
            val name = "loc.mourning_light_temple_beam_cross_" + varbit.substringAfter("cross_")
            val coords = crossCoords(varbit)
            assertTrue(placed(name, coords), "$name at $coords")
            val type = loc(name)
            assertEquals(varbit.asRSCM(RSCMType.VARBIT), type.multiVarBit, name)
            assertEquals(30, checkNotNull(type.transforms).size, "$name: off, then north-south, east-west, rising and crossed in seven colours")
            assertEquals(place.first - 1, coords.level)
        }
        for (varbit in TempleGeometry.gapVarbits.values + TempleGeometry.links.map { it.varbit } + TempleGeometry.doors.map { it.varbit }) {
            assertNotNull(ServerCacheManager.getVarbit(varbit.asRSCM(RSCMType.VARBIT)), varbit)
        }
        assertTrue(placed("loc.mourning_temple_crystal", CoordGrid(1908, 4638, 0)), "the great crystal under the emitter")
        assertTrue(placed(TempleDiscoveries.BLACK_CRYSTAL, CoordGrid(1908, 4638, 2)), "the black crystal above it")
        val black = loc(TempleDiscoveries.BLACK_CRYSTAL)
        assertEquals("varbit.mourning_light_temple_safe_guards".asRSCM(RSCMType.VARBIT), black.multiVarBit)
        assertEquals(listOf("loc.mourning_temple_obsidian_crystal_dead", "loc.mourning_temple_obsidian_crystal_new"), checkNotNull(black.transforms).take(2).map { RSCM.getReverseMapping(RSCMType.LOC, it) })
    }

    @Test fun `the chests, dispenser, guards and altar are where the scripts expect`() {
        val chests =
            mapOf(
                TempleChest.BLUE_DOOR to CoordGrid(1917, 4613, 1),
                TempleChest.MAGENTA_DOOR to CoordGrid(1917, 4665, 1),
                TempleChest.NORTH_WEST to CoordGrid(1880, 4659, 0),
                TempleChest.SOUTH_WEST to CoordGrid(1858, 4613, 0),
                TempleChest.SOUTH_EAST to CoordGrid(1910, 4622, 0),
            )
        for ((chest, at) in chests) {
            assertTrue(placed(chest.closed, at), "$chest at $at")
            assertEquals("Open", loc(chest.closed).actions.getOpOrNull(0))
            assertEquals(listOf("Search", "Shut"), (0..1).map { loc(chest.open).actions.getOpOrNull(it) })
        }
        assertTrue(placed(CrystalDispenser.LEVER, CoordGrid(1913, 4639, 1)))
        assertTrue(placed(CrystalDispenser.COLLECTOR, CoordGrid(1914, 4639, 1)))
        assertTrue(placed(TempleDiscoveries.EDERN, CoordGrid(1925, 4642, 0)))
        assertTrue(placed(TempleDiscoveries.DISPENSER_GUARD, CoordGrid(1910, 4635, 1)))
        assertTrue(placed("loc.deathtemple_ruined", CoordGrid(1859, 4638, 0)))
        assertTrue(placed(TempleObstacles.WALL_HOLE, CoordGrid(1856, 4638, 0)))
        assertTrue(placed(TempleObstacles.UPASS_TUNNEL, CoordGrid(2311, 9792, 0)))
        val tunnel = loc(TempleObstacles.UPASS_TUNNEL)
        assertEquals("varbit.mourning_light_door_1_c_first_time".asRSCM(RSCMType.VARBIT), tunnel.multiVarBit, "the dwarves' tunnel opens once the player has met Thorgel")
        assertEquals("Descend", loc(checkNotNull(tunnel.transforms)[1]).actions.getOpOrNull(0))
        val thorgel = checkNotNull(ServerCacheManager.getNpc(Thorgel.THORGEL.asRSCM(RSCMType.NPC)))
        assertEquals("varbit.mourning_dwarf_vis".asRSCM(RSCMType.VARBIT), thorgel.multiVarBit)
        assertEquals(Thorgel.THORGEL_VIS.asRSCM(RSCMType.NPC), checkNotNull(thorgel.transforms)[1])
        assertTrue(CoordGrid(1860, 4641, 0) in spawns(Thorgel.THORGEL), "Thorgel waits by the altar")
        assertTrue(spawns(TempleShadows.SHADOW).size > 50, "the shadows roam all three floors")
    }

    @Test fun `every tile a script moves the player to can be stood on and leads somewhere`() {
        for (climb in TempleObstacles.CLIMBS) {
            assertTrue(placed(climb.loc, climb.coords), "${climb.loc} at ${climb.coords}")
            assertTrue(open(climb.dest), "landing ${climb.dest} from ${climb.coords}")
        }
        val tiles =
            listOf(
                TempleObstacles.ROPE_TOP, TempleObstacles.ROPE_BOTTOM, TempleObstacles.SUPPORTS_WEST, TempleObstacles.SUPPORTS_EAST,
                TempleObstacles.UPASS_LANDING, TempleObstacles.TEMPLE_LANDING, CoordGrid(1863, 4639, 0),
            ) + TempleObstacles.DOORWAY_Z.flatMap { listOf(CoordGrid(TempleObstacles.DOORWAY_INSIDE_X, it, 0), CoordGrid(TempleObstacles.DOORWAY_OUTSIDE_X, it, 0)) }
        for (tile in tiles) assertTrue(open(tile), "$tile")
        for (x in TempleObstacles.SUPPORTS_WEST.x + 1 until TempleObstacles.SUPPORTS_EAST.x) {
            assertTrue(placed(TempleObstacles.WALL_SUPPORT, CoordGrid(x, 4612, 1)), "support at $x")
            assertTrue(open(CoordGrid(x, 4612, 0)), "a slip from the support at $x lands on the ground floor")
        }
        for (door in TempleGeometry.doors) {
            for (tile in door.tiles) {
                val sides = if (door.axis == Axis.EAST_WEST) listOf(Facing.EAST, Facing.WEST) else listOf(Facing.NORTH, Facing.SOUTH)
                for (side in sides) assertTrue(open(step(tile, side)), "${door.id}: the $side side of $tile")
            }
        }
        val validator = StepValidator(collision)
        for (doorway in TempleObstacles.DOORWAY_Z) {
            assertTrue(!validator.canTravel(0, TempleObstacles.DOORWAY_OUTSIDE_X, doorway, -1, 0), "the doorway is sealed for the server to open at $doorway")
        }
        for (trap in TempleObstacles.Trap.entries) {
            assertTrue(open(trap.wall) && open(trap.beyond), "$trap: both sides")
            assertTrue(!validator.canTravel(trap.wall.level, trap.wall.x, trap.wall.z, trap.edge.dx, trap.edge.dz), "$trap: only a dodge gets past")
            assertTrue(placedLocs.any { it.coords == trap.wall && it.id == "loc.inviswall_serverside".asRSCM(RSCMType.LOC) }, "$trap: on its server-side wall")
        }
        assertTrue(placed(TempleObstacles.ROCK, CoordGrid(1876, 4620, 1)))
        assertTrue(placed(TempleObstacles.ROPE_MULTI, CoordGrid(1877, 4620, 0)))
        assertEquals("varbit.mourning_temple_rope".asRSCM(RSCMType.VARBIT), loc(TempleObstacles.ROPE_MULTI).multiVarBit)
        assertTrue(placed(TempleObstacles.LOW_WALL, CoordGrid(1883, 4658, 1)) && placed(TempleObstacles.LOW_WALL, CoordGrid(1883, 4620, 1)))
    }

    @Test fun `the pieces, documents and Thorgel's list name real items`() {
        for (item in TempleItem.entries) assertNotNull(ServerCacheManager.getItem(item.obj.asRSCM()), item.obj)
        val noBank = listOf(TempleItem.MIRROR, TempleItem.YELLOW, TempleItem.CYAN, TempleItem.FRACTURED_HORIZONTAL, TempleItem.FRACTURED_VERTICAL)
        for (item in noBank) assertEquals(1, ServerCacheManager.getItem(item.obj.asRSCM())!!.paramsRaw?.get(59), "${item.obj} stays out of the bank")
        for ((obj, label) in ThorgelList.GUARANTEED) assertEquals(label, item(obj).name, obj)
        for ((objs, label) in ThorgelList.TICKETS + ThorgelList.BOOKS + ThorgelList.KEYS) {
            for (obj in objs) assertNotNull(ServerCacheManager.getItem(obj.asRSCM()), obj)
            if (objs.size == 1) assertEquals(label, item(objs[0]).name)
        }
        assertEquals(50, ThorgelList.SIZE)
        assertEquals(47, ThorgelList.GUARANTEED.toMap().size, "47 different items")
        assertEquals("Read", item(MourningsEndPart2Quest.ITEM_LIST).interfaceOptions[0])
        assertEquals("Read", item(MourningsEndPart2Quest.JOURNAL).interfaceOptions[0])
        assertEquals("Read", item(MourningsEndPart2Quest.NOTES).interfaceOptions[0])
        assertEquals(Thorgel.LIST_INTERFACE.asRSCM(RSCMType.INTERFACE), Thorgel.LIST_TEXT.asRSCM(RSCMType.COMPONENT) ushr 16)
        for (obj in MourningsEndPart2Quest.ALTAR_KEYS + MourningsEndPart2Quest.ALTAR_CAPES) assertNotNull(ServerCacheManager.getItem(obj.asRSCM()), obj)
        for (obj in listOf(MourningsEndPart2Quest.SAMPLE, MourningsEndPart2Quest.NEW_CRYSTAL, MourningsEndPart2Quest.CHARGED_CRYSTAL, MourningsEndPart2Quest.TRINKET, MourningsEndPart2Quest.NEW_KEY)) {
            assertNotNull(ServerCacheManager.getItem(obj.asRSCM()), obj)
        }
    }

    private fun holes(model: String, angle: Int): Set<Facing> {
        val base =
            when {
                model.endsWith("pillar_light") -> listOf(Facing.NORTH, Facing.EAST, Facing.SOUTH, Facing.WEST)
                model.endsWith("_1") -> listOf(Facing.EAST)
                model.endsWith("_2") -> listOf(Facing.EAST, Facing.SOUTH)
                model.endsWith("_3") -> listOf(Facing.NORTH, Facing.EAST, Facing.SOUTH)
                model.endsWith("_4") -> listOf(Facing.NORTH, Facing.SOUTH)
                else -> error("Unknown pillar model $model")
            }
        val ring = listOf(Facing.NORTH, Facing.EAST, Facing.SOUTH, Facing.WEST)
        return base.map { ring[(ring.indexOf(it) + angle) % 4] }.toSet()
    }

    private fun step(coords: CoordGrid, side: Facing): CoordGrid =
        when (side) {
            Facing.NORTH -> coords.translateZ(1)
            Facing.SOUTH -> coords.translateZ(-1)
            Facing.EAST -> coords.translateX(1)
            Facing.WEST -> coords.translateX(-1)
            else -> coords
        }

    private fun straight(a: CoordGrid, b: CoordGrid): List<CoordGrid> =
        if (a.x == b.x) {
            (minOf(a.z, b.z) + 1 until maxOf(a.z, b.z)).map { CoordGrid(a.x, it, a.level) }
        } else {
            (minOf(a.x, b.x) + 1 until maxOf(a.x, b.x)).map { CoordGrid(it, a.z, a.level) }
        }

    private fun crossCoords(varbit: String): CoordGrid {
        val (floor, column) = TempleGeometry.crosses.getValue(varbit)
        val (x, z) = TempleGeometry.columns.getValue(column)
        return CoordGrid(x, z, floor - 1)
    }

    private fun segmentTiles(varbit: String): Set<CoordGrid> {
        val id = varbit.asRSCM(RSCMType.VARBIT)
        return placedLocs.filter { loc(it.id).multiVarBit == id && RSCM.getReverseMapping(RSCMType.LOC, it.id).contains("beam") && !RSCM.getReverseMapping(RSCMType.LOC, it.id).contains("vertical") }.map { it.coords }.toSet()
    }

    private fun width(varbit: String): Int {
        val type = varbit(varbit)
        return type.endBit - type.startBit + 1
    }

    private fun varbit(name: String) = checkNotNull(ServerCacheManager.getVarbit(name.asRSCM(RSCMType.VARBIT))) { name }

    private fun varp(name: String) = checkNotNull(ServerCacheManager.getVarp(name.asRSCM(RSCMType.VARP))) { name }

    private fun loc(name: String) = checkNotNull(ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC))) { name }

    private fun loc(id: Int) = checkNotNull(ServerCacheManager.getObject(id)) { "loc $id" }

    private fun item(name: String) = checkNotNull(ServerCacheManager.getItem(name.asRSCM(RSCMType.OBJ))) { name }

    private fun placed(name: String, at: CoordGrid): Boolean = placedLocs.any { it.id == name.asRSCM(RSCMType.LOC) && it.coords == at }

    private fun placedAngle(name: String, at: CoordGrid): Int = placedLocs.first { it.id == name.asRSCM(RSCMType.LOC) && it.coords == at }.angle

    private fun open(tile: CoordGrid): Boolean =
        collision[tile.x, tile.z, tile.level] and (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC) == 0

    private data class Placed(val id: Int, val coords: CoordGrid, val angle: Int)

    private companion object {
        /** The temple, the mines, the Underground Pass dwarf camp and the Death Altar. */
        val SQUARES = listOf(29 to 72, 30 to 72, 31 to 72, 36 to 153, 34 to 75)

        val collision = CollisionFlagMap()
        val placedLocs = mutableListOf<Placed>()
        lateinit var cache: dev.openrune.filesystem.Cache

        fun file(path: String): java.io.File = listOf("", "../../").map { java.io.File("$it$path") }.first { it.exists() }

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
