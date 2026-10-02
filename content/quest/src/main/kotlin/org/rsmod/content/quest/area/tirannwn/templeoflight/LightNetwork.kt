package org.rsmod.content.quest.area.tirannwn.templeoflight

/** The pieces of the puzzle the player carries. [colour] is a coloured crystal's tint. */
enum class TempleItem(val obj: String, val colour: LightColour? = null) {
    MIRROR("obj.mourning_mirror"),
    YELLOW("obj.mourning_crystal_yellow", LightColour.YELLOW),
    CYAN("obj.mourning_crystal_cyan", LightColour.CYAN),
    BLUE("obj.mourning_crystal_blue", LightColour.BLUE),

    /** Lets light in through its clear north edge and splits it west, south and east. */
    FRACTURED_HORIZONTAL("obj.mourning_fractured_crystal_1"),

    /** Lets light in through its clear east edge and splits it north, west and south. */
    FRACTURED_VERTICAL("obj.mourning_fractured_crystal_2");

    val fractured: Boolean
        get() = this == FRACTURED_HORIZONTAL || this == FRACTURED_VERTICAL

    /** The side a fractured crystal's clear edge faces, and the only side light enters it by. */
    val clearSide: Facing?
        get() =
            when (this) {
                FRACTURED_HORIZONTAL -> Facing.NORTH
                FRACTURED_VERTICAL -> Facing.EAST
                else -> null
            }

    companion object {
        val crystals: List<TempleItem> = entries - MIRROR
    }
}

sealed interface PillarContent {
    data object Empty : PillarContent

    data class Mirror(val facing: Facing) : PillarContent

    data class Crystal(val item: TempleItem) : PillarContent {
        init {
            require(item != TempleItem.MIRROR)
        }
    }
}

/**
 * What a player has placed in the adjustable pillars. Pillars missing from [contents] hold their
 * default: empty, or the preset mirror facing its starting way.
 */
data class PuzzleState(val contents: Map<String, PillarContent> = emptyMap()) {
    fun contentOf(pillar: Pillar): PillarContent =
        when (pillar.kind) {
            PillarKind.FIXED_CRYSTAL, PillarKind.FINAL -> PillarContent.Empty
            PillarKind.PRESET_MIRROR ->
                contents[pillar.id] as? PillarContent.Mirror ?: PillarContent.Mirror(checkNotNull(pillar.presetFacing))
            PillarKind.MOVABLE -> contents[pillar.id] ?: PillarContent.Empty
        }

    operator fun get(id: String): PillarContent = contentOf(TempleGeometry.pillarsById.getValue(id))

    fun with(id: String, content: PillarContent): PuzzleState = PuzzleState(contents + (id to content))

    /** How many of each carried piece sit in the pillars; the preset mirrors are the temple's own. */
    fun placed(): Map<TempleItem, Int> {
        val counts = HashMap<TempleItem, Int>()
        for (pillar in TempleGeometry.adjustable) {
            if (pillar.kind != PillarKind.MOVABLE) continue
            val item =
                when (val content = contentOf(pillar)) {
                    is PillarContent.Mirror -> TempleItem.MIRROR
                    is PillarContent.Crystal -> content.item
                    PillarContent.Empty -> null
                } ?: continue
            counts[item] = (counts[item] ?: 0) + 1
        }
        return counts
    }
}

/**
 * Everything one arrangement lights. Colours are kept as sets of [LightColour.bit]s: beams of
 * different colours share a path or a mirror without blending.
 */
class LightResult(
    val links: Map<Link, Int>,
    /** Beam colours by (floor, column), for the vertical gap above that floor. */
    val gaps: Map<Pair<Int, Int>, Int>,
    /** Beam colours reaching each door, per beam path that reaches it. */
    val doorLight: Map<String, List<Int>>,
    /** Beam colours that entered each pillar, by the side they came in through. */
    val arrivals: Map<Port, Int>,
    val finalLit: Boolean,
    val steps: Int,
) {
    fun linkColours(varbit: String): Int = links.entries.firstOrNull { it.key.varbit == varbit }?.value ?: 0

    fun isOpen(door: LightDoor): Boolean = doorLight[door.id].orEmpty().any { LightColour.only(it) == door.required }

    fun openDoors(): Set<String> = TempleGeometry.doors.filter { isOpen(it) }.map { it.id }.toSet()

    /** The colours that entered [pillar], whichever side they came in through. */
    fun lightAt(pillar: String): Int =
        arrivals.entries.filter { it.key.pillar == pillar }.fold(0) { acc, e -> acc or e.value }
}

/**
 * Traces the temple's light for one arrangement. White light rises from the great crystal into
 * the middle floor's emitter pillar and every beam is followed through the pillars it reaches:
 * - an empty pillar lets it straight through, vertically too, up or down the shaft;
 * - a coloured crystal tints or stops it ([LightColour.through]) without turning it;
 * - a mirror sends whatever reaches it out of the side it faces;
 * - a fractured crystal takes light only through its clear edge and splits it three ways;
 * - the Final Pillar sends white light west at the black door once the beams have settled with
 *   blue, red and green each alone on the side that wants them (its only way out leads to the
 *   door, so nothing it sends can come back to it);
 * - a door, a wall, the floor, the ceiling or the black crystal stops it.
 *
 * A pillar side only ever gains colours, and there are seven, so every side is revisited at most
 * seven times and a closed loop of mirrors ends on its own. [MAX_STEPS] guards against a geometry
 * mistake rather than a reachable state.
 */
