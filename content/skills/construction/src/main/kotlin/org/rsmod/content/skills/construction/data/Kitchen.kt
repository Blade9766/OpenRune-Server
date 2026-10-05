package org.rsmod.content.skills.construction.data

import org.rsmod.content.skills.construction.house.HouseState
import org.rsmod.content.skills.construction.house.Room

/**
 * What a house's kitchen and dining room let a servant serve, and the tea and drinks that only exist
 * inside a house. Shelf tiers and boosts come from the Old School wiki.
 */
object Kitchen {
    /** A tea cup, by the shelves it comes from, and the Construction boost its tea gives. */
    enum class Cup(private val cup: String, private val pot: String, val boost: Int) {
        CLAY("poh_claycup", "clay", 1),
        PORCELAIN("poh_chinacup", "porcelain", 2),
        TRIMMED("poh_giltchinacup", "giltporcelain", 3);

        /** The teapot that goes with this cup, from the same shelves. */
        val teapot: String
            get() = "obj.poh_teapot_${pot}_empty"

        val teapotWithLeaves: String
            get() = "obj.poh_teapot_${pot}_leaves"

        /** This teapot holding [servings] cups of tea, 1 to [SERVINGS]. */
        fun teapot(servings: Int): String = "obj.poh_teapot_${pot}_$servings"

        val tea: String
            get() = "obj.${cup}_tea"

        val milky: String
            get() = "obj.${cup}_tea_milky"

        val empty: String
            get() = "obj.${cup}_empty"
    }

    const val BEER_GLASS: String = "obj.poh_beer_glass"

    /** The glasses a barrel will fill: the house's own, or an ordinary empty beer glass. */
    val FILLABLE_GLASSES: List<String> = listOf(BEER_GLASS, "obj.beer_glass")

    /** Every barrel, in build order - the same order as [BARREL_DRINKS]. */
    val BARRELS: List<String> = (1..6).map { "loc.poh_barrel_$it" }

    const val KETTLE_EMPTY: String = "obj.poh_kettle_empty"
    const val KETTLE_WATER: String = "obj.poh_kettle_water"
    const val KETTLE_BOILED: String = "obj.poh_kettle_boiled"
    const val TEA_LEAVES: String = "obj.poh_tea_leaves"
    const val MILK: String = "obj.bucket_milk"

    /** A full pot of tea pours this many cups. */
    const val SERVINGS: Int = 4

    /** Making a pot of tea needs this Cooking level and gives [BREWING_XP]. */
    const val BREWING_LEVEL: Int = 20
    const val BREWING_XP: Double = 52.0

    private val KETTLES = listOf(KETTLE_EMPTY, KETTLE_WATER, KETTLE_BOILED)

    val SINKS: List<String> = (1..3).map { "loc.poh_sink_$it" }

    /** Stoves 1-3 are firepits, which cannot boil a kettle; 4-7 are ovens and ranges. */
    val FIREPITS: List<String> = (1..3).map { "loc.poh_stove_$it" }
    val OVENS: List<String> = (4..7).map { "loc.poh_stove_$it" }

    /** Each larder, mapped to what it holds: every tier keeps the last tier's stock and adds more. */
    val LARDERS: Map<String, List<String>> =
        mapOf(
            "loc.poh_larder_1" to listOf(TEA_LEAVES, MILK),
            "loc.poh_larder_2" to listOf(TEA_LEAVES, MILK, "obj.egg", "obj.pot_flour"),
            "loc.poh_larder_3" to
                listOf(
                    TEA_LEAVES,
                    MILK,
                    "obj.egg",
                    "obj.pot_flour",
                    "obj.potato",
                    "obj.garlic",
                    "obj.onion",
                    "obj.cheese",
                ),
        )

    /** Every set of shelves, both halves, mapped to its option index in build order. */
    val SHELVES: Map<String, Int> =
        (1..7).flatMap { tier -> listOf("loc.poh_kitchen_shelves_$tier", "loc.poh_kitchen_crockery_$tier").map { it to tier - 1 } }
            .toMap()

    /**
     * Everything beyond the teapot, cup and kettle that shelves hold, in the order each tier adds
     * it: wooden shelves 1 hold none of these, and every tier after adds the next.
     */
    private val SHELF_EXTRAS =
        listOf(
            BEER_GLASS,
            "obj.cake_tin",
            "obj.bowl_empty",
            "obj.piedish",
            "obj.pot_empty",
            "obj.chefs_hat",
        )

    /** What the shelves built as option [index] hand out. */
    fun shelfItems(index: Int): List<String> {
        val cup = SHELF_CUPS[index]
        return listOf(cup.teapot, cup.empty, KETTLES.first()) + SHELF_EXTRAS.take(index)
    }

    /** The cup each shelves option holds, in build order: wooden 1-3, oak 1-2, teak 1-2. */
    private val SHELF_CUPS =
        listOf(Cup.CLAY, Cup.CLAY, Cup.PORCELAIN, Cup.CLAY, Cup.PORCELAIN, Cup.PORCELAIN, Cup.TRIMMED)

    /** The house drink each barrel option pours, in build order. */
    val BARREL_DRINKS: List<String> =
        listOf(
            "obj.poh_beer",
            "obj.poh_cider",
            "obj.poh_asgarnian_ale",
            "obj.poh_greenmans_ale",
            "obj.poh_dragon_bitter",
            "obj.poh_chefs_delight",
        )

    /** The ordinary drink a house barrel drink copies: `obj.poh_beer` is `obj.beer`, and so on. */
    fun ordinaryDrink(houseDrink: String): String = houseDrink.replace("obj.poh_", "obj.")

    /** Every stove option before this one is a firepit, which cannot boil a kettle. */
    private const val FIRST_OVEN = 3

    /** Teas and drinks that vanish when their holder leaves the house. */
    val HOUSE_ONLY: List<String> =
        Cup.entries.flatMap { cup ->
            listOf(cup.tea, cup.milky, cup.empty, cup.teapot, cup.teapotWithLeaves) +
                (1..SERVINGS).map(cup::teapot)
        } +
            KETTLES +
            TEA_LEAVES +
            BARREL_DRINKS +
            BEER_GLASS

    private fun HouseState.kitchens(): List<Room> = rooms.values.filter { it.type == RoomType.KITCHEN }

    /**
     * The best cup a servant can make tea in: a kitchen needs a stove that is not a firepit, a
     * larder, a sink and shelves, as making tea by hand does.
     */
    fun teaCup(state: HouseState): Cup? =
        state.kitchens()
            .filter { kitchen ->
                (kitchen.furniture["stove"] ?: -1) >= FIRST_OVEN &&
                    "larder" in kitchen.furniture &&
                    "sink" in kitchen.furniture
            }
            .mapNotNull { kitchen -> kitchen.furniture["shelves"]?.let(SHELF_CUPS::getOrNull) }
            .maxByOrNull { it.boost }

    /** A meal needs a dining table to be served on and a kitchen with a stove and larder. */
    fun canServeDinner(state: HouseState): Boolean =
        state.rooms.values.any { it.type == RoomType.DINING_ROOM && "table" in it.furniture } &&
            state.kitchens().any { "stove" in it.furniture && "larder" in it.furniture }

    /** The drink the house's best barrel pours, or null without one. */
    fun drink(state: HouseState): String? =
        state.kitchens().mapNotNull { it.furniture["barrel"] }.maxOrNull()?.let(BARREL_DRINKS::getOrNull)
}
