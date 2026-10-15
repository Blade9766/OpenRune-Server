package org.rsmod.content.skills.hunter.rumours

import org.rsmod.content.skills.hunter.rumours.RumourHunter.Aco
import org.rsmod.content.skills.hunter.rumours.RumourHunter.Cervus
import org.rsmod.content.skills.hunter.rumours.RumourHunter.Gilman
import org.rsmod.content.skills.hunter.rumours.RumourHunter.Ornus
import org.rsmod.content.skills.hunter.rumours.RumourHunter.Teco
import org.rsmod.content.skills.hunter.rumours.RumourHunter.Wolf

enum class RumourHunter(
    val npc: String,
    val displayName: String,
    val level: Int,
    val xpModifier: Int,
    val sack: String,
    val assignedVarbit: String,
) {
    Gilman("npc.hg_gilman", "Huntmaster Gilman", 46, 50, "obj.hg_lootsack_t0", "varbit.hunter_rumour_gilman"),
    Cervus("npc.hg_cervus", "Guild Hunter Cervus", 57, 50, "obj.hg_lootsack_t1", "varbit.hunter_rumour_cervus"),
    Ornus("npc.hg_ornus", "Guild Hunter Ornus", 57, 50, "obj.hg_lootsack_t1", "varbit.hunter_rumour_ornus"),
    Aco("npc.hg_aco", "Guild Hunter Aco", 72, 55, "obj.hg_lootsack_t2", "varbit.hunter_rumour_aco"),
    Teco("npc.hg_teco", "Guild Hunter Teco", 72, 55, "obj.hg_lootsack_t2", "varbit.hunter_rumour_teco"),
    Wolf("npc.hg_wolf", "Guild Hunter Wolf", 91, 60, "obj.hg_lootsack_t3", "varbit.hunter_rumour_wolf"),
}

/**
 * How a rumour creature is caught. A rare part drops at 1/[rate] per catch and is guaranteed once
 * [pity] catches pass without one; the full guild hunter outfit lowers that to [outfitPity].
 */
enum class RumourMethod(val rate: Int, val pity: Int, val outfitPity: Int) {
    BirdSnare(20, 40, 38),
    BoxTrap(50, 100, 94),
    ButterflyNet(40, 80, 76),
    Deadfall(15, 30, 28),
    NetTrap(25, 50, 46),
    SpikedPit(15, 30, 28),
    Tracking(15, 30, 28),
    Herbiboar(7, 14, 12),
    Falconry(10, 20, 18),
    GoatPit(48, 96, 90),
}

/**
 * Hunters' Rumour creatures. [catchKey] is the enum name of the creature in its hunting method
 * (trap prey, butterfly, pitfall creature, tracking area or `Herbiboar`) reported on each catch.
 * New rumours are
 * appended so the ids stored in the assignment varbits stay stable.
 */
