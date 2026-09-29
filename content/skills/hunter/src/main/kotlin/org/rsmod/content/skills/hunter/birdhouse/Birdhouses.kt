package org.rsmod.content.skills.hunter.birdhouse

/**
 * Bird house tiers. In the space multiloc (`loc.birdhouse_1..4`, one client varp each) tier `t`
 * occupies transform values `1 + 3t` (built), `2 + 3t` (seeded) and `3 + 3t` (birds caught).
 * [nestHigh] is the wiki per-roll nest chance at level 99, in thousandths.
 */
enum class Birdhouse(
    val obj: String,
    val locPrefix: String,
    val level: Int,
    val xp: Double,
    val nestHigh: Int,
) {
    Normal("obj.birdhouse_normal", "loc.birdhouse_normal", 5, 112.0, 300),
    Oak("obj.birdhouse_oak", "loc.birdhouse_oak", 14, 168.0, 375),
    Willow("obj.birdhouse_willow", "loc.birdhouse_willow", 24, 224.0, 384),
    Teak("obj.birdhouse_teak", "loc.birdhouse_teak", 34, 280.0, 390),
    Maple("obj.birdhouse_maple", "loc.birdhouse_maple", 44, 369.0, 420),
    Mahogany("obj.birdhouse_mahogany", "loc.birdhouse_mahogany", 49, 480.0, 450),
    Yew("obj.birdhouse_yew", "loc.birdhouse_yew", 59, 612.0, 480),
    Magic("obj.birdhouse_magic", "loc.birdhouse_magic", 74, 969.0, 510),
    Redwood("obj.birdhouse_redwood", "loc.birdhouse_redwood", 89, 1200.0, 525);

    val builtValue: Int
        get() = 1 + ordinal * 3

    val seededValue: Int
        get() = builtValue + 1

    val readyValue: Int
        get() = builtValue + 2

    val builtLoc: String
        get() = "${locPrefix}_built"

    val seededLoc: String
        get() = "${locPrefix}_full"

    val readyLoc: String
        get() = "${locPrefix}_bird"

    companion object {
        fun forValue(value: Int): Birdhouse? = if (value <= 0) null else entries.getOrNull((value - 1) / 3)
    }
}

/** A bird house space: its base multiloc and the client varp that selects its transform. */
enum class BirdhouseSpace(val loc: String, val clientVarp: Int, val stateVarp: String, val timeVarp: String) {
    VerdantNorth("loc.birdhouse_1", 1626, "varp.hunter_birdhouse_state_1", "varp.hunter_birdhouse_time_1"),
    VerdantSouth("loc.birdhouse_2", 1627, "varp.hunter_birdhouse_state_2", "varp.hunter_birdhouse_time_2"),
    MushroomMeadow("loc.birdhouse_3", 1628, "varp.hunter_birdhouse_state_3", "varp.hunter_birdhouse_time_3"),
    TarSwamp("loc.birdhouse_4", 1629, "varp.hunter_birdhouse_state_4", "varp.hunter_birdhouse_time_4"),
}

/** Seeds that bait a bird house, with how many "units" each is worth towards the 10 needed. */
val BIRDHOUSE_SEEDS: Map<String, Int> =
    listOf(
            "obj.barley_seed",
            "obj.hammerstone_hop_seed",
            "obj.asgarnian_hop_seed",
            "obj.jute_seed",
            "obj.yanillian_hop_seed",
            "obj.krandorian_hop_seed",
            "obj.guam_seed",
            "obj.marrentill_seed",
            "obj.tarromin_seed",
            "obj.harralander_seed",
            "obj.marigold_seed",
            "obj.rosemary_seed",
            "obj.nasturtium_seed",
            "obj.woad_seed",
            "obj.limpwurt_seed",
            "obj.white_lily_seed",
            "obj.potato_seed",
            "obj.onion_seed",
            "obj.cabbage_seed",
            "obj.tomato_seed",
            "obj.sweetcorn_seed",
            "obj.strawberry_seed",
            "obj.watermelon_seed",
            "obj.snape_grass_seed",
            "obj.redberry_bush_seed",
            "obj.poisonivy_bush_seed",
            "obj.cadavaberry_bush_seed",
            "obj.dwellberry_bush_seed",
            "obj.jangerberry_bush_seed",
            "obj.whiteberry_bush_seed",
        )
        .associateWith { 1 } +
        listOf(
                "obj.wildblood_hop_seed",
                "obj.ranarr_seed",
                "obj.toadflax_seed",
                "obj.irit_seed",
                "obj.avantoe_seed",
                "obj.kwuarm_seed",
                "obj.snapdragon_seed",
                "obj.cadantine_seed",
                "obj.lantadyme_seed",
                "obj.dwarf_weed_seed",
                "obj.torstol_seed",
            )
            .associateWith { 2 }
