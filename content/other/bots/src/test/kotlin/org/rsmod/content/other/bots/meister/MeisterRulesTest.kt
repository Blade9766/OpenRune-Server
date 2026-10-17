package org.rsmod.content.other.bots.meister

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MeisterRulesTest {
    private val h = MeisterHarness()

    private fun rulesMode(cash: Long = 1_000_000) {
        h.settings {
            it.copy(useRules = true, buyChancePercent = 100, sellChancePercent = 0, itemCooldownMinutes = 0, flipCooldownHours = 0, maxBuyOffers = 4, maxSellOffers = 4, maxActiveOffers = 8)
        }
        h.startingCash = cash
        h.start()
    }

    @Test
    fun `in rule mode he buys only enabled listed items, and nothing with an empty list`() {
        rulesMode()
        h.exchange.playerSells(PLAYER, ORE, 100, 50)
        h.runDecisions(5)
        assertTrue(h.exchange.raw(MeisterTrader.OWNER_ID).isEmpty(), "an empty buy list buys nothing")

        h.trader.updateRules(MeisterRules(buy = listOf(BuyRule(LOGS, maxPrice = 90, quantity = 10, enabled = false), BuyRule(ORE, maxPrice = 60, quantity = 20))))
        h.runDecisions(3)
        h.tick(5)
        assertEquals(setOf(ORE), (h.history.all().map { it.itemId } + h.exchange.raw(MeisterTrader.OWNER_ID).map { it.item }).toSet(), "only the enabled rule's item")
        h.assertConserved()
    }

    @Test
    fun `a fixed ceiling bids the maximum and pays the seller's lower price`() {
        rulesMode()
        h.trader.updateRules(MeisterRules(buy = listOf(BuyRule(ORE, maxPrice = 60, quantity = 20))))
        val seller = h.exchange.playerSells(PLAYER, ORE, 20, 45)
        h.runDecisions(1)
        h.tick(5)
        assertEquals(20, seller.completed)
        assertEquals(500 + 20, h.trader.holding(ORE))
        assertEquals(1_000_000L - 20 * 45, h.trader.cash, "the difference between ceiling and price is refunded")
        val fill = h.history.all().single()
        assertEquals(-900L, fill.net)
        assertEquals(45L, fill.unitPrice)
        h.assertConserved()
    }

    @Test
    fun `he stops buying at the holdings limit, counting open bids`() {
        rulesMode()
        h.trader.updateRules(MeisterRules(buy = listOf(BuyRule(ORE, maxPrice = 10, quantity = 100, maxHoldings = 530))))
        h.runDecisions(3)
        val bid = h.exchange.raw(MeisterTrader.OWNER_ID).single()
        assertEquals(30, bid.total, "500 held + 30 = the limit")
        h.trader.cancelAll("test")
        h.tick(5)
        h.trader.updateRules(MeisterRules(buy = listOf(BuyRule(ORE, maxPrice = 10, quantity = 100, maxHoldings = 400))))
        h.runDecisions(3)
        assertTrue(h.exchange.raw(MeisterTrader.OWNER_ID).isEmpty())
    }

    @Test
    fun `a sell rule keeps its reserve and never asks under its floor`() {
        h.settings { it.copy(useRules = true, buyChancePercent = 0, sellChancePercent = 100, itemCooldownMinutes = 0, flipCooldownHours = 0) }
        h.startingCash = 1_000
        h.start()
        h.trader.updateRules(MeisterRules(sell = listOf(SellRule(ORE, minPrice = 120, quantity = 1000, keepAtLeast = 450))))
        h.runDecisions(2)
        val sale = h.exchange.raw(MeisterTrader.OWNER_ID).single()
        assertEquals(50, sale.total, "500 held, keep 450")
        assertEquals(120, sale.price)
        assertEquals(450, h.trader.holding(ORE))
        h.assertConserved()
    }

    @Test
    fun `sales of purchased units realize profit against the weighted average cost`() {
        h.catalogItems = listOf(MeisterItem(ORE, "Iron ore", 100, 0))
        rulesMode()
        h.trader.updateRules(MeisterRules(buy = listOf(BuyRule(ORE, maxPrice = 100, quantity = 10, cooldownMinutes = 0))))
        h.exchange.playerSells(PLAYER, ORE, 10, 80)
        h.runDecisions(1)
        h.tick(5)
        h.exchange.playerSells(PLAYER + 1, ORE, 10, 100)
        h.runDecisions(1)
        h.tick(5)
        assertEquals(CostBasis(20, 1800, 0), h.trader.basisOf(ORE), "average 90")

        h.trader.updateSettings { it.copy(buyChancePercent = 0, sellChancePercent = 100) }
        h.trader.updateRules(MeisterRules(sell = listOf(SellRule(ORE, minPrice = 150, quantity = 10))))
        h.runDecisions(1)
        h.tick(2)
        h.exchange.playerBuys(PLAYER + 2, ORE, 10, 150)
        h.tick(5)
        val sale = h.history.all().last()
        val fee = h.exchange.taxPerItem(ORE, 150).toLong() * 10
        assertEquals(1500 - fee, sale.net)
        assertEquals(900L, sale.costBasis)
        assertEquals(1500 - fee - 900, sale.realized)
        assertEquals(CostBasis(10, 900, 0), h.trader.basisOf(ORE))
        h.assertConserved()
    }

    @Test
    fun `sales of starting stock have an unknown cost basis and no realized profit`() {
        h.settings { it.copy(useRules = true, buyChancePercent = 0, sellChancePercent = 100, itemCooldownMinutes = 0, flipCooldownHours = 0) }
        h.startingCash = 1_000
        h.start()
        assertEquals(CostBasis(0, 0, 500), h.trader.basisOf(ORE))
        h.trader.updateRules(MeisterRules(sell = listOf(SellRule(ORE, minPrice = 100, quantity = 5))))
        h.runDecisions(1)
        h.tick(2)
        h.exchange.playerBuys(PLAYER, ORE, 5, 100)
        h.tick(5)
        val sale = h.history.all().single()
        assertEquals(5, sale.unknownQuantity)
        assertNull(sale.realized)
        assertNull(sale.costBasis)
        assertEquals(0L, h.trader.realizedProfit())
    }

    @Test
    fun `fills are recorded once, also when a restart counts them again`() {
        rulesMode()
        h.trader.updateRules(MeisterRules(buy = listOf(BuyRule(ORE, maxPrice = 60, quantity = 20))))
        h.runDecisions(1)
        h.tick(2)
        assertEquals(1, h.exchange.raw(MeisterTrader.OWNER_ID).size)
        val beforeFill = h.store.saved!!
        h.exchange.playerSells(PLAYER, ORE, 20, 50)
        h.tick(1)
        assertEquals(1, h.history.all().size)
        h.store.saved = beforeFill
        h.restart()
        h.tick(4)
        assertEquals(1, h.history.all().size, "the recount after a crash is a duplicate")
        assertEquals(CostBasis(20, 1200, 500), h.trader.basisOf(ORE), "the basis moved once")
        h.assertConserved()
    }

    @Test
    fun `partial fills at different prices are separate records with exact totals`() {
        rulesMode()
        h.trader.updateRules(MeisterRules(buy = listOf(BuyRule(ORE, maxPrice = 60, quantity = 30))))
        h.exchange.playerSells(PLAYER, ORE, 10, 40)
        h.runDecisions(1)
        h.tick(3)
        h.exchange.playerSells(PLAYER + 1, ORE, 5, 55)
        h.tick(3)
        val fills = h.history.all()
        assertEquals(listOf(10, 5), fills.map { it.quantity })
        assertEquals(listOf(400L, 300L), fills.map { it.gross }, "the second seller met his resting bid at its price")
        assertEquals(listOf(40L, 60L), fills.map { it.unitPrice })
        assertEquals(CostBasis(15, 700, 500), h.trader.basisOf(ORE))
        h.assertConserved()
    }

    @Test
    fun `cancel and replace keeps the partial fill and places a fresh offer on the new rule`() {
        rulesMode()
        h.trader.updateRules(MeisterRules(buy = listOf(BuyRule(ORE, maxPrice = 60, quantity = 30))))
        h.exchange.playerSells(PLAYER, ORE, 10, 50)
        h.runDecisions(1)
        h.tick(3)
        val old = h.exchange.raw(MeisterTrader.OWNER_ID).single()
        assertEquals(10, old.completed)
        h.trader.updateRules(MeisterRules(buy = listOf(BuyRule(ORE, maxPrice = 70, quantity = 5))))
        h.trader.updateSettings { it.copy(buyChancePercent = 0) }
        assertNull(h.trader.cancelOffer(old.slot, replace = true, reason = "test"))
        h.tick(8)
        val fresh = h.exchange.raw(MeisterTrader.OWNER_ID).single()
        assertTrue(fresh.createdAt != old.createdAt)
        assertEquals(70, fresh.price)
        assertEquals(5, fresh.total)
        assertEquals(510, h.trader.holding(ORE), "the partial fill was kept")
        h.assertConserved()
    }

    @Test
    fun `buy and sell offer caps are separate`() {
        rulesMode()
        h.trader.updateSettings { it.copy(maxBuyOffers = 1) }
        h.trader.updateRules(MeisterRules(buy = listOf(BuyRule(ORE, maxPrice = 10, quantity = 5), BuyRule(LOGS, maxPrice = 10, quantity = 5))))
        h.runDecisions(6)
        assertEquals(1, h.exchange.raw(MeisterTrader.OWNER_ID).count { !it.sell })
    }

    @Test
    fun `rules are validated and loss loops are reported`() {
        val known = { id: Int -> id != 999 }
        val dupes = MeisterRules(buy = listOf(BuyRule(ORE, 10, 1), BuyRule(ORE, 12, 1)))
        assertTrue(dupes.validate(known).any { "two buy rules" in it })
        assertTrue(MeisterRules(buy = listOf(BuyRule(999, 10, 1))).validate(known).isNotEmpty())
        assertTrue(MeisterRules(buy = listOf(BuyRule(ORE, Int.MAX_VALUE, 2))).validate(known).isNotEmpty())
        val loop = MeisterRules(buy = listOf(BuyRule(ORE, 100, 1)), sell = listOf(SellRule(ORE, 90, 1)))
        assertEquals(listOf(ORE), loop.lossLoops())
        assertTrue(MeisterRules(buy = listOf(BuyRule(ORE, 100, 1)), sell = listOf(SellRule(ORE, 120, 1))).lossLoops().isEmpty())
        rulesMode()
        assertNotNull(h.trader.updateRules(dupes))
    }

    private companion object {
        const val LOGS = 1511
        const val ORE = 440
        const val PLAYER = 7
    }
}

