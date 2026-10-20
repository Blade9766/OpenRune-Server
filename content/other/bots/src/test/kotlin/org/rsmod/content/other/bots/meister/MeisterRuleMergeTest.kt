package org.rsmod.content.other.bots.meister

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MeisterRuleMergeTest {
    private val base = MeisterRules(buy = listOf(BuyRule(1, maxPrice = 10), BuyRule(2, maxPrice = 20)), sell = listOf(SellRule(3, minPrice = 30)))

    @Test
    fun `edits to different items from two administrators both survive`() {
        val mine = base.copy(buy = listOf(BuyRule(1, maxPrice = 11), BuyRule(2, maxPrice = 20)))
        val live = base.copy(sell = listOf(SellRule(3, minPrice = 31), SellRule(4, minPrice = 40)))
        val merged = MeisterRuleMerge.merge(base, mine, live)!!
        assertEquals(listOf(BuyRule(1, maxPrice = 11), BuyRule(2, maxPrice = 20)), merged.buy)
        assertEquals(listOf(SellRule(3, minPrice = 31), SellRule(4, minPrice = 40)), merged.sell)
    }

    @Test
    fun `an addition and a removal apply on top of the live rules`() {
        val mine = base.copy(buy = listOf(BuyRule(2, maxPrice = 20), BuyRule(5, maxPrice = 50)))
        val live = base.copy(buy = base.buy + BuyRule(6, maxPrice = 60))
        val merged = MeisterRuleMerge.merge(base, mine, live)!!
        assertEquals(listOf(2, 6, 5), merged.buy.map { it.itemId })
    }

    @Test
    fun `two different edits to the same rule are refused`() {
        val mine = base.copy(buy = listOf(BuyRule(1, maxPrice = 11), BuyRule(2, maxPrice = 20)))
        val live = base.copy(buy = listOf(BuyRule(1, maxPrice = 12), BuyRule(2, maxPrice = 20)))
        assertNull(MeisterRuleMerge.merge(base, mine, live))
    }

    @Test
    fun `the same edit made twice is not a conflict`() {
        val mine = base.copy(buy = listOf(BuyRule(1, maxPrice = 11), BuyRule(2, maxPrice = 20)))
        assertEquals(mine, MeisterRuleMerge.merge(base, mine, mine))
    }
}
