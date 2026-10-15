package org.rsmod.content.quest.area.varrock.ragandboneman

/**
 * The eight specimens on the Odd Old Man's wish-list, in wish-list order. Each keeps its own obj
 * through every stage of cleaning, and [id] is what the pot-boiler records in
 * `varbit.rag_potboiler` while the specimen is in it.
 */
enum class Specimen(
    val id: Int,
    val label: String,
    val raw: String,
    val inVinegar: String,
    val polished: String,
    val source: String,
    val habitat: String,
) {
    GIANT_RAT(
        1,
        "Giant rat bone",
        "obj.rag_giant_rat_bone",
        "obj.rag_pot_giant_rat_bone",
        "obj.rag_polished_giant_rat_bone",
        "a giant rat",
        "Giant rats scurry about Lumbridge Swamp, south of Lumbridge.",
    ),
    UNICORN(
        2,
        "Unicorn bone",
        "obj.rag_unicorn_bone",
        "obj.rag_pot_unicorn_bone",
        "obj.rag_polished_unicorn_bone",
        "a unicorn",
        "Unicorns graze just south of the mine south-east of Varrock.",
    ),
    BEAR(
        3,
        "Bear ribs",
        "obj.rag_bear_bone",
        "obj.rag_pot_bear_bone",
        "obj.rag_polished_bear_bone",
        "a bear",
        "A black bear prowls beside the unicorns, south of the mine south-east of Varrock.",
    ),
    RAM(
        4,
        "Ram skull",
        "obj.rag_ram_bone",
        "obj.rag_pot_ram_bone",
        "obj.rag_polished_ram_bone",
        "a ram",
        "Rams graze among the sheep in the fields north of Lumbridge.",
    ),
    GOBLIN(
        5,
        "Goblin skull",
        "obj.rag_goblin_bone",
        "obj.rag_pot_goblin_bone",
        "obj.rag_polished_goblin_bone",
        "a goblin",
        "Goblins loiter east of the River Lum, over the bridge from Lumbridge Castle.",
    ),
    BIG_FROG(
        6,
        "Big frog leg",
        "obj.rag_medium_frog_bone",
        "obj.rag_pot_medium_frog_bone",
        "obj.rag_polished_medium_frog_bone",
        "a big frog",
        "Big frogs hop about Lumbridge Swamp. Only the big ones will do, not the small or giant.",
    ),
    MONKEY(
        7,
        "Monkey paw",
        "obj.rag_monkey_bone",
        "obj.rag_pot_monkey_bone",
        "obj.rag_polished_monkey_bone",
        "a monkey",
        "Monkeys swing through the Karamja jungle. Ships leave Port Sarim for Musa Point.",
    ),
    GIANT_BAT(
        8,
        "Giant bat wing",
        "obj.rag_giant_bat_bone",
        "obj.rag_pot_giant_bat_bone",
        "obj.rag_polished_giant_bat_bone",
        "a giant bat",
        "Giant bats roost in the cave under the Karamja volcano, north of Musa Point.",
    ),
    ;

    companion object {
        fun byId(id: Int): Specimen? = entries.firstOrNull { it.id == id }

        fun byRaw(obj: String): Specimen? = entries.firstOrNull { it.raw == obj }

        fun byInVinegar(obj: String): Specimen? = entries.firstOrNull { it.inVinegar == obj }

        fun byPolished(obj: String): Specimen? = entries.firstOrNull { it.polished == obj }
    }
}

/**
 * How far one specimen has got, from not found to handed over; later stages compare greater. The
 * colours are for the parchment of the quest journal and wish-list.
 */
enum class SpecimenState(val label: String, val journalColour: String) {
    MISSING("not yet found", "800000"),
    RAW("found, needs cleaning", "804000"),
    IN_VINEGAR("soaking in a pot of vinegar", "804000"),
    BOILING("in the pot-boiler", "804000"),
    BOILED("boiled clean, waiting in the pot-boiler", "804000"),
    POLISHED("polished", "006000"),
    DELIVERED("delivered", "006000"),
}
