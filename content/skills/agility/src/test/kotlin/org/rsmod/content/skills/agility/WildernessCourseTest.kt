package org.rsmod.content.skills.agility

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.content.skills.agility.wilderness.DispenserLoot
import org.rsmod.content.skills.agility.wilderness.WildernessCourseScript
import org.rsmod.content.skills.agility.wilderness.WildernessLaps
import org.rsmod.map.CoordGrid

class WildernessCourseTest {
    @Test
    fun `loot brackets follow the lap streak`() {
        assertEquals(1..15, DispenserLoot.bracket(0).laps)
        assertEquals(1..15, DispenserLoot.bracket(15).laps)
        assertEquals(16..30, DispenserLoot.bracket(16).laps)
        assertEquals(31..60, DispenserLoot.bracket(60).laps)
        assertEquals(61, DispenserLoot.bracket(500).laps.first)
    }

    @Test
    fun `every bracket rolls on noted resources and armour`() {
        for (bracket in DispenserLoot.brackets) {
            assertEquals(31, bracket.resources.sumOf { it.weight })
            assertTrue(bracket.armour.isNotEmpty())
            assertTrue((bracket.resources + bracket.armour).all { it.obj.startsWith("obj.cert_") })
        }
        assertEquals(16, DispenserLoot.extraSupply.sumOf { it.weight })
    }

    @Test
    fun `bigger ticket batches earn more per ticket`() {
        assertEquals(200, WildernessCourseScript.ticketXp(10))
        assertEquals(210, WildernessCourseScript.ticketXp(11))
        assertEquals(220, WildernessCourseScript.ticketXp(100))
        assertEquals(230, WildernessCourseScript.ticketXp(101))
    }

    @Test
    fun `the course covers the yard and the dungeon but not the entrance corridor`() {
        assertTrue(WildernessLaps.inCourse(CoordGrid(3005, 3936, 0)))
        assertTrue(WildernessLaps.inCourse(CoordGrid(3005, 10357, 0)))
        assertFalse(WildernessLaps.inCourse(CoordGrid(2998, 3925, 0)))
        assertFalse(WildernessLaps.inCourse(CoordGrid(3005, 3936, 1)))
    }
}
