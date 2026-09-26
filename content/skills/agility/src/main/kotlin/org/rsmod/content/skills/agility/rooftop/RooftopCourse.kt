package org.rsmod.content.skills.agility.rooftop

import kotlin.math.sign
import org.rsmod.content.skills.agility.AgilityAnims
import org.rsmod.content.skills.agility.BalanceStyle
import org.rsmod.content.skills.agility.shortcuts.ShortcutQuest
import org.rsmod.map.CoordGrid

/**
 * The lap-based agility courses, with the Agility level needed to start them and the marks of
 * grace chance ([markNumerator] in [markDenominator]) rolled when a full lap is completed. A
 * [quest] must be completed before any obstacle of the course can be used.
 */
enum class RooftopCourse(
    val displayName: String,
    val level: Int,
    val markNumerator: Int,
    val markDenominator: Int,
    val quest: ShortcutQuest? = null,
) {
    Gnome("Gnome Stronghold", 1, 1, 3),
    Draynor("Draynor Village", 1, 1, 3),
    AlKharid("Al Kharid", 20, 1, 3),
    Varrock("Varrock", 30, 1, 3),
    Barbarian(
        "Barbarian Outpost",
        35,
        1,
        3,
        ShortcutQuest("miniquest_barcrawl", "Alfred Grimhand's Barcrawl"),
    ),
    Canifis("Canifis", 40, 2, 3),
    Wilderness("Wilderness", 49, 0, 1),
    Falador("Falador", 50, 1, 5),
    Seers("Seers' Village", 60, 1, 3),
    Pollnivneach("Pollnivneach", 70, 1, 3),
    Rellekka("Rellekka", 80, 1, 3),
    Ardougne("Ardougne", 90, 1, 3),
}

/** How the player is moved once an obstacle is started. */
sealed class ObstacleMove {
    abstract val destination: CoordGrid

    /**
     * Play [seq] for [ticks] cycles, then appear on [dest]. Used for walls, trees and baskets. When
     * [ticks] is null the move lasts as long as the animation, rounded down to whole ticks.
     */
    data class Climb(
        val dest: CoordGrid,
        val seq: String = AgilityAnims.CLIMB_LADDER,
        val ticks: Int? = null,
    ) : ObstacleMove() {
        override val destination: CoordGrid get() = dest
    }

    /**
     * Glide to [dest] over [ticks] cycles with an `exactmove`. Used for gaps, swings and edges. When
     * [ticks] is null the glide lasts as long as the animation, rounded down to whole ticks.
     *
     * A leap to another level glides on [glideLevel], the starting level unless given, and changes
     * level on landing. Set it where the starting level has no terrain past the edge (the client
     * then draws the glide rising instead of falling) or where the drop was authored into another
     * level's tile heights.
     */
    data class Leap(
        val dest: CoordGrid,
        val seq: String = AgilityAnims.JUMP,
        val ticks: Int? = null,
        val glideLevel: Int? = null,
    ) : ObstacleMove() {
        override val destination: CoordGrid get() = dest
    }

    /**
     * Jump down, or across a short gap, to [dest] over [ticks] cycles: take off, hold the mid-air
     * pose and land. [glideLevel] works as for [Leap].
     */
    data class Drop(val dest: CoordGrid, val ticks: Int = 1, val glideLevel: Int? = null) :
        ObstacleMove() {
        override val destination: CoordGrid get() = dest
    }

    /** Walk [path] one tile per tick with the [style] animations. Used for ropes and bars. */
    data class Balance(
        val path: List<CoordGrid>,
        val style: BalanceStyle = BalanceStyle.Tightrope,
    ) : ObstacleMove() {
        init {
            require(path.isNotEmpty()) { "Balance path must not be empty." }
        }

        override val destination: CoordGrid get() = path.last()
    }

    /** Grab a zip line and slide to [dest] over [ticks] cycles. */
    data class Zipline(val dest: CoordGrid, val ticks: Int = 3) : ObstacleMove() {
        override val destination: CoordGrid get() = dest
    }

    /** Jump from stone to stone along [stones]. */
    data class Hop(val stones: List<CoordGrid>) : ObstacleMove() {
        init {
            require(stones.isNotEmpty()) { "Stepping stones must not be empty." }
        }

        override val destination: CoordGrid get() = stones.last()
    }

    /** Squeeze through a long pipe to [dest]. */
    data class Pipe(val dest: CoordGrid) : ObstacleMove() {
        override val destination: CoordGrid get() = dest
    }
}

