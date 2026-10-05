package org.rsmod.content.skills.construction.data

/**
 * Chapel altars and incense burners.
 *
 * Every altar tier exists once per god (`poh_altar_<god>_<tier>`); the furniture table only names
 * the Saradomin one, and the house swaps in the god of whichever icon is built in the same room.
 */
object Chapel {
    enum class God(val key: String) {
        SARADOMIN("saradomin"),
        ZAMORAK("zamorak"),
        GUTHIX("guthix"),
        GNOME_CHILD("gnomechild"),
    }

    const val TIERS: Int = 7

    /** Prayer experience multiplier for bones offered with no burner lit, by tier. */
    private val TIER_MULTIPLIERS = doubleArrayOf(1.0, 1.1, 1.25, 1.5, 1.75, 2.0, 2.5)

    /** Added to the multiplier for every lit burner in the altar's room. */
    const val BURNER_BONUS: Double = 0.5

    /** The god each icon dedicates the altar to, in icon option order. Bob has no altar. */
    private val ICON_GODS: List<God> =
        listOf(
            God.SARADOMIN,
            God.ZAMORAK,
            God.GUTHIX,
            God.SARADOMIN,
            God.ZAMORAK,
            God.GUTHIX,
            God.SARADOMIN,
        )

    val BURNERS: Map<String, String> =
        mapOf(
            "loc.poh_torch_5" to "loc.poh_torch_5_lit",
            "loc.poh_torch_6" to "loc.poh_torch_6_lit",
            "loc.poh_torch_7" to "loc.poh_torch_7_lit",
        )

    val LIT_BURNERS: Map<String, String> = BURNERS.entries.associate { (unlit, lit) -> lit to unlit }

    /** Every altar variant, mapped to its tier. */
    val ALTARS: Map<String, Int> =
        God.entries
            .flatMap { god -> (1..TIERS).map { tier -> altar(god, tier) to tier } }
            .toMap()

    fun altar(god: God, tier: Int): String = "loc.poh_altar_${god.key}_$tier"

    fun tierOf(altar: String): Int? = ALTARS[altar]

    /** The furniture table's name for [altar], whichever god it was dedicated to. */
    fun undedicated(altar: String): String = tierOf(altar)?.let { altar(God.SARADOMIN, it) } ?: altar

    /** [altar] dedicated to the god of the icon built at [iconOption], or as-is with no icon. */
    fun dedicate(altar: String, iconOption: Int?): String {
        val tier = tierOf(altar) ?: return altar
        val god = iconOption?.let(ICON_GODS::getOrNull) ?: God.SARADOMIN
        return altar(god, tier)
    }

    fun multiplier(altar: String, litBurners: Int): Double? {
        val tier = tierOf(altar) ?: return null
        return TIER_MULTIPLIERS[tier - 1] + BURNER_BONUS * litBurners
    }
}