class MeisterHistoryTest {
    @Test
    fun `cost basis sells known and unknown units in proportion`() {
        val sale = CostBasisBook.sell(CostBasis(knownQuantity = 10, knownCost = 1000, unknownQuantity = 30), 8)
        assertEquals(6, sale.unknownQuantity)
        assertEquals(2, sale.knownQuantity)
        assertEquals(200L, sale.cost)
        assertEquals(CostBasis(8, 800, 24), sale.basis)
        val none = CostBasisBook.sell(CostBasis(0, 0, 5), 5)
        assertNull(none.cost)
        assertEquals(5, none.unknownQuantity)
        val short = CostBasisBook.sell(CostBasis(2, 200, 0), 5)
        assertEquals(2, short.knownQuantity)
        assertEquals(3, short.unknownQuantity)
    }

    @Test
    fun `summaries cover every matching record and filters combine`() {
        val day = 24 * 60 * 60_000L
        val now = 100 * day
        val fills =
            listOf(
                MeisterFill("a", now - 1000, 1, buy = true, quantity = 10, gross = 1000, net = -1000, knownQuantity = 10),
                MeisterFill("b", now - 500, 1, buy = false, quantity = 5, gross = 800, fee = 16, net = 784, knownQuantity = 5, costBasis = 500, realized = 284),
                MeisterFill("c", now - 3 * day, 2, buy = false, quantity = 4, gross = 400, fee = 8, net = 392, unknownQuantity = 4),
            )
        val names = mapOf(1 to "Shark", 2 to "Dragon bones")
        val all = MeisterHistoryView.summarize(fills)
        assertEquals(1000, all.spent)
        assertEquals(1200, all.grossSales)
        assertEquals(24, all.fees)
        assertEquals(1176, all.netProceeds)
        assertEquals(176, all.netCashFlow)
        assertEquals(284, all.realized)
        assertEquals(4, all.unknownBasisQuantity)
        val today = MeisterHistoryView.filter(fills, HistoryQuery(range = HistoryRange.Today), now) { names.getValue(it) }
        assertEquals(listOf("b", "a"), today.map { it.id })
        val sells = MeisterHistoryView.filter(fills, HistoryQuery(search = "drag", side = HistorySide.Sells), now) { names.getValue(it) }
        assertEquals(listOf("c"), sells.map { it.id })
        val byProfit = MeisterHistoryView.filter(fills, HistoryQuery(sort = HistorySort.Profit, descending = true), now) { names.getValue(it) }
        assertEquals("b", byProfit.first().id)
    }

    @Test
    fun `csv export quotes names and keeps unknown basis empty`() {
        val csv =
            MeisterHistoryView.csv(
                listOf(MeisterFill("x", 0, 1, buy = false, quantity = 1, gross = 10, net = 10, unknownQuantity = 1)),
            ) { "Shark \"raw\"" }
        val lines = csv.trim().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[1].contains("\"Shark \"\"raw\"\"\""))
        assertTrue(lines[1].contains(",SELL,"))
        assertTrue(lines[1].endsWith(",,,false"))
    }
}
