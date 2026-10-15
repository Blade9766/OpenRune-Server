package org.rsmod.content.skills.construction.data

/**
 * The treasure room's chests and guardians. Levels, materials, experience, coin limits and the
 * five-minute wait between deposits come from the Old School wiki.
 */
object Treasure {
    /** A treasure chest, by its closed and opened locs, and the most coins it holds. */
    enum class Chest(val closed: String, val open: String, val limit: Int) {
        WOODEN_CRATE("loc.poh_treasure_woodencrate", "loc.poh_treasure_openwoodencrate", 10_000),
        OAK("loc.poh_treasure_oak_chest", "loc.poh_treasure_oak_openchest", 20_000),
        TEAK("loc.poh_treasure_teak_chest", "loc.poh_treasure_teak_openchest", 50_000),
        MAHOGANY("loc.poh_treasure_mag_chest", "loc.poh_treasure_mag_openchest", 75_000),
        MAGIC("loc.poh_treasure_magic_chest", "loc.poh_treasure_magic_openchest", 100_000),
    }

    /** The guardians, by the name both their statue loc and their npc share, in build order. */
    val GUARDIANS: List<String> =
        listOf("poh_demon", "poh_kalphite_soldier", "poh_tok_xil", "poh_dagganoth", "poh_steel_dragon", "poh_rune_dragon")

    /** Five minutes between deposits. */
    const val DEPOSIT_COOLDOWN: Int = 500

    const val TREASURE_VARP: String = "varp.poh_treasure"
    const val COOLDOWN_VARP: String = "varp.poh_treasure_cooldown"
}
