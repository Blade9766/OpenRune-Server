package org.rsmod.content.quest.area.falador.knightssword

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
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
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.table.QuestRow
import org.rsmod.api.table.mining.MiningRocksRow
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.BLURITE_ORE
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.ICE_DUNGEON_TRAPDOOR
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.PORTRAIT
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.SIR_VYVIN
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.falador.knightssword.VyvinsRoom.Companion.CUPBOARD
import org.rsmod.content.quest.area.falador.knightssword.VyvinsRoom.Companion.CUPBOARD_SHUT
import org.rsmod.content.quest.area.falador.knightssword.VyvinsRoom.Post
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Pins the cache and map facts The Knight's Sword is written against: the quest row, the
 * server-only flag, Sir Vyvin's routine against the real walls of his room (he sees the cupboard
 * from his desk and cannot from the window), and that every blurite deposit can be reached from
 * the ladder without stepping next to an ice warrior or ice giant post, so no kill is needed.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class KnightsSwordCacheTest {

    @Test fun `the quest row matches the stages and requirements`() {
        val row = QuestRow.getRow("dbrow.${KnightsSwordQuest.QUEST_KEY}".asRSCM())
        assertEquals(STAGE_COMPLETE, row.endstate)
        assertEquals(1, row.questpoints)
        assertEquals(mapOf("mining" to 10), row.requirementStats.associate { it.t0.displayName to it.t1 })
        assertTrue(row.requirementQuests.isEmpty())
        assertEquals(listOf("npc.squire".asRSCM(RSCMType.NPC)), row.startnpc.map { it.id })
    }

    @Test fun `the forged flag is a saved server-only bit of its own varp`() {
        val varp = "varp.knightssword_state".asRSCM(RSCMType.VARP)
        assertEquals(VarpLifetime.Perm, ServerCacheManager.getVarp(varp)!!.scope)
        val flag = checkNotNull(ServerCacheManager.getVarbit("varbit.knightssword_sword_forged".asRSCM(RSCMType.VARBIT)))
        assertEquals(varp, flag.baseVar.id)
        assertEquals(VarpLifetime.Perm, ServerCacheManager.getVarp("varp.squire".asRSCM(RSCMType.VARP))!!.scope)
    }

    @Test fun `sir vyvin keeps to his routine and the portrait can be looked at`() {
        val vyvin = checkNotNull(ServerCacheManager.getNpc(SIR_VYVIN.asRSCM(RSCMType.NPC)))
        assertEquals(0, vyvin.wanderRange)
        assertEquals(NpcMode.None, vyvin.defaultMode)
        val portrait = checkNotNull(ServerCacheManager.getItem(PORTRAIT.asRSCM(RSCMType.OBJ)))
        assertEquals("Look-at", portrait.interfaceOptions[0])
    }

    @Test fun `blurite needs level 10 mining and gives blurite ore`() {
        val blurite = listOf("loc.blurite_rock_1", "loc.blurite_rock_2").map { it.asRSCM(RSCMType.LOC) }
        val rows = MiningRocksRow.all().filter { row -> row.rockObject.any { it.id in blurite } }
        assertEquals(1, rows.size)
        assertEquals(10, rows.single().level)
        assertEquals(BLURITE_ORE.asRSCM(RSCMType.OBJ), rows.single().oreItem?.id)
    }

    @Test fun `every quest loc stands where the scripts expect`() {
        assertLoc(CUPBOARD_SHUT, CUPBOARD)
        assertLoc("loc.fai_trapdoor", ICE_DUNGEON_TRAPDOOR)
        assertLoc("loc.ladder_from_cellar", CoordGrid(3008, 9550, 0))
        assertEquals(4, placed.count { it.first in BLURITE_ROCKS })
    }

    @Test fun `sir vyvin walks between his posts`() {
        for (post in Post.entries) assertTrue(open(post.tile), "$post")
        assertTrue(walks(Post.DESK.tile, Post.WINDOW.tile, level = 2))
    }

    @Test fun `from his desk sir vyvin sees the cupboard and from the window he cannot`() {
        val approach = cupboardApproach()
        assertTrue(approach.isNotEmpty())
        for (tile in approach) {
            assertTrue(VyvinsRoom.sees(rays, Post.DESK.tile, Post.DESK.facing, tile), "desk misses $tile")
            assertFalse(VyvinsRoom.sees(rays, Post.WINDOW.tile, Post.WINDOW.facing, tile), "window sees $tile")
        }
    }

    @Test fun `walls hide the cupboard from anyone looking in from the landing`() {
        val landing = CoordGrid(2983, 3338, 2)
        for (tile in cupboardApproach()) {
            assertFalse(VyvinsRoom.sees(rays, landing, Direction.South, tile), "seen through the wall at $tile")
        }
    }

    @Test fun `the trapdoor lands beside the ladder on open ground`() {
        val landing = ICE_DUNGEON_TRAPDOOR.translateZ(1).translateZ(DUNGEON_OFFSET)
        assertTrue(open(landing), "$landing")
    }

    @Test fun `the blurite can be reached while keeping clear of every ice warrior and giant`() {
        val landing = ICE_DUNGEON_TRAPDOOR.translateZ(1).translateZ(DUNGEON_OFFSET)
        val guards = guardSpawns()
        assertTrue(guards.size >= 10, "only ${guards.size} guards")
        val targets = BLURITE.flatMap { rock -> rock.neighbours().filter(::open) }.toSet()
        assertTrue(targets.isNotEmpty())
        assertTrue(reaches(landing, targets, clearance = SAFE_CLEARANCE, guards = guards), "no path $SAFE_CLEARANCE tiles from the guards")
        for (rock in BLURITE) {
            val beside = rock.neighbours().filter(::open).toSet()
            assertTrue(reaches(landing, beside, clearance = SAFE_CLEARANCE, guards = guards), "$rock")
        }
    }

    private fun cupboardApproach(): List<CoordGrid> {
        val type = checkNotNull(ServerCacheManager.getObject(CUPBOARD_SHUT.asRSCM(RSCMType.LOC)))
        val angle = placedAngle(CUPBOARD_SHUT, CUPBOARD)
        val (w, l) = if (angle % 2 == 1) type.length to type.width else type.width to type.length
        val footprint = (0 until w).flatMap { dx -> (0 until l).map { dz -> CUPBOARD.translate(dx, dz) } }.toSet()
        return footprint.flatMap { it.neighbours() }
            .filter { it !in footprint && open(it) && walks(Post.DESK.tile, it, level = 2) }
            .distinct()
    }

    private fun CoordGrid.neighbours(): List<CoordGrid> =
        listOf(translateX(1), translateX(-1), translateZ(1), translateZ(-1))

    private fun open(tile: CoordGrid): Boolean =
        collision[tile.x, tile.z, tile.level] and (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC) == 0

    private fun walks(from: CoordGrid, to: CoordGrid, level: Int): Boolean = reaches(from, setOf(to), 0, emptyList(), level)

    private fun reaches(
        from: CoordGrid,
        targets: Set<CoordGrid>,
        clearance: Int,
        guards: List<CoordGrid>,
        level: Int = 0,
    ): Boolean {
        val steps = StepValidator(collision)
        val seen = hashSetOf(from)
        val queue = ArrayDeque(listOf(from))
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            if (c in targets) return true
            for (dx in -1..1) for (dz in -1..1) {
                if ((dx == 0 && dz == 0) || !steps.canTravel(level, c.x, c.z, dx, dz)) continue
                val n = c.translate(dx, dz)
                if (n.x / 64 !in LOADED_X || guards.any { it.chebyshevDistance(n) < clearance }) continue
                if (seen.add(n)) queue += n
            }
        }
        return false
    }

    private fun guardSpawns(): List<CoordGrid> {
        val dir = listOf("", "../../").map { java.io.File("${it}.data/raw-cache/map/npcs") }.first { it.isDirectory }
        val text = dir.resolve("asgarnia_ice_dungeon.toml").readText()
        return SPAWN.findAll(text).filter { "icewarrior" in it.groupValues[1] || "icegiant" in it.groupValues[1] }
            .map { m ->
                val (level, mx, mz, lx, lz) = m.groupValues[2].split("_").map(String::toInt)
                CoordGrid(mx * 64 + lx, mz * 64 + lz, level)
            }.toList()
    }

    private fun placedAngle(name: String, at: CoordGrid): Int {
        val id = name.asRSCM(RSCMType.LOC)
        return placed.first { it.first == id && it.second == at }.third
    }

    private fun assertLoc(name: String, at: CoordGrid) {
        val id = name.asRSCM(RSCMType.LOC)
        assertNotNull(placed.firstOrNull { it.first == id && it.second == at }, "$name is not at $at")
    }

    private companion object {
        const val DUNGEON_OFFSET = 6400
        const val SAFE_CLEARANCE = 2

        val SPAWN = Regex("npc = \"(npc[.][a-z0-9_]+)\"\\s*\\ncoords = \"([0-9_]+)\"")
        val LOADED_X = 46..47
        val BLURITE_ROCKS by lazy { listOf("loc.blurite_rock_1", "loc.blurite_rock_2").map { it.asRSCM(RSCMType.LOC) } }

        val collision = CollisionFlagMap()
        val rays = RayCastValidator(collision)
        val placed = mutableListOf<Triple<Int, CoordGrid, Int>>()
        val BLURITE = mutableListOf<CoordGrid>()
        lateinit var cache: dev.openrune.filesystem.Cache

        @JvmStatic @BeforeAll fun load() {
            cache = ServerCacheManager.init(240)
            for ((mx, mz) in listOf(46 to 52, 46 to 49, 47 to 49, 46 to 148, 47 to 148, 46 to 149, 47 to 149, 46 to 150, 47 to 150)) {
                val square = MapSquareKey(mx, mz)
                val group = (mx shl 8) or mz
                val tiles = MapTileDecoder.decode(InlineByteBuf(cache.data(MAPS, group, 0) ?: continue))
                val spawns = MapLocListDecoder.decode(InlineByteBuf(cache.data(MAPS, group, 1) ?: continue))
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
                        val loc = LocEntity(entry.intValue)
                        placed += Triple(loc.id, base.translate(key.x, key.z), loc.angle)
                    }
                }
            }
            BLURITE += placed.filter { it.first in BLURITE_ROCKS }.map { it.second }
        }

        @JvmStatic @AfterAll fun close() {
            cache.close()
        }
    }
}
