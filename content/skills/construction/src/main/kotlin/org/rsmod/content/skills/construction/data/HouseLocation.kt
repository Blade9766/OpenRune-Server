package org.rsmod.content.skills.construction.data

import org.rsmod.map.CoordGrid

/** A town whose portal leads to the player's house, and where they land on the way back out. */
enum class HouseLocation(
    val label: String,
    val level: Int,
    val cost: Int,
    val portal: String,
    val arrive: CoordGrid,
) {
    RIMMINGTON("Rimmington", 1, 5_000, "loc.poh_rimmington_portal", CoordGrid(2954, 3224)),
    TAVERLEY("Taverley", 10, 5_000, "loc.poh_taverly_portal", CoordGrid(2894, 3465)),
    POLLNIVNEACH("Pollnivneach", 20, 7_500, "loc.poh_pollnivneach_portal", CoordGrid(3341, 3003)),
    HOSIDIUS("Hosidius", 25, 8_750, "loc.poh_kourend_portal", CoordGrid(1743, 3517)),
    RELLEKKA("Rellekka", 30, 10_000, "loc.poh_rellekka_portal", CoordGrid(2671, 3631)),
    BRIMHAVEN("Brimhaven", 40, 15_000, "loc.poh_brimhaven_portal", CoordGrid(2758, 3178)),
    YANILLE("Yanille", 50, 25_000, "loc.poh_yanille_portal", CoordGrid(2545, 3099)),
    PRIFDDINAS("Prifddinas", 70, 50_000, "loc.poh_prifddinas_portal", CoordGrid(3240, 6079));

    companion object {
        fun forPortal(loc: String): HouseLocation? = entries.firstOrNull { it.portal == loc }
    }
}
