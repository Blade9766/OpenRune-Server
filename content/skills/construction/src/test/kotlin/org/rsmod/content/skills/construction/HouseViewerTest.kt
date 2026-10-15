package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.table.PohRoomRow
import org.rsmod.content.skills.construction.Construction.STARTER_CELL
import org.rsmod.content.skills.construction.data.Floor
import org.rsmod.content.skills.construction.data.Furniture
import org.rsmod.content.skills.construction.data.RoomType
import org.rsmod.content.skills.construction.data.Side
import org.rsmod.content.skills.construction.house.HouseLayout
import org.rsmod.content.skills.construction.house.HouseState
import org.rsmod.content.skills.construction.house.HouseViewer
import org.rsmod.content.skills.construction.house.Room

class HouseViewerTest {
    private val max = HouseLayout.MAX_SIZE

    private fun starter(): HouseState = HouseState().apply { createStarterHouse() }

    @Test
    fun `the viewer window sits one cell south-west of the building area`() {
        val state = starter()
        state[Floor.GROUND, STARTER_CELL + 1, STARTER_CELL] = Room(RoomType.PARLOUR, 1)
        val layout = HouseViewer.layout(state)!!
        assertEquals(HouseLayout.ORIGIN - 1, layout.originX)
        assertEquals(HouseLayout.ORIGIN - 1, layout.originZ)
        assertEquals(listOf(1, 2), layout.rooms.map { it.slot })
        // The starter garden is in the middle of the first 3x3, so two cells in on the map.
        assertEquals(HouseViewer.coord(1, 2, 2), layout.corners().first)
        assertEquals(HouseViewer.coord(1, 3, 2), layout.corners().second)

        val cell = layout.cell(Floor.GROUND, STARTER_CELL + 1, STARTER_CELL)
        assertEquals(HouseViewer.CELLS + HouseViewer.SIZE * 2 + 3, cell)
        assertEquals(Triple(Floor.GROUND, STARTER_CELL + 1, STARTER_CELL), layout.cellAt(cell))
    }

    @Test
    fun `a house with a room outside the building area can't be shown`() {
        val state = starter()
        for (x in HouseLayout.ORIGIN until HouseLayout.ORIGIN + HouseLayout.MAX_SIZE) {
            state[Floor.GROUND, x, STARTER_CELL] = Room(RoomType.GARDEN, 0)
        }
        assertNotNull(HouseViewer.layout(state))
        state[Floor.GROUND, HouseLayout.ORIGIN - 1, STARTER_CELL] = Room(RoomType.GARDEN, 0)
        assertNull(HouseViewer.layout(state))
    }

    @Test
    fun `door bits run north east south west`() {
        assertEquals(0b1111, HouseViewer.doorBits(RoomType.GARDEN))
        // The kitchen's template has doors west and south.
        assertEquals(0b1100, HouseViewer.doorBits(RoomType.KITCHEN))
        assertEquals(0, HouseViewer.viewerBit(Side.NORTH))
        assertEquals(1, HouseViewer.viewerBit(Side.EAST))
        assertEquals(2, HouseViewer.viewerBit(Side.SOUTH))
        assertEquals(3, HouseViewer.viewerBit(Side.WEST))
        // Every template has a south door, which the side panel takes for granted.
        assertTrue(RoomType.entries.all { it.doors and (1 shl Side.SOUTH) != 0 })
    }

    @Test
    fun `adjacent doors leave out the room being moved away`() {
        val state = starter()
        state[Floor.GROUND, STARTER_CELL + 1, STARTER_CELL] = Room(RoomType.GARDEN, 0)
        val east = 1 shl HouseViewer.viewerBit(Side.EAST)
        assertEquals(east, HouseViewer.adjacentDoors(state, Floor.GROUND, STARTER_CELL, STARTER_CELL))
        assertEquals(
            0,
            HouseViewer.adjacentDoors(state, Floor.GROUND, STARTER_CELL, STARTER_CELL, STARTER_CELL + 1, STARTER_CELL),
        )
    }

