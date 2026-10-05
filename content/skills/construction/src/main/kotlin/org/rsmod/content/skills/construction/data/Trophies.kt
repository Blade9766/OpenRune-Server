package org.rsmod.content.skills.construction.data

/**
 * The skill hall's head and fishing trophies.
 *
 * A display is built empty; a stuffed head or fish used on it in building mode is mounted for good,
 * remembered per account in a bitmask, and any mounted trophy of the display's tier or lower can be
 * shown on it. Levels come from the cache's furniture rows, experience and taxidermy prices from the
 * Old School wiki.
 */
object Trophies {
    enum class Kind(val woods: List<String>, val blank: String, val maskVarbit: String, val shownVarbit: String) {
        HEAD(
            listOf("teak", "mahogany", "gilded"),
            "head_blank",
            "varbit.poh_trophies_heads",
            "varbit.poh_trophy_head_shown",
        ),
        FISH(
            listOf("oak", "teak", "mahogany"),
            "fish_blank",
            "varbit.poh_trophies_fish",
            "varbit.poh_trophy_fish_shown",
        );

        fun blank(tier: Int): String = "loc.poh_trophy_${blank}_${woods[tier - 1]}"
    }

    /**
     * [skill] gets [skillXp] when the trophy is mounted; with no skill, the boss heads offer
     * [skillXp] in every combat skill instead, which the player may turn down.
     */
    class Trophy(
        val key: String,
        val kind: Kind,
        val tier: Int,
        val level: Int,
        val label: String,
        val raw: List<String>,
        val stuffed: List<String>,
        val cost: Int,
        val constructionXp: Double,
        val skillXp: Double,
        val skill: String?,
    ) {
        /** The loc this trophy shows as on a display of [tier]. */
        fun loc(displayTier: Int): String = "loc.poh_trophy_${key}_${kind.woods[displayTier - 1]}"

        /** The bit this trophy takes in its kind's mounted mask. */
        val bit: Int
            get() = ALL.filter { it.kind == kind }.indexOf(this)
    }

    private const val SLAYER = "stat.slayer"
    private const val FISHING = "stat.fishing"
    private const val DROP = "obj.poh_trophydrop_"

    private fun head(key: String, drop: String, tier: Int, level: Int, label: String, cost: Int, con: Double, slayer: Double) =
        Trophy(key, Kind.HEAD, tier, level, label, listOf(DROP + drop), listOf(DROP + drop + "_stuffed"), cost, con, slayer, SLAYER)

    private fun boss(key: String, raw: List<String>, stuffed: List<String>, label: String) =
        Trophy(key, Kind.HEAD, 3, 78, label, raw, stuffed, 50_000, 223.0, 200.0, null)

    private fun fish(key: String, tier: Int, level: Int, label: String, cost: Int, con: Double, fishing: Double) =
        Trophy(key, Kind.FISH, tier, level, label, listOf(DROP + key), listOf(DROP + key + "_stuffed"), cost, con, fishing, FISHING)

    val ALL: List<Trophy> =
        listOf(
            head("crawlinghand", "crawlinghand", 1, 38, "Crawling hand", 1_000, 31.0, 261.0),
            head("cockatrice", "cockatrice", 1, 38, "Cockatrice head", 2_000, 44.0, 294.0),
            head("basilisk", "basilisk", 1, 38, "Basilisk head", 4_000, 63.0, 343.0),
            head("kurask", "kurask", 2, 58, "Kurask head", 6_000, 77.0, 657.0),
            head("abyssal", "abyssaldemon", 2, 58, "Abyssal head", 12_000, 109.0, 889.0),
            boss("kbd", listOf("obj.poh_trophydrop_kbd"), listOf("obj.poh_trophydrop_kbd_stuffed"), "KBD heads"),
            boss(
                "kalphitequeen",
                listOf("obj.poh_trophydrop_kalphitequeen", "obj.poh_pitydrop_kalphitequeen"),
                listOf("obj.poh_trophydrop_kalphitequeen_stuffed", "obj.poh_pitydrop_kalphitequeen_stuffed"),
                "KQ head",
            ),
            boss("vorkath", listOf("obj.vorkath_head"), listOf("obj.vorkath_head_stuffed"), "Vorkath's head"),
            boss(
                "alchemical_hydra",
                listOf("obj.poh_alchemical_hydra_head"),
                listOf("obj.poh_alchemical_hydra_head_stuffed"),
                "Alchemical hydra heads",
            ),
            fish("bass", 1, 36, "Mounted bass", 1_000, 31.0, 151.0),
            fish("swordfish", 2, 56, "Mounted swordfish", 2_500, 50.0, 230.0),
            fish("harpoonfish", 2, 66, "Mounted harpoonfish", 3_500, 59.0, 239.0),
            fish("giant_krill", 2, 64, "Mounted giant blue krill", 5_000, 70.0, 250.0),
            fish("haddock", 2, 68, "Mounted golden haddock", 5_000, 70.0, 250.0),
            fish("yellowfin", 2, 74, "Mounted orangefin", 5_000, 70.0, 250.0),
            fish("shark", 3, 76, "Mounted shark", 5_000, 70.0, 350.0),
            fish("halibut", 3, 78, "Mounted huge halibut", 5_500, 74.0, 354.0),
            fish("bluefin", 3, 82, "Mounted purplefin", 6_000, 77.0, 357.0),
            fish("marlin", 3, 86, "Mounted swift marlin", 6_500, 80.0, 360.0),
        )

    val COMBAT_SKILLS: List<String> =
        listOf("stat.attack", "stat.strength", "stat.defence", "stat.magic", "stat.ranged")

    /** Every display loc - empty or showing a trophy - mapped to its kind and tier. */
    val DISPLAYS: Map<String, Pair<Kind, Int>> =
        buildMap {
            for (kind in Kind.entries) {
                for (tier in 1..3) {
                    put(kind.blank(tier), kind to tier)
                }
            }
            for (trophy in ALL) {
                for (tier in trophy.tier..3) {
                    put(trophy.loc(tier), trophy.kind to tier)
                }
            }
        }

    /** The empty display a trophy-showing [loc] was built as, so Remove and Upgrade can find it. */
    fun blankOf(loc: String): String? = DISPLAYS[loc]?.let { (kind, tier) -> kind.blank(tier) }

    /** What a blank display shows: [shown] is a 1-based index into [kind]'s trophies, 0 for none. */
    fun shownOn(kind: Kind, tier: Int, shown: Int, mask: Int): String? {
        val trophy = ALL.filter { it.kind == kind }.getOrNull(shown - 1) ?: return null
        if (trophy.tier > tier || mask and (1 shl trophy.bit) == 0) {
            return null
        }
        return trophy.loc(tier)
    }
}
