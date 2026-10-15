package org.rsmod.content.quest.area.tirannwn.templeoflight

import org.rsmod.map.CoordGrid

/**
 * The colours of the temple's light, in their order round the colour wheel the dead guard's notes
 * point to: red, yellow, green, cyan, blue, magenta. White is the light the temple's crystal gives
 * off and holds every colour.
 *
 * A crystal in a pillar works round the wheel, not by mixing primaries: white light takes the
 * crystal's colour, light of the crystal's own colour passes unchanged, light two steps away comes
 * out as the colour between the two (cyan through yellow is green, green through blue cyan, red
 * through green yellow), and light next to or opposite the crystal's colour is stopped. A light
 * door opens only for a beam of nothing but the colour opposite its own; the black door wants
 * white, which only the Final Pillar makes.
 */
enum class LightColour(val wheel: Int, val label: String) {
    RED(0, "red"),
    YELLOW(1, "yellow"),
    GREEN(2, "green"),
    CYAN(3, "cyan"),
    BLUE(4, "blue"),
    MAGENTA(5, "magenta"),
    WHITE(-1, "white");

    val bit: Int
        get() = 1 shl ordinal

    /** The colour's place in every beam multiloc's transforms. */
    val displayIndex: Int
        get() = if (this == WHITE) 7 else wheel + 1

    /** The colour opposite on the wheel; white has none. */
    val complement: LightColour?
        get() = if (this == WHITE) null else ofWheel(wheel + 3)

    /** What this light becomes passing a crystal of [crystal]'s colour, or null if it is stopped. */
    fun through(crystal: LightColour): LightColour? {
        if (this == WHITE) return crystal
        if (crystal == WHITE || crystal == this) return this
        val apart = Math.floorMod(crystal.wheel - wheel, 6)
        return when (apart) {
            2 -> ofWheel(wheel + 1)
            4 -> ofWheel(wheel - 1)
            else -> null
        }
    }

    companion object {
        private val byWheel = entries.filter { it != WHITE }.sortedBy { it.wheel }

        fun ofWheel(index: Int): LightColour = byWheel[Math.floorMod(index, 6)]

        fun ofBits(bits: Int): List<LightColour> = entries.filter { bits and it.bit != 0 }

        /** The single colour a set of beams holds, or null when it is dark or mixed. */
        fun only(bits: Int): LightColour? = entries.singleOrNull { bits == it.bit }
    }
}

enum class Facing(val label: String) {
    NORTH("north"),
    EAST("east"),
    SOUTH("south"),
    WEST("west"),
    UP("up"),
    DOWN("down");

    val opposite: Facing
        get() =
            when (this) {
                NORTH -> SOUTH
                EAST -> WEST
                SOUTH -> NORTH
                WEST -> EAST
                UP -> DOWN
                DOWN -> UP
            }

    val vertical: Boolean
        get() = this == UP || this == DOWN
}

enum class PillarKind {
    /** Takes a mirror or a crystal from the player. */
    MOVABLE,

    /** Holds a mirror the player may turn but never take out. */
    PRESET_MIRROR,

    /** A crystal fused into the pillar; it colours light but cannot be touched. */
    FIXED_CRYSTAL,

    /** The Final Pillar: whatever reaches it merges and leaves towards the black door. */
    FINAL,
}

/**
 * A pillar of light. [floor] is the temple's own numbering (1 is the ground floor, map level 0)
 * and [column] the vertical shaft it stands in, shared by the pillars above and below it; 1_b and
 * the Final Pillar sit off the grid. [holes] are the sides its model is open on, from its variant
 * and rotation in the map.
 */
data class Pillar(
    val id: String,
    val floor: Int,
    val column: Int?,
    val coords: CoordGrid,
    val holes: Set<Facing>,
    val kind: PillarKind = PillarKind.MOVABLE,
    val presetFacing: Facing? = null,
    val fixedColour: LightColour? = null,
) {
    val loc: String
        get() = if (kind == PillarKind.FINAL) FINAL_LOC else "loc.mourning_temple_pillar_$id"

    val crossShaped: Boolean
        get() = holes.containsAll(HORIZONTAL)

    private companion object {
        const val FINAL_LOC = "loc.mourning_temple_final_pillar_of_light"
        val HORIZONTAL = setOf(Facing.NORTH, Facing.EAST, Facing.SOUTH, Facing.WEST)
    }
}