    @Test
    fun `a room packs its cell, floor, turn, type and furniture into the script's bits`() {
        val state = starter()
        val layout = HouseViewer.layout(state)!!
        val placed = layout.rooms.single()
        placed.room.rotation = 3
        val hotspots = IntArray(9).also { it[0] = 0b10101010; it[6] = 0b00011; it[8] = 0b11111 }
        val (first, second, third) = HouseViewer.pack(layout, placed, hotspots).toList()

        assertEquals(2, first and 7)
        assertEquals(2, (first shr 3) and 7)
        assertEquals(Floor.GROUND.ordinal, (first shr 6) and 3)
        assertEquals(3, (first shr 8) and 3)
        assertEquals(RoomType.GARDEN.roomTypeId, (first shr 10) and 31)
        assertTrue(first >= 0 && second >= 0 && third >= 0)

        // Hotspot one's bits 1, 3, 5 and 7 land on word one bit 16, word two bit 0 and word three bits 0 and 2.
        assertEquals(1, (first shr 16) and 1)
        assertEquals(1, second and 1)
        assertEquals(0b101, third and 0b111)
        // Hotspot seven's second bit stands in for the first word's sign bit.
        assertEquals(1, (first shr 30) and 1)
        assertEquals(1, (second shr 30) and 1)
        assertEquals(0b11111, (second shr 23) and 0b11111)
    }

    /** A dungeon stairs room at [x], [z] with its staircase built, and cross rooms east of it. */
    private fun dungeon(x: Int, z: Int, crosses: Int): HouseState {
        val state = starter()
        val stairs = Room(RoomType.DUNGEON_STAIRS, 0)
        val (key, option) =
            RoomType.DUNGEON_STAIRS.hotspots.firstNotNullOf { group ->
                val index = group.options.indexOfFirst { option -> option.built.any { it in Furniture.STAIRS_DOWN.keys || it in Furniture.STAIRS_DOWN.values } }
                if (index >= 0) group.key to index else null
            }
        stairs.furniture[key] = option
        assertTrue(HouseLayout.hasStairs(stairs))
        state[Floor.DUNGEON, x, z] = stairs
        for (i in 1..crosses) {
            state[Floor.DUNGEON, x + i, z] = Room(RoomType.DUNGEON_CROSS, 0)
        }
        return state
    }

    @Test
    fun `a change may not cut rooms off from the stairs`() {
        val x = STARTER_CELL
        val state = dungeon(x, x, crosses = 2)
        assertEquals(0, HouseLayout.cutOff(state, Floor.DUNGEON, max))

        // Taking out the middle room strands the far one; the far one itself can go.
        assertNotNull(HouseLayout.removalProblem(state, Floor.DUNGEON, x + 1, x, false, max))
        assertNull(HouseLayout.removalProblem(state, Floor.DUNGEON, x + 2, x, false, max))
        assertNotNull(HouseLayout.moveProblem(state, Floor.DUNGEON, x + 1, x, x + 1, x + 1, 0, max))
        assertNull(HouseLayout.moveProblem(state, Floor.DUNGEON, x + 2, x, x + 1, x + 1, 0, max))
        assertNotNull(HouseLayout.addProblem(state, Floor.DUNGEON, x + 4, x, RoomType.DUNGEON_CROSS, 0, max))
        assertNull(HouseLayout.addProblem(state, Floor.DUNGEON, x + 3, x, RoomType.DUNGEON_CROSS, 0, max))

        // A corridor joined on its west door can't be turned to run north-south, away from the rest.
        val joined = RoomType.DUNGEON_CORRIDOR.rotationsFacing(Side.WEST).first()
        state[Floor.DUNGEON, x + 3, x] = Room(RoomType.DUNGEON_CORRIDOR, joined)
        val away = (0..3).first { it !in RoomType.DUNGEON_CORRIDOR.rotationsFacing(Side.WEST) }
        assertNull(HouseLayout.rotateProblem(state, Floor.DUNGEON, x + 3, x, joined, max))
        assertNotNull(HouseLayout.rotateProblem(state, Floor.DUNGEON, x + 3, x, away, max))
    }

    @Test
    fun `a dungeon room holds nothing up, so the stairs room under a hall can come out`() {
        val x = STARTER_CELL
        val state = dungeon(x, x, crosses = 0)
        state[Floor.GROUND, x, x] = Room(RoomType.SKILL_HALL, 0)
        assertTrue(!state.supportsRoomAbove(Floor.DUNGEON, x, x))
        assertNull(HouseLayout.removalProblem(state, Floor.DUNGEON, x, x, false, max))
        // The hall over it still can't, while the stairs below rise into it.
        assertNotNull(HouseLayout.removalProblem(state, Floor.GROUND, x, x, false, max))
    }

