package org.rsmod.content.skills.construction.data

/**
 * Family crests, as Sir Renitee hands them out, and the heraldic items painted with them. Names,
 * requirements, levels and experience come from the Old School wiki.
 */
object Heraldry {
    enum class Requirement {
        NONE,
        QUEST,
        PRAYER,
        TOY_HORSEY,
        SKULLED,
    }

    /** [option] is what the player picks from Sir Renitee's list; [record] is how he reads it out. */
    enum class Crest(
        val key: String,
        val option: String,
        val record: String,
        val requirement: Requirement = Requirement.NONE,
        val quest: String? = null,
    ) {
        ARRAV("arrav", "Shield of Arrav", "the symbol of the Shield of Arrav", Requirement.QUEST, "quest_shieldofarrav"),
        ASGARNIA("asgarnia", "Asgarnia", "the symbol of Asgarnia"),
        DORGESHUUN("dorgeshuun", "Dorgeshuun symbol", "the Dorgeshuun brooch", Requirement.QUEST, "quest_losttribe"),
        DRAGON("dragon", "Dragon", "a dragon", Requirement.QUEST, "quest_dragonslayer1"),
        FAIRY("fairy", "Fairy", "a fairy", Requirement.QUEST, "quest_lostcity"),
        GUTHIX("guthix", "Guthix", "the symbol of Guthix", Requirement.PRAYER),
        HAM("ham", "HAM", "the symbol of the HAM cult"),
        HORSE("horse", "Horse", "a horse", Requirement.TOY_HORSEY),
        JOGRE("jogre", "Jogre", "a jungle ogre"),
        KANDARIN("kandarin", "Kandarin", "the symbol of Kandarin"),
        MISTHALIN("misthalin", "Misthalin", "the symbol of Misthalin"),
        MONEY("money", "Money", "a money bag"),
        SARADOMIN("saradomin", "Saradomin", "the symbol of Saradomin", Requirement.PRAYER),
        SKULL("skull", "Skull", "a skull", Requirement.SKULLED),
        VARROCK("varrock", "Varrock", "the symbol of Varrock"),
        ZAMORAK("zamorak", "Zamorak", "the symbol of Zamorak", Requirement.PRAYER);

        /** The value stored in the crest varp; zero means no crest has been assigned yet. */
        val id: Int
            get() = ordinal + 1

        companion object {
            fun of(id: Int): Crest? = entries.getOrNull(id - 1)
        }
    }

    /**
     * The crests a first, free assignment picks from. The wiki only says the first crest is random;
     * these are the ones nothing is required for, so nobody is handed a crest they could not choose.
     */
    val FREE_CRESTS: List<Crest> = Crest.entries.filter { it.requirement == Requirement.NONE && it != Crest.MONEY }

    const val CHANGE_COST: Int = 5_000
    const val MONEY_COST: Int = 500_000
    const val CONSTRUCTION_LEVEL: Int = 16
    const val PRAYER_LEVEL: Int = 70

    val TOY_HORSEYS: List<String> =
        listOf("obj.horsey_brown", "obj.horsey_white", "obj.horsey_black", "obj.horsey_grey")

    enum class Metal(val suffix: String, val helm: String, val kiteshield: String) {
        STEEL("", "obj.steel_full_helm", "obj.steel_kiteshield"),
        ADAMANT("_adamant", "obj.adamant_full_helm", "obj.adamant_kiteshield"),
        RUNE("_rune", "obj.rune_full_helm", "obj.rune_kiteshield"),
    }

    class Product(
        val output: String,
        val stand: Int,
        val level: Int,
        val xp: Double,
        val materials: List<Pair<String, Int>>,
    )

    /** The heraldry stands, in build order, mapped to their tier. */
    val STANDS: Map<String, Int> =
        mapOf("loc.poh_repair_4" to 1, "loc.poh_repair_5" to 2, "loc.poh_repair_6" to 3)

    /** Everything the stands can paint with [crest], helmets first. */
    fun products(crest: Crest): List<Product> =
        Metal.entries.map { metal ->
            Product("obj.poh_helmet_${crest.key}${metal.suffix}", 1, 38, 37.0, listOf(metal.helm to 1))
        } +
            Metal.entries.map { metal ->
                Product(
                    "obj.poh_shield_${crest.key}${metal.suffix}",
                    2,
                    43,
                    40.0,
                    listOf(metal.kiteshield to 1),
                )
            } +
            Product(
                "obj.poh_banner_${crest.key}",
                3,
                48,
                42.5,
                listOf("obj.woodplank" to 1, "obj.cloth" to 1),
            )

    /**
     * A throne room shield, as the build menu names it: painted with a placeholder crest, which
     * [crestDecor] swaps for the owner's when the house is put together.
     */
    fun crestDecor(wood: String): String = "loc.poh_decor_${wood}_${Crest.ARRAV.key}"

    private val CREST_DECOR = Regex("loc\\.poh_decor_(oak|teak|mahogany)_[a-z]+")

    fun isCrestDecor(built: String): Boolean = CREST_DECOR.matches(built)

    /** [built] painted with [crest], or null when it is not a crest shield. */
    fun crestDecor(built: String, crest: Crest?): String? {
        if (crest == null || !isCrestDecor(built)) {
            return null
        }
        return built.substringBeforeLast('_') + "_" + crest.key
    }
}
