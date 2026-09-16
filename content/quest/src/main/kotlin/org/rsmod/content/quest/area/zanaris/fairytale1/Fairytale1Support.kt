package org.rsmod.content.quest.area.zanaris.fairytale1

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.MAGIC_SECATEURS
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.map.CoordGrid

/** Whether the player carries or wears [obj]. */
internal fun ProtectedAccess.carries(obj: String): Boolean =
    player.inv.contains(obj) || player.worn.contains(obj)

internal fun Player.wieldsMagicSecateurs(): Boolean = righthand?.isType(MAGIC_SECATEURS) == true

internal object Fairytale1Coords {
    /** The gravestone in the yard behind Draynor Manor that hides the Draynor skull. */
    val DRAYNOR_GRAVE = CoordGrid(3106, 3384, 0)

    /**
     * Either side of the three-tile-thick wall south-west of the Zanaris wheat field. Its gap
     * locs sit at (2399, 4379) and (2397, 4379) with map-blocked ground between them, so squeezing
     * through has to step the player straight across to the far side.
     */
    val OUTSIDE_THE_WALL = CoordGrid(2400, 4379, 0)
    val INSIDE_THE_WALL = CoordGrid(2396, 4379, 0)

    /** The chamber at the far end of the tunnel, where the Tanglefoot waits. */
    val TANGLEFOOT_LAIR = CoordGrid(2374, 4392, 0)

    /** Everything west of the wall, where the tanglefeet live. */
    fun inTanglefootTunnel(coords: CoordGrid): Boolean =
        coords.level == 0 && coords.x in 2368..2398 && coords.z in 4352..4400
}