enum class Rumour(
    val displayName: String,
    val level: Int,
    val method: RumourMethod,
    val part: String,
    val hunters: Set<RumourHunter>,
    val catchKey: String = "",
) {
    TropicalWagtail("tropical wagtail", 19, RumourMethod.BirdSnare, "obj.hg_tailfeather", setOf(Gilman)),
    WildKebbit("wild kebbit", 23, RumourMethod.Deadfall, "obj.hg_kebbittuft", setOf(Gilman)),
    SapphireGlacialis(
        "sapphire glacialis",
        25,
        RumourMethod.ButterflyNet,
        "obj.hg_butterflywing_blue",
        setOf(Gilman),
    ),
    SwampLizard("swamp lizard", 29, RumourMethod.NetTrap, "obj.hg_lizardclaw", setOf(Gilman, Cervus)),
    SpinedLarupia(
        "spined larupia",
        31,
        RumourMethod.SpikedPit,
        "obj.hg_larupia_ear",
        setOf(Gilman, Ornus),
    ),
    BarbTailedKebbit("barb-tailed kebbit", 33, RumourMethod.Deadfall, "obj.hg_kebbittuft", setOf(Gilman)),
    SnowyKnight(
        "snowy knight",
        35,
        RumourMethod.ButterflyNet,
        "obj.hg_butterflywing_white",
        setOf(Gilman, Ornus),
    ),
    PricklyKebbit("prickly kebbit", 37, RumourMethod.Deadfall, "obj.hg_kebbittuft", setOf(Gilman)),
    HornedGraahk(
        "horned graahk",
        41,
        RumourMethod.SpikedPit,
        "obj.hg_graahk_horn",
        setOf(Gilman, Cervus),
    ),
    BlackWarlock(
        "black warlock",
        45,
        RumourMethod.ButterflyNet,
        "obj.hg_butterflywing_black",
        setOf(Gilman, Cervus),
    ),
    OrangeSalamander(
        "orange salamander",
        47,
        RumourMethod.NetTrap,
        "obj.hg_lizardclaw_orange",
        setOf(Gilman, Cervus, Ornus, Aco),
    ),
    RazorBackedKebbit(
        "razor-backed kebbit",
        49,
        RumourMethod.Tracking,
        "obj.hg_kebbittuft",
        setOf(Gilman, Cervus),
        catchKey = "RazorBacked",
    ),
    SabreToothedKebbit(
        "sabre-toothed kebbit",
        51,
        RumourMethod.Deadfall,
        "obj.hg_kebbittuft",
        setOf(Gilman, Cervus, Ornus, Aco, Teco),
    ),
    GreyChinchompa(
        "grey chinchompa",
        53,
        RumourMethod.BoxTrap,
        "obj.hg_chinchompatuft",
        setOf(Gilman, Cervus, Aco, Teco),
        catchKey = "Chinchompa",
    ),
    SabreToothedKyatt(
        "sabre-toothed kyatt",
        55,
        RumourMethod.SpikedPit,
        "obj.hg_kyatttooth",
        setOf(Gilman, Ornus, Aco, Teco),
    ),
    PyreFox("pyre fox", 57, RumourMethod.Deadfall, "obj.hg_foxfluff", setOf(Gilman, Cervus, Ornus)),
    RedSalamander(
        "red salamander",
        59,
        RumourMethod.NetTrap,
        "obj.hg_lizardclaw_red",
        setOf(Gilman, Ornus, Aco, Teco, Wolf),
    ),
    RedChinchompa(
        "red chinchompa",
        63,
        RumourMethod.BoxTrap,
        "obj.hg_chinchompatuft_red",
        RumourHunter.entries.toSet(),
        catchKey = "CarnivorousChinchompa",
    ),
    SunlightAntelope(
        "sunlight antelope",
        72,
        RumourMethod.SpikedPit,
        "obj.hg_antelopehoof_sun",
        setOf(Gilman, Aco, Teco, Wolf),
    ),
    SunlightMoth(
        "sunlight moth",
        75,
        RumourMethod.ButterflyNet,
        "obj.hg_mothwing_sun",
        setOf(Gilman, Cervus, Teco),
    ),
    TecuSalamander(
        "tecu salamander",
        79,
        RumourMethod.NetTrap,
        "obj.hg_lizardclaw_mountain",
        setOf(Gilman, Aco, Wolf),
    ),
    Herbiboar("herbiboar", 80, RumourMethod.Herbiboar, "obj.hg_herbytuft", setOf(Gilman, Teco, Wolf)),
    MoonlightMoth(
        "moonlight moth",
        85,
        RumourMethod.ButterflyNet,
        "obj.hg_mothwing_moon",
        setOf(Gilman, Aco, Wolf),
    ),
    MoonlightAntelope(
        "moonlight antelope",
        91,
        RumourMethod.SpikedPit,
        "obj.hg_antelopehoof_moon",
        setOf(Gilman, Wolf),
    ),
    SpottedKebbit(
        "spotted kebbit",
        43,
        RumourMethod.Falconry,
        "obj.hg_kebbittuft",
        setOf(Gilman, Cervus, Ornus),
    ),
    DarkKebbit(
        "dark kebbit",
        57,
        RumourMethod.Falconry,
        "obj.hg_kebbittuft",
        setOf(Gilman, Cervus, Aco, Teco),
    ),
    DashingKebbit(
        "dashing kebbit",
        69,
        RumourMethod.Falconry,
        "obj.hg_kebbittuft",
        setOf(Gilman, Aco, Teco, Wolf),
    ),
    EmbertailedJerboa(
        "embertailed jerboa",
        39,
        RumourMethod.BoxTrap,
        "obj.hg_jerboatail_large",
        setOf(Gilman, Ornus),
    ),
    WyrmscraigGoat(
        "Wyrmscraig goat",
        60,
        RumourMethod.GoatPit,
        "obj.hg_goat_pit_hoof",
        setOf(Gilman, Cervus, Teco),
    );

    val key: String
        get() = catchKey.ifEmpty { name }

    val id: Int
        get() = ordinal + 1

    companion object {
        fun byId(id: Int): Rumour? = entries.getOrNull(id - 1)
    }
}
