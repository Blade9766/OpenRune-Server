package org.rsmod.content.skills.construction.house

import org.rsmod.api.player.stat.statBase
import org.rsmod.content.skills.construction.Construction
import org.rsmod.content.skills.construction.data.Floor
import org.rsmod.content.skills.construction.data.Furniture
import org.rsmod.content.skills.construction.data.RoomType
import org.rsmod.content.skills.construction.data.Side
import org.rsmod.game.entity.Player

/**
 * The rules for changing where rooms stand, shared by the doorway build menu and the house viewer.
 * Each check returns why the change can't be made, or null when it can.
 *
 * Every room has to be reachable: door to door from a staircase, or on the ground floor from a
 * doorway onto the yard's lawn, which leads outside. A change - building, moving, turning or
 * removing a room - may never leave more rooms cut off than before ([cutOff]), so nothing is
 * stranded, and a new floor can only be started with a staircase.
 *
 * Sizes and room limits go by the owner's base Construction level, not a boosted one, so a boost
 * can't build past what the yard will show once it wears off.
 *
 * A house is built inside a square area that grows with Construction level, from 3x3 rooms to
 * [MAX_SIZE] by [MAX_SIZE]. Its south-west corner is fixed at [ORIGIN], so it grows a row to the
 * north and a column to the east each time, as the wiki describes; the starter garden stands in the
 * middle of the first 3x3.
 */
object HouseLayout {
    const val MAX_SIZE: Int = 7
    const val ORIGIN: Int = Construction.STARTER_CELL - 1

    /** How many rooms across, and up, a house may be at a Construction [level]. */
    fun maxSize(level: Int): Int =
        when {
            level >= 60 -> 7
            level >= 45 -> 6
            level >= 30 -> 5
            level >= 15 -> 4
            else -> 3
        }

    /** The area an owner may build in: [maxSize] for their level, or all of it in free build. */
    fun sizeFor(level: Int, free: Boolean): Int = if (free) MAX_SIZE else maxSize(level)

    fun sizeOf(owner: Player): Int = sizeFor(owner.statBase(Construction.STAT), owner.freeBuild)

    fun inArea(gx: Int, gz: Int, size: Int): Boolean = gx - ORIGIN in 0 until size && gz - ORIGIN in 0 until size

    /**
     * The yard: the building area for an owner whose area is [size] rooms across, with a ring of
     * lawn one room wide all round it. Beyond it there is nothing.
     */
    fun inYard(gx: Int, gz: Int, size: Int): Boolean =
        gx - ORIGIN + 1 in 0 until size + 2 && gz - ORIGIN + 1 in 0 until size + 2

    /** Why a room can't go at [gx], [gz] in an area [size] rooms across, or null when it can. */
    fun areaProblem(gx: Int, gz: Int, size: Int): String? =
        when {
            !inArea(gx, gz, MAX_SIZE) -> "You cannot build any further out in that direction."
            !inArea(gx, gz, size) -> "You need a higher Construction level to build your house out that far."
            else -> null
        }

    /**
     * How many rooms, gardens and dungeon rooms included, a house may have at a Construction
     * [level]: 24, one more at 26 and every six levels after up to 92, and one each at 96 and 99.
     */
    fun maxRooms(level: Int): Int =
        when {
            level < 26 -> 24
            else -> 25 + (minOf(level, 92) - 26) / 6 + (if (level >= 96) 1 else 0) + (if (level >= 99) 1 else 0)
        }

    /**
     * Why [state] can't take another room, for an owner at Construction [level]. Free build goes to
     * the most any level allows, which is also all the house viewer can show.
     */
    fun roomLimitProblem(state: HouseState, level: Int, free: Boolean = false): String? {
        val max = if (free) maxRooms(MAX_LEVEL) else maxRooms(level)
        return when {
            state.rooms.size < max -> null
            free -> "Your house can't hold more than $max rooms."
            else -> "You can only have $max rooms in your house at your Construction level."
        }
    }

    fun roomLimitProblem(state: HouseState, owner: Player): String? =
        roomLimitProblem(state, owner.statBase(Construction.STAT), owner.freeBuild)

    /**
     * The menagerie a player picking [selected] at [gx], [gz] gets: the outdoor one, with its
     * habitat, when it would stand beside an outdoor room - the same for a doorway and the viewer.
     */
    fun menagerieFor(state: HouseState, floor: Floor, gx: Int, gz: Int, selected: RoomType): RoomType {
        if (selected != RoomType.MENAGERIE_INDOOR) {
            return selected
        }
        val outdoors = Side.ALL.any { state.neighbour(floor, gx, gz, it)?.type?.outdoors == true }
        return if (outdoors) RoomType.MENAGERIE_OUTDOOR else selected
    }

    fun hasStairs(room: Room): Boolean =
        room.type.hotspots.any { group ->
            val option = room.furniture[group.key] ?: return@any false
            group.options[option].built.any { it in Furniture.STAIRS_DOWN.keys || it in Furniture.STAIRS_DOWN.values }
        }

    /** A room tied to another floor by a staircase, its own or the one rising into it. */
    fun stairsLinked(state: HouseState, floor: Floor, gx: Int, gz: Int): Boolean {
        val room = state[floor, gx, gz] ?: return false
        return hasStairs(room) || state.hasStairsBelow(floor, gx, gz)
    }

