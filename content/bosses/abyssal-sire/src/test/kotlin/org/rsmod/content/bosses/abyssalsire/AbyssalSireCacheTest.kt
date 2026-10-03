package org.rsmod.content.bosses.abyssalsire

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
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
import org.rsmod.api.table.slayer.SlayerTaskRow
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.routefinder.RouteFinding
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Pins the Abyssal Sire to the cache and the map: every form the fight uses, the chambers' thrones,
 * respiratory systems and tentacles against the npc spawns, the animations and graphics, the Slayer
 * task, and the walkable tiles the Sire walks to and the player is moved to.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class AbyssalSireCacheTest {

    @Test fun `every Sire form is the 425 hitpoint size 6 Abyssal Sire`() {
        for (form in SireFights.SIRE_FORMS) {
            val type = npc(form)
            assertEquals("Abyssal Sire", type.name, form)
            assertEquals(SireChamber.SIRE_SIZE, type.size, form)
            assertEquals(425, type.hitpoints, form)
        }
        assertTrue(npc(SireFights.SIRE_APOCALYPSE).defence < npc(SireFights.SIRE_AWAKE).defence, "the explosion form is easier to hit")
    }

    @Test fun `tentacles, respiratory systems and minions match the wiki`() {
        for (form in SireFights.TENTACLE_SLEEPING + SireFights.TENTACLE_ACTIVE + SireFights.TENTACLE_STUNNED) {
            assertEquals(SireChamber.TENTACLE_SIZE, npc(form).size, form)
            assertEquals("Tentacle", npc(form).name)
        }
        assertEquals("Respiratory system", npc(SireFights.LUNG).name)
        assertEquals(50, npc(SireFights.LUNG).hitpoints)
        assertEquals((0..4).map { null }, (0..4).map { npc(SireFights.LUNG_DYING).actions.getOpOrNull(it) }, "a dead vent cannot be attacked")
        assertEquals(15, npc(SireFights.SPAWN).hitpoints)
        assertEquals(50, npc(SireFights.SCION).hitpoints)
        for (dying in listOf(SireFights.SPAWN_DYING, SireFights.SCION_DYING)) assertEquals(null, npc(dying).actions.getOpOrNull(1), dying)
    }

    @Test fun `each chamber's throne, systems and tentacles are the map spawns`() {
        assertEquals(SireChamber.entries.map { it.throne }.toSet(), spawns(SireFights.SIRE_SLEEPING).toSet())
        val lungs = spawns(SireFights.LUNG).toSet()
        val tentacles = SireFights.TENTACLE_SLEEPING.flatMap(::spawns).toSet()
        for (chamber in SireChamber.entries) {
            assertEquals(4, chamber.lungs.size)
            assertTrue(lungs.containsAll(chamber.lungs), "${chamber.name} systems")
            assertEquals(6, chamber.tentacles.size)
            assertTrue(tentacles.containsAll(chamber.tentacles), "${chamber.name} tentacles")
            for (tile in chamber.lungs + chamber.tentacles + chamber.throne) assertTrue(chamber.contains(tile), "${chamber.name} holds $tile")
        }
        assertEquals(16, lungs.size)
        assertEquals(24, tentacles.size)
    }

    @Test fun `the wiki's row tiles line up with the Sire's positions`() {
        val nw = SireChamber.NorthWest
        assertEquals(CoordGrid(2979, 4844), nw.rowOne)
        assertEquals(CoordGrid(2979, 4836), nw.rowTwo)
        assertEquals(CoordGrid(2979, 4834), nw.rowThree)
        assertEquals(CoordGrid(2969, 4780), SireChamber.SouthWest.rowOne)
        assertEquals(CoordGrid(3104, 4836), SireChamber.NorthEast.rowTwo)
        assertEquals(CoordGrid(3109, 4770), SireChamber.SouthEast.rowThree)
    }

    @Test fun `the Sire's stops and the player's rows are open, and row 3 escapes the explosion`() {
        for (chamber in SireChamber.entries) {
            for (dx in 0 until SireChamber.SIRE_SIZE) for (dz in 0 until SireChamber.SIRE_SIZE) {
                val tile = chamber.centreSpot.translate(dx, dz)
                assertFalse(blocked(tile), "${chamber.name}: phase 3 Sire tile $tile")
            }
            for (dx in 1 until SireChamber.SIRE_SIZE - 1) {
                val tile = chamber.meleeSpot.translate(dx, 0)
                assertFalse(blocked(tile), "${chamber.name}: phase 2 Sire's front tile $tile, at the alcove's mouth")
            }
            for (row in listOf(chamber.rowOne, chamber.rowTwo, chamber.rowThree)) assertFalse(blocked(row), "${chamber.name} $row")
            assertEquals(1, distance(chamber.meleeSpot, chamber.rowOne), "row 1 is in melee reach")
            assertTrue(distance(chamber.centreSpot, chamber.rowTwo) <= SireFights.EXPLOSION_REACH, "row 2 is in the blast")
            assertTrue(distance(chamber.centreSpot, chamber.rowThree) > SireFights.EXPLOSION_REACH, "row 3 escapes it")
        }
    }

    @Test fun `phase 1 spawns land on open ground with a way into the arena`() {
        val routes = RouteFinding(collision)
        for (chamber in SireChamber.entries) {
            for (tile in chamber.alcoveFront) {
                assertFalse(blocked(tile), "${chamber.name}: spawn tile $tile")
                val route =
                    routes.findRoute(
                        level = tile.level,
                        srcX = tile.x,
                        srcZ = tile.z,
                        destX = chamber.rowThree.x,
                        destZ = chamber.rowThree.z,
                        moveNear = false,
                    )
                assertTrue(route.success, "${chamber.name}: spawn tile $tile reaches the arena")
            }
            for (stop in listOf(chamber.meleeSpot, chamber.centreSpot)) {
                val open = SireFights.spawnArea(stop).count { !blocked(it) }
                assertTrue(open >= SireFights.spawnArea(stop).size / 2, "${chamber.name}: open spawn tiles in front of $stop")
            }
        }
    }

    @Test fun `the centre lane is clear of every tentacle`() {
        for (chamber in SireChamber.entries) {
            for (row in listOf(chamber.rowOne, chamber.rowTwo, chamber.rowThree)) {
                for (tentacle in chamber.tentacles) {
                    val inside = row.x in tentacle.x until tentacle.x + SireChamber.TENTACLE_SIZE &&
                        row.z in tentacle.z until tentacle.z + SireChamber.TENTACLE_SIZE
                    assertFalse(inside, "${chamber.name}: $row under the tentacle at $tentacle")
                }
            }
        }
    }

    @Test fun `the animations, graphics and spells the fight names exist`() {
        val seqs =
            listOf(
                "sire_waking", "sire_attack_miasma", "sire_attack_miasma_two", "sire_attack_spawns", "sire_attack_spawns_two",
                "sire_attack_teleport_player", "sire_panic_mode", "sire_apocalypse", "sire_death", "sire_right_hook",
                "sire_attack_right_whip", "sire_attack_double_whips", "abyssal_tentacle_waking", "abyssal_tentacle_waking_reversed",
                "abyssal_tentacle_stunned", "abyssal_tentacle_unstunned", "abyssal_tentacle_attack", "nexus_lung_death",
                "abyssal_scion_spawn", "abyssal_spawn_death", "abyssal_scion_death", "abyssal_spawn_attack",
                "abyssal_scion_attack_melee", "abyssal_scion_attack_ranged",
            )
        for (seq in seqs) assertNotNull(ServerCacheManager.getAnim("seq.$seq".asRSCM(RSCMType.SEQ)), seq)
        for (spotanim in listOf("abyssal_miasma_spotanim", "abyssal_spawn_projanim")) "spotanim.$spotanim".asRSCM(RSCMType.SPOTANIM)
        for (spell in listOf("52_shadow_rush", "64_shadow_burst", "76_shadow_blitz", "88_shadow_barrage")) {
            assertNotNull(ServerCacheManager.getItem("obj.$spell".asRSCM(RSCMType.OBJ)), spell)
        }
    }

    @Test fun `the Sire counts for abyssal demon tasks`() {
        val task = npc(SireFights.SIRE_SLEEPING).paramOrNull(BaseParams.slayer_task_id)
        assertNotNull(task)
        assertEquals("abyssal demons", SlayerTaskRow.all().single { it.id == task }.nameLowercase)
    }

    private fun blocked(tile: CoordGrid): Boolean =
        collision[tile.x, tile.z, tile.level] and (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC) != 0

    private fun distance(sire: CoordGrid, tile: CoordGrid): Int {
        val dx = maxOf(sire.x - tile.x, tile.x - (sire.x + SireChamber.SIRE_SIZE - 1), 0)
        val dz = maxOf(sire.z - tile.z, tile.z - (sire.z + SireChamber.SIRE_SIZE - 1), 0)
        return maxOf(dx, dz)
    }

    private fun npc(name: String) = checkNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC))) { name }

    private companion object {
        /** The Abyssal Nexus's four chambers. */
        val SQUARES = listOf(46 to 74, 46 to 75, 48 to 74, 48 to 75)

        val collision = CollisionFlagMap()
        lateinit var cache: dev.openrune.filesystem.Cache

        fun file(path: String): java.io.File =
            listOf("", "../../", "../../../").map { java.io.File("$it$path") }.first { it.exists() }

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
                val tileData = checkNotNull(cache.data(MAPS, group, 0)) { "map $sx,$sz" }
                val locData = checkNotNull(cache.data(MAPS, group, 1)) { "locs $sx,$sz" }
                val square = MapSquareKey(sx, sz)
                for (level in 0..3) for (x in sx * 64 until sx * 64 + 64 step 8) {
                    for (z in sz * 64 until sz * 64 + 64 step 8) collision.allocateIfAbsent(x, z, level)
                }
                val tiles = MapTileDecoder.decode(InlineByteBuf(tileData))
                GameMapDecoder.putMaps(collision, square, tiles)
                GameMapDecoder.putLocs(GameMapBuilder(), collision, square, tiles, MapLocListDecoder.decode(InlineByteBuf(locData)))
            }
        }

        @JvmStatic @AfterAll fun close() {
            cache.close()
        }
    }
}
