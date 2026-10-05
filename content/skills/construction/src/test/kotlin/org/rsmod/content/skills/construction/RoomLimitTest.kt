package org.rsmod.content.skills.construction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.content.skills.construction.data.Floor
import org.rsmod.content.skills.construction.data.RoomType
import org.rsmod.content.skills.construction.house.HouseLayout
import org.rsmod.content.skills.construction.house.HouseState
import org.rsmod.content.skills.construction.house.HouseViewer
import org.rsmod.content.skills.construction.house.Room

class RoomLimitTest {
    @Test
    fun `the room limit follows the wiki's table`() {
        val table =
            mapOf(
                1 to 24, 25 to 24, 26 to 25, 31 to 25, 32 to 26, 38 to 27, 44 to 28, 50 to 29, 56 to 30,
                62 to 31, 68 to 32, 74 to 33, 80 to 34, 86 to 35, 92 to 36, 95 to 36, 96 to 37, 98 to 37,
                99 to 38,
            )
        for ((level, rooms) in table) {
            assertEquals(rooms, HouseLayout.maxRooms(level), "level $level")
        }
        assertEquals(HouseViewer.MAX_ROOMS, HouseLayout.maxRooms(99))
    }

    @Test
    fun `the house grows from 3x3 to 7x7 to the north and east`() {
        val table = mapOf(1 to 3, 14 to 3, 15 to 4, 29 to 4, 30 to 5, 45 to 6, 59 to 6, 60 to 7, 99 to 7)
        for ((level, size) in table) {
            assertEquals(size, HouseLayout.maxSize(level), "level $level")
        }
        val origin = HouseLayout.ORIGIN
        // The starter garden is the middle of the first 3x3.
        assertEquals(Construction.STARTER_CELL, origin + 1)
        assertNull(HouseLayout.areaProblem(origin + 2, origin + 2, 3))
        assertNotNull(HouseLayout.areaProblem(origin + 3, origin, 3))
        assertNull(HouseLayout.areaProblem(origin + 3, origin, 4))
        // It only ever grows north and east, so nothing is gained to the south or west.
        assertNotNull(HouseLayout.areaProblem(origin - 1, origin, 7))
        assertNotNull(HouseLayout.areaProblem(origin, origin + 7, 7))
        assertEquals(HouseLayout.MAX_SIZE, HouseLayout.sizeFor(1, free = true))
        assertTrue(origin + HouseLayout.MAX_SIZE <= Construction.GRID)

        val state = HouseState().apply { createStarterHouse() }
        val east = Construction.STARTER_CELL + 2
        assertNotNull(HouseLayout.addProblem(state, Floor.GROUND, east, Construction.STARTER_CELL, RoomType.GARDEN, 0, 3))
    }

    @Test
    fun `the yard is the building area with a ring of lawn round it`() {
        val origin = HouseLayout.ORIGIN
        for (size in 3..HouseLayout.MAX_SIZE) {
            val yard = (0 until Construction.GRID).filter { HouseLayout.inYard(it, origin, size) }
            assertEquals((origin - 1..origin + size).toList(), yard, "size $size")
        }
        assertTrue(HouseLayout.inYard(origin - 1, origin - 1, 3))
        assertTrue(!HouseLayout.inYard(origin - 2, origin, 7))
        // The widest yard still fits the grid, so its edge is never a region border.
        assertTrue(origin - 1 >= 0 && origin + HouseLayout.MAX_SIZE < Construction.GRID)
    }

    @Test
    fun `a full house can't take another room`() {
        val state = HouseState()
        for (i in 0 until 24) {
            state[Floor.GROUND, i % 12, i / 12] = Room(RoomType.GARDEN, 0)
        }
        assertNotNull(HouseLayout.roomLimitProblem(state, 1))
        assertNull(HouseLayout.roomLimitProblem(state, 26))
        // Free build ignores the level but still stops at the 38 the house viewer can show.
        assertNull(HouseLayout.roomLimitProblem(state, 1, free = true))
        for (i in 24 until HouseViewer.MAX_ROOMS) {
            state[Floor.GROUND, i % 12, 2 + i / 12] = Room(RoomType.GARDEN, 0)
        }
        assertNotNull(HouseLayout.roomLimitProblem(state, 1, free = true))
    }
}
