package org.rsmod.content.skills.construction.data

import org.rsmod.map.CoordGrid

/**
 * Portal chamber destinations.
 *
 * Each portal space - 1 to the left of the chamber's door, 2 opposite it, 3 to its right - keeps
 * its destination in [varbit], and every chamber in the house shares the three. A destination is a
 * teleport spell, found by its cache name [spell] (and [spellbook] where two books share a name):
 * directing a portal costs a hundred times that spell's runes, needs its Magic level and quest, and
 * gives five times its experience, as the Old School wiki describes.
 *
 * A destination's stored value is its ordinal plus one, so new destinations only ever go on the end.
 * The Troll Stronghold and Weiss have no spell: they are the stony and icy basalt teleports, so
 * they land on a [fixed] spot, cost a hundred times the basalt's ingredients ([cost]) and need its
 * [quest], and give no experience. Respawn and Teleport to Boat are left out, having no fixed spot
 * to land on.
 */
object Portals {
    /**
     * A portal destination. [level] corrects a spell whose coordinates land on the wrong level, and
     * [alternate] is a two-way portal's second place to go. A destination with no [spell] is a
     * teleport item's instead: [teleportObj], landing on [fixed].
     */
    enum class Destination(
        val key: String,
        val label: String,
        val spell: String?,
        val spellbook: String? = null,
        val level: Int? = null,
        val alternate: Alternate? = null,
        val fixed: CoordGrid? = null,
        val cost: List<Pair<String, Int>> = emptyList(),
        val quest: String? = null,
        val teleportObj: String? = null,
    ) {
        ARCEUUS_LIBRARY("arceuus_library", "Arceuus Library", "Arceuus Library Teleport"),
        DRAYNOR_MANOR("draynor_manor", "Draynor Manor", "Draynor Manor Teleport"),
        BATTLEFRONT("battlefront", "Battlefront", "Battlefront Teleport"),
        VARROCK(
            "varrock",
            "Varrock",
            "Varrock Teleport",
            alternate = Alternate("varbit.varrock_ge_teleport", "Grand Exchange", CoordGrid(3164, 3487, 0)),
        ),
        MIND_ALTAR("mind_altar", "Mind Altar", "Mind Altar Teleport"),
        LUMBRIDGE("lumbridge", "Lumbridge", "Lumbridge Teleport"),
        FALADOR("falador", "Falador", "Falador Teleport"),
        SALVE_GRAVEYARD("salve_graveyard", "Salve Graveyard", "Salve Graveyard Teleport"),
        CAMELOT(
            "camelot",
            "Camelot",
            "Camelot Teleport",
            alternate = Alternate("varbit.seers_camelot_teleport", "Seers' Village", CoordGrid(2725, 3485, 0)),
        ),
        FENKENSTRAIN("fenkenstrain", "Fenkenstrain's Castle", "Fenkenstrain's Castle Teleport"),
        KOUREND("kourend", "Kourend Castle", "Kourend Castle Teleport"),
        ARDOUGNE("ardougne", "Ardougne", "Ardougne Teleport"),
        CIVITAS("fortis", "Civitas illa Fortis", "Civitas illa Fortis Teleport"),
        PADDEWWA("paddewwa", "Paddewwa", "Paddewwa Teleport"),
        WATCHTOWER(
            "yanille",
            "Watchtower",
            "Watchtower Teleport",
            level = 2,
            alternate = Alternate("varbit.yanille_teleport_location", "Yanille", CoordGrid(2544, 3095, 0)),
        ),
        SENNTISTEN("senntisten", "Senntisten", "Senntisten Teleport"),
        TROLLHEIM("trollheim", "Trollheim", "Trollheim Teleport"),
        WEST_ARDOUGNE("west_ardougne", "West Ardougne", "West Ardougne Teleport"),
        MARIM("marim", "Marim", "Ape Atoll Teleport", spellbook = "Standard", level = 1),
        HARMONY_ISLAND("harmony_island", "Harmony Island", "Harmony Island Teleport"),
        KHARYRLL("kharyrll", "Kharyrll", "Kharyrll Teleport"),
        LUNAR_ISLE("lunarisle", "Lunar Isle", "Moonclan Teleport"),
        CEMETERY("cemetery", "Cemetery", "Cemetery Teleport"),
        OURANIA("ourania", "Ourania", "Ourania Teleport"),
        LASSAR("lassar", "Lassar", "Lassar Teleport"),
        WATERBIRTH("waterbirth", "Waterbirth Island", "Waterbirth Teleport"),
        BARBARIAN("barbarian", "Barbarian Outpost", "Barbarian Teleport"),
        DAREEYAK("dareeyak", "Dareeyak", "Dareeyak Teleport"),
        KHAZARD("khazard", "Port Khazard", "Khazard Teleport"),
        BARROWS("barrows", "Barrows", "Barrows Teleport"),
        CARRALLANGAR("carrallangar", "Carrallanger", "Carrallanger Teleport"),
        FISHING_GUILD("fishingguild", "Fishing Guild", "Fishing Guild Teleport"),
        CATHERBY("catherby", "Catherby", "Catherby Teleport"),
        ICE_PLATEAU("iceplateau", "Ice Plateau", "Ice Plateau Teleport"),
        ANNAKARL("annakarl", "Annakarl", "Annakarl Teleport"),
        APE_ATOLL_DUNGEON("ape_atoll", "Ape Atoll Dungeon", "Ape Atoll Teleport", spellbook = "Arceuus"),
        GHORROCK("ghorrock", "Ghorrock", "Ghorrock Teleport"),

        /** Outside the stronghold's cave entrance; the roof needs the hard Fremennik diary, not tracked. */
        TROLL_STRONGHOLD(
            "stronghold",
            "Troll Stronghold",
            spell = null,
            fixed = CoordGrid(2845, 3694, 0),
            cost = listOf("obj.basalt" to 100, "obj.red_salt" to 100, "obj.green_salt" to 300),
            quest = MY_ARM,
            teleportObj = "obj.stronghold_teleport_basalt",
        ),
        WEISS(
            "weiss",
            "Weiss",
            spell = null,
            fixed = CoordGrid(2846, 3938, 0),
            cost = listOf("obj.basalt" to 100, "obj.red_salt" to 100, "obj.blue_salt" to 300),
            quest = MY_ARM,
            teleportObj = "obj.weiss_teleport_basalt",
        );

        /** This destination's portal in a frame, from the frame's empty loc. */
        fun portal(frame: String): String = frame.removeSuffix(EMPTY_SUFFIX) + "_" + key

        val stored: Int
            get() = ordinal + 1

        companion object {
            fun of(stored: Int): Destination? = entries.getOrNull(stored - 1)

            fun ofKey(key: String): Destination? = entries.firstOrNull { it.key == key }

            fun ofTeleportObj(obj: String): Destination? = entries.firstOrNull { it.teleportObj == obj }
        }
    }

