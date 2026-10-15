package org.rsmod.content.skills.construction.data

/**
 * What the workshop's furniture does once it is built. Recipes, levels, experience and reward
 * odds come from the Old School wiki.
 */
object Workshop {
    /** The tools each tool store hands out, by the loc of its slot. */
    val TOOL_STORES: Map<String, List<String>> =
        mapOf(
            "loc.poh_tools1" to listOf("obj.poh_saw", "obj.hammer", "obj.chisel", "obj.shears"),
            "loc.poh_tools2" to
                listOf("obj.bucket_empty", "obj.knife", "obj.spade", "obj.tinderbox"),
            "loc.poh_tools3" to listOf("obj.brown_apron", "obj.glassblowingpipe", "obj.needle"),
            "loc.poh_tools4" to
                listOf(
                    "obj.amulet_mould",
                    "obj.necklace_mould",
                    "obj.ring_mould",
                    "obj.holy_symbol_mould",
                    "obj.jewl_bracelet_mould",
                    "obj.tiara_mould",
                ),
            "loc.poh_tools5" to
                listOf(
                    "obj.rake",
                    "obj.spade",
                    "obj.gardening_trowel",
                    "obj.dibber",
                    "obj.watering_can_8",
                    "obj.secateurs",
                ),
        )

    /** One input of a recipe; any of [objs] will do. */
    class Ingredient(val objs: List<String>, val count: Int = 1) {
        constructor(obj: String, count: Int = 1) : this(listOf(obj), count)
    }

    class ClockworkRecipe(
        val output: String,
        val table: Int,
        val level: Int,
        val ingredients: List<Ingredient>,
    ) {
        val xp: Double
            get() = CLOCKMAKING_XP
    }

    private const val PLANK = "obj.woodplank"
    private const val CLOCKWORK = "obj.poh_clockwork_mechanism"
    private const val STEEL_BAR = "obj.steel_bar"
    private const val CLOCKMAKING_XP = 15.0

    private val FURS = Ingredient(listOf("obj.fur", "obj.werewolve_fur", "obj.grey_wolf_fur"))

    /** The crafting tables, by tier, mapped to the loc each tier is built as. */
    val CRAFTING_TABLES: Map<String, Int> = (1..4).associateBy { "loc.poh_clockmaking_$it" }

    val CLOCKWORK_RECIPES: List<ClockworkRecipe> =
        listOf(
            ClockworkRecipe("obj.horsey_brown", 1, 10, listOf(Ingredient(PLANK))),
            ClockworkRecipe("obj.horsey_white", 1, 10, listOf(Ingredient(PLANK))),
            ClockworkRecipe("obj.horsey_black", 1, 10, listOf(Ingredient(PLANK))),
            ClockworkRecipe("obj.horsey_grey", 1, 10, listOf(Ingredient(PLANK))),
            ClockworkRecipe("obj.brain_inv_wooden_cat", 1, 16, listOf(Ingredient(PLANK), FURS)),
            ClockworkRecipe(CLOCKWORK, 2, 8, listOf(Ingredient(STEEL_BAR))),
            ClockworkRecipe(
                "obj.poh_toy_soldier_unwound",
                3,
                13,
                listOf(Ingredient(CLOCKWORK), Ingredient(PLANK)),
            ),
            ClockworkRecipe(
                "obj.poh_toy_doll_unwound",
                3,
                18,
                listOf(Ingredient(CLOCKWORK), Ingredient(PLANK)),
            ),
            ClockworkRecipe(
                "obj.peng_suit_unwound",
                3,
                30,
                listOf(Ingredient(CLOCKWORK), Ingredient(PLANK), Ingredient("obj.silk")),
            ),
            ClockworkRecipe("obj.trail_sextant", 4, 23, listOf(Ingredient(STEEL_BAR))),
            ClockworkRecipe(
                "obj.trail_watch",
                4,
                28,
                listOf(Ingredient(STEEL_BAR), Ingredient(CLOCKWORK)),
            ),
            ClockworkRecipe(
                "obj.poh_toy_mouse_unwound",
                4,
                33,
                listOf(Ingredient(CLOCKWORK), Ingredient(PLANK)),
            ),
            ClockworkRecipe(
                "obj.poh_toy_cat",
                4,
                85,
                listOf(Ingredient(CLOCKWORK), Ingredient(PLANK)),
            ),
        )