data class Port(val pillar: String, val side: Facing)

enum class Axis {
    NORTH_SOUTH,
    EAST_WEST,
}

/** A beam passing a floor's beam-crossing loc ([cross] is its varbit) along [axis]. */
data class CrossPass(val cross: String, val axis: Axis)

sealed interface LinkEnd {
    data class ToPillar(val port: Port) : LinkEnd

    data class ToDoor(val door: String) : LinkEnd

    /** The beam runs into a wall. */
    data object Wall : LinkEnd
}

/**
 * One beam path the client can draw, shown by [varbit]. Light leaves [from] and travels to [to];
 * a path between two pillars carries light both ways.
 */
data class Link(
    val varbit: String,
    val from: Port,
    val to: LinkEnd,
    val crosses: List<CrossPass> = emptyList(),
)

/**
 * A light door on [tiles], passed along [axis]. [colour] is the door's own, null for the black door,
 * and it lets the player through while a beam of nothing but [required] reaches it.
 */
data class LightDoor(
    val id: String,
    val colour: LightColour?,
    val tiles: List<CoordGrid>,
    val axis: Axis,
) {
    val required: LightColour
        get() = colour?.complement ?: LightColour.WHITE

    val varbit: String
        get() = "varbit.mourning_door_$id"

    val loc: String
        get() = "loc.mourning_door_$id"
}

/** What a vertical shaft holds on one floor when no pillar stands there. */
enum class ShaftStop {
    /** The great crystal under the middle floor that feeds the temple its light. */
    SOURCE,

    /** The black crystal on the top floor. */
    OBSIDIAN,
}

/**
 * The temple's light network, read from the cache map: every pillar of light and the varbit-driven
 * beam multilocs between them. A beam path's tiles run from one pillar to the next (or to a door or
 * wall) in the map, and its varbit is named after the pillars it joins; the cross locs sit where a
 * beam passes a column with no pillar on that floor. The three `3_a` pillars are fixed bends: the
 * paths named 3_5_west, 3_10_west and 3_8_east run along the top floor, drop through them and
 * continue on the ground floor, so here they are single links. `MourningsEnd2CacheTest` checks all
 * of it against the map.
 */
object TempleGeometry {
    const val FINAL = "final"
    const val SOURCE_PILLAR = "2_9"
    val SOURCE_COLOUR = LightColour.WHITE

    private val N = Facing.NORTH
    private val E = Facing.EAST
    private val S = Facing.SOUTH
    private val W = Facing.WEST

    private fun at(x: Int, z: Int, level: Int) = CoordGrid(x, z, level)

