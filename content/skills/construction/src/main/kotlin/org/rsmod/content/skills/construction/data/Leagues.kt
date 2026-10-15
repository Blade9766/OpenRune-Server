package org.rsmod.content.skills.construction.data

/**
 * The league hall's displays.
 *
 * Each league's trophies, banner and relic hunter outfit go on show by swapping the display for the
 * cache's variant of it carrying that league's piece. What is on show is kept in the cache's own
 * league hall varbits: each pedestal's league and trophy, the banner stand's league, and the outfit
 * stand's league and tier, each stored as its index plus one so zero is an empty display.
 */
object Leagues {
    /**
     * A league, by the name its pedestal and outfit variants use ([key]), its banner variant
     * ([banner]), its items' prefix, its relic hunter outfit's prefix and what its outfit's head
     * piece is called.
     */
    enum class League(
        val key: String,
        val banner: String,
        private val items: String,
        private val outfit: String,
        private val head: String,
    ) {
        TWISTED("twisted", "twisted", "twisted", "twisted_relic_hunter", "hat"),
        TRAILBLAZER("trailblazer", "trailblazer", "trailblazer", "trailblazer_relic_hunter", "hood"),
        SHATTERED_RELICS("league_3", "shattered", "league_3", "league_3_relic_hunter", "hood"),
        TRAILBLAZER_RELOADED("league_4", "trailblazer02", "league_4", "league_4_relic_hunter", "hat"),
        RAGING_ECHOES("league_5", "ragingechoes01", "league_5", "league5_relic_hunter", "hat"),
        DEMONIC_PACTS("league_6", "league06", "league_6", "league_6_relic_hunter", "hood");

        fun trophy(metal: Int): String = "obj.${items}_${METALS[metal]}_trophy"

        val bannerObj: String
            get() = "obj.${items}_banner"

        /** The four pieces of [tier]'s outfit (1 to 3). */
        fun outfit(tier: Int): List<String> = listOf(head, "top", "legs", "boots").map { "obj.${outfit}_${it}_t$tier" }
    }

    val METALS: List<String> = listOf("bronze", "iron", "steel", "mithril", "adamant", "rune", "dragon")

    const val OUTFIT_TIERS: Int = 3

    /** A trophy pedestal's built loc for pedestal [number] (1 to 3) of [style] (simple or decorative). */
    fun pedestal(number: Int, style: String): String = "loc.poh_leaguehall_pedestal_${number}_$style"

    val PEDESTAL_STYLES: List<String> = listOf("simple", "decorative")
    val STAND_STYLES: List<String> = listOf("simple", "decorative")
    val OUTFIT_STYLES: List<String> = listOf("oak", "mahogany")

    fun pedestalLeague(number: Int): String = "varbit.poh_leaguehall_pedestal_type_$number"

    fun pedestalTrophy(number: Int): String = "varbit.poh_leaguehall_pedestal_trophy_type_$number"

    const val BANNER_LEAGUE: String = "varbit.poh_leaguehall_bannerstand_type"
    const val OUTFIT_LEAGUE: String = "varbit.poh_leaguehall_outfitstand_type"
    const val OUTFIT_TIER: String = "varbit.poh_leaguehall_outfitstand_relichunter_type"

    /** The pedestal showing [league]'s [metal] trophy. */
    fun pedestalWith(base: String, league: League, metal: Int): String = "${base}_${league.key}_${METALS[metal]}"

    fun bannerStand(style: String): String = "loc.poh_leaguehall_bannerstand_$style"

    fun bannerStandWith(base: String, league: League): String = "${base}_${league.banner}"

    fun outfitStand(style: String): String = "loc.poh_leaguehall_outfitstand_$style"

    fun outfitStandWith(base: String, league: League, tier: Int): String = "${base}_${league.key}_t$tier"

    /** Every built display, empty, with every variant of it showing something. */
    val DISPLAYS: Map<String, List<String>> by lazy {
        val pedestals =
            (1..3).flatMap { number ->
                PEDESTAL_STYLES.map { style ->
                    val base = pedestal(number, style)
                    base to League.entries.flatMap { league -> METALS.indices.map { pedestalWith(base, league, it) } }
                }
            }
        val banners =
            STAND_STYLES.map { style ->
                val base = bannerStand(style)
                base to League.entries.map { bannerStandWith(base, it) }
            }
        val outfits =
            OUTFIT_STYLES.map { style ->
                val base = outfitStand(style)
                base to League.entries.flatMap { league -> (1..OUTFIT_TIERS).map { outfitStandWith(base, league, it) } }
            }
        (pedestals + banners + outfits).toMap()
    }

    /** The empty display a variant shows something on, or null when [loc] is not a variant. */
    fun baseOf(loc: String): String? = DISPLAYS.entries.firstOrNull { loc in it.value }?.key

    /** What an owner's [built] display shows, or null when it is not a league display. */
    fun shown(built: String, vars: (String) -> Int): String? {
        for (number in 1..3) {
            for (style in PEDESTAL_STYLES) {
                if (built != pedestal(number, style)) continue
                val league = League.entries.getOrNull(vars(pedestalLeague(number)) - 1) ?: return built
                val metal = vars(pedestalTrophy(number)) - 1
                return if (metal in METALS.indices) pedestalWith(built, league, metal) else built
            }
        }
        if (STAND_STYLES.any { built == bannerStand(it) }) {
            val league = League.entries.getOrNull(vars(BANNER_LEAGUE) - 1) ?: return built
            return bannerStandWith(built, league)
        }
        if (OUTFIT_STYLES.any { built == outfitStand(it) }) {
            val league = League.entries.getOrNull(vars(OUTFIT_LEAGUE) - 1) ?: return built
            val tier = vars(OUTFIT_TIER)
            return if (tier in 1..OUTFIT_TIERS) outfitStandWith(built, league, tier) else built
        }
        return null
    }

    val TROPHY_CASES: Map<String, String> =
        mapOf(
            "loc.poh_leaguehall_trophycase_oak" to "loc.poh_leaguehall_trophycase_oak_open",
            "loc.poh_leaguehall_trophycase_mahogany" to "loc.poh_leaguehall_trophycase_mahogany_open",
        )

    const val SCROLL: String = "loc.poh_leaguehall_accomplishment_scroll"
    val STATUES: List<String> =
        listOf("loc.poh_leaguehall_statue_simple", "loc.poh_leaguehall_statue_decorative", "loc.poh_leaguehall_statue_trailblazer")
    const val GLOBE: String = "loc.poh_leaguehall_statue_trailblazer"
}