    /**
     * The second place a two-way portal can go: [varbit] picks which one its first op leads to, and
     * the portal's Toggle op flips it. The wiki gates the toggle behind achievement diaries, which
     * this server does not track, so anyone can toggle.
     */
    class Alternate(val varbit: String, val label: String, val coords: CoordGrid)

    /** Each portal space's destination, by space number 1 to 3. */
    val VARBITS: List<String> = (1..3).map { "varbit.poh_portal_$it" }

    /** The empty frames, as built: teak, mahogany and marble. */
    val FRAMES: List<String> =
        listOf("loc.poh_portal_teak_empty", "loc.poh_portal_mag_empty", "loc.poh_portal_marble_empty")

    const val EMPTY_SUFFIX: String = "_empty"

    const val MY_ARM: String = "quest_makingfriendswithmyarm"

    /** A cost's item and its banknote both count towards it, as the wiki says they do for basalt. */
    val NOTES: Map<String, String> = mapOf("obj.basalt" to "obj.cert_basalt")

    /** Directing a portal takes a hundred casts' runes and gives five casts' experience. */
    const val RUNE_MULTIPLIER: Int = 100
    const val XP_MULTIPLIER: Double = 5.0

    /** The centrepieces that can direct portals. */
    val FOCI: List<String> =
        listOf("loc.poh_teleport_centrepiece", "loc.poh_teleport_centrepiece_grand", "loc.poh_scrying_pool")
}
