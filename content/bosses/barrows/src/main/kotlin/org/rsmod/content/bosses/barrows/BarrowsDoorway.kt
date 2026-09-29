package org.rsmod.content.bosses.barrows

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import kotlin.random.Random
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/** A room in the 3x3 tunnel grid; [RING] is the outer passage joining the four corner rooms. */
internal enum class TunnelRoom {
    NORTH_WEST,
    NORTH,
    NORTH_EAST,
    WEST,
    CENTRE,
    EAST,
    SOUTH_WEST,
    SOUTH,
    SOUTH_EAST,
    RING,
}

/**
 * One corridor of the tunnels. The cache gives each corridor a single door varbit (1 = locked)
 * shared by every door leaf on it; the four long passages each join two corner rooms to the ring.
 */
internal enum class BarrowsDoorway(
    val varbit: String,
    val links: List<Pair<TunnelRoom, TunnelRoom>>,
) {
    NORTH_LONG_PASSAGE(
        "varbit.barrows_door_a",
        listOf(TunnelRoom.NORTH_WEST to TunnelRoom.RING, TunnelRoom.NORTH_EAST to TunnelRoom.RING),
    ),
    WEST_LONG_PASSAGE(
        "varbit.barrows_door_b",
        listOf(TunnelRoom.NORTH_WEST to TunnelRoom.RING, TunnelRoom.SOUTH_WEST to TunnelRoom.RING),
    ),
    NORTH_WEST_TO_WEST("varbit.barrows_door_c", listOf(TunnelRoom.NORTH_WEST to TunnelRoom.WEST)),
    NORTH_TO_NORTH_WEST("varbit.barrows_door_d", listOf(TunnelRoom.NORTH_WEST to TunnelRoom.NORTH)),
    NORTH_TO_CENTER("varbit.barrows_door_e", listOf(TunnelRoom.NORTH to TunnelRoom.CENTRE)),
    NORTH_EAST_TO_NORTH("varbit.barrows_door_f", listOf(TunnelRoom.NORTH to TunnelRoom.NORTH_EAST)),
    EAST_TO_NORTH_EAST("varbit.barrows_door_g", listOf(TunnelRoom.NORTH_EAST to TunnelRoom.EAST)),
    EAST_LONG_PASSAGE(
        "varbit.barrows_door_h",
        listOf(TunnelRoom.NORTH_EAST to TunnelRoom.RING, TunnelRoom.SOUTH_EAST to TunnelRoom.RING),
    ),
    WEST_TO_CENTER("varbit.barrows_door_i", listOf(TunnelRoom.WEST to TunnelRoom.CENTRE)),
    EAST_TO_CENTER("varbit.barrows_door_j", listOf(TunnelRoom.CENTRE to TunnelRoom.EAST)),
    WEST_TO_SOUTH_WEST("varbit.barrows_door_k", listOf(TunnelRoom.WEST to TunnelRoom.SOUTH_WEST)),
    SOUTH_TO_CENTER("varbit.barrows_door_l", listOf(TunnelRoom.CENTRE to TunnelRoom.SOUTH)),
    SOUTH_EAST_TO_EAST("varbit.barrows_door_m", listOf(TunnelRoom.EAST to TunnelRoom.SOUTH_EAST)),
    SOUTH_WEST_TO_SOUTH("varbit.barrows_door_n", listOf(TunnelRoom.SOUTH_WEST to TunnelRoom.SOUTH)),
    SOUTH_TO_SOUTH_EAST("varbit.barrows_door_o", listOf(TunnelRoom.SOUTH to TunnelRoom.SOUTH_EAST)),
    SOUTH_LONG_PASSAGE(
        "varbit.barrows_door_p",
        listOf(TunnelRoom.SOUTH_WEST to TunnelRoom.RING, TunnelRoom.SOUTH_EAST to TunnelRoom.RING),
    );

    val locs: List<String> =
        varbit.removePrefix("varbit.barrows_door_").let { letter ->
            listOf("loc.barrows_door_${letter}_r", "loc.barrows_door_${letter}_l")
        }

    val entersCentre: Boolean
        get() = links.any { TunnelRoom.CENTRE in it.toList() }

    val mask: Int
        get() = 1 shl ordinal

    companion object {
        private val byLocId: Map<Int, BarrowsDoorway> by lazy {
            entries.flatMap { door -> door.locs.map { it.asRSCM(RSCMType.LOC) to door } }.toMap()
        }

        fun forLoc(locId: Int): BarrowsDoorway? = byLocId[locId]
    }
}

