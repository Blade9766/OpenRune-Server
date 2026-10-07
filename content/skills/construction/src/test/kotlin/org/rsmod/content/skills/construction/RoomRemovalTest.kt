package org.rsmod.content.skills.construction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.rsmod.content.skills.construction.data.Floor
import org.rsmod.content.skills.construction.data.RoomType
import org.rsmod.content.skills.construction.house.HouseLayout
import org.rsmod.content.skills.construction.house.HouseState
import org.rsmod.content.skills.construction.house.Room

class RoomRemovalTest {
    private val hallX = Construction.STARTER_CELL + 1
    private val hallZ = Construction.STARTER_CELL

    private fun houseWithHall(vararg furniture: Pair<String, Int>): HouseState =
        HouseState().apply {
            createStarterHouse()
            this[Floor.GROUND, hallX, hallZ] = Room(RoomType.SKILL_HALL, 0, furniture.toMap(LinkedHashMap()))
        }

    private fun problem(state: HouseState): String? =
        HouseLayout.removalProblem(state, Floor.GROUND, hallX, hallZ, costumesStored = false, size = HouseLayout.MAX_SIZE)

    @Test
    fun `a room displaying an armour suit can't be removed`() {
        assertEquals(
            "You must remove the furniture holding your items before you can remove this room.",
            problem(houseWithHall("armour" to 2)),
        )
        assertEquals(
            "You must remove the furniture holding your items before you can remove this room.",
            problem(houseWithHall("cw_armour" to 0)),
        )
    }

    @Test
    fun `a room whose furniture holds nothing can be removed`() {
        assertNull(problem(houseWithHall()))
        assertNull(problem(houseWithHall("fishing_trophy" to 0)))
    }
}
