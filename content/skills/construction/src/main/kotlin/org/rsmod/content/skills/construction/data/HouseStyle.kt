package org.rsmod.content.skills.construction.data

import org.rsmod.map.zone.ZoneKey

/**
 * A house decoration style.
 *
 * Every style is a complete copy of the room templates, and the copies are stacked on the four
 * levels of the same map squares: level 0 through 3 of map square 29 are Rimmington, Lumbridge,
 * Pollnivneach and Rellekka, and so on. That is why a style is nothing more than a template block
 * plus a level - picking one changes which zone every room is copied from, and with it the walls,
 * windows and doors.
 *
 * [doorHotspot] names the door hotspot pair authored on that level, and [wall] the plain wall used
 * to close a doorway that leads nowhere. A template's windows are all `loc.poh_dynamic_window`,
 * a placeholder the server swaps for the style's own [window].
 */
enum class HouseStyle(
    val label: String,
    val level: Int,
    val cost: Int,
    val blockZoneX: Int,
    val templateLevel: Int,
    val doorHotspot: String,
    val wall: String,
    val window: String,
) {
    BASIC_WOOD("Basic wood", 1, 5_000, 232, 0, "rimmington", "loc.village_wall", "loc.village_wall_window"),
    BASIC_STONE("Basic stone", 10, 5_000, 232, 1, "lumbridge", "loc.brickwall", "loc.brickwall_window"),
    WHITEWASHED_STONE("Whitewashed stone", 20, 7_500, 232, 2, "pollnivneach", "loc.desertwall", "loc.desert_wall_window"),
    FREMENNIK_WOOD("Fremennik-style wood", 30, 10_000, 232, 3, "rellekka", "loc.viking_longhall_wall_inner", "loc.viking_longhall_wall_window_inner"),
    TROPICAL_WOOD("Tropical wood", 40, 15_000, 240, 0, "brimhaven", "loc.poh_timberwall", "loc.timberwall_with_window_2"),
    FANCY_STONE("Fancy stone", 50, 25_000, 240, 1, "yanille", "loc.yanille_poh_wall", "loc.yanille_poh_wall_window"),
    DEATHLY_MANSION("Deathly mansion", 60, 50_000, 240, 2, "deathly", "loc.deathly_poh_wall", "loc.deathly_poh_wall_window"),
    TWISTED("Twisted", 70, 50_000, 240, 3, "twisted", "loc.twisted_poh_wall_plain", "loc.twisted_poh_wall_window_inner"),
    HOSIDIUS("Hosidius", 25, 12_500, 248, 0, "hosidius", "loc.hosidius_poh_wall", "loc.hosidius_poh_wall_window"),
    CIVITAS("Civitas illa Fortis", 35, 25_000, 248, 2, "civitas", "loc.civitas_poh_wall_default", "loc.civitas_poh_wall_window"),
    CANIFIS("Canifis", 45, 25_000, 248, 3, "canifis", "loc.canifis_poh_wall_plain", "loc.canifis_poh_wall_window_inner");

    /** The block's lawn - grass, sand or mud by style - laid over every empty ground-floor cell. */
    val grassZone: ZoneKey
        get() = ZoneKey(blockZoneX + GRASS_OFFSET_X, FILLER_ZONE_Z, templateLevel)

    /** The block's solid rock, laid over every empty cell of a house with a dungeon. */
    val rockZone: ZoneKey
        get() = ZoneKey(blockZoneX + ROCK_OFFSET_X, FILLER_ZONE_Z, templateLevel)

    val doorLeft: String
        get() = "loc.poh_hotspot_doorl_$doorHotspot"

    val doorRight: String
        get() = "loc.poh_hotspot_doorr_$doorHotspot"

    /**
     * The double door hung in this style's doorways. Each door hotspot is a ghost of its style's door
     * (the models sit side by side in the cache), except basic stone, which has no door of its own
     * and takes basic wood's.
     */
    val doors: HouseDoors
        get() =
            when (this) {
                BASIC_WOOD,
                BASIC_STONE -> HouseDoors("loc.village_door_l", "loc.village_door_r")
                WHITEWASHED_STONE -> HouseDoors("loc.desert_door_l", "loc.desert_door_r")
                FREMENNIK_WOOD -> HouseDoors("loc.rellekka_poh_doubledoorl", "loc.rellekka_poh_doubledoor")
                TROPICAL_WOOD -> HouseDoors("loc.timberwall_doorl", "loc.timberwall_door")
                FANCY_STONE -> HouseDoors("loc.yanille_poh_double_doorl", "loc.yanille_poh_double_door")
                DEATHLY_MANSION -> HouseDoors("loc.deathly_poh_double_doorl", "loc.deathly_poh_double_door")
                TWISTED -> HouseDoors("loc.twisted_poh_doubledoorl", "loc.twisted_poh_doubledoor")
                HOSIDIUS -> HouseDoors("loc.hosidius_poh_doubledoorl", "loc.hosidius_poh_doubledoor")
                CIVITAS -> HouseDoors("loc.civitas_poh_door_l", "loc.civitas_poh_door_r")
                CANIFIS -> HouseDoors("loc.canifis_poh_doubledoorl", "loc.canifis_poh_doubledoor")
            }

    private companion object {
        const val GRASS_OFFSET_X = 1
        const val ROCK_OFFSET_X = 3
        const val FILLER_ZONE_Z = 880
    }
}

/** A style's double door: each panel closed, and the `_open` loc it swings to. */
class HouseDoors(val left: String, val right: String) {
    val leftOpen: String
        get() = left + "_open"

    val rightOpen: String
        get() = right + "_open"
}
