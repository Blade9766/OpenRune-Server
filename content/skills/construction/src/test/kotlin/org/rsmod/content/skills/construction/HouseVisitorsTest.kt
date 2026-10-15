package org.rsmod.content.skills.construction

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.content.skills.construction.house.HouseVisitors
import org.rsmod.content.skills.construction.house.HouseVisitors.PRIVATE_FRIENDS
import org.rsmod.content.skills.construction.house.HouseVisitors.PRIVATE_OFF
import org.rsmod.content.skills.construction.house.HouseVisitors.PRIVATE_ON

class HouseVisitorsTest {
    private val friends = listOf("Zezima", "Iron Mike")
    private val ignores = listOf("Spammer_99")

    @Test
    fun `private chat on lets anyone in who isn't ignored`() {
        assertTrue(HouseVisitors.admits(PRIVATE_ON, friends, ignores, "Stranger"))
        assertFalse(HouseVisitors.admits(PRIVATE_ON, friends, ignores, "spammer 99"))
    }

    @Test
    fun `private chat on friends lets only friends in`() {
        assertTrue(HouseVisitors.admits(PRIVATE_FRIENDS, friends, ignores, "zezima"))
        assertTrue(HouseVisitors.admits(PRIVATE_FRIENDS, friends, ignores, "Iron_Mike"))
        assertTrue(HouseVisitors.admits(PRIVATE_FRIENDS, friends, ignores, "Iron Mike"))
        assertFalse(HouseVisitors.admits(PRIVATE_FRIENDS, friends, ignores, "Stranger"))
    }

    @Test
    fun `private chat off lets nobody in`() {
        assertFalse(HouseVisitors.admits(PRIVATE_OFF, friends, ignores, "Zezima"))
        assertFalse(HouseVisitors.admits(PRIVATE_OFF, friends, ignores, "Stranger"))
    }

    @Test
    fun `an ignored friend is still kept out`() {
        assertFalse(HouseVisitors.admits(PRIVATE_FRIENDS, friends, friends, "Zezima"))
    }
}
