package org.rsmod.content.other.bots.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SupplyLoadsTest {
    private val steel = listOf(SupplyPart(IRON, 1), SupplyPart(COAL, 2))

    private fun bank(vararg counts: Pair<Int, Long>): (Int) -> Long = { id -> counts.toMap()[id] ?: 0L }

    @Test
    fun `a full inventory of whole sets fits the free slots`() {
        val load = SupplyLoads.plan(steel, free = 28, bank(IRON to 500L, COAL to 500L))
        assertEquals(mapOf(IRON to 9, COAL to 18), load)
    }

    @Test
    fun `the scarcer part limits the load and no part is stranded`() {
        val load = SupplyLoads.plan(steel, free = 28, bank(IRON to 100L, COAL to 7L))
        assertEquals(mapOf(IRON to 3, COAL to 6), load)
    }

    @Test
    fun `too few of any part or too little room means no load`() {
        assertTrue(SupplyLoads.plan(steel, free = 28, bank(IRON to 100L, COAL to 1L)).isEmpty())
        assertTrue(SupplyLoads.plan(steel, free = 2, bank(IRON to 100L, COAL to 100L)).isEmpty())
        assertTrue(SupplyLoads.plan(steel, free = 0, bank(IRON to 100L, COAL to 100L)).isEmpty())
        assertTrue(SupplyLoads.plan(emptyList(), free = 28, bank(IRON to 100L)).isEmpty())
    }

    @Test
    fun `bank stacks beyond Int range never overflow the load`() {
        val huge = Int.MAX_VALUE.toLong() * 4
        val load = SupplyLoads.plan(steel, free = 28, bank(IRON to huge, COAL to huge))
        assertEquals(mapOf(IRON to 9, COAL to 18), load)
    }

    @Test
    fun `a negative bank count is treated as none`() {
        assertTrue(SupplyLoads.plan(steel, free = 28, bank(IRON to -5L, COAL to 100L)).isEmpty())
    }

    @Test
    fun `shortfall lists only parts short of one set, scarcest first`() {
        assertEquals(listOf(COAL), SupplyLoads.shortfall(steel, bank(IRON to 50L, COAL to 1L)))
        assertEquals(listOf(IRON, COAL), SupplyLoads.shortfall(steel, bank(COAL to 1L)))
        assertTrue(SupplyLoads.shortfall(steel, bank(IRON to 1L, COAL to 2L)).isEmpty())
    }

    private companion object {
        private const val IRON = 440
        private const val COAL = 453
    }
}
