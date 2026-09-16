package org.rsmod.content.skills.construction.house

import org.rsmod.content.skills.construction.Construction
import org.rsmod.content.skills.construction.data.Floor
import org.rsmod.content.skills.construction.data.HouseLocation
import org.rsmod.content.skills.construction.data.HouseStyle
import org.rsmod.content.skills.construction.data.RoomType
import org.rsmod.content.skills.construction.data.Side

/** A room placed on the house grid, and what has been built on its hotspots. */
class Room(val type: RoomType, var rotation: Int, val furniture: MutableMap<String, Int> = LinkedHashMap()) {
    fun encode(): String {
        val built = furniture.entries.joinToString("|") { "${it.key}=${it.value}" }
        return "${type.name},$rotation,$built"
    }

    companion object {
        fun decode(encoded: String): Room? {
            val parts = encoded.split(',')
            if (parts.size != 3) {
                return null
            }
            val type = RoomType.entries.firstOrNull { it.name == parts[0] } ?: return null
            val rotation = parts[1].toIntOrNull()?.takeIf { it in 0..3 } ?: return null
            val room = Room(type, rotation)
            for (entry in parts[2].split('|')) {
                if (entry.isEmpty()) {
                    continue
                }
                val split = entry.indexOf('=')
                if (split <= 0) {
                    continue
                }
                val key = entry.substring(0, split)
                val option = entry.substring(split + 1).toIntOrNull() ?: continue
                val hotspot = type.hotspot(key) ?: continue
                if (option in hotspot.options.indices) {
                    room.furniture[key] = option
                }
            }
            return room
        }
    }
}

/**
 * Everything the server remembers about a house between visits: who owns one, how it is decorated,
 * and which rooms sit where. The region it is assembled into is thrown away and rebuilt from this
 * every time the player walks through a portal.
 */
class HouseState(
    var owned: Boolean = false,
    var style: HouseStyle = HouseStyle.BASIC_WOOD,
    var location: HouseLocation = HouseLocation.RIMMINGTON,
    val rooms: MutableMap<Int, Room> = LinkedHashMap(),
) {
    operator fun get(floor: Floor, gx: Int, gz: Int): Room? = rooms[key(floor, gx, gz)]

    operator fun set(floor: Floor, gx: Int, gz: Int, room: Room?) {
        val key = key(floor, gx, gz)
        if (room == null) {
            rooms.remove(key)
        } else {
            rooms[key] = room
        }
    }

    fun neighbour(floor: Floor, gx: Int, gz: Int, side: Int): Room? =
        get(floor, gx + Side.deltaX(side), gz + Side.deltaZ(side))

    /** True when both rooms have a door on the shared edge, so the two are actually joined. */
    fun connected(floor: Floor, gx: Int, gz: Int, side: Int): Boolean {
        val room = get(floor, gx, gz) ?: return false
        if (!room.type.hasDoor(side, room.rotation)) {
            return false
        }
        val other = neighbour(floor, gx, gz, side) ?: return false
        return other.type.hasDoor(Side.opposite(side), other.rotation)
    }

    /** True when a room sits directly on top of this cell, holding it up. */
    fun supportsRoomAbove(floor: Floor, gx: Int, gz: Int): Boolean {
        val above = Floor.entries.getOrNull(floor.ordinal + 1) ?: return false
        return get(above, gx, gz) != null
    }

    /** The garden the exit portal stands in, which is the only way out on foot. */
    fun isEntrance(room: Room): Boolean =
        room.type == RoomType.GARDEN && room.furniture[CENTREPIECE] == EXIT_PORTAL

    /** Lays down the single garden every new house starts with, portal already standing. */
    fun createStarterHouse() {
        rooms.clear()
        val garden = Room(RoomType.GARDEN, rotation = 0)
        val centrepiece = RoomType.GARDEN.hotspot(CENTREPIECE)
        if (centrepiece != null) {
            garden.furniture[CENTREPIECE] = EXIT_PORTAL
        }
        this[Floor.GROUND, Construction.STARTER_CELL, Construction.STARTER_CELL] = garden
        owned = true
    }

    fun encode(): String {
        val header = "${if (owned) 1 else 0},${style.name},${location.name}"
        val body =
            rooms.entries.joinToString(";") { (key, room) ->
                "${floorOf(key).name},${gxOf(key)},${gzOf(key)},${room.encode()}"
            }
        return "$header;$body"
    }

    companion object {
        const val CENTREPIECE = "centrepiece"

        /** Index of the exit portal in the garden centrepiece's option list. */
        const val EXIT_PORTAL = 0

        private const val AXIS_BITS = 5
        private const val AXIS_MASK = (1 shl AXIS_BITS) - 1

        fun key(floor: Floor, gx: Int, gz: Int): Int =
            (floor.ordinal shl (AXIS_BITS * 2)) or (gx shl AXIS_BITS) or gz

        fun floorOf(key: Int): Floor = Floor.entries[key shr (AXIS_BITS * 2)]

        private fun gxOf(key: Int): Int = (key shr AXIS_BITS) and AXIS_MASK

        private fun gzOf(key: Int): Int = key and AXIS_MASK

        fun inBounds(gx: Int, gz: Int): Boolean = gx in 0 until Construction.GRID && gz in 0 until Construction.GRID

        fun decode(encoded: String): HouseState {
            val sections = encoded.split(';')
            val state = HouseState()
            val header = sections.firstOrNull()?.split(',') ?: return state
            if (header.size != 3) {
                return state
            }
            state.owned = header[0] == "1"
            state.style = HouseStyle.entries.firstOrNull { it.name == header[1] } ?: state.style
            state.location = HouseLocation.entries.firstOrNull { it.name == header[2] } ?: state.location
            for (section in sections.drop(1)) {
                if (section.isEmpty()) {
                    continue
                }
                val fields = section.split(',', limit = 4)
                if (fields.size != 4) {
                    continue
                }
                val floor = Floor.entries.firstOrNull { it.name == fields[0] } ?: continue
                val gx = fields[1].toIntOrNull() ?: continue
                val gz = fields[2].toIntOrNull() ?: continue
                if (!inBounds(gx, gz)) {
                    continue
                }
                val room = Room.decode(fields[3]) ?: continue
                state[floor, gx, gz] = room
            }
            return state
        }
    }
}
