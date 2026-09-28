package org.rsmod.content.skills.hunter.herbiboar

import org.rsmod.map.CoordGrid

/**
 * A place on the herbiboar trail network: a start object, the search objects that continue a
 * trail from here, and the tunnel a trail can end at. [herbiboar] is the value of
 * `varbit.fossil_herbiboar_visible` that shows the herbiboar outside [tunnel].
 */
class HerbiboarSpot(
    val index: Int,
    val starts: List<CoordGrid>,
    val clues: List<CoordGrid>,
    val tunnel: CoordGrid?,
    val herbiboar: Int,
)

class HerbiboarSegment(val varbit: String, val a: Int, val b: Int, val forwardFromA: Boolean) {
    fun other(spot: Int): Int = if (spot == a) b else a
}

/**
 * The Mushroom Forest trail graph, derived from the cache map: start, search and tunnel locs
 * cluster into spots and each `hunting_trailN_M` footprint set joins the two spots at its ends.
 */
object HerbiboarTrails {
    val spots: List<HerbiboarSpot> =
        listOf(
            spot(starts = emptyList(), clues = listOf(CoordGrid(3667, 3862, 0), CoordGrid(3668, 3865, 0)), tunnel = null, herbiboar = 0),
            spot(starts = emptyList(), clues = listOf(CoordGrid(3672, 3890, 0), CoordGrid(3670, 3889, 0)), tunnel = null, herbiboar = 0),
            spot(starts = emptyList(), clues = listOf(CoordGrid(3680, 3836, 0), CoordGrid(3680, 3838, 0)), tunnel = null, herbiboar = 0),
            spot(starts = emptyList(), clues = listOf(CoordGrid(3681, 3859, 0), CoordGrid(3681, 3860, 0)), tunnel = CoordGrid(3681, 3863, 0), herbiboar = 9),
            spot(starts = listOf(CoordGrid(3686, 3870, 0)), clues = emptyList(), tunnel = CoordGrid(3685, 3869, 0), herbiboar = 8),
            spot(starts = listOf(CoordGrid(3695, 3800, 0)), clues = emptyList(), tunnel = CoordGrid(3693, 3798, 0), herbiboar = 1),
            spot(starts = emptyList(), clues = listOf(CoordGrid(3698, 3847, 0), CoordGrid(3694, 3847, 0)), tunnel = null, herbiboar = 0),
            spot(starts = emptyList(), clues = listOf(CoordGrid(3697, 3875, 0), CoordGrid(3699, 3875, 0)), tunnel = CoordGrid(3700, 3877, 0), herbiboar = 5),
            spot(starts = listOf(CoordGrid(3704, 3810, 0)), clues = listOf(CoordGrid(3706, 3811, 0)), tunnel = CoordGrid(3702, 3808, 0), herbiboar = 2),
            spot(starts = listOf(CoordGrid(3705, 3830, 0)), clues = emptyList(), tunnel = CoordGrid(3703, 3826, 0), herbiboar = 3),
            spot(starts = emptyList(), clues = listOf(CoordGrid(3710, 3877, 0), CoordGrid(3708, 3876, 0)), tunnel = CoordGrid(3710, 3881, 0), herbiboar = 4),
            spot(starts = emptyList(), clues = listOf(CoordGrid(3713, 3840, 0)), tunnel = CoordGrid(3715, 3840, 0), herbiboar = 6),
            spot(starts = emptyList(), clues = listOf(CoordGrid(3713, 3850, 0), CoordGrid(3715, 3851, 0)), tunnel = null, herbiboar = 0),
            spot(starts = emptyList(), clues = listOf(CoordGrid(3728, 3893, 0)), tunnel = null, herbiboar = 0),
            spot(starts = listOf(CoordGrid(3751, 3850, 0)), clues = emptyList(), tunnel = CoordGrid(3751, 3849, 0), herbiboar = 7),
        )
            .mapIndexed { index, build -> build(index) }

    val segments: List<HerbiboarSegment> =
        listOf(
            segment(9, 0, a = 4, b = 7, forwardFromA = true),
            segment(9, 1, a = 1, b = 4, forwardFromA = false),
            segment(9, 2, a = 3, b = 4, forwardFromA = false),
            segment(9, 3, a = 7, b = 10, forwardFromA = true),
            segment(9, 4, a = 13, b = 7, forwardFromA = false),
            segment(9, 5, a = 1, b = 13, forwardFromA = true),
            segment(9, 6, a = 1, b = 0, forwardFromA = true),
            segment(9, 7, a = 3, b = 2, forwardFromA = true),
            segment(9, 8, a = 3, b = 6, forwardFromA = true),
            segment(9, 9, a = 10, b = 12, forwardFromA = true),
            segment(10, 0, a = 10, b = 6, forwardFromA = true),
            segment(10, 1, a = 7, b = 13, forwardFromA = false),
            segment(10, 2, a = 10, b = 13, forwardFromA = false),
            segment(10, 3, a = 0, b = 3, forwardFromA = true),
            segment(10, 4, a = 0, b = 2, forwardFromA = true),
            segment(10, 5, a = 2, b = 8, forwardFromA = true),
            segment(10, 6, a = 2, b = 5, forwardFromA = true),
            segment(10, 7, a = 6, b = 9, forwardFromA = true),
            segment(10, 8, a = 11, b = 12, forwardFromA = false),
            segment(10, 9, a = 14, b = 12, forwardFromA = false),
            segment(11, 0, a = 12, b = 6, forwardFromA = true),
            segment(11, 1, a = 5, b = 8, forwardFromA = true),
            segment(11, 2, a = 11, b = 8, forwardFromA = false),
            segment(11, 3, a = 10, b = 11, forwardFromA = false),
        )

    fun segmentsAt(spot: Int): List<HerbiboarSegment> =
        segments.filter { it.a == spot || it.b == spot }

    fun spotWithStart(coords: CoordGrid): HerbiboarSpot? =
        spots.firstOrNull { spot -> spot.starts.any { it.sameTile(coords) } }

    fun spotWithClue(coords: CoordGrid): HerbiboarSpot? =
        spots.firstOrNull { spot -> spot.clues.any { it.sameTile(coords) } }

    fun spotWithTunnel(coords: CoordGrid): HerbiboarSpot? =
        spots.firstOrNull { spot -> spot.tunnel?.sameTile(coords) == true }

    private fun CoordGrid.sameTile(other: CoordGrid): Boolean = x == other.x && z == other.z
}

private fun spot(
    starts: List<CoordGrid>,
    clues: List<CoordGrid>,
    tunnel: CoordGrid?,
    herbiboar: Int,
): (Int) -> HerbiboarSpot = { index -> HerbiboarSpot(index, starts, clues, tunnel, herbiboar) }

private fun segment(group: Int, index: Int, a: Int, b: Int, forwardFromA: Boolean) =
    HerbiboarSegment("varbit.hunting_trail_state${group}_$index", a, b, forwardFromA)