    val pillars: List<Pillar> =
        listOf(
            Pillar("1_1", 1, 1, at(1860, 4665, 0), setOf(E, S), PillarKind.PRESET_MIRROR, presetFacing = E),
            Pillar("1_3", 1, 3, at(1898, 4665, 0), setOf(W, E, S)),
            Pillar("1_6", 1, 6, at(1898, 4650, 0), setOf(N, E, S, W)),
            Pillar("1_7", 1, 7, at(1909, 4650, 0), setOf(W)),
            Pillar("1_10", 1, 10, at(1887, 4628, 0), setOf(E, S)),
            Pillar("1_11", 1, 11, at(1898, 4628, 0), setOf(N, E, S, W)),
            Pillar("1_12", 1, 12, at(1909, 4628, 0), setOf(W)),
            Pillar("1_13", 1, 13, at(1860, 4613, 0), setOf(N, E), PillarKind.PRESET_MIRROR, presetFacing = N),
            Pillar("1_14", 1, 14, at(1887, 4613, 0), setOf(W, N, E)),
            Pillar("1_16", 1, 16, at(1915, 4613, 0), setOf(N, W)),
            Pillar("1_b", 1, null, at(1881, 4639, 0), setOf(N, E, S, W), PillarKind.PRESET_MIRROR, presetFacing = E),
            Pillar(FINAL, 1, null, at(1869, 4639, 0), setOf(N, E, S, W), PillarKind.FINAL),
            Pillar("2_2", 2, 2, at(1887, 4665, 1), setOf(W, E, S)),
            Pillar("2_3", 2, 3, at(1898, 4665, 1), setOf(W, E, S)),
            Pillar("2_5", 2, 5, at(1887, 4650, 1), setOf(N, E)),
            Pillar("2_6", 2, 6, at(1898, 4650, 1), setOf(N, E, S, W)),
            Pillar("2_7", 2, 7, at(1909, 4650, 1), setOf(W, S)),
            Pillar("2_9", 2, 9, at(1909, 4639, 1), setOf(N, S)),
            Pillar("2_10", 2, 10, at(1887, 4628, 1), setOf(E, S), PillarKind.FIXED_CRYSTAL, fixedColour = LightColour.GREEN),
            Pillar("2_11", 2, 11, at(1898, 4628, 1), setOf(N, E, S, W)),
            Pillar("2_15", 2, 15, at(1898, 4613, 1), setOf(W, N, E)),
            Pillar("2_16", 2, 16, at(1915, 4613, 1), setOf(N, W)),
            Pillar("3_1", 3, 1, at(1860, 4665, 2), setOf(E, S)),
            Pillar("3_2", 3, 2, at(1887, 4665, 2), setOf(W, E, S), PillarKind.FIXED_CRYSTAL, fixedColour = LightColour.MAGENTA),
            Pillar("3_3", 3, 3, at(1898, 4665, 2), setOf(W, E, S)),
            Pillar("3_5", 3, 5, at(1887, 4650, 2), setOf(W, N, E)),
            Pillar("3_6", 3, 6, at(1898, 4650, 2), setOf(N, E, S, W)),
            Pillar("3_8", 3, 8, at(1860, 4639, 2), setOf(N, E, S)),
            Pillar("3_10", 3, 10, at(1887, 4628, 2), setOf(W, E, S)),
            Pillar("3_11", 3, 11, at(1898, 4628, 2), setOf(N, E, S, W)),
            Pillar("3_12", 3, 12, at(1909, 4628, 2), setOf(W)),
            Pillar("3_13", 3, 13, at(1860, 4613, 2), setOf(N, E)),
            Pillar("3_15", 3, 15, at(1898, 4613, 2), setOf(W, N, E)),
            Pillar("3_16", 3, 16, at(1915, 4613, 2), setOf(N, W)),
        )

    val pillarsById: Map<String, Pillar> = pillars.associateBy { it.id }

    /** The pillars a player can change, in the order their contents are saved. */
    val adjustable: List<Pillar> =
        pillars.filter { it.kind == PillarKind.MOVABLE || it.kind == PillarKind.PRESET_MIRROR }

    private fun beam(name: String) = "varbit.mourning_light_temple_$name"

    private fun cross(name: String, axis: Axis) = CrossPass("varbit.mourning_pillar_light_cross_$name", axis)

    private fun link(name: String, from: Port, to: LinkEnd, vararg crosses: CrossPass) =
        Link(beam(name), from, to, crosses.toList())

    private fun p(pillar: String, side: Facing) = Port(pillar, side)

    private fun to(pillar: String, side: Facing) = LinkEnd.ToPillar(Port(pillar, side))

    private fun door(id: String) = LinkEnd.ToDoor(id)

    private val wall = LinkEnd.Wall
    private val ew = Axis.EAST_WEST
    private val ns = Axis.NORTH_SOUTH

