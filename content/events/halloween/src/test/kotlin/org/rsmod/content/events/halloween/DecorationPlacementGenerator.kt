package org.rsmod.content.events.halloween

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
import java.io.File
import kotlin.random.Random
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * One-off generator for `HalloweenDecorationsTable.kt`: picks outdoor tiles in each town from the
 * real map collision and writes the pack source. Run with `HALLOWEEN_DECOR_OUT=<file>`.
 */
class DecorationPlacementGenerator {
    private data class Town(val key: String, val x1: Int, val z1: Int, val x2: Int, val z2: Int, val count: Int)

    private data class Placed(val id: Int, val x: Int, val z: Int, val shape: Int)

    @Test
    fun generate() {
        val out = System.getenv("HALLOWEEN_DECOR_OUT")
        assumeTrue(out != null)
        val cache = ServerCacheManager.init(241)
        val rows = StringBuilder()
        val report = StringBuilder()
        for (town in TOWNS) {
            val collision = CollisionFlagMap()
            val placed = mutableListOf<Placed>()
            val squares = mutableSetOf<MapSquareKey>()
            for (x in town.x1 - 8..town.x2 + 8 step 8) for (z in town.z1 - 8..town.z2 + 8 step 8) {
                squares += MapSquareKey(x shr 6, z shr 6)
            }
            for (square in squares) {
                val group = (square.x shl 8) or square.z
                val tilesData = cache.data(MAPS, group, 0) ?: continue
                val locData = cache.data(MAPS, group, 1) ?: continue
                val tiles = MapTileDecoder.decode(InlineByteBuf(tilesData))
                val spawns = MapLocListDecoder.decode(InlineByteBuf(locData))
                for (level in 0..3) for (x in square.x * 64 until square.x * 64 + 64 step 8) {
                    for (z in square.z * 64 until square.z * 64 + 64 step 8) {
                        collision.allocateIfAbsent(x, z, level)
                    }
                }
                val builder = GameMapBuilder()
                GameMapDecoder.putMaps(collision, square, tiles)
                GameMapDecoder.putLocs(builder, collision, square, tiles, spawns)
                for ((packed, zone) in builder.zoneBuilders) {
                    val base = ZoneKey(packed).toCoords()
                    if (base.level != 0) continue
                    for (entry in zone.build().byte2IntEntrySet()) {
                        val key = LocZoneKey(entry.byteKey)
                        val loc = LocEntity(entry.intValue)
                        placed += Placed(loc.id, base.x + key.x, base.z + key.z, loc.shape)
                    }
                }
            }
            val avoid = mutableSetOf<Long>()
            for (p in placed) {
                val name = ServerCacheManager.getObject(p.id)?.name?.lowercase() ?: continue
                val busy = AVOID_WORDS.any { it in name }
                val r = if (busy) 2 else 0
                if (busy || p.shape in 10..11) {
                    for (dx in -r..r) for (dz in -r..r) avoid += key(p.x + dx, p.z + dz)
                }
            }
            fun flag(x: Int, z: Int) = collision[x, z, 0]
            fun open(x: Int, z: Int) =
                key(x, z) !in avoid && flag(x, z) and (0xFF or BLOCKING or CollisionFlag.ROOF) == 0
            fun walkable(x: Int, z: Int) = flag(x, z) and BLOCKING == 0
            fun wallside(x: Int, z: Int) =
                (-1..1).any { dx -> (-1..1).any { dz -> !(dx == 0 && dz == 0) && !walkable(x + dx, z + dz) } }
            fun keepsConnected(x: Int, z: Int): Boolean {
                val ring = listOf(x + 1 to z, x - 1 to z, x to z + 1, x to z - 1).filter { (a, b) -> walkable(a, b) }
                if (ring.size <= 1) return ring.size == 1
                val seen = mutableSetOf(ring.first())
                val queue = ArrayDeque(listOf(ring.first()))
                while (queue.isNotEmpty()) {
                    val (a, b) = queue.removeFirst()
                    for ((na, nb) in listOf(a + 1 to b, a - 1 to b, a to b + 1, a to b - 1)) {
                        if (na == x && nb == z) continue
                        if (kotlin.math.abs(na - x) > 3 || kotlin.math.abs(nb - z) > 3) continue
                        if (!walkable(na, nb) || !seen.add(na to nb)) continue
                        queue += na to nb
                    }
                }
                return ring.all { it in seen }
            }
            val random = Random(town.key.hashCode())
            val candidates =
                (town.x1..town.x2).flatMap { x -> (town.z1..town.z2).map { z -> x to z } }
                    .filter { (x, z) -> open(x, z) }
                    .shuffled(random)
                    .sortedByDescending { (x, z) -> wallside(x, z) }
            val chosen = mutableListOf<Triple<Int, Int, String>>()
            for ((x, z) in candidates) {
                if (chosen.size >= town.count) break
                if (chosen.any { kotlin.math.max(kotlin.math.abs(it.first - x), kotlin.math.abs(it.second - z)) < SPACING }) continue
                val solid = wallside(x, z) && keepsConnected(x, z) && random.nextInt(3) != 0
                val loc = if (solid) SOLID.random(random) else LIGHT.random(random)
                chosen += Triple(x, z, loc)
            }
            report.appendLine("${town.key}: ${chosen.size}/${town.count} (${candidates.size} candidates)")
            val locs = chosen.joinToString(", ") { "\"loc.${it.third}\"" }
            val coords = chosen.joinToString(", ") { "CoordGrid(${it.first}, ${it.second}, 0)" }
            val angles = chosen.joinToString(", ") { random.nextInt(4).toString() }
            rows.appendLine("            town(")
            rows.appendLine("                \"${town.key}\",")
            rows.appendLine("                locs = listOf($locs),")
            rows.appendLine("                coords = listOf($coords),")
            rows.appendLine("                angles = listOf($angles),")
            rows.appendLine("            )")
        }
        File(out!!).writeText(rows.toString())
        File("$out.report").writeText(report.toString())
        cache.close()
    }

