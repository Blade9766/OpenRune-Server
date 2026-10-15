package org.rsmod.content.skills.hunter.tracking

import org.rsmod.map.CoordGrid

class TrackNode(
    val index: Int,
    val dens: List<CoordGrid>,
    val hides: List<CoordGrid>,
    val junction: Boolean,
)

/**
 * One track segment between nodes [a] and [b]. Inspecting [connector] reveals it; [varbit] shows
 * its footprints, where value 3 walks the prints from [a] to [b] when [forwardFromA] is set.
 */
class TrackSegment(
    val varbit: String,
    val connector: String,
    val a: Int,
    val b: Int,
    val forwardFromA: Boolean,
) {
    fun other(node: Int): Int = if (node == a) b else a
}

/**
 * Kebbit tracking areas. The node/segment graphs were derived from the cache map: dens and hiding
 * spots cluster into nodes, tunnel connectors into junctions, and each `hunting_trailN_M` footprint
 * set joins the two nodes its ends reach. Connector `hunting_trail_clueN_M` reveals segment N_M.
 */
enum class TrackingArea(
    val displayName: String,
    val level: Int,
    val xp: Double,
    val minNodes: Int,
    val loot: List<String>,
    nodes: List<(Int) -> TrackNode>,
    val segments: List<TrackSegment>,
) {
    Polar(
        displayName = "polar kebbit",
        level = 1,
        xp = 30.0,
        minNodes = 3,
        loot = listOf("obj.bones", "obj.huntingbeast_polar_fur", "obj.spit_raw_beast_meat"),
        nodes =
            listOf(
                node(dens = emptyList(), hides = listOf(CoordGrid(2708, 3819, 0)), junction = false),
                node(dens = listOf(CoordGrid(2711, 3830, 0)), hides = listOf(CoordGrid(2712, 3831, 0)), junction = false),
                node(dens = emptyList(), hides = listOf(CoordGrid(2716, 3827, 0)), junction = false),
                node(dens = listOf(CoordGrid(2717, 3819, 0)), hides = listOf(CoordGrid(2718, 3820, 0)), junction = false),
                node(dens = emptyList(), hides = emptyList(), junction = true),
                node(dens = emptyList(), hides = emptyList(), junction = true),
            ),
        segments =
            listOf(
                segment(8, 0, a = 0, b = 3, forwardFromA = false),
                segment(8, 1, a = 0, b = 4, forwardFromA = false),
                segment(8, 2, a = 3, b = 4, forwardFromA = false),
                segment(8, 3, a = 3, b = 5, forwardFromA = false),
                segment(8, 4, a = 0, b = 1, forwardFromA = true),
                segment(8, 5, a = 2, b = 4, forwardFromA = false),
                segment(8, 6, a = 2, b = 5, forwardFromA = true),
                segment(8, 7, a = 1, b = 2, forwardFromA = true),
                segment(8, 8, a = 1, b = 5, forwardFromA = false),
            ),
    ),
    Common(
        displayName = "common kebbit",
        level = 3,
        xp = 36.0,
        minNodes = 5,
        loot = listOf("obj.bones", "obj.huntingbeast_woodland_fur", "obj.spit_raw_beast_meat"),
        nodes =
            listOf(
                node(dens = emptyList(), hides = listOf(CoordGrid(2322, 3570, 0)), junction = false),
                node(dens = emptyList(), hides = listOf(CoordGrid(2323, 3563, 0)), junction = false),
                node(dens = listOf(CoordGrid(2322, 3576, 0)), hides = emptyList(), junction = false),
                node(dens = emptyList(), hides = listOf(CoordGrid(2327, 3573, 0)), junction = false),
                node(dens = listOf(CoordGrid(2331, 3562, 0)), hides = emptyList(), junction = false),
                node(dens = emptyList(), hides = listOf(CoordGrid(2332, 3568, 0)), junction = false),
                node(dens = emptyList(), hides = listOf(CoordGrid(2332, 3578, 0)), junction = false),
                node(dens = emptyList(), hides = listOf(CoordGrid(2337, 3565, 0)), junction = false),
                node(dens = emptyList(), hides = listOf(CoordGrid(2336, 3571, 0)), junction = false),
                node(dens = listOf(CoordGrid(2341, 3577, 0)), hides = emptyList(), junction = false),
                node(dens = emptyList(), hides = listOf(CoordGrid(2343, 3568, 0)), junction = false),
            ),
        segments =
            listOf(
                segment(3, 0, a = 1, b = 4, forwardFromA = false),
                segment(3, 1, a = 4, b = 5, forwardFromA = true),
                segment(3, 2, a = 4, b = 7, forwardFromA = true),
                segment(3, 3, a = 7, b = 10, forwardFromA = false),
                segment(3, 4, a = 1, b = 0, forwardFromA = true),
                segment(3, 5, a = 1, b = 5, forwardFromA = false),
                segment(3, 6, a = 7, b = 8, forwardFromA = true),
                segment(3, 7, a = 8, b = 10, forwardFromA = false),
                segment(3, 8, a = 9, b = 10, forwardFromA = true),
                segment(3, 9, a = 0, b = 2, forwardFromA = false),
                segment(4, 0, a = 0, b = 3, forwardFromA = true),
                segment(4, 1, a = 3, b = 5, forwardFromA = true),
                segment(4, 2, a = 2, b = 3, forwardFromA = true),
                segment(4, 3, a = 2, b = 6, forwardFromA = true),
                segment(4, 4, a = 6, b = 8, forwardFromA = true),
                segment(4, 5, a = 8, b = 9, forwardFromA = false),
                segment(4, 6, a = 6, b = 9, forwardFromA = true),
            ),
    ),
    FeldipWeasel(
        displayName = "feldip weasel",
        level = 7,
        xp = 48.0,
        minNodes = 5,
        loot = listOf("obj.bones", "obj.huntingbeast_jungle_fur", "obj.spit_raw_beast_meat"),
        nodes =
            listOf(
                node(dens = listOf(CoordGrid(2525, 2889, 0)), hides = emptyList(), junction = false),
                node(dens = emptyList(), hides = listOf(CoordGrid(2525, 2882, 0)), junction = false),
                node(dens = emptyList(), hides = listOf(CoordGrid(2531, 2890, 0)), junction = false),
                node(dens = emptyList(), hides = emptyList(), junction = false),
                node(dens = emptyList(), hides = listOf(CoordGrid(2533, 2885, 0)), junction = false),
                node(dens = emptyList(), hides = listOf(CoordGrid(2540, 2886, 0)), junction = false),
                node(dens = emptyList(), hides = emptyList(), junction = false),
                node(dens = emptyList(), hides = listOf(CoordGrid(2542, 2881, 0)), junction = false),
                node(dens = listOf(CoordGrid(2554, 2882, 0)), hides = emptyList(), junction = false),
                node(dens = emptyList(), hides = listOf(CoordGrid(2553, 2888, 0)), junction = false),
            ),
        segments =
            listOf(
                segment(6, 3, a = 1, b = 0, forwardFromA = true),
                segment(6, 4, a = 1, b = 0, forwardFromA = false),
                segment(6, 5, a = 0, b = 2, forwardFromA = true),
                segment(6, 6, a = 4, b = 2, forwardFromA = false),
                segment(6, 7, a = 1, b = 3, forwardFromA = false),
                segment(6, 8, a = 3, b = 4, forwardFromA = true),
                segment(7, 0, a = 6, b = 2, forwardFromA = false),
                segment(7, 1, a = 4, b = 5, forwardFromA = true),
                segment(7, 2, a = 3, b = 7, forwardFromA = false),
                segment(7, 3, a = 5, b = 7, forwardFromA = false),
                segment(7, 4, a = 5, b = 6, forwardFromA = true),
                segment(7, 5, a = 6, b = 9, forwardFromA = true),
                segment(7, 6, a = 7, b = 8, forwardFromA = false),
                segment(7, 7, a = 9, b = 8, forwardFromA = true),
                segment(7, 8, a = 8, b = 9, forwardFromA = false),
            ),
    ),
    DesertDevil(
        displayName = "desert devil",
        level = 13,
        xp = 66.0,
        minNodes = 5,
        loot = listOf("obj.bones", "obj.huntingbeast_desert_fur", "obj.spit_raw_beast_meat"),
        nodes =
            listOf(
                node(dens = emptyList(), hides = listOf(CoordGrid(3393, 3122, 0)), junction = false),
                node(dens = listOf(CoordGrid(3396, 3106, 0)), hides = emptyList(), junction = false),
                node(dens = emptyList(), hides = emptyList(), junction = false),
                node(dens = emptyList(), hides = listOf(CoordGrid(3400, 3114, 0)), junction = false),
                node(dens = listOf(CoordGrid(3402, 3131, 0)), hides = emptyList(), junction = false),
                node(dens = emptyList(), hides = listOf(CoordGrid(3405, 3124, 0)), junction = false),
                node(dens = emptyList(), hides = emptyList(), junction = false),
                node(dens = emptyList(), hides = listOf(CoordGrid(3407, 3121, 0)), junction = false),
                node(dens = emptyList(), hides = listOf(CoordGrid(3411, 3108, 0)), junction = false),
                node(dens = emptyList(), hides = listOf(CoordGrid(3414, 3121, 0)), junction = false),
            ),
        segments =
            listOf(
                segment(4, 7, a = 0, b = 4, forwardFromA = true),
                segment(4, 8, a = 4, b = 5, forwardFromA = false),
                segment(5, 0, a = 4, b = 9, forwardFromA = true),
                segment(5, 1, a = 0, b = 2, forwardFromA = false),
                segment(5, 2, a = 2, b = 5, forwardFromA = false),
                segment(5, 3, a = 7, b = 5, forwardFromA = true),
                segment(5, 4, a = 7, b = 9, forwardFromA = true),
                segment(5, 5, a = 0, b = 1, forwardFromA = false),
                segment(5, 6, a = 2, b = 3, forwardFromA = true),
                segment(5, 7, a = 6, b = 7, forwardFromA = true),
                segment(5, 8, a = 8, b = 9, forwardFromA = false),
                segment(5, 9, a = 1, b = 3, forwardFromA = false),
                segment(6, 0, a = 3, b = 6, forwardFromA = true),
                segment(6, 1, a = 6, b = 8, forwardFromA = false),
                segment(6, 2, a = 1, b = 8, forwardFromA = false),
            ),
    ),
    RazorBacked(
        displayName = "razor-backed kebbit",
        level = 49,
        xp = 348.0,
        minNodes = 5,
        loot = listOf("obj.bones", "obj.huntingbeast_bigspike", "obj.spit_raw_beast_meat"),
        nodes =
            listOf(
                node(dens = emptyList(), hides = emptyList(), junction = false),
                node(dens = emptyList(), hides = listOf(CoordGrid(2349, 3604, 0)), junction = false),
                node(dens = emptyList(), hides = listOf(CoordGrid(2351, 3619, 0)), junction = false),
                node(dens = listOf(CoordGrid(2353, 3595, 0)), hides = emptyList(), junction = false),
                node(dens = emptyList(), hides = listOf(CoordGrid(2354, 3609, 0)), junction = false),
                node(dens = emptyList(), hides = listOf(CoordGrid(2355, 3601, 0)), junction = false),
                node(dens = listOf(CoordGrid(2357, 3624, 0)), hides = emptyList(), junction = false),
                node(dens = emptyList(), hides = listOf(CoordGrid(2357, 3607, 0)), junction = false),
                node(dens = emptyList(), hides = listOf(CoordGrid(2358, 3620, 0)), junction = false),
                node(dens = listOf(CoordGrid(2360, 3611, 0)), hides = emptyList(), junction = false),
                node(dens = emptyList(), hides = listOf(CoordGrid(2360, 3602, 0)), junction = false),
                node(dens = emptyList(), hides = listOf(CoordGrid(2362, 3615, 0)), junction = false),
            ),
        segments =
            listOf(
                segment(1, 0, a = 3, b = 10, forwardFromA = false),
                segment(1, 1, a = 3, b = 5, forwardFromA = false),
                segment(1, 2, a = 1, b = 3, forwardFromA = true),
                segment(1, 3, a = 5, b = 10, forwardFromA = true),
                segment(1, 4, a = 1, b = 5, forwardFromA = true),
                segment(1, 5, a = 7, b = 10, forwardFromA = true),
                segment(1, 6, a = 10, b = 9, forwardFromA = false),
                segment(1, 7, a = 7, b = 9, forwardFromA = false),
                segment(1, 8, a = 4, b = 7, forwardFromA = true),
                segment(1, 9, a = 1, b = 4, forwardFromA = true),
                segment(2, 0, a = 6, b = 11, forwardFromA = false),
                segment(2, 1, a = 2, b = 6, forwardFromA = false),
                segment(2, 2, a = 6, b = 8, forwardFromA = true),
                segment(2, 3, a = 0, b = 1, forwardFromA = false),
                segment(2, 4, a = 0, b = 4, forwardFromA = true),
                segment(2, 5, a = 0, b = 2, forwardFromA = true),
                segment(2, 6, a = 9, b = 2, forwardFromA = false),
                segment(2, 7, a = 9, b = 11, forwardFromA = true),
                segment(2, 8, a = 8, b = 11, forwardFromA = false),
            ),
    ),
    ;

    val nodes: List<TrackNode> = nodes.mapIndexed { index, build -> build(index) }

    fun segmentsAt(node: Int): List<TrackSegment> =
        segments.filter { it.a == node || it.b == node }

    fun nodeWithDen(coords: CoordGrid): TrackNode? =
        nodes.firstOrNull { node -> node.dens.any { it.sameTile(coords) } }

    fun nodeWithHide(coords: CoordGrid): TrackNode? =
        nodes.firstOrNull { node -> node.hides.any { it.sameTile(coords) } }

    companion object {
        val byConnector: Map<String, Pair<TrackingArea, TrackSegment>> =
            entries.flatMap { area -> area.segments.map { it.connector to (area to it) } }.toMap()

        fun forDen(coords: CoordGrid): TrackingArea? =
            entries.firstOrNull { it.nodeWithDen(coords) != null }

        fun forHide(coords: CoordGrid): TrackingArea? =
            entries.firstOrNull { it.nodeWithHide(coords) != null }
    }
}

private fun CoordGrid.sameTile(other: CoordGrid): Boolean = x == other.x && z == other.z

private fun node(
    dens: List<CoordGrid>,
    hides: List<CoordGrid>,
    junction: Boolean,
): (Int) -> TrackNode = { index -> TrackNode(index, dens, hides, junction) }

private fun segment(
    group: Int,
    index: Int,
    a: Int,
    b: Int,
    forwardFromA: Boolean,
): TrackSegment =
    TrackSegment(
        varbit = "varbit.hunting_trail_state${group}_$index",
        connector = "loc.hunting_trail_clue${group}_$index",
        a = a,
        b = b,
        forwardFromA = forwardFromA,
    )