/**
 * Failure rules for an obstacle: it can be failed until the player reaches [noFailLevel], dropping
 * them on [landing] for [minDamage]..[maxDamage] damage.
 *
 * When [chance] is given the pass roll is the game's skill-success roll between its low and high
 * values out of 256; otherwise the chance rises linearly to certainty at [noFailLevel]. [seq] and
 * [message] replace the obstacle's own animation and the default fall message.
 *
 * A balance or hop fails at step [failAt] when given (halfway otherwise). With [currentHpPercent]
 * the fall deals that share of the player's current hitpoints plus one instead of a random amount.
 */
data class ObstacleFailure(
    val noFailLevel: Int,
    val landing: CoordGrid,
    val minDamage: Int,
    val maxDamage: Int,
    val chance: IntRange? = null,
    val seq: String? = null,
    val message: String = DEFAULT_FALL_MESSAGE,
    val failAt: Int? = null,
    val currentHpPercent: Int? = null,
) {
    companion object {
        const val DEFAULT_FALL_MESSAGE = "You lose your footing and fall to the ground below."
    }
}

/**
 * One obstacle of a rooftop course.
 *
 * @param locs Every loc gameval that starts this obstacle (some ledges are split over two locs).
 * @param start The tile the player is placed on before the movement begins.
 * @param xp Experience granted every time the obstacle is completed.
 * @param lapBonusXp Extra experience granted when this obstacle completes a full, in-order lap.
 *   Only the final obstacle of a course has a bonus; it also rolls for a mark of grace.
 * @param apRange When greater than zero the obstacle also starts once the player is within this
 *   many tiles of the loc with a line of sight to it, without having to reach it. Needed where the
 *   map fences the loc off from the tile it is used from (a railing between a landing platform
 *   and the tree that is swung from), which makes the loc unreachable to the route finder.
 * @param lapBonusStrengthXp Strength experience paid with [lapBonusXp].
 * @param locSeq Animation the obstacle loc itself plays when used (a rope swinging).
 * @param locAt Where the obstacle's loc stands, for loc types the course uses more than once (the
 *   Barbarian Outpost's three crumbling walls); the clicked loc then picks the obstacle.
 * @param alternative This obstacle is another way through the obstacle listed before it (the
 *   Gnome Stronghold's two pipes) and counts as the same step of a lap.
 * @param messages Chat messages shown as the obstacle starts and, after it, as it completes.
 * @param shout What the course trainer nearest the player shouts when the obstacle is started.
 */
data class RooftopObstacle(
    val locs: List<String>,
    val name: String,
    val xp: Double,
    val start: CoordGrid,
    val move: ObstacleMove,
    val failure: ObstacleFailure? = null,
    val lapBonusXp: Double = 0.0,
    val apRange: Int = 0,
    val lapBonusStrengthXp: Double = 0.0,
    val locSeq: String? = null,
    val locAt: CoordGrid? = null,
    val alternative: Boolean = false,
    val messages: Pair<String?, String?> = null to null,
    val shout: String? = null,
) {
    val isFinish: Boolean get() = lapBonusXp > 0.0

    /**
     * Obstacles are crossed one way only. A positioned one needs the player on its start side; any
     * other obstacle that stays on one level refuses a player nearer its far end than its start.
     */
    fun isBehind(coords: CoordGrid): Boolean {
        val at = locAt
        if (at != null) {
            val wrongX = (coords.x - at.x).sign * (start.x - at.x).sign < 0
            val wrongZ = (coords.z - at.z).sign * (start.z - at.z).sign < 0
            return wrongX || wrongZ
        }
        val dest = move.destination
        if (coords.level != start.level || dest.level != start.level) {
            return false
        }
        return coords.chebyshevDistance(dest) < coords.chebyshevDistance(start)
    }

    val totalXp: Double get() = xp + lapBonusXp
}

/**
 * A course together with its obstacles (in lap order), the tiles marks of grace spawn on and the
 * [trainer] npc that shouts at players on the course.
 */
class CourseLayout(
    val course: RooftopCourse,
    val obstacles: List<RooftopObstacle>,
    val markTiles: List<CoordGrid>,
    val trainer: String? = null,
) {
    /** The lap step of each obstacle; alternatives share the step of the obstacle before them. */
    val steps: List<Int> =
        obstacles.runningFold(-1) { step, obstacle -> if (obstacle.alternative) step else step + 1 }
            .drop(1)

    /** Bit mask with one bit per lap step; a lap is complete when every bit has been set. */
    val fullMask: Int = (1 shl (steps.last() + 1)) - 1

    /** Experience for a lap taking the first way through every step. */
    val lapXp: Double get() = obstacles.filterNot { it.alternative }.sumOf { it.totalXp }
}
