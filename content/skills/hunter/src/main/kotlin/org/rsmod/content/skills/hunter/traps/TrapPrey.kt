package org.rsmod.content.skills.hunter.traps

import org.rsmod.game.map.Direction

data class TrapLoot(
    val obj: String,
    val min: Int,
    val max: Int = min,
    val rareObj: String? = null,
    val rareChance: Int = 0,
)

/**
 * A creature that can be caught in a [TrapKind]. [low]/[high] are the catch-rate endpoints out of
 * 256 at level 1 and 99, as published by Jagex for each creature.
 */
enum class TrapPrey(
    val npc: String?,
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
    val rareMessage: String? = null,
    val petChance: Int = 0,
    val catchMessage: String? = null,
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
    EmbertailedJerboa(
        npc = "npc.varlamore_hunterjerboa01",
        kind = TrapKind.BoxTrap,
        displayName = "embertailed jerboa",
        level = 39,
        xp = 137.0,
        low = 40,
        high = 280,
        trappingLocs = box("loc.hunting_boxtrap_trapping_jerboa"),
        fullLoc = "loc.hunting_boxtrap_full_jerboa",
        loot = listOf(TrapLoot("obj.hunting_jerboa_tail", 1)),
        escapeAnim = "seq.jerboa_backoff",
        catchMessage = "You've caught an embertailed jerboa... or maybe not.",
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
        petChance = 131395,
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
        petChance = 98373,
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
        petChance = 82758,
    ),
    SwampLizard(
        npc = "npc.salamander_green",
        kind = TrapKind.NetSwamp,
        displayName = "swamp lizard",
        level = 29,
        xp = 152.0,
        low = 52,
        high = 360,
        trappingLocs = single("loc.hunting_sapling_catching_green"),
        fullLoc = "loc.hunting_sapling_full_green",
        loot = listOf(TrapLoot("obj.green_salamander", 1)),
    ),
    OrangeSalamander(
        npc = "npc.salamander_orange",
        kind = TrapKind.NetOrange,
        displayName = "orange salamander",
        level = 47,
        xp = 224.0,
        low = 16,
        high = 288,
        trappingLocs = single("loc.hunting_sapling_catching_orange"),
        fullLoc = "loc.hunting_sapling_full_orange",
        loot = listOf(TrapLoot("obj.orange_salamander", 1)),
    ),
    RedSalamander(
        npc = "npc.salamander_red",
        kind = TrapKind.NetRed,
        displayName = "red salamander",
        level = 59,
        xp = 272.0,
        low = 0,
        high = 240,
        trappingLocs = single("loc.hunting_sapling_catching_red"),
        fullLoc = "loc.hunting_sapling_full_red",
        loot = listOf(TrapLoot("obj.red_salamander", 1)),
    ),
    BlackSalamander(
        npc = "npc.salamander_black",
        kind = TrapKind.NetBlack,
        displayName = "black salamander",
        level = 67,
        xp = 319.2,
        low = 0,
        high = 212,
        trappingLocs = single("loc.hunting_sapling_catching_black"),
        fullLoc = "loc.hunting_sapling_full_black",
        loot = listOf(TrapLoot("obj.black_salamander", 1)),
    ),
    TecuSalamander(
        npc = "npc.salamander_mountain",
        kind = TrapKind.NetMountain,
        displayName = "tecu salamander",
        level = 79,
        xp = 344.0,
        low = 1,
        high = 212,
        trappingLocs = single("loc.hunting_sapling_catching_mountain"),
        fullLoc = "loc.hunting_sapling_full_mountain",
        loot =
            listOf(
                TrapLoot(
                    "obj.immature_mountain_salamander",
                    1,
                    rareObj = "obj.mountain_salamander",
                    rareChance = 1000,
                ),
            ),
        rareMessage = "The salamander is fully grown, you could use this as a weapon",
    ),
    WildKebbit(
        npc = "npc.huntingbeast_claws",
        kind = TrapKind.Deadfall,
        displayName = "wild kebbit",
        level = 23,
        xp = 102.4,
        low = 29,
        high = 385,
        trappingLocs = deadfall("loc.hunting_deadfall_trapping_claw"),
        fullLoc = "loc.hunting_deadfall_full_claw",
        loot =
            listOf(
                TrapLoot("obj.bones", 1),
                TrapLoot("obj.huntingbeast_claws", 1),
                TrapLoot("obj.huntingbeast_wild_meat", 1),
            ),
        escapeAnim = "seq.huntingbeast_backoff",
    ),
    BarbTailedKebbit(
        npc = "npc.huntingbeast_barbedtail",
        kind = TrapKind.Deadfall,
        displayName = "barb-tailed kebbit",
        level = 33,
        xp = 134.4,
        low = -220,
        high = 1037,
        trappingLocs = deadfall("loc.hunting_deadfall_trapping_barbed"),
        fullLoc = "loc.hunting_deadfall_full_barbed",
        loot =
            listOf(
                TrapLoot("obj.bones", 1),
                TrapLoot("obj.hunting_barbed_harpoon", 1),
                TrapLoot("obj.huntingbeast_barbed_meat", 1),
            ),
        escapeAnim = "seq.huntingbeast_backoff",
    ),
    PricklyKebbit(
        npc = "npc.huntingbeast_spiky",
        kind = TrapKind.Deadfall,
        displayName = "prickly kebbit",
        level = 37,
        xp = 147.2,
        low = -70,
        high = 331,
        trappingLocs = deadfall("loc.hunting_deadfall_trapping_spike"),
        fullLoc = "loc.hunting_deadfall_full_spike",
        loot = listOf(TrapLoot("obj.bones", 1), TrapLoot("obj.huntingbeast_spike", 1)),
        escapeAnim = "seq.huntingbeast_backoff",
    ),
    SabreToothedKebbit(
        npc = "npc.huntingbeast_sabreteeth",
        kind = TrapKind.Deadfall,
        displayName = "sabre-toothed kebbit",
        level = 51,
        xp = 160.0,
        low = -434,
        high = 820,
        trappingLocs = deadfall("loc.hunting_deadfall_trapping_sabre"),
        fullLoc = "loc.hunting_deadfall_full_sabre",
        loot = listOf(TrapLoot("obj.bones", 1), TrapLoot("obj.huntingbeast_sabreteeth", 1)),
        escapeAnim = "seq.huntingbeast_backoff",
    ),
    PyreFox(
        npc = "npc.varlamore_hunterfox01",
        kind = TrapKind.Deadfall,
        displayName = "pyre fox",
        level = 57,
        xp = 177.6,
        low = -475,
        high = 750,
        trappingLocs = deadfall("loc.hunting_deadfall_trapping_fennec"),
        fullLoc = "loc.hunting_deadfall_full_fennec",
        loot =
            listOf(
                TrapLoot("obj.bones", 1),
                TrapLoot("obj.hunting_fennecfox_fur", 1),
                TrapLoot("obj.hunting_fennecfox_meat", 1),
            ),
        escapeAnim = "seq.huntingfox_backoff",
    ),
    WhiteRabbit(
        npc = null,
        kind = TrapKind.RabbitSnare,
        displayName = "white rabbit",
        level = 27,
        xp = 144.0,
        low = 256,
        high = 256,
        trappingLocs = emptyMap(),
        fullLoc = "loc.hunting_snare_rabbit",
        loot =
            listOf(
                TrapLoot("obj.bones", 1),
                TrapLoot("obj.raw_rabbit", 1),
                TrapLoot("obj.hunting_rabbit_foot", 1),
            ),
    ),
    ;

    companion object {
        val byNpc: Map<String, TrapPrey> =
            entries.mapNotNull { prey -> prey.npc?.let { it to prey } }.toMap()

        val byFullLoc: Map<String, TrapPrey> = entries.associateBy { it.fullLoc }

        fun forKind(kind: TrapKind): List<TrapPrey> = entries.filter { it.kind == kind }
    }
}

private fun bird(loc: String): Map<Direction, String> = single(loc)

private fun single(loc: String): Map<Direction, String> = CARDINALS.associateWith { loc }

private fun deadfall(loc: String): Map<Direction, String> =
    mapOf(
        Direction.North to loc,
        Direction.East to loc,
        Direction.South to "${loc}_m",
        Direction.West to "${loc}_m",
    )

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