    val links: List<Link> =
        listOf(
            link("1_1_east", p("1_1", E), door("1_1_east")),
            link("1_1_south", p("1_1", S), door("1_1_south")),
            link("1_3_west", p("1_3", W), door("1_1_east"), cross("1_2", ew)),
            link("1_3_east", p("1_3", E), wall),
            link("1_3_6", p("1_3", S), to("1_6", N)),
            link("1_6_west", p("1_6", W), wall, cross("1_5", ew)),
            link("1_6_7", p("1_6", E), to("1_7", W)),
            link("1_6_11", p("1_6", S), to("1_11", N)),
            link("1_10_11", p("1_10", E), to("1_11", W)),
            link("1_10_14", p("1_10", S), to("1_14", N)),
            link("1_11_12", p("1_11", E), to("1_12", W)),
            link("1_11_south", p("1_11", S), wall, cross("1_15", ns)),
            link("1_13_north", p("1_13", N), door("1_13_north")),
            link("1_13_east", p("1_13", E), door("1_13_east")),
            link("1_14_west", p("1_14", W), door("1_13_east")),
            link("1_14_east", p("1_14", E), door("1_16_west"), cross("1_15", ew)),
            link("1_16_north", p("1_16", N), door("1_16_north")),
            link("1_16_west", p("1_16", W), door("1_16_west")),
            link("1_b_east", p("1_b", E), door("1_b")),
            link("1_b_west", p("1_b", W), to(FINAL, E)),
            link("1_d_west", p(FINAL, W), door("1_c")),
            link("3_5_west", p("3_5", W), to(FINAL, N)),
            link("3_10_west", p("3_10", W), to(FINAL, S)),
            link("3_8_east", p("3_8", E), to("1_b", Facing.UP)),
            link("2_2_west", p("2_2", W), wall, cross("2_1", ew)),
            link("2_2_3", p("2_2", E), to("2_3", W)),
            link("2_2_5", p("2_2", S), to("2_5", N)),
            link("2_3_east", p("2_3", E), door("2_4_west")),
            link("2_3_south", p("2_3", S), wall),
            link("2_5_6", p("2_5", E), to("2_6", W)),
            link("2_6_north", p("2_6", N), wall),
            link("2_6_7", p("2_6", E), to("2_7", W)),
            link("2_6_11", p("2_6", S), to("2_11", N)),
            link("2_7_9", p("2_7", S), to("2_9", N)),
            link("2_9_south", p("2_9", S), wall),
            link("2_10_11", p("2_11", W), to("2_10", E)),
            link("2_11_east", p("2_11", E), wall, cross("2_12", ew)),
            link("2_11_15", p("2_11", S), to("2_15", N)),
            link("2_15_east", p("2_15", E), door("2_16_west")),
            link("2_15_west", p("2_15", W), wall, cross("2_14", ew), cross("2_13", ew)),
            link("2_16_north", p("2_16", N), door("2_16_north")),
            link("2_16_west", p("2_16", W), door("2_16_west")),
            link("3_1_2", p("3_1", E), to("3_2", W)),
            link("3_1_8", p("3_1", S), to("3_8", N)),
            link("3_2_3", p("3_2", E), to("3_3", W)),
            link("3_2_5", p("3_2", S), to("3_5", N)),
            link("3_3_east", p("3_3", E), wall, cross("3_4", ew)),
            link("3_3_south", p("3_3", S), wall),
            link("3_5_6", p("3_5", E), to("3_6", W)),
            link("3_6_north", p("3_6", N), wall),
            link("3_6_east", p("3_6", E), wall, cross("3_7", ew)),
            link("3_6_11", p("3_6", S), to("3_11", N)),
            link("3_8_13", p("3_8", S), to("3_13", N)),
            link("3_10_11", p("3_10", E), to("3_11", W)),
            link("3_10_south", p("3_10", S), wall, cross("3_14", ns)),
            link("3_11_12", p("3_11", E), to("3_12", W)),
            link("3_11_15", p("3_11", S), to("3_15", N)),
            link("3_13_15", p("3_13", E), to("3_15", W), cross("3_14", ew)),
            link("3_15_16", p("3_15", E), to("3_16", W)),
            link("3_16_north", p("3_16", N), wall, cross("3_4", ns)),
        )

    /** Every link by each pillar port it touches, so light can leave a pillar from either end. */
    val linksByPort: Map<Port, Link> =
        buildMap {
            for (link in links) {
                put(link.from, link)
                val end = link.to
                if (end is LinkEnd.ToPillar) put(end.port, link)
            }
        }