    @Test
    fun `only a hall can stand at the top of a staircase`() {
        val x = STARTER_CELL
        val state = dungeon(x, x, crosses = 0)
        val hall = Room(RoomType.SKILL_HALL, 0)
        val stairs = RoomType.SKILL_HALL.hotspots.firstNotNullOf { group ->
            val index = group.options.indexOfFirst { option -> option.built.any { it in Furniture.STAIRS_DOWN.keys } }
            if (index >= 0) group.key to index else null
        }
        hall.furniture[stairs.first] = stairs.second
        state[Floor.GROUND, x + 1, x] = hall
        assertNotNull(HouseLayout.addProblem(state, Floor.UPPER, x + 1, x, RoomType.PARLOUR, 0, max))
        assertNull(HouseLayout.addProblem(state, Floor.UPPER, x + 1, x, RoomType.SKILL_HALL, 0, max))
    }

    @Test
    fun `on the ground floor a doorway onto the lawn is a way in`() {
        val state = starter()
        val x = STARTER_CELL
        // A garden two cells off, joined to nothing, still opens onto the lawn.
        assertNull(HouseLayout.addProblem(state, Floor.GROUND, x + 2, x, RoomType.GARDEN, 0, max))
        state[Floor.GROUND, x + 1, x] = Room(RoomType.GARDEN, 0)
        state[Floor.GROUND, x + 2, x] = Room(RoomType.GARDEN, 0)
        assertNull(HouseLayout.removalProblem(state, Floor.GROUND, x + 1, x, false, max))
        assertEquals(0, HouseLayout.cutOff(state, Floor.GROUND, max))
    }

    @Test
    fun `an upstairs room needs a room below, whichever way it is built`() {
        val state = starter()
        val x = STARTER_CELL
        assertNotNull(HouseLayout.addProblem(state, Floor.UPPER, x + 1, x, RoomType.PARLOUR, 0, max))
    }

    @Test
    fun `a menagerie beside an outdoor room is the outdoor one`() {
        val state = starter()
        val x = STARTER_CELL
        assertEquals(RoomType.MENAGERIE_OUTDOOR, HouseLayout.menagerieFor(state, Floor.GROUND, x + 1, x, RoomType.MENAGERIE_INDOOR))
        assertEquals(RoomType.MENAGERIE_INDOOR, HouseLayout.menagerieFor(state, Floor.GROUND, x + 3, x, RoomType.MENAGERIE_INDOOR))
        assertEquals(RoomType.KITCHEN, HouseLayout.menagerieFor(state, Floor.GROUND, x + 1, x, RoomType.KITCHEN))
    }

    @Test
    fun `a new floor can only be started by a staircase`() {
        val state = starter()
        val problem = HouseLayout.addProblem(state, Floor.UPPER, STARTER_CELL, STARTER_CELL, RoomType.PARLOUR, 0, max)
        assertNotNull(problem)
        assertNotNull(HouseLayout.addProblem(state, Floor.UPPER, 0, 0, RoomType.PARLOUR, 0, max))
    }

    @Test
    @ResourceLock("ServerCacheManager")
    fun `the viewer's interface, varbits and scripts exist and every room has a cache row`() {
        ServerCacheManager.init(240).close()
        "interface.poh_viewer".asRSCM(RSCMType.INTERFACE)
        (listOf("map", "move", "rotate", "clockwise", "anticlockwise", "delete", "cancel", "done", "portal") +
            (1..HouseViewer.MAX_ROOMS).map { "%02d".format(it) })
            .forEach { "component.poh_viewer:$it".asRSCM(RSCMType.COMPONENT) }
        listOf("selectedroom", "destination", "rot", "enable_rot", "type", "selecteddoors", "adjacentdoors")
            .forEach { "varbit.poh_viewer_$it".asRSCM(RSCMType.VARBIT) }
        "clientscript.[clientscript,poh_viewer_setroom]".asRSCM(RSCMType.CLIENTSCRIPT)
        "clientscript.[clientscript,script1382]".asRSCM(RSCMType.CLIENTSCRIPT)
        val types = PohRoomRow.all().map { it.roomType }.toSet()
        assertTrue(RoomType.entries.all { it.roomTypeId in types })
    }
}
