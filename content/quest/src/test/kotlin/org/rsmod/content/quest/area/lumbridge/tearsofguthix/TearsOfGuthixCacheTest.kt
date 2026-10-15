package org.rsmod.content.quest.area.lumbridge.tearsofguthix

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.table.QuestRow
import org.rsmod.content.quest.area.lumbridge.tearsofguthix.TearsOfGuthixQuest.Companion.QP_REQ
import org.rsmod.content.quest.area.lumbridge.tearsofguthix.TearsOfGuthixQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.lumbridge.tearsofguthix.TearsOfGuthixQuest.Companion.STAGE_STARTED
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/** Pins the cache facts Tears of Guthix is written against. */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class TearsOfGuthixCacheTest {
    @Test fun `the quest row matches the stages and requirements`() {
        val row = QuestRow.getRow("dbrow.${TearsOfGuthixQuest.QUEST_KEY}".asRSCM())
        assertEquals(STAGE_COMPLETE, row.endstate)
        assertEquals(1, row.questpoints)
        assertEquals(QP_REQ, row.requirementQuestpoints)
    }

    @Test fun `the minigame varbits share the quest varp`() {
        val stage = varbit("varbit.tog_juna_bowl")
        assertEquals("varp.tog_minigame".asRSCM(RSCMType.VARP), stage.varp)
        assertTrue((1 shl (stage.endBit - stage.startBit + 1)) > STAGE_COMPLETE)
        for (name in listOf("varbit.tog_juna_stories", "varbit.tog_minigame_collecting", "varbit.tog_tears_collected", "varbit.tog_qp_before_return")) {
            assertEquals(stage.varp, varbit(name).varp, name)
        }
        assertTrue(varbit("varbit.tog_countdown").let { it.endBit - it.startBit } >= 9)
    }

    @Test fun `Juna gains her Story op and the light creatures their Attract op with the stage`() {
        val juna = loc(TearsOfGuthixQuest.JUNA)
        assertEquals("varbit.tog_juna_bowl".asRSCM(RSCMType.VARBIT), juna.multiVarBit)
        val transforms = checkNotNull(juna.transforms)
        assertEquals("loc.tog_juna_1op".asRSCM(RSCMType.LOC), transforms[STAGE_STARTED])
        assertEquals("loc.tog_juna_2ops".asRSCM(RSCMType.LOC), transforms[STAGE_COMPLETE])
        assertEquals("Story", loc("loc.tog_juna_2ops").actions.getOpOrNull(1))
        val creature = checkNotNull(ServerCacheManager.getNpc(LuxGrotto.LIGHT_CREATURE.asRSCM(RSCMType.NPC)))
        assertEquals("npc.tog_light_creature_noop".asRSCM(RSCMType.NPC), creature.multiNpc[0].toInt() and 0xFFFF)
        assertEquals("Attract", ServerCacheManager.getNpc("npc.tog_light_creature_op".asRSCM(RSCMType.NPC))?.actions?.getOpOrNull(0))
    }

    @Test fun `the bowl is wielded and the lit lantern extinguished from the first op`() {
        val bowl = checkNotNull(ServerCacheManager.getItem(TearsOfGuthixQuest.STONE_BOWL.asRSCM(RSCMType.OBJ)))
        assertEquals(3, bowl.wearpos1)
        val lit = checkNotNull(ServerCacheManager.getItem(TearsOfGuthixQuest.LIT_LANTERN.asRSCM(RSCMType.OBJ)))
        assertEquals("Extinguish", lit.interfaceOptions[0])
    }

    @Test fun `the weeping walls stand where the streams paint them`() {
        val walls = placed("loc.tog_weepingwall")
        for (wall in TearStreams.WALLS) {
            assertTrue(wall.coords in walls, "${wall.coords}")
            val decor = placedAll(wall.coords)
            assertTrue(decor.any { it.first == wall.decor(wall.initial).asRSCM(RSCMType.LOC) && it.second == wall.angle.id }, "${wall.coords}")
            for (colour in TearStreams.Colour.entries) wall.decor(colour).asRSCM(RSCMType.LOC)
        }
    }

    @Test fun `every landing is open and on the side of the chasm it should be`() {
        val north = reach(CoordGrid(3219, 9532, 2))
        val juna = reach(TearsCave.CAVE_EXIT)
        val south = reach(CoordGrid(3222, 9495, 2))
        assertTrue(LuxGrotto.NORTH_LANDING in north)
        assertTrue(LuxGrotto.SOUTH_LANDING in south)
        assertTrue(LuxGrotto.SOUTH_LANDING !in north && LuxGrotto.SOUTH_LANDING !in juna)
        assertTrue(CoordGrid(3220, 9497, 2) in south, "next to the magical rocks")
        assertTrue(open(TearsCave.CHAMBER_ENTRY))
        assertTrue(TearsCave.inChamber(TearsCave.CHAMBER_ENTRY))
        assertTrue(!TearsCave.inChamber(TearsCave.CAVE_EXIT))
    }

    private fun varbit(name: String) = checkNotNull(ServerCacheManager.getVarbit(name.asRSCM(RSCMType.VARBIT))) { name }

    private fun loc(name: String) = checkNotNull(ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC))) { name }

    private fun placed(name: String): Set<CoordGrid> {
        val id = name.asRSCM(RSCMType.LOC)
        return spawns.filter { it.first == id }.map { it.second }.toSet()
    }

    private fun placedAll(at: CoordGrid): List<Pair<Int, Int>> =
        spawns.filter { it.second == at }.map { it.first to it.third }

    private fun open(tile: CoordGrid): Boolean =
        collision[tile.x, tile.z, tile.level] and (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC) == 0

    private fun reach(from: CoordGrid): Set<CoordGrid> {
        val steps = StepValidator(collision)
        val seen = hashSetOf(from)
        val queue = ArrayDeque(listOf(from))
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            for (dx in -1..1) for (dz in -1..1) {
                if ((dx == 0 && dz == 0) || !steps.canTravel(c.level, c.x, c.z, dx, dz)) continue
                val n = c.translate(dx, dz)
                if (n.x !in 3190..3270 || n.z !in 9470..9545) continue
                if (seen.add(n)) queue += n
            }
        }
        return seen
    }

    private companion object {
        val collision = CollisionFlagMap()
        val spawns = mutableListOf<Triple<Int, CoordGrid, Int>>()
        lateinit var cache: dev.openrune.filesystem.Cache

        @JvmStatic @BeforeAll fun load() {
            cache = ServerCacheManager.init(240)
            for (sx in 49..51) for (sz in 147..149) {
                val group = (sx shl 8) or sz
                val tileData = cache.data(MAPS, group, 0) ?: continue
                val locData = cache.data(MAPS, group, 1) ?: continue
                val square = MapSquareKey(sx, sz)
                for (level in 0..3) for (x in sx * 64 until sx * 64 + 64 step 8) {
                    for (z in sz * 64 until sz * 64 + 64 step 8) collision.allocateIfAbsent(x, z, level)
                }
                val tiles = MapTileDecoder.decode(InlineByteBuf(tileData))
                val locs = MapLocListDecoder.decode(InlineByteBuf(locData))
                GameMapDecoder.putMaps(collision, square, tiles)
                GameMapDecoder.putLocs(GameMapBuilder(), collision, square, tiles, locs)
                for (spawn in locs.spawns.map(::MapLocDefinition)) {
                    val coords = square.toCoords(spawn.level).translate(spawn.localX, spawn.localZ)
                    spawns += Triple(spawn.id, coords, spawn.angle)
                }
            }
        }

        @JvmStatic @AfterAll fun close() {
            cache.close()
        }
    }
}