    /**
     * How many rooms on [floor] can't be reached: walking door to door from a way onto the floor -
     * a staircase, or on the ground floor a doorway onto the lawn of a yard [size] rooms across.
     */
    fun cutOff(state: HouseState, floor: Floor, size: Int): Int {
        val cells = state.cells(floor).toSet()
        val start =
            cells.filter { (gx, gz) ->
                stairsLinked(state, floor, gx, gz) || (floor == Floor.GROUND && opensOntoLawn(state, gx, gz, size))
            }
        val reached = start.toMutableSet()
        val queue = ArrayDeque(start)
        while (queue.isNotEmpty()) {
            val (gx, gz) = queue.removeFirst()
            for (side in Side.ALL) {
                val next = gx + Side.deltaX(side) to gz + Side.deltaZ(side)
                if (next in cells && next !in reached && state.connected(floor, gx, gz, side)) {
                    reached += next
                    queue.addLast(next)
                }
            }
        }
        return cells.size - reached.size
    }

    private fun opensOntoLawn(state: HouseState, gx: Int, gz: Int, size: Int): Boolean {
        val room = state[Floor.GROUND, gx, gz] ?: return false
        return Side.ALL.any { side ->
            val nx = gx + Side.deltaX(side)
            val nz = gz + Side.deltaZ(side)
            room.type.hasDoor(side, room.rotation) && state[Floor.GROUND, nx, nz] == null && inYard(nx, nz, size)
        }
    }

    fun rotateProblem(state: HouseState, floor: Floor, gx: Int, gz: Int, rotation: Int, size: Int): String? {
        state[floor, gx, gz] ?: return NO_ROOM
        if (stairsLinked(state, floor, gx, gz)) {
            return "You can't turn a room that a staircase runs through."
        }
        val after = state.copy()
        after[floor, gx, gz]?.rotation = rotation
        return splitProblem(state, after, floor, size)
    }

    fun moveProblem(
        state: HouseState,
        floor: Floor,
        gx: Int,
        gz: Int,
        toX: Int,
        toZ: Int,
        rotation: Int,
        size: Int,
    ): String? {
        val room = state[floor, gx, gz] ?: return NO_ROOM
        placeProblem(state, floor, toX, toZ, size)?.let { return it }
        stairsTopProblem(state, floor, toX, toZ, room.type)?.let { return it }
        if (stairsLinked(state, floor, gx, gz)) {
            return "You can't move a room that a staircase runs through."
        }
        if (state.supportsRoomAbove(floor, gx, gz)) {
            return "You must move the room above before you can move this one."
        }
        val after = state.copy()
        after[floor, gx, gz] = null
        after[floor, toX, toZ] = Room(room.type, rotation, LinkedHashMap(room.furniture))
        return splitProblem(state, after, floor, size)
    }

    fun addProblem(state: HouseState, floor: Floor, gx: Int, gz: Int, type: RoomType, rotation: Int, size: Int): String? {
        placeProblem(state, floor, gx, gz, size)?.let { return it }
        stairsTopProblem(state, floor, gx, gz, type)?.let { return it }
        if (floor !in type.floors) {
            return "You cannot build a ${type.label.lowercase()} on the ${floor.label}."
        }
        if (type.unique && state.rooms.values.any { it.type == type }) {
            return "You can only have one ${type.label.lowercase()} in your house."
        }
        val after = state.copy()
        after[floor, gx, gz] = Room(type, rotation)
        if (cutOff(after, floor, size) > cutOff(state, floor, size)) {
            return "The new room must join onto a doorway of your house."
        }
        return null
    }

    fun removalProblem(state: HouseState, floor: Floor, gx: Int, gz: Int, costumesStored: Boolean, size: Int): String? {
        val room = state[floor, gx, gz] ?: return NO_ROOM
        val after = state.copy()
        after[floor, gx, gz] = null
        return when {
            state.isEntrance(room) -> "You cannot remove the garden your exit portal stands in."
            state.supportsRoomAbove(floor, gx, gz) -> "You must remove the room above before you can take this one out."
            floor == Floor.GROUND && state.hasStairsBelow(floor, gx, gz) ->
                "You must remove the dungeon stairs below before you can take this one out."
            room.type == RoomType.COSTUME_ROOM && costumesStored ->
                "You must empty the costume room's furniture before you can remove it."
            else -> splitProblem(state, after, floor, size)
        }
    }

    /** The top of a staircase from below has to be a hall, whose template has the stairs' head. */
    private fun stairsTopProblem(state: HouseState, floor: Floor, gx: Int, gz: Int, type: RoomType): String? =
        if (floor == Floor.UPPER && state.hasStairsBelow(floor, gx, gz) && !type.isHall) {
            "Only a hall can stand at the top of a staircase."
        } else {
            null
        }

    private fun placeProblem(state: HouseState, floor: Floor, gx: Int, gz: Int, size: Int): String? =
        when {
            areaProblem(gx, gz, size) != null -> areaProblem(gx, gz, size)
            state[floor, gx, gz] != null -> "There is already a room there."
            floor == Floor.UPPER && state[Floor.GROUND, gx, gz] == null -> "There is no room below to hold it up."
            else -> null
        }

    private fun splitProblem(before: HouseState, after: HouseState, floor: Floor, size: Int): String? =
        if (cutOff(after, floor, size) > cutOff(before, floor, size)) {
            "That would leave part of your house with no way in."
        } else {
            null
        }

    private fun HouseState.copy(): HouseState = HouseState.decode(encode())

    private const val NO_ROOM = "There is no room there."
    private const val MAX_LEVEL = 99
}
