package org.rsmod.content.skills.construction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.content.skills.construction.data.Floor
import org.rsmod.content.skills.construction.data.RoomType
import org.rsmod.content.skills.construction.data.Side
import org.rsmod.content.skills.construction.house.HouseState
import org.rsmod.content.skills.construction.house.Room

class RoomRotationTest {
    @Test
    fun `a room can be turned to any rotation that keeps a door on the doorway`() {
        assertEquals(listOf(0, 1, 2, 3), RoomType.GARDEN.rotationsFacing(Side.NORTH))
        val kitchen = RoomType.KITCHEN.rotationsFacing(Side.NORTH)
        assertEquals(2, kitchen.size)
        assertTrue(kitchen.all { RoomType.KITCHEN.hasDoor(Side.NORTH, it) })
        // The portal chamber has one door, so it can only ever face one way.
        assertEquals(listOf(2), RoomType.PORTAL_CHAMBER.rotationsFacing(Side.NORTH))
    }

    @Test
    fun `a preview copy of the house can be changed without touching the real one`() {
        val state = HouseState()
        state.createStarterHouse()
        val parlour = Room(RoomType.PARLOUR, rotation = 0)
        parlour.furniture["chair_1"] = 1
        state[Floor.GROUND, 5, 6] = parlour
        assertEquals(null, state[Floor.GROUND, 1, 1])

        val preview = HouseState.decode(state.encode())
        preview[Floor.GROUND, 5, 6]?.rotation = 2
        preview[Floor.GROUND, 1, 1] = Room(RoomType.KITCHEN, rotation = 1)

        assertEquals(0, state[Floor.GROUND, 5, 6]?.rotation)
        assertEquals(null, state[Floor.GROUND, 1, 1])
        assertEquals(1, preview[Floor.GROUND, 5, 6]?.furniture?.get("chair_1"))
    }
}