    val doors: List<LightDoor> =
        listOf(
            LightDoor("1_1_south", LightColour.YELLOW, listOf(at(1860, 4662, 0)), ns),
            LightDoor("1_1_east", LightColour.YELLOW, listOf(at(1863, 4665, 0)), ew),
            LightDoor("1_13_north", LightColour.CYAN, listOf(at(1860, 4616, 0)), ns),
            LightDoor("1_13_east", LightColour.CYAN, listOf(at(1863, 4613, 0)), ew),
            LightDoor("1_16_north", LightColour.YELLOW, listOf(at(1915, 4616, 0)), ns),
            LightDoor("1_16_west", LightColour.MAGENTA, listOf(at(1912, 4613, 0)), ew),
            LightDoor("2_4_west", LightColour.MAGENTA, listOf(at(1912, 4665, 1)), ew),
            LightDoor("2_4_south", LightColour.MAGENTA, listOf(at(1915, 4662, 1)), ns),
            LightDoor("2_16_north", LightColour.BLUE, listOf(at(1915, 4616, 1)), ns),
            LightDoor("2_16_west", LightColour.BLUE, listOf(at(1912, 4613, 1)), ew),
            LightDoor("1_b", LightColour.CYAN, listOf(at(1885, 4639, 0)), ew),
            LightDoor("1_c", null, listOf(at(1865, 4638, 0), at(1865, 4639, 0), at(1865, 4640, 0)), ew),
        )

    val doorsById: Map<String, LightDoor> = doors.associateBy { it.id }

    /** The black door into the Death Altar chamber. */
    const val ALTAR_DOOR = "1_c"

    /**
     * The colour each side of the Final Pillar wants. Only when all three arrive, each alone on its
     * side, do they merge into the white beam that opens the black door.
     */
    val finalInputs: Map<Facing, LightColour> =
        mapOf(Facing.NORTH to LightColour.BLUE, Facing.EAST to LightColour.RED, Facing.SOUTH to LightColour.GREEN)

    /** The cyan door between the middle staircase and the Final Pillar's room. */
    const val ENTRANCE_DOOR = "1_b"

    /** What stands in each column on each floor where there is no pillar to stop a vertical beam. */
    val shaftStops: Map<Pair<Int, Int>, ShaftStop> =
        mapOf((1 to 9) to ShaftStop.SOURCE, (3 to 9) to ShaftStop.OBSIDIAN)

    /** Pillar ids by (floor, column). */
    val shaft: Map<Pair<Int, Int>, String> =
        pillars.filter { it.column != null }.associate { (it.floor to it.column!!) to it.id }

    /** Where each column of the grid stands, on every floor. */
    val columns: Map<Int, Pair<Int, Int>> =
        mapOf(
            1 to (1860 to 4665), 2 to (1887 to 4665), 3 to (1898 to 4665), 4 to (1915 to 4665),
            5 to (1887 to 4650), 6 to (1898 to 4650), 7 to (1909 to 4650), 8 to (1860 to 4639),
            9 to (1909 to 4639), 10 to (1887 to 4628), 11 to (1898 to 4628), 12 to (1909 to 4628),
            13 to (1860 to 4613), 14 to (1887 to 4613), 15 to (1898 to 4613), 16 to (1915 to 4613),
        )

    /** The cross locs, by varbit, and the floor and column they stand in. */
    val crosses: Map<String, Pair<Int, Int>> =
        mapOf(
            "1_2" to (1 to 2),
            "1_5" to (1 to 5),
            "1_15" to (1 to 15),
            "2_1" to (2 to 1),
            "2_12" to (2 to 12),
            "2_13" to (2 to 13),
            "2_14" to (2 to 14),
            "3_4" to (3 to 4),
            "3_7" to (3 to 7),
            "3_14" to (3 to 14),
        ).mapKeys { "varbit.mourning_pillar_light_cross_${it.key}" }

    /**
     * The vertical beam varbits: `F_C_up` draws the beam between floor F and the one above it in
     * column C (above the top floor, to the ceiling). Pillar multilocs show the beam rising from
     * their top through the varbit of their own floor.
     */
    val gapVarbits: Map<Pair<Int, Int>, String> =
        buildMap {
            val columns =
                mapOf(
                    1 to listOf(1, 2, 3, 5, 6, 7, 10, 11, 12, 13, 14, 15, 16),
                    2 to listOf(1, 2, 3, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16),
                    3 to listOf(1, 2, 3, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16),
                )
            for ((floor, list) in columns) {
                for (column in list) put(floor to column, "varbit.mourning_light_temple_${floor}_${column}_up")
            }
        }

    fun pillarAt(coords: CoordGrid): Pillar? = pillars.firstOrNull { it.coords == coords }

    fun pillarByLoc(loc: String): Pillar? = pillars.firstOrNull { it.kind != PillarKind.FINAL && it.loc == loc }

    fun doorAt(coords: CoordGrid): LightDoor? = doors.firstOrNull { coords in it.tiles }
}
