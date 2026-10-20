package org.rsmod.content.skills.mining.dense

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DenseRunestoneDepletionTest {
    @Test
    fun `survival runs from 85_5 percent at 38 to 90_2 percent at 99`() {
        assertEquals(855, DenseRunestoneDepletion.surviveChance(38))
        assertEquals(902, DenseRunestoneDepletion.surviveChance(99))
    }

    @Test
    fun `levels outside 38 to 99 are clamped`() {
        assertEquals(855, DenseRunestoneDepletion.surviveChance(1))
        assertEquals(902, DenseRunestoneDepletion.surviveChance(120))
    }

    @Test
    fun `survival never drops as mining rises`() {
        val chances = (38..99).map(DenseRunestoneDepletion::surviveChance)
        assertTrue(chances.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(878, DenseRunestoneDepletion.surviveChance(68))
    }

    @Test
    fun `a roll at the survival chance depletes and one below does not`() {
        assertFalse(DenseRunestoneDepletion.depletes(38, 854))
        assertTrue(DenseRunestoneDepletion.depletes(38, 855))
        assertTrue(DenseRunestoneDepletion.depletes(99, DenseRunestoneDepletion.ROLL_RANGE - 1))
        assertFalse(DenseRunestoneDepletion.depletes(99, 0))
    }
}
