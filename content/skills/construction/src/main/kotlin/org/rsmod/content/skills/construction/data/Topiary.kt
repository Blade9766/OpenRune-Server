package org.rsmod.content.skills.construction.data

/**
 * The superior garden's topiary bush and the boss shapes it can be clipped into, the wiki's list.
 * A shape needs the boss killed once, by its kill count varp; the shape the owner has clipped is
 * kept in [VARP] as its index plus one, zero for the plain bush.
 */
object Topiary {
    enum class Shape(val label: String, private val key: String, val kills: String) {
        KRAKEN("Kraken", "kraken", "varp.total_kraken_boss_kills"),
        ZULRAH("Zulrah", "zulrah", "varp.total_snakeboss_kills"),
        KALPHITE_QUEEN("Kalphite Queen", "kq", "varp.total_kalphite_kills"),
        CERBERUS("Cerberus", "cerb", "varp.total_cerberus_kills"),
        ABYSSAL_SIRE("Abyssal Sire", "sire", "varp.total_abyssalsire_kills"),
        SKOTIZO("Skotizo", "skotizo", "varp.total_cata_boss_kills"),
        VORKATH("Vorkath", "vorkath", "varp.total_vorkath_kills"),
        HYDRA("Alchemical Hydra", "hydra", "varp.total_hydraboss_kills"),
        NIGHTMARE("The Nightmare", "nightmare", "varp.total_nightmare_kills");

        val loc: String
            get() = "loc.poh_topiary_$key"
    }

    const val BUSH: String = "loc.poh_topiary_null"
    const val VARP: String = "varp.poh_topiary"

    /** What an owner's built [built] bush is clipped into, or null when it is not the bush. */
    fun shown(built: String, varp: Int): String? =
        if (built == BUSH) Shape.entries.getOrNull(varp - 1)?.loc ?: BUSH else null

    val LOCS: List<String>
        get() = listOf(BUSH) + Shape.entries.map { it.loc }

    fun isShape(loc: String): Boolean = Shape.entries.any { it.loc == loc }
}
