package org.rsmod.content.skills.construction.house

import org.rsmod.content.skills.construction.data.Floor
import org.rsmod.content.skills.construction.data.RoomType
import org.rsmod.content.skills.construction.data.Side

/**
 * How a house is laid out on the house viewer's map.
 *
 * The viewer draws a 9x9 grid per floor: the 7x7 area a house can be built in (see [HouseLayout])
 * with a free cell all round, so its window sits one cell south-west of [HouseLayout.ORIGIN]. A
 * house with a room outside that area - built before the area was enforced - can't be shown. Rooms
 * fill its 38 slots in the order the house keeps them, and the map's cells are numbered floor by
 * floor, row by row from the south.
 */
object HouseViewer {
    const val SIZE: Int = HouseLayout.MAX_SIZE + 2
    const val ORIGIN: Int = HouseLayout.ORIGIN - 1
    const val CELLS: Int = SIZE * SIZE
    const val MAX_ROOMS: Int = 38

    /** `poh_viewer_destination` while the player is still picking where to move a room to. */
    const val CHOOSING: Int = CELLS * 3 + 1

    class Placed(val slot: Int, val floor: Floor, val gx: Int, val gz: Int, val room: Room)

    class Layout(val originX: Int, val originZ: Int, val rooms: List<Placed>) {
        val minFloor: Floor = if (rooms.any { it.floor == Floor.DUNGEON }) Floor.DUNGEON else Floor.GROUND
        val maxFloor: Floor = if (rooms.any { it.floor == Floor.UPPER }) Floor.UPPER else Floor.GROUND

        fun slot(slot: Int): Placed? = rooms.getOrNull(slot - 1)

        fun cell(floor: Floor, gx: Int, gz: Int): Int = floor.ordinal * CELLS + (gz - originZ) * SIZE + (gx - originX)

        /** The floor and house grid cell under map cell [index], or null for one off the map. */
        fun cellAt(index: Int): Triple<Floor, Int, Int>? {
            if (index !in 0 until CELLS * Floor.entries.size) {
                return null
            }
            val within = index % CELLS
            return Triple(Floor.entries[index / CELLS], originX + within % SIZE, originZ + within / SIZE)
        }

        /** The south-west and north-east corners of the house, as the coords the map script reads. */
        fun corners(): Pair<Int, Int> =
            coord(minFloor.ordinal, rooms.minOf { it.gx } - originX, rooms.minOf { it.gz } - originZ) to
                coord(maxFloor.ordinal, rooms.maxOf { it.gx } - originX, rooms.maxOf { it.gz } - originZ)
    }

    fun layout(state: HouseState): Layout? {
        val keys = state.rooms.keys.toList()
        if (keys.isEmpty() || keys.size > MAX_ROOMS) {
            return null
        }
        val cells = keys.map { key -> state.rooms.getValue(key) to cellOf(key) }
        if (cells.any { (_, cell) -> !HouseLayout.inArea(cell.second, cell.third, HouseLayout.MAX_SIZE) }) {
            return null
        }
        val placed = cells.mapIndexed { index, (room, cell) -> Placed(index + 1, cell.first, cell.second, cell.third, room) }
        return Layout(ORIGIN, ORIGIN, placed)
    }

    /** The viewer's door bits run north, east, south, west; ours are indexed by [Side]. */
    fun viewerBit(side: Int): Int = (side + 3) and 3

    /** The doors [type]'s template has before it is turned, as the viewer's bits. */
    fun doorBits(type: RoomType): Int = Side.ALL.filter { type.doors and (1 shl it) != 0 }.sumOf { 1 shl viewerBit(it) }

    /**
     * Which sides of [gx], [gz] have a neighbour with a door facing it, ignoring the room standing
     * at [ignoreX], [ignoreZ] - the one being moved away from there.
     */
    fun adjacentDoors(state: HouseState, floor: Floor, gx: Int, gz: Int, ignoreX: Int = -1, ignoreZ: Int = -1): Int =
        Side.ALL.filter { side ->
            val nx = gx + Side.deltaX(side)
            val nz = gz + Side.deltaZ(side)
            val neighbour = state[floor, nx, nz]
            neighbour != null &&
                !(nx == ignoreX && nz == ignoreZ) &&
                neighbour.type.hasDoor(Side.opposite(side), neighbour.rotation)
        }.sumOf { 1 shl viewerBit(it) }

    /**
     * Packs a room into the three words `poh_viewer_setroom` takes: its viewer cell, floor,
     * rotation and room type, then for each of the room's hotspots the 1-based index of what is
     * built there in that hotspot's build list, its bits scattered over the words as the script
     * reads them back. The script can't be handed a negative word, so bit 31 of the first travels
     * as bit 30 of the second.
     */
    fun pack(layout: Layout, placed: Placed, hotspots: IntArray): IntArray {
        val words = IntArray(3)
        words[0] =
            (placed.gx - layout.originX) or
            ((placed.gz - layout.originZ) shl 3) or
            (placed.floor.ordinal shl 6) or
            (placed.room.rotation shl 8) or
            (placed.room.type.roomTypeId shl 10)
        for ((hotspot, value) in hotspots.withIndex()) {
            val bits = HOTSPOT_BITS.getOrNull(hotspot) ?: break
            if (value <= 0 || value >= 1 shl bits.size) {
                continue
            }
            for ((bit, target) in bits.withIndex()) {
                if (value and (1 shl bit) != 0) {
                    val (word, at) = target
                    words[word] = words[word] or (1 shl at)
                }
            }
        }
        return words
    }

    fun coord(level: Int, x: Int, z: Int): Int = (level shl 28) or (x shl 14) or z

    private fun cellOf(key: Int): Triple<Floor, Int, Int> =
        Triple(HouseState.floorOf(key), HouseState.gxOf(key), HouseState.gzOf(key))

    /** For each hotspot, the (word, bit) each bit of its value goes to, lowest bit first. */
    private val HOTSPOT_BITS: List<List<Pair<Int, Int>>> =
        listOf(
            listOf(0 to 15, 0 to 16, 0 to 17, 1 to 0, 1 to 1, 2 to 0, 2 to 1, 2 to 2),
            listOf(0 to 18, 0 to 19, 0 to 20, 1 to 2, 1 to 3, 2 to 3, 2 to 4, 2 to 5),
            listOf(0 to 21, 0 to 22, 0 to 23, 1 to 4, 1 to 5, 2 to 6, 2 to 7, 2 to 8),
            listOf(0 to 24, 0 to 25, 1 to 6, 1 to 7, 1 to 8, 2 to 9, 2 to 10),
            listOf(0 to 26, 0 to 27, 1 to 9, 1 to 10, 1 to 11),
            listOf(0 to 28, 0 to 29, 1 to 12, 1 to 13, 1 to 14),
            listOf(0 to 30, 1 to 30, 1 to 15, 1 to 16, 1 to 17),
            (18..22).map { 1 to it },
            (23..27).map { 1 to it },
        )
}
