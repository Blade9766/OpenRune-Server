package org.rsmod.content.skills.construction.data

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

    val doorLeft: String
        get() = "loc.poh_hotspot_doorl_$doorHotspot"

    val doorRight: String
        get() = "loc.poh_hotspot_doorr_$doorHotspot"
}
