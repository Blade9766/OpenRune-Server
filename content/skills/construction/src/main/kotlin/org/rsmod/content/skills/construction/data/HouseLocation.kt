package org.rsmod.content.skills.construction.data

import org.rsmod.map.CoordGrid

/**
 * A town whose portal leads to the player's house, and where they land on the way back out.
 * [board] is the house advertisement board beside the portal, and [boardId] the number the board's
 * clientscripts know the town by.
 */
enum class HouseLocation(
    val label: String,
    val level: Int,
    val cost: Int,
    val portal: String,
    val arrive: CoordGrid,
    val board: String,
    val boardId: Int,
) {
    RIMMINGTON("Rimmington", 1, 5_000, "loc.poh_rimmington_portal", CoordGrid(2954, 3224), "loc.poh_board_rimmington", 1),
    TAVERLEY("Taverley", 10, 5_000, "loc.poh_taverly_portal", CoordGrid(2894, 3465), "loc.poh_board_taverly", 2),
    POLLNIVNEACH(
        "Pollnivneach",
        20,
        7_500,
        "loc.poh_pollnivneach_portal",
        CoordGrid(3341, 3003),
        "loc.poh_board_pollnivneach",
        3,
    ),
    HOSIDIUS("Hosidius", 25, 8_750, "loc.poh_kourend_portal", CoordGrid(1743, 3517), "loc.poh_board_kourend", 8),
    RELLEKKA("Rellekka", 30, 10_000, "loc.poh_rellekka_portal", CoordGrid(2671, 3631), "loc.poh_board_rellekka", 4),
    BRIMHAVEN("Brimhaven", 40, 15_000, "loc.poh_brimhaven_portal", CoordGrid(2758, 3178), "loc.poh_board_brimhaven", 5),
    YANILLE("Yanille", 50, 25_000, "loc.poh_yanille_portal", CoordGrid(2545, 3099), "loc.poh_board_yanille", 6),
    PRIFDDINAS("Prifddinas", 70, 50_000, "loc.poh_prifddinas_portal", CoordGrid(3240, 6079), "loc.poh_board_prifddinas", 9);

    companion object {
        fun forPortal(loc: String): HouseLocation? = entries.firstOrNull { it.portal == loc }

        fun forBoardId(id: Int): HouseLocation? = entries.firstOrNull { it.boardId == id }
    }
}
