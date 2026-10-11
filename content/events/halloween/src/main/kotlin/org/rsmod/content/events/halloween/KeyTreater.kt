package org.rsmod.content.events.halloween

enum class KeyTreater(val npcs: List<String>, val varbit: String, val pointer: String) {
    KingRoald(
        listOf("npc.king_roald"),
        "varbit.hw22_treated_king_roald",
        "You might like to pay King Roald in Varrock a visit. I hear he's being very generous " +
            "with treats!",
    ),
    Aggie(
        listOf("npc.aggie", "npc.aggie_1op", "npc.aggie_2ops"),
        "varbit.hw22_treated_aggie",
        "You know who else would love to see a colourful costume? Aggie in Draynor Village!",
    ),
    Hetty(
        listOf("npc.hetty"),
        "varbit.hw22_treated_hetty",
        "You know, Hetty in Rimmington loves a good spook as well! You should show her your " +
            "costume!",
    ),
    DukeHoracio(
        listOf("npc.duke_of_lumbridge"),
        "varbit.hw22_treated_duke_horacio",
        "I hear Duke Horacio in Lumbridge is welcoming all Trick-or-Treaters with a generous " +
            "supply of treats!",
    ),
    PartyPete(
        listOf("npc.partyroom_pete"),
        "varbit.hw22_treated_party_pete",
        "I hear Party Pete in Falador loves a bit of Trick-or-Treating and has plenty of treats " +
            "to hand out!",
    ),
    Iffie(
        listOf("npc.varrock_granny_1"),
        "varbit.hw23_treated_iffie",
        "You might like to pay Iffie in Varrock a visit. I hear she's being very generous with " +
            "treats and stories!",
    ),
    Ned(
        listOf("npc.ned"),
        "varbit.hw23_treated_ned",
        "You know who else would love to see a colourful costume? Ned in Draynor Village!",
    ),
    LumbridgeGuide(
        listOf("npc.lumbridge_guide"),
        "varbit.hw23_treated_lumbridge_guide",
        "I hear the Lumbridge Guide is welcoming all Trick-or-Treaters with a generous supply " +
            "of treats!",
    ),
    TownCrier(
        listOf("npc.pmod_town_crier_falador"),
        "varbit.hw23_treated_town_crier",
        "I hear the Town crier in Falador loves a bit of Trick-or-Treating and has plenty of " +
            "treats to hand out!",
    ),
    Elsie(
        listOf("npc.varrock_granny_2"),
        "varbit.hw23_treated_elsie",
        "You know, Elsie in the Varrock church loves a good spook as well! You should show her " +
            "your costume!",
    ),
}
