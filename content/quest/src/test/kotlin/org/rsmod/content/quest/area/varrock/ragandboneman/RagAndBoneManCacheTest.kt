package org.rsmod.content.quest.area.varrock.ragandboneman

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
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.BOILER_BOILED
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.BOILER_BOILING
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.BOILER_EMPTY
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.BOILER_LOADED
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.BOILER_LOGS
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.varrock.ragandboneman.npcs.Fortunato
import org.rsmod.content.quest.area.varrock.ragandboneman.npcs.OddOldMan
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Pins the cache, map and drop-table facts Rag and Bone Man I is written against: the quest row and
 * rewards, the boiler multiloc and its vars, the camp locs and spawns, which creatures drop which
 * specimen (and that no lookalike does), and Fortunato's two shop stocks.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class RagAndBoneManCacheTest {

    @Test fun `the quest row matches the stages, rewards and sequel prerequisite`() {
        val row = QuestRow.getRow("dbrow.${RagAndBoneManQuest.QUEST_KEY}".asRSCM())
        assertEquals(STAGE_COMPLETE, row.endstate)
        assertEquals(1, row.questpoints)
        assertTrue(row.requirementStats.isEmpty())
        assertTrue(row.requirementQuests.isEmpty())
        val xp = row.statXpAwarded.associate { it.t0.displayName to it.t1 }
        assertEquals(mapOf("cooking" to 5000, "prayer" to 5000), xp, "tenths of the 500 xp awarded")
        val sequel = QuestRow.getRow("dbrow.quest_ragandboneman2".asRSCM())
        assertTrue(sequel.requirementQuests.any { it.rowId == row.rowId }, "the sequel needs this quest")
        assertEquals(VarpLifetime.Perm, ServerCacheManager.getVarp("varp.rag_quest".asRSCM(RSCMType.VARP))!!.scope)
    }

    @Test fun `the boiler vars are permanent and wide enough`() {
        for (name in listOf("varbit.rag_boiler", "varbit.rag_potboiler", "varbit.rag_boil_steps")) {
            val bit = ServerCacheManager.getVarbit(name.asRSCM(RSCMType.VARBIT))!!
            assertEquals(VarpLifetime.Perm, ServerCacheManager.getVarp(bit.baseVar.id)!!.scope, name)
        }
        val contents = ServerCacheManager.getVarbit("varbit.rag_potboiler".asRSCM(RSCMType.VARBIT))!!
        assertTrue((1 shl (contents.endBit - contents.startBit + 1)) > Specimen.entries.maxOf { it.id })
        val steps = ServerCacheManager.getVarbit("varbit.rag_boil_steps".asRSCM(RSCMType.VARBIT))!!
        assertTrue((1 shl (steps.endBit - steps.startBit + 1)) > PotBoiler.BOIL_STEPS)
        assertEquals(12.0, PotBoiler.BOIL_STEPS * PotBoiler.BOIL_STEP_TICKS * 0.6, 0.001, "about 12 seconds")
    }

    @Test fun `each boiler state shows its own variant`() {
        val boiler = loc(PotBoiler.BOILER)
        assertEquals("varbit.rag_boiler".asRSCM(RSCMType.VARBIT), boiler.multiVarBit)
        val transforms = checkNotNull(boiler.transforms)
        val expected = mapOf(
            BOILER_EMPTY to "loc.rag_potboiler_nologs",
            BOILER_LOGS to "loc.rag_potboiler_nopot",
            BOILER_LOADED to PotBoiler.BOILER_WITH_POT,
            BOILER_BOILING to "loc.rag_potboiler_onfire",
            BOILER_BOILED to PotBoiler.BOILER_WITH_BONE,
        )
        for ((state, name) in expected) assertEquals(name.asRSCM(RSCMType.LOC), transforms[state], name)
        assertEquals("Remove-Pot", loc(PotBoiler.BOILER_WITH_POT).actions.getOpOrNull(0))
        assertEquals("Remove-Bone", loc(PotBoiler.BOILER_WITH_BONE).actions.getOpOrNull(0))
        assertEquals("Read", loc(OddOldMan.WISH_LIST).actions.getOpOrNull(0))
    }

    @Test fun `the boiler and wish-list stand at the camp beside the Odd Old Man`() {
        assertTrue(placed.any { it.first == PotBoiler.BOILER.asRSCM(RSCMType.LOC) && it.second == PotBoiler.BOILER_TILE })
        assertTrue(placed.any { it.first == OddOldMan.WISH_LIST.asRSCM(RSCMType.LOC) && it.second == CoordGrid(3361, 3507, 0) })
        val spawns = rawSpawns()
        assertTrue(spawns.any { it.first == RagAndBoneManQuest.ODD_OLD_MAN && it.second.within(PotBoiler.BOILER_TILE, 2) })
        assertTrue(spawns.any { it.first == RagAndBoneManQuest.FORTUNATO && it.second.within(CoordGrid(3085, 3250, 0), 3) })
        assertTrue(open(CAMP), "the ::ragboneman camp tile is blocked")
        assertTrue(walks(CAMP, CoordGrid(3361, 3504, 0)), "the boiler can't be reached from the camp entrance")
    }

    @Test fun `every specimen's creatures live in the habitat the hints name`() {
        val spawns = rawSpawns()
        fun near(npc: String, x: IntRange, z: IntRange) =
            assertTrue(spawns.any { it.first == npc && it.second.x in x && it.second.z in z }, npc)
        near("npc.giantrat", 3150..3210, 3155..3200)
        near("npc.medium_frog_nodrops", 3170..3230, 3155..3200)
        near("npc.unicorn", 3280..3290, 3345..3355)
        near("npc.darkbear", 3290..3300, 3340..3350)
        near("npc.ramunsheered", 3190..3210, 3255..3280)
        near("npc.goblin_unarmed_melee_1", 3240..3265, 3220..3260)
        near("npc.monkey", 2860..2880, 3140..3160)
        near("npc.bat", 2830..2865, 9560..9580)
    }

    @Test fun `each specimen drops guaranteed during the quest from the right creatures only`() {
        val tables = dropTables()
        for (specimen in Specimen.entries) {
            val dropping = tables.filter { specimen.raw in it.text }
            assertTrue(dropping.isNotEmpty(), "nothing drops ${specimen.label}")
            for (table in dropping) {
                val entry = Regex("numerator = (\\d+)\\r?\\ndenominator = (\\d+)\\r?\\nobj = \"${Regex.escape(specimen.raw)}\"[^\\[]*")
                    .find(table.text) ?: error("${table.name} drops ${specimen.raw} oddly")
                assertEquals("1", entry.groupValues[1], table.name)
                assertEquals("1", entry.groupValues[2], table.name)
                assertTrue("quest = \"quest_ragandboneman1\"" in entry.value && "quest_mode = \"during\"" in entry.value, table.name)
                for (npc in table.npcs) {
                    val name = npc(npc).name.lowercase()
                    assertTrue(qualifies(specimen, name), "${table.name}: '$name' shouldn't drop ${specimen.label}")
                }
            }
        }
    }

    @Test fun `lookalike creatures drop no specimen`() {
        val tables = dropTables()
        for (lookalike in listOf("npc.rat", "npc.giant_frog", "npc.giant_frog_nodrops", "npc.little_frog_nodrops", "npc.sheepunsheered")) {
            for (table in tables.filter { lookalike in it.npcs }) {
                assertFalse(Specimen.entries.any { it.raw in table.text }, "$lookalike in ${table.name}")
            }
        }
        assertEquals("Big frog", npc("npc.medium_frog_nodrops").name)
        assertEquals("Giant bat", npc("npc.bat").name)
        assertFalse(npc("npc.little_frog_nodrops").name == "Big frog")
    }

    @Test fun `vinegar is only in Fortunato's unlocked stock, at a coin a jug`() {
        val locked = Inventory.create(Fortunato.WINE_SHOP)
        val unlocked = Inventory.create(Fortunato.VINEGAR_SHOP)
        assertFalse(RagAndBoneManQuest.VINEGAR in locked)
        assertTrue("obj.jug_wine" in locked)
        assertTrue(RagAndBoneManQuest.VINEGAR in unlocked)
        assertEquals(RagAndBoneManQuest.VINEGAR_PRICE, item(RagAndBoneManQuest.VINEGAR).cost)
    }

    @Test fun `every item carries the ops the scripts answer`() {
        for (specimen in Specimen.entries) {
            for (obj in listOf(specimen.raw, specimen.inVinegar, specimen.polished)) assertFalse(item(obj).stackable, obj)
            assertEquals("Empty", item(specimen.inVinegar).interfaceOptions[3], specimen.inVinegar)
        }
        assertEquals("Empty", item(RagAndBoneManQuest.VINEGAR).interfaceOptions[3])
        assertEquals("Empty", item(RagAndBoneManQuest.POT_OF_VINEGAR).interfaceOptions[3])
    }

    @Test fun `every symbol the scripts use resolves`() {
        for (seq in listOf(PotBoiler.PLACE_SEQ, PotBoiler.LIGHT_SEQ, VinegarPreparation.POUR_SEQ)) {
            assertNotNull(ServerCacheManager.getAnim(seq.asRSCM(RSCMType.SEQ)), seq)
        }
        for (synth in listOf(PotBoiler.LIGHT_SOUND, PotBoiler.BUBBLE_SOUND, PotBoiler.DONE_SOUND, VinegarPreparation.POUR_SOUND)) {
            assertTrue(synth.asRSCM(RSCMType.SYNTH) >= 0, synth)
        }
        for (spot in listOf(PotBoiler.SMOKE_SPOTANIM, PotBoiler.STEAM_SPOTANIM)) assertTrue(spot.asRSCM(RSCMType.SPOTANIM) >= 0)
        assertTrue(PotBoiler.BOIL_TIMER.asRSCM(RSCMType.TIMER) >= 0)
        assertTrue(OddOldMan.SCROLL_RESET.asRSCM(RSCMType.CLIENTSCRIPT) >= 0)
        for (obj in listOf(RagAndBoneManQuest.POT, RagAndBoneManQuest.JUG, RagAndBoneManQuest.LOGS, RagAndBoneManQuest.TINDERBOX)) {
            assertNotNull(item(obj), obj)
        }
    }

    @Test fun `every goto place is open ground with room to move`() {
        val places = RagAndBoneManCommands.PLACES
        val squares = places.values.map { MapSquareKey(it.x / 64, it.z / 64) }.toSet()
        val map = CollisionFlagMap()
        for (square in squares) {
            val group = (square.x shl 8) or square.z
            val tiles = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 0))))
            val spawns = MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 1))))
            for (level in 0..3) for (x in square.x * 64 until square.x * 64 + 64 step 8) {
                for (z in square.z * 64 until square.z * 64 + 64 step 8) map.allocateIfAbsent(x, z, level)
            }
            GameMapDecoder.putMaps(map, square, tiles)
            GameMapDecoder.putLocs(GameMapBuilder(), map, square, tiles, spawns)
        }
        val steps = org.rsmod.routefinder.StepValidator(map)
        for ((name, tile) in places) {
            assertEquals(0, map[tile.x, tile.z, tile.level] and CollisionFlag.BLOCK_WALK, "$name $tile is blocked")
            val seen = hashSetOf(tile)
            val queue = ArrayDeque(listOf(tile))
            while (queue.isNotEmpty() && seen.size < ROOM) {
                val c = queue.removeFirst()
                for (dx in -1..1) for (dz in -1..1) {
                    if ((dx == 0 && dz == 0) || !steps.canTravel(c.level, c.x, c.z, dx, dz)) continue
                    val n = c.translate(dx, dz)
                    if (MapSquareKey(n.x / 64, n.z / 64) in squares && seen.add(n)) queue += n
                }
            }
            assertTrue(seen.size >= ROOM, "$name $tile is cut off: only ${seen.size} tiles reachable")
        }
    }

    private fun qualifies(specimen: Specimen, name: String): Boolean =
        when (specimen) {
            Specimen.GIANT_RAT -> "giant" in name && "rat" in name
            Specimen.UNICORN -> "unicorn" in name
            Specimen.BEAR -> "bear" in name
            Specimen.RAM -> name == "ram"
            Specimen.GOBLIN -> "goblin" in name
            Specimen.BIG_FROG -> name == "big frog"
            Specimen.MONKEY -> "monkey" in name
            Specimen.GIANT_BAT -> name == "giant bat" || name == "albino bat"
        }

    private class Table(val name: String, val text: String, val npcs: List<String>)

    private fun dropTables(): List<Table> {
        val dir = listOf("", "../../").map { java.io.File("${it}content/drops/src/main/resources/drops/tables") }.first { it.isDirectory }
        return dir.walkTopDown().filter { it.name.endsWith(".toml") }.map { file ->
            val text = file.readText()
            val npcs = Regex("\"(npc\\.[a-z0-9_]+)\"").findAll(text.substringBefore("[[").substringBefore("[main]")).map { it.groupValues[1] }.toList()
            Table(file.name, text, npcs)
        }.toList()
    }

    private fun CoordGrid.within(other: CoordGrid, distance: Int): Boolean =
        level == other.level && kotlin.math.abs(x - other.x) <= distance && kotlin.math.abs(z - other.z) <= distance

    private fun open(tile: CoordGrid): Boolean =
        collision[tile.x, tile.z, tile.level] and (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC) == 0

    private fun walks(from: CoordGrid, to: CoordGrid): Boolean {
        val steps = org.rsmod.routefinder.StepValidator(collision)
        val seen = hashSetOf(from)
        val queue = ArrayDeque(listOf(from))
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            if (c == to) return true
            for (dx in -1..1) for (dz in -1..1) {
                if ((dx == 0 && dz == 0) || !steps.canTravel(c.level, c.x, c.z, dx, dz)) continue
                val n = c.translate(dx, dz)
                if (MapSquareKey(n.x / 64, n.z / 64) == CAMP_SQUARE && seen.add(n)) queue += n
            }
        }
        return false
    }

    private fun npc(name: String) = checkNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC))) { name }

    private fun loc(name: String) = checkNotNull(ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC))) { name }

    private fun item(name: String) = checkNotNull(ServerCacheManager.getItem(name.asRSCM(RSCMType.OBJ))) { name }

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
        val CAMP = CoordGrid(3360, 3503, 0)
        val CAMP_SQUARE = MapSquareKey(52, 54)
        const val ROOM = 100

        val collision = CollisionFlagMap()
        val placed = mutableListOf<Pair<Int, CoordGrid>>()
        lateinit var cache: dev.openrune.filesystem.Cache

        @JvmStatic @BeforeAll fun load() {
            cache = ServerCacheManager.init(240)
            val square = CAMP_SQUARE
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

        @JvmStatic @AfterAll fun close() {
            cache.close()
        }
    }
}