object LightNetwork {
    const val MAX_STEPS = 8192

    fun trace(state: PuzzleState): LightResult = Tracer(state).run()

    private class Tracer(private val state: PuzzleState) {
        private val linkLight = HashMap<Link, Int>()
        private val gaps = HashMap<Pair<Int, Int>, Int>()
        private val doorLight = HashMap<String, MutableMap<Link, Int>>()
        private val arrivals = HashMap<Port, Int>()
        private val pending = ArrayDeque<Triple<String, Facing, Int>>()
        private var finalLit = false
        private var steps = 0

        fun run(): LightResult {
            arrive(Port(TempleGeometry.SOURCE_PILLAR, Facing.DOWN), TempleGeometry.SOURCE_COLOUR.bit)
            while (pending.isNotEmpty()) {
                if (++steps > MAX_STEPS) {
                    error("Temple light did not settle after $MAX_STEPS steps.")
                }
                val (pillar, side, colours) = pending.removeFirst()
                emit(pillar, side, colours)
            }
            finalLit =
                TempleGeometry.finalInputs.all { (side, wanted) ->
                    LightColour.only(arrivals[Port(TempleGeometry.FINAL, side)] ?: 0) == wanted
                }
            if (finalLit) {
                emit(TempleGeometry.FINAL, Facing.WEST, LightColour.WHITE.bit)
            }
            return LightResult(
                links = linkLight.toMap(),
                gaps = gaps.toMap(),
                doorLight = doorLight.mapValues { it.value.values.toList() },
                arrivals = arrivals.toMap(),
                finalLit = finalLit,
                steps = steps,
            )
        }

        /** Beams of [colours] enter [port.pillar] through [port.side]. */
        private fun arrive(port: Port, colours: Int) {
            val previous = arrivals[port] ?: 0
            val added = colours and previous.inv()
            if (added == 0) {
                return
            }
            arrivals[port] = previous or added
            val pillar = TempleGeometry.pillarsById.getValue(port.pillar)
            val travelling = port.side.opposite
            when (pillar.kind) {
                PillarKind.FINAL -> Unit
                PillarKind.FIXED_CRYSTAL -> queue(pillar.id, travelling, tint(added, checkNotNull(pillar.fixedColour)))
                else ->
                    when (val content = state.contentOf(pillar)) {
                        PillarContent.Empty -> queue(pillar.id, travelling, added)
                        is PillarContent.Mirror -> queue(pillar.id, content.facing, added)
                        is PillarContent.Crystal -> through(pillar, content.item, port.side, travelling, added)
                    }
            }
        }

        private fun tint(colours: Int, crystal: LightColour): Int {
            var out = 0
            for (colour in LightColour.ofBits(colours)) {
                colour.through(crystal)?.let { out = out or it.bit }
            }
            return out
        }

        private fun through(pillar: Pillar, item: TempleItem, enteredBy: Facing, travelling: Facing, colours: Int) {
            val tint = item.colour
            if (tint != null) {
                queue(pillar.id, travelling, tint(colours, tint))
                return
            }
            if (enteredBy != item.clearSide) {
                return
            }
            for (side in Facing.entries) {
                if (side.vertical || side == enteredBy) continue
                queue(pillar.id, side, colours)
            }
        }

        private fun queue(pillar: String, side: Facing, colours: Int) {
            if (colours != 0) {
                pending.addLast(Triple(pillar, side, colours))
            }
        }

        /** Beams leave [pillar] through [side]. */
        private fun emit(pillar: String, side: Facing, colours: Int) {
            val link = TempleGeometry.linksByPort[Port(pillar, side)]
            if (link != null) {
                follow(link, Port(pillar, side), colours)
                return
            }
            if (side.vertical) {
                climb(TempleGeometry.pillarsById.getValue(pillar), side, colours)
            }
        }

        private fun follow(link: Link, leaving: Port, colours: Int) {
            linkLight[link] = (linkLight[link] ?: 0) or colours
            val target = if (link.from == leaving) link.to else LinkEnd.ToPillar(link.from)
            when (target) {
                is LinkEnd.ToPillar -> arrive(target.port, colours)
                is LinkEnd.ToDoor -> {
                    val hits = doorLight.getOrPut(target.door) { HashMap() }
                    hits[link] = (hits[link] ?: 0) or colours
                }
                LinkEnd.Wall -> Unit
            }
        }

        /** A beam leaving a grid pillar up or down runs along its shaft to the next pillar. */
        private fun climb(from: Pillar, direction: Facing, colours: Int) {
            val column = from.column ?: return
            val step = if (direction == Facing.UP) 1 else -1
            var floor = from.floor
            while (true) {
                val gapFloor = if (step > 0) floor else floor - 1
                if (gapFloor in 1..3) {
                    val key = gapFloor to column
                    gaps[key] = (gaps[key] ?: 0) or colours
                }
                floor += step
                if (floor !in 1..3 || TempleGeometry.shaftStops[floor to column] != null) {
                    return
                }
                val next = TempleGeometry.shaft[floor to column] ?: continue
                arrive(Port(next, direction.opposite), colours)
                return
            }
        }
    }
}