    private companion object {
        const val BLOCKING = CollisionFlag.LOC or CollisionFlag.BLOCK_WALK or CollisionFlag.GROUND_DECOR
        const val SPACING = 5

        fun key(x: Int, z: Int): Long = (x.toLong() shl 32) or z.toLong()

        val AVOID_WORDS =
            listOf("door", "gate", "stair", "ladder", "booth", "bank", "portal", "trapdoor", "anvil", "range", "furnace")

        val SOLID =
            listOf(
                "hw22_lantern_pumpkin01_lit01_large01",
                "hw22_lantern_pumpkin01_lit01_large01_red01",
                "hw22_lantern_pumpkin01_lit01_large01_yellow01",
                "hw22_lantern_pumpkin01_lit01_large01_white01",
                "hw22_decoration_reaper01_scarecrow01",
                "hw22_decoration_skeleton01_gravestone01",
                "hw22_bundle_hay01_bale01",
                "hw22_bundle_hay01_stack01",
                "hw22_lantern_turnip01_lit01_large01",
                "hw22_lantern_turnip01_lit01_medium01",
            )

        val LIGHT =
            listOf(
                "hw22_lantern_pumpkin01_lit01_small02",
                "hw22_lantern_pumpkin01_lit01_small02_red01",
                "hw22_lantern_pumpkin01_lit01_small02_blue01",
                "hw22_lantern_pumpkin01_lit01_small02_yellow01",
                "hw22_lantern_pumpkin01_alt01_lit01_small02",
                "hw22_lantern_pumpkin01_lit01_small03",
                "hw22_lantern_turnip01_lit01_small01",
                "hw22_decoration_candy01_bowl01",
            )

        val TOWNS =
            listOf(
                Town("lumbridge", 3205, 3200, 3245, 3240, 12),
                Town("varrock", 3175, 3375, 3290, 3505, 18),
                Town("falador", 2940, 3320, 3065, 3395, 16),
                Town("draynor", 3075, 3235, 3115, 3280, 10),
                Town("edgeville", 3075, 3480, 3110, 3515, 8),
                Town("al_kharid", 3265, 3150, 3320, 3215, 12),
                Town("port_sarim", 3010, 3185, 3060, 3260, 10),
                Town("rimmington", 2945, 3195, 2985, 3230, 8),
                Town("barbarian_village", 3070, 3405, 3100, 3440, 8),
                Town("taverley", 2875, 3405, 2935, 3460, 10),
                Town("burthorpe", 2885, 3525, 2935, 3565, 8),
                Town("catherby", 2795, 3415, 2830, 3455, 8),
                Town("seers_village", 2690, 3460, 2745, 3505, 10),
                Town("camelot", 2745, 3470, 2775, 3500, 6),
                Town("ardougne", 2610, 3280, 2680, 3340, 15),
                Town("yanille", 2535, 3075, 2620, 3110, 10),
            )
    }
}
