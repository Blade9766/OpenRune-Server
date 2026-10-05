package org.rsmod.content.skills.construction.data

/**
 * The paintings Sir Renitee sells for the quest hall. A portrait or landscape is offered only once
 * every quest behind it is done; a map needs quest points. Prices and requirements come from the
 * Old School wiki.
 */
object Paintings {
    class Painting(
        val option: String,
        val obj: String,
        val cost: Int,
        val quests: List<String> = emptyList(),
        val questPoints: Int = 0,
    )

    private const val PREFIX = "obj.poh_unframed_painting_"
    private const val PORTRAIT_COST = 1_000
    private const val LANDSCAPE_COST = 2_000
    private const val MAP_COST = 1_000

    val PORTRAITS: List<Painting> =
        listOf(
            Painting(
                "King Arthur",
                PREFIX + "kingarthur",
                PORTRAIT_COST,
                listOf("quest_merlinscrystal", "quest_holygrail"),
            ),
            Painting("Elena of Ardougne", PREFIX + "elena", PORTRAIT_COST, listOf("quest_plaguecity")),
            Painting(
                "King Alvis of Keldagrim",
                PREFIX + "giantdwarf",
                PORTRAIT_COST,
                listOf("quest_giantdwarf"),
            ),
            Painting(
                "The Prince and Princess of Miscellania",
                PREFIX + "prince+princess",
                PORTRAIT_COST,
                listOf("quest_throneofmiscellania"),
            ),
        )

    val LANDSCAPES: List<Painting> =
        listOf(
            Painting(
                "The River Lum",
                PREFIX + "lumbridge",
                LANDSCAPE_COST,
                listOf(
                    "quest_cooksassistant",
                    "quest_runemysteries",
                    "quest_sheepshearer",
                    "quest_restlessghost",
                ),
            ),
            Painting(
                "The Kharid desert",
                PREFIX + "desert",
                LANDSCAPE_COST,
                listOf("quest_princealirescue", "quest_touristtrap", "quest_feud", "quest_golem"),
            ),
            Painting(
                "Morytania",
                PREFIX + "morytania",
                LANDSCAPE_COST,
                listOf(
                    "quest_ghostsahoy",
                    "quest_shadesofmortton",
                    "quest_creatureoffenkenstrain",
                    "quest_hauntedmine",
                ),
            ),
            Painting(
                "Karamja",
                PREFIX + "karamja",
                LANDSCAPE_COST,
                listOf("quest_piratestreasure", "quest_shilovillage", "quest_taibwowannaitrio"),
            ),
            Painting("Isafdar", PREFIX + "istafar", LANDSCAPE_COST, listOf("quest_rovingelves")),
        )

    val MAPS: List<Painting> =
        listOf(
            Painting("Small", PREFIX + "small_map", MAP_COST, questPoints = 51),
            Painting("Medium", PREFIX + "medium_map", MAP_COST, questPoints = 101),
            Painting("Large", PREFIX + "large_map", MAP_COST, questPoints = 151),
        )
}
