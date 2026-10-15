package org.rsmod.content.quest.area.gnomestronghold.grandtree

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.area.varrock.demonslayer.fadeFromBlack
import org.rsmod.content.quest.area.varrock.demonslayer.fadeToBlack
import org.rsmod.map.CoordGrid

/** Tiles the Grand Tree scripts move players and npcs between. */
object GrandTree {
    /** The trapdoor on the Grand Tree's ground floor, north-west of the King. */
    val TRAPDOOR = CoordGrid(2463, 3497, 0)
    val SURFACE_LANDING = CoordGrid(2463, 3496, 0)

    /** Foot of the ladder up from the roots; the tunnel King stands just east of it. */
    val TUNNEL_LANDING = CoordGrid(2463, 9898, 0)
    val TUNNEL_NARNODE = CoordGrid(2464, 9897, 0)

    /** The main ladder through the tree; players land on the tile east of it on every level. */
    val MAIN_LADDER = CoordGrid(2466, 3495, 0)
    const val LADDER_LANDING_X = 2467
    const val LADDER_LANDING_Z = 3495

    /** Charlie's cage on the top floor sits west of the prison door. */
    val CELL = CoordGrid(2464, 3496, 3)
    val CELL_DOORSTEP = CoordGrid(2465, 3496, 3)
    val CELL_EXIT = CoordGrid(2466, 3496, 3)

    /** Where the glider comes down west of the Karamja shipyard; Errdo waits beside the wreck. */
    val CRASH_LANDING = CoordGrid(2917, 3055, 0)
    val CRASH_ERRDO = CoordGrid(2918, 3057, 0)

    /** The stronghold's south gate is two tiles deep; Femi's cart waits just outside it. */
    val INSIDE_GATE = CoordGrid(2461, 3386, 0)

    /** The shipyard gate hangs on the west edge of its tiles; the yard lies east of it. */
    const val SHIPYARD_GATE_X = 2945
    val SHIPYARD_GUARD_POST = CoordGrid(2944, 3040, 0)

    val TOWER_TRAPDOOR = CoordGrid(2487, 3464, 2)
    val TOWER_TREE_TOP = CoordGrid(2485, 3465, 2)
    val TOWER_TREE_BOTTOM = CoordGrid(2483, 3464, 1)

    /** Below the watchtower trapdoor: the broken ladder is one tile north-east of the landing. */
    val DEMON_LANDING = CoordGrid(2490, 9863, 0)
    val DEMON_SPAWN = CoordGrid(2486, 9866, 0)
    val GLOUGH_LAIR = CoordGrid(2489, 9865, 0)
    val GLOUGH_FLEE = CoordGrid(2478, 9868, 0)

    /** Errdo's post-quest glider network, by the route names painted on the glider map. */
    val GANDIUS = CoordGrid(2970, 2970, 0)
    val KAR_HEWO = CoordGrid(3283, 3213, 0)
    val LEMANTO_ANDRA = CoordGrid(3321, 3431, 0)
    val SINDARPOS = CoordGrid(2846, 3499, 0)
    val LEMANTOLLY_UNDRI = CoordGrid(2544, 2970, 0)

    const val TUNNEL_MIN_Z = 9000
    const val KARAMJA_MAX_Z = 3200

    const val LADDER_SEQ = "seq.human_reachforladder"
    const val SEARCH_SEQ = "seq.human_pickupfloor"
    const val GUARD_NAME = "Gnome guard"
    const val KING_NAME = "King Narnode Shareen"
}

/** The first of [candidates] a player can stand on, or the first one if the map says none. */
internal fun ProtectedAccess.freeTileNear(candidates: List<CoordGrid>): CoordGrid =
    candidates.firstOrNull { !mapBlocked(it) } ?: candidates.first()

/** [tile] if it can be stood on, otherwise the nearest of its four neighbours that can. */
internal fun ProtectedAccess.landNear(tile: CoordGrid): CoordGrid =
    freeTileNear(listOf(tile, tile.translateX(1), tile.translateX(-1), tile.translateZ(1), tile.translateZ(-1)))

/** Fades the screen out, drops the player on [dest] (or beside it, unless [exact]) and fades back in. */
internal suspend fun ProtectedAccess.fadeTeleport(dest: CoordGrid, exact: Boolean = false) {
    fadeToBlack()
    telejump(if (exact) dest else landNear(dest))
    delay(1)
    fadeFromBlack()
    closeFadeOverlay()
}
