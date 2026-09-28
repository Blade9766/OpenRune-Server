package org.rsmod.content.skills.hunter.traps

import org.rsmod.game.map.Direction

data class TrapLoot(val obj: String, val min: Int, val max: Int = min)

/**
 * A creature that can be caught in a [TrapKind]. [low]/[high] are the catch-rate endpoints out of
 * 256 at level 1 and 99, as published by Jagex for each creature.
 */
enum class TrapPrey(
    val npc: String,
    val kind: TrapKind,
    val displayName: String,
    val level: Int,
    val xp: Double,
    val low: Int,
    val high: Int,
    val trappingLocs: Map<Direction, String>,
    val fullLoc: String,
    val loot: List<TrapLoot>,
    val escapeAnim: String? = null,
) {
    CrimsonSwift(
        npc = "npc.hunting_bird_jungle",
        kind = TrapKind.BirdSnare,
        displayName = "crimson swift",
        level = 1,
        xp = 34.0,
        low = 100,
        high = 420,
        trappingLocs = bird("loc.hunting_ojibway_trap_trapping_jungle"),
        fullLoc = "loc.hunting_ojibway_trap_full_jungle",
        loot = birdLoot("obj.hunting_jungle_feather"),
    ),
    GoldenWarbler(
        npc = "npc.hunting_bird_desert",
        kind = TrapKind.BirdSnare,
        displayName = "golden warbler",
        level = 5,
        xp = 47.0,
        low = 92,
        high = 400,
        trappingLocs = bird("loc.hunting_ojibway_trap_trapping_desert"),
        fullLoc = "loc.hunting_ojibway_trap_full_desert",
        loot = birdLoot("obj.hunting_desert_feather"),
    ),
    CopperLongtail(
        npc = "npc.hunting_bird_woodland",
        kind = TrapKind.BirdSnare,
        displayName = "copper longtail",
        level = 9,
        xp = 61.2,
        low = 85,
        high = 390,
        trappingLocs = bird("loc.hunting_ojibway_trap_trapping_woodland"),
        fullLoc = "loc.hunting_ojibway_trap_full_woodland",
        loot = birdLoot("obj.hunting_woodland_feather"),
    ),
    CeruleanTwitch(
        npc = "npc.hunting_bird_polar",
        kind = TrapKind.BirdSnare,
        displayName = "cerulean twitch",
        level = 11,
        xp = 64.67,
        low = 82,
        high = 380,
        trappingLocs = bird("loc.hunting_ojibway_trap_trapping_polar"),
        fullLoc = "loc.hunting_ojibway_trap_full_polar",
        loot = birdLoot("obj.hunting_polar_feather"),
    ),
    TropicalWagtail(
        npc = "npc.multicoloured_bird",
        kind = TrapKind.BirdSnare,
        displayName = "tropical wagtail",
        level = 19,
        xp = 95.0,
        low = 75,
        high = 370,
        trappingLocs = bird("loc.hunting_ojibway_trap_trapping_coloured"),
        fullLoc = "loc.hunting_ojibway_trap_full_coloured",
        loot = birdLoot("obj.hunting_stripy_bird_feather"),
    ),
    Ferret(
        npc = "npc.hunting_ferret",
        kind = TrapKind.BoxTrap,
        displayName = "ferret",
        level = 27,
        xp = 115.0,
        low = 60,
        high = 300,
        trappingLocs = box("loc.hunting_boxtrap_trapping_ferret"),
        fullLoc = "loc.hunting_boxtrap_full_ferret",
        loot = listOf(TrapLoot("obj.hunting_ferret", 1)),
        escapeAnim = "seq.hunting_ferret_backoff",
    ),
    Chinchompa(
        npc = "npc.hunting_chinchompa",
        kind = TrapKind.BoxTrap,
        displayName = "chinchompa",
        level = 53,
        xp = 198.4,
        low = 6,
        high = 268,
        trappingLocs = box("loc.hunting_boxtrap_trapping_chinchompa"),
        fullLoc = "loc.hunting_boxtrap_full_chinchompa",
        loot = listOf(TrapLoot("obj.chinchompa_captured", 1)),
        escapeAnim = "seq.hunting_chinchompa_backoff",
    ),
    CarnivorousChinchompa(
        npc = "npc.hunting_chinchompa_big",
        kind = TrapKind.BoxTrap,
        displayName = "carnivorous chinchompa",
        level = 63,
        xp = 265.0,
        low = -78,
        high = 228,
        trappingLocs = box("loc.hunting_boxtrap_trapping_chinchompa_big"),
        fullLoc = "loc.hunting_boxtrap_full_chinchompa_big",
        loot = listOf(TrapLoot("obj.chinchompa_big_captured", 1)),
        escapeAnim = "seq.hunting_chinchompa_backoff",
    ),
    BlackChinchompa(
        npc = "npc.hunting_chinchompa_black",
        kind = TrapKind.BoxTrap,
        displayName = "black chinchompa",
        level = 73,
        xp = 315.0,
        low = -78,
        high = 228,
        trappingLocs = box("loc.hunting_boxtrap_trapping_chinchompa_black"),
        fullLoc = "loc.hunting_boxtrap_full_chinchompa_black",
        loot = listOf(TrapLoot("obj.chinchompa_black", 1)),
        escapeAnim = "seq.hunting_chinchompa_backoff",
    ),
    ;

    companion object {
        val byNpc: Map<String, TrapPrey> = entries.associateBy { it.npc }

        val byFullLoc: Map<String, TrapPrey> = entries.associateBy { it.fullLoc }

        fun forKind(kind: TrapKind): List<TrapPrey> = entries.filter { it.kind == kind }
    }
}

private fun bird(loc: String): Map<Direction, String> =
    CARDINALS.associateWith { loc }

private fun box(prefix: String): Map<Direction, String> =
    mapOf(
        Direction.North to "${prefix}_n",
        Direction.East to "${prefix}_e",
        Direction.South to "${prefix}_s",
        Direction.West to "${prefix}_w",
    )

private fun birdLoot(feather: String): List<TrapLoot> =
    listOf(
        TrapLoot("obj.bones", 1),
        TrapLoot("obj.spit_raw_bird_meat", 1),
        TrapLoot(feather, 5, 10),
    )

private val CARDINALS = listOf(Direction.North, Direction.East, Direction.South, Direction.West)