    /** A repair bench tier: the repair bench, whetstone and armour stand, in build order. */
    val REPAIR_BENCHES: Map<String, Int> =
        mapOf("loc.poh_repair_1" to 1, "loc.poh_repair_2" to 2, "loc.poh_repair_3" to 3)

    class Reward(val obj: String?, val weight: Int, val count: IntRange = 1..1)

    /**
     * A broken item and what repairing it can give. [low] and [high] are the wiki's success chart
     * for [stat]: a failed roll destroys the item.
     */
    class Repair(
        val item: String,
        val bench: Int,
        val stat: String,
        val xp: Double,
        val low: Int,
        val high: Int,
        val rewards: List<Reward>,
    )

    val REPAIRS: List<Repair> =
        listOf(
            Repair(
                "obj.digsitearrow",
                1,
                "stat.fletching",
                8.0,
                180,
                240,
                listOf(
                    Reward("obj.iron_arrow", 57),
                    Reward("obj.bronze_arrow", 24),
                    Reward("obj.steel_arrow", 10),
                    Reward("obj.headless_arrow", 5),
                    Reward("obj.mithril_arrow", 4),
                ),
            ),
            Repair(
                "obj.digsitestaff",
                1,
                "stat.crafting",
                14.5,
                150,
                240,
                listOf(
                    Reward("obj.plainstaff", 57),
                    Reward("obj.magic_staff", 26),
                    Reward("obj.staff_of_water", 5),
                    Reward("obj.staff_of_fire", 4),
                    Reward("obj.staff_of_air", 4),
                    Reward("obj.staff_of_earth", 4),
                ),
            ),
            Repair(
                "obj.digsitesword",
                2,
                "stat.smithing",
                25.0,
                100,
                240,
                listOf(
                    Reward("obj.iron_sword", 20),
                    Reward("obj.iron_longsword", 37),
                    Reward("obj.bronze_sword", 17),
                    Reward("obj.bronze_longsword", 7),
                    Reward("obj.steel_sword", 6),
                    Reward("obj.steel_longsword", 4),
                    Reward("obj.mithril_sword", 1),
                    Reward("obj.mithril_longsword", 1),
                    Reward("obj.black_sword", 3),
                    Reward("obj.black_longsword", 2),
                    Reward("obj.coins", 1, 1500..1999),
                    Reward(null, 1),
                ),
            ),
            Repair(
                "obj.digsitearmour1",
                3,
                "stat.smithing",
                25.0,
                75,
                240,
                listOf(
                    Reward("obj.iron_platebody", 57),
                    Reward("obj.bronze_platebody", 24),
                    Reward("obj.steel_platebody", 10),
                    Reward("obj.black_platebody", 5),
                    Reward("obj.mithril_platebody", 4),
                ),
            ),
            Repair(
                "obj.digsitearmour2",
                3,
                "stat.smithing",
                25.0,
                70,
                240,
                listOf(
                    Reward("obj.iron_plateskirt", 57),
                    Reward("obj.iron_platelegs", 57),
                    Reward("obj.bronze_plateskirt", 24),
                    Reward("obj.bronze_platelegs", 24),
                    Reward("obj.steel_plateskirt", 10),
                    Reward("obj.steel_platelegs", 10),
                    Reward("obj.black_plateskirt", 5),
                    Reward("obj.black_platelegs", 5),
                    Reward("obj.mithril_plateskirt", 4),
                    Reward("obj.mithril_platelegs", 4),
                ),
            ),
        )
}
