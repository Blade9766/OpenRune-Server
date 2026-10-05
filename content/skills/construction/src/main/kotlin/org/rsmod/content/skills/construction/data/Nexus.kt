package org.rsmod.content.skills.construction.data

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.map.CoordGrid

/**
 * The portal nexus and its mounted amulets.
 *
 * The cache lists every nexus destination in `enum.poh_nexus_destinations`: ids 1 to 41 are the
 * teleports themselves, and a two-way one's second place sits at its id plus [ALTERNATE_OFFSET]
 * (the Grand Exchange, Seers' Village, Yanille). Each destination's struct gives its name, its
 * runes and counts - already a thousand casts' worth - its Magic level and the teleport spell
 * behind it. The owner's nexus keeps its teleports in order in the `poh_nexus_tele_<slot>` varbits,
 * a destination id each, and its left-click teleport in `varbit.poh_nexus_left_click`; how many
 * slots there are depends on the nexus built.
 */
object Nexus {
    const val ALTERNATE_OFFSET: Int = 150

    /** A nexus destination as the cache describes it. */
    class Destination(
        val id: Int,
        val label: String,
        val runes: List<Pair<Int, Int>>,
        val level: Int,
        val spellObj: Int?,
        val alternate: Int?,
        val primary: Int?,
    )

    val destinations: List<Destination> by lazy {
        val enum = ServerCacheManager.getEnum(ENUM.asRSCM(RSCMType.ENUM)) ?: return@lazy emptyList()
        enum.values.entries
            .map { (key, value) -> (key as Number).toInt() to (value as Number).toInt() }
            .sortedBy { it.first }
            .mapNotNull { (id, struct) -> destination(id, struct) }
    }

    private fun destination(id: Int, struct: Int): Destination? {
        val params = ServerCacheManager.getStruct(struct)?.params ?: return null
        fun int(name: String): Int? = (params[param(name)] as? Number)?.toInt()
        val runes =
            (1..RUNE_TYPES).mapNotNull { index ->
                val rune = int("poh_nexus_dest_rune_$index") ?: return@mapNotNull null
                val count = int("poh_nexus_dest_count_$index") ?: return@mapNotNull null
                rune to count
            }
        return Destination(
            id = id,
            label = params[param("poh_nexus_dest_name")]?.toString() ?: return null,
            runes = runes,
            level = int("poh_nexus_dest_level") ?: 1,
            spellObj = int("poh_nexus_dest_spell"),
            alternate = if (int("poh_nexus_dest_alternate") != null && id < ALTERNATE_OFFSET) id + ALTERNATE_OFFSET else null,
            primary = if (id > ALTERNATE_OFFSET) id - ALTERNATE_OFFSET else null,
        )
    }

    private fun param(name: String): Int = "param.$name".asRSCM(RSCMType.PARAM)

    fun of(id: Int): Destination? = destinations.firstOrNull { it.id == id }

    private const val RUNE_TYPES = 4
    private const val ENUM = "enum.poh_nexus_destinations"

    /** The teleports a nexus holds, in the order its menu lists them. */
    val SLOTS: List<String> = (1..45).map { "varbit.poh_nexus_tele_$it" }

    const val LEFT_CLICK: String = "varbit.poh_nexus_left_click"

    /**
     * The interfaces' working copies of [SLOTS] and [LEFT_CLICK], which the configuration interface
     * edits and the teleport menu reads, and which nexus they belong to (1 marble to 3 crystalline).
     */
    val TEMP_SLOTS: List<String> = (1..45).map { "varbit.poh_nexus_tele_${it}_temp" }
    const val LEFT_CLICK_TEMP: String = "varbit.poh_nexus_left_click_temp"
    const val TIER_VARBIT: String = "varbit.poh_nexus_id"

    /** Destination ids in the order the configuration interface lists them, its rows numbered from 1. */
    val ORDER: List<Int> by lazy {
        val enum = ServerCacheManager.getEnum(ORDER_ENUM.asRSCM(RSCMType.ENUM)) ?: return@lazy emptyList()
        enum.values.entries
            .map { (key, value) -> (key as Number).toInt() to (value as Number).toInt() }
            .sortedBy { it.first }
            .map { it.second }
    }

    private const val ORDER_ENUM = "enum.poh_nexus_order"

    /** A built nexus, by its loc and how many teleports it holds, as the wiki gives them. */
    enum class Tier(val loc: String, val slots: Int) {
        MARBLE("loc.poh_nexus_portal_1", 4),
        GILDED("loc.poh_nexus_portal_2", 8),
        CRYSTALLINE("loc.poh_nexus_portal_3", 41),
    }

    /**
     * A mounted amulet: its plain loc, its [varbit] holding the left-click destination (0 for none),
     * and its destinations in varbit order, each with the loc showing it as the left-click.
     */
    enum class Amulet(val label: String, val loc: String, val varbit: String, val places: List<Place>) {
        XERIC(
            "Xeric's talisman",
            "loc.poh_amulet_xeric",
            "varbit.poh_nexus_xeric",
            listOf(
                Place("Xeric's Lookout", "loc.poh_amulet_xeric_lookout", CoordGrid(1579, 3530, 0)),
                Place("Xeric's Glade", "loc.poh_amulet_xeric_glade", CoordGrid(1752, 3566, 0)),
                Place("Xeric's Inferno", "loc.poh_amulet_xeric_inferno", CoordGrid(1504, 3815, 0)),
                Place("Xeric's Heart", "loc.poh_amulet_xeric_heart", CoordGrid(1644, 3673, 0)),
                Place("Xeric's Honour", "loc.poh_amulet_xeric_honour", CoordGrid(1254, 3560, 0)),
            ),
        ),
        DIGSITE(
            "Digsite pendant",
            "loc.poh_amulet_digsite",
            "varbit.poh_nexus_digsite",
            listOf(
                Place("Digsite", "loc.poh_amulet_dig_digsite", CoordGrid(3341, 3445, 0)),
                Place("Fossil Island", "loc.poh_amulet_dig_fossil", CoordGrid(3763, 3869, 1)),
                Place("Lithkren", "loc.poh_amulet_dig_lithkren", CoordGrid(3549, 10456, 0)),
            ),
        );

        /** The loc showing [leftClick] (1-based, 0 for none). */
        fun shown(leftClick: Int): String = places.getOrNull(leftClick - 1)?.loc ?: loc

        val locs: List<String>
            get() = listOf(loc) + places.map { it.loc }

        companion object {
            fun ofLoc(loc: String): Amulet? = entries.firstOrNull { loc in it.locs }
        }
    }

    class Place(val label: String, val loc: String, val coords: CoordGrid)
}