/**
 * Picks which doorways are locked. Every room but the chest room stays reachable from every other
 * through open doors (the ring counts as a room), and exactly one doorway into the chest room is
 * open, so wherever the hidden tunnel drops the player there is a route to the puzzle door and
 * back to a ladder.
 */
internal object TunnelLayout {
    private const val OPEN_CHANCE_PERCENT = 55

    fun generate(random: Random = Random.Default): Set<BarrowsDoorway> {
        val centreDoors = BarrowsDoorway.entries.filter { it.entersCentre }
        val outerDoors = BarrowsDoorway.entries.filterNot { it.entersCentre }
        while (true) {
            val open =
                outerDoors.filterTo(mutableSetOf()) { random.nextInt(100) < OPEN_CHANCE_PERCENT }
            if (!outerRoomsConnected(open)) {
                continue
            }
            open += centreDoors[random.nextInt(centreDoors.size)]
            return BarrowsDoorway.entries.toSet() - open
        }
    }

    fun outerRoomsConnected(open: Set<BarrowsDoorway>): Boolean {
        val outer = TunnelRoom.entries - TunnelRoom.CENTRE
        val edges = open.flatMap { it.links }.filter { TunnelRoom.CENTRE !in it.toList() }
        val seen = mutableSetOf(outer.first())
        val queue = ArrayDeque(seen)
        while (queue.isNotEmpty()) {
            val room = queue.removeFirst()
            for ((a, b) in edges) {
                val next =
                    when (room) {
                        a -> b
                        b -> a
                        else -> continue
                    }
                if (seen.add(next)) {
                    queue.addLast(next)
                }
            }
        }
        return seen.containsAll(outer)
    }

    fun toMask(locked: Set<BarrowsDoorway>): Int = locked.sumOf { it.mask }

    fun fromMask(mask: Int): Set<BarrowsDoorway> =
        BarrowsDoorway.entries.filterTo(mutableSetOf()) { mask and it.mask != 0 }
}

internal var Player.lockedDoorMask by intVarBit("varbit.barrows_locked_doors")

internal fun Player.shuffleTunnels() {
    lockedDoorMask = TunnelLayout.toMask(TunnelLayout.generate())
    rollPuzzle()
}

internal val Player.shutDoorways: Set<BarrowsDoorway>
    get() = TunnelLayout.fromMask(lockedDoorMask)

internal fun Player.refreshDoors(picking: Boolean) {
    val shut = if (picking) emptySet() else shutDoorways
    for (doorway in BarrowsDoorway.entries) {
        VarPlayerIntMapSetter.set(this, doorway.varbit, if (doorway in shut) 1 else 0)
    }
}

/**
 * The tile on the other side of a wall door from [from]. A wall sits on one edge of its own tile
 * (angle 0 west, 1 north, 2 east, 3 south); the player keeps their row or column so either leaf of
 * a double door works.
 */
internal fun crossingTile(from: CoordGrid, door: CoordGrid, angle: Int): CoordGrid =
    when (angle) {
        0 -> CoordGrid(if (from.x < door.x) door.x else door.x - 1, from.z, from.level)
        2 -> CoordGrid(if (from.x <= door.x) door.x + 1 else door.x, from.z, from.level)
        1 -> CoordGrid(from.x, if (from.z <= door.z) door.z + 1 else door.z, from.level)
        else -> CoordGrid(from.x, if (from.z < door.z) door.z else door.z - 1, from.level)
    }
