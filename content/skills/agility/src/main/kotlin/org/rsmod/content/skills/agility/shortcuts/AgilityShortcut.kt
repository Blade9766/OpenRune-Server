package org.rsmod.content.skills.agility.shortcuts

import org.rsmod.content.skills.agility.AgilityAnims
import org.rsmod.map.CoordGrid

/** How a player crosses a shortcut. Paths are given from side A to side B and reversed as needed. */
sealed class ShortcutMove {
    /** Play [seq] for [ticks] cycles and appear on the far side. Chains between levels. */
    data class Climb(val seq: String = AgilityAnims.CLIMB_LADDER, val ticks: Int = 2) : ShortcutMove()

    /** Climb across a rock face one tile per tick with the climbing walk. Rocks. */
    data object Scramble : ShortcutMove()

    /**
     * Climb over a low obstacle with a glide that starts once [seq] has lifted the player off the
     * ground. Crumbling walls, broken windows.
     */
    data class ClimbOver(val seq: String = AgilityAnims.CRUMBLED_WALL) : ShortcutMove()

    /**
     * Glide to the far side over [ticks] cycles (the length of the animation when null) while
     * [seq] plays. Fences, walls, ledges.
     */
    data class Jump(val seq: String = AgilityAnims.JUMP_UP, val ticks: Int? = null) : ShortcutMove()

    /** Play [enter], pass through after [ticks] cycles and play [leave]. Cracks and long pipes. */
    data class Squeeze(
        val enter: String = AgilityAnims.CRACK_ENTER,
        val leave: String = AgilityAnims.CRACK_LEAVE,
        val ticks: Int = 2,
    ) : ShortcutMove()

    /**
     * Drop into a hole, shuffle underground one tile per tick to the far side and climb out.
     * Underwall tunnels.
     */
    data class Tunnel(
        val enter: String = AgilityAnims.TUNNEL_ENTER,
        val walk: String = AgilityAnims.TUNNEL_WALK,
        val leave: String = AgilityAnims.TUNNEL_EXIT,
    ) : ShortcutMove()

    /** Balance across [tiles] (the tiles between the two sides) one per tick. Log balances. */
    data class Balance(val tiles: List<CoordGrid>) : ShortcutMove()

    /** Hop from stone to stone across [stones] (the tiles between the two sides). */
    data class Hop(val stones: List<CoordGrid>) : ShortcutMove()

    /** Squeeze through an obstacle pipe three tiles at a time. */
    data object Pipe : ShortcutMove()

    companion object {
        val PIPE: Squeeze = Squeeze(AgilityAnims.PIPE_SQUEEZE, AgilityAnims.PIPE_UNSQUEEZE, ticks = 3)
    }
}

/** A quest that unlocks a shortcut: its dbrow [key] and the [name] shown to the player. */
data class ShortcutQuest(val key: String, val name: String)

/**
 * A two-way agility shortcut between [sideA] and [sideB]. The player is taken to whichever side is
 * further from them. A loc may be shared by several shortcuts (the same crack model is reused
 * around the world), in which case the shortcut nearest the player is used.
 *
 * When [apRange] is greater than zero the shortcut also starts from up to that many tiles away with
 * a line of sight to the loc, for locs the route finder can never reach (a stepping stone in a
 * river, a rock face behind blocked scree).
 *
 * A [quest] must be completed before the shortcut can be used, and a [oneWay] shortcut only takes
 * the player from [sideA] to [sideB].
 */
data class AgilityShortcut(
    val name: String,
    val level: Int,
    val xp: Double,
    val locs: List<String>,
    val sideA: CoordGrid,
    val sideB: CoordGrid,
    val move: ShortcutMove,
    val apRange: Int = 0,
    val quest: ShortcutQuest? = null,
    val oneWay: Boolean = false,
) {
    init {
        require(sideA != sideB) { "Shortcut '$name' needs two distinct sides." }
        require(locs.isNotEmpty()) { "Shortcut '$name' needs at least one loc." }
    }

    /** Distance from [coords] to the nearest side, with other levels pushed far away. */
    fun distanceTo(coords: CoordGrid): Int = minOf(coords.distanceTo(sideA), coords.distanceTo(sideB))

    /** True when a player at [coords] should be taken from [sideA] to [sideB]. */
    fun startsFromA(coords: CoordGrid): Boolean =
        coords.distanceTo(sideA) <= coords.distanceTo(sideB)

    private fun CoordGrid.distanceTo(other: CoordGrid): Int =
        chebyshevDistance(other) + LEVEL_PENALTY * kotlin.math.abs(level - other.level)

    private companion object {
        const val LEVEL_PENALTY = 1000
    }
}
