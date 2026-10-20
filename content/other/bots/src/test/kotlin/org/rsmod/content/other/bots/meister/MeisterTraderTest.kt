package org.rsmod.content.other.bots.meister

import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.content.other.bots.core.FakeExchange

class MeisterTraderTest {
    private val h = MeisterHarness()

    @Test
    fun `initialization grants the allocation once and never again after a restart`() {
        h.start()
        assertEquals(MeisterTrader.MAX_CASH, h.trader.cash)
        assertEquals(1000, h.trader.holding(LOGS))
        assertEquals(10, h.trader.holding(WHIP))
        assertEquals(1, h.store.writes)

        h.restart()
        assertFalse(h.trader.initialize(MeisterTrader.MAX_CASH, h.now), "a saved allocation is never applied again")
        assertEquals(MeisterTrader.MAX_CASH, h.trader.cash)
        assertEquals(1000, h.trader.holding(LOGS))
    }

    @Test
    fun `an allocation that never reached disk is granted once on the next start`() {
        h.store.failing = true
        h.start()
        assertNull(h.store.saved)
        h.store.failing = false
        h.restart(fromNothing = true)
        assertEquals(1000, h.trader.holding(LOGS))
        assertEquals(MeisterTrader.MAX_CASH, h.trader.cash)
    }

    @Test
    fun `he buys from a fair listing, pays the seller's price and collects the items`() {
        h.settings { it.copy(buyChancePercent = 100, sellChancePercent = 0) }
        h.start()
        val seller = h.exchange.playerSells(PLAYER, LOGS, 300, 98)
        h.runDecisions(1)
        h.tick(10)

        assertTrue(seller.completed > 0, "the player's listing was bought from")
        val bought = seller.completed
        assertEquals(1000 + bought, h.trader.holding(LOGS))
        assertEquals(MeisterTrader.MAX_CASH - bought * 98L, h.trader.cash, "he paid the listed price, refunded the rest")
        assertEquals(bought.toLong(), h.trader.stats.itemsBought)
        h.assertConserved()
    }

    @Test
    fun `overpriced listings are never bought`() {
        h.settings { it.copy(buyChancePercent = 100, sellChancePercent = 0, maxBuyPremiumPercent = 5) }
        h.start()
        val greedy = h.exchange.playerSells(PLAYER, LOGS, 1000, 500)
        h.runDecisions(5)
        h.tick(10)

        assertEquals(0, greedy.completed)
        for (offer in h.exchange.raw(MeisterTrader.OWNER_ID)) {
            assertTrue(offer.price <= 105, "bid ${offer.price} is above guide + 5%")
        }
        h.assertConserved()
    }

    @Test
    fun `a standing bid sits under the reference and a player can sell into it`() {
        h.settings { it.copy(buyChancePercent = 100, sellChancePercent = 0, standingBidDiscountPercent = 10) }
        h.start()
        h.runDecisions(1)
        h.tick(3)
        val bid = h.exchange.raw(MeisterTrader.OWNER_ID).single { !it.sell }
        assertTrue(bid.price <= 90, "bid ${bid.price} should be at least 10% under the guide price")

        val player = h.exchange.place(PLAYER, 0, true, bid.item, bid.total, 1)!!
        assertEquals(bid.total, player.completed)
        assertEquals(bid.total.toLong() * bid.price - h.exchange.taxPerItem(bid.item, bid.price).toLong() * bid.total, player.collectCoins.toLong(), "the player sells at his bid")
        h.tick(5)
        h.assertConserved()
    }

    @Test
    fun `he sells only a small share and receives proceeds net of tax`() {
        h.settings { it.copy(buyChancePercent = 0, sellChancePercent = 100, maxSellFractionPercent = 10, maxSellQuantity = 1000) }
        h.startingCash = 1_000_000
        h.start()
        h.runDecisions(1)
        h.tick(3)
        val sale = h.exchange.raw(MeisterTrader.OWNER_ID).single()
        assertTrue(sale.sell)
        assertTrue(sale.total <= h.allocation(sale.item) / 10 + 1, "sold ${sale.total} of ${h.allocation(sale.item)}")
        assertTrue(sale.price >= h.exchange.guidePrice(sale.item)!! * 98 / 100, "never under 98% of the reference")
        assertEquals(h.allocation(sale.item) - sale.total, h.trader.holding(sale.item))

        h.exchange.playerBuys(PLAYER, sale.item, sale.total, sale.price)
        h.tick(5)
        val gross = sale.total.toLong() * sale.price
        val tax = h.exchange.taxPerItem(sale.item, sale.price).toLong() * sale.total
        assertEquals(1_000_000 + gross - tax, h.trader.cash)
        assertEquals(tax, h.trader.stats.taxPaid)
        h.assertConserved()
    }

    @Test
    fun `with his cash at the cap he does not sell, since he could not take the proceeds`() {
        h.settings { it.copy(buyChancePercent = 0, sellChancePercent = 100) }
        h.start()
        h.runDecisions(5)
        assertTrue(h.exchange.raw(MeisterTrader.OWNER_ID).isEmpty())
        assertEquals(MeisterTrader.MAX_CASH, h.trader.cash)
    }

    @Test
    fun `sale proceeds are collected once there is room under the cash cap`() {
        h.settings { it.copy(buyChancePercent = 0, sellChancePercent = 100) }
        h.startingCash = 1_000
        h.start()
        h.runDecisions(1)
        h.tick(3)
        val sale = h.exchange.raw(MeisterTrader.OWNER_ID).single()
        h.exchange.playerBuys(PLAYER, sale.item, sale.total, sale.price)
        h.tick(5)
        val net = sale.total.toLong() * (sale.price - h.exchange.taxPerItem(sale.item, sale.price))
        assertEquals(1_000 + net, h.trader.cash)
        assertNull(h.exchange.offers(MeisterTrader.OWNER_ID).firstOrNull(), "the finished, emptied offer frees its slot")
        h.assertConserved()
    }

    @Test
    fun `a partly filled buy offer times out, and the items and refund are collected`() {
        h.settings { it.copy(buyChancePercent = 100, sellChancePercent = 0, buyOfferTimeoutMinutes = 5, maxActiveOffers = 1) }
        h.exchange.guides[LOGS] = 100
        h.start()
        h.exchange.playerSells(PLAYER, LOGS, 1, 100)
        h.runDecisions(1)
        h.tick(3)
        val offer = h.exchange.raw(MeisterTrader.OWNER_ID).single()
        assertEquals(LOGS, offer.item)
        assertTrue(offer.completed >= 1)

        h.trader.updateSettings { it.copy(buyingPaused = true) }
        h.now += 6 * 60_000L
        h.tick(6)
        assertTrue(h.exchange.raw(MeisterTrader.OWNER_ID).none { it.createdAt == offer.createdAt }, "stale offer cancelled and emptied")
        assertEquals(1001, h.trader.holding(LOGS))
        assertEquals(MeisterTrader.MAX_CASH - 100, h.trader.cash)
        assertEquals(1L, h.trader.stats.offersCancelled)
        h.assertConserved()
    }

    @Test
    fun `cancelling every offer returns everything to his ledger`() {
        h.settings { it.copy(buyChancePercent = 100, sellChancePercent = 0, maxActiveOffers = 3) }
        h.start()
        h.runDecisions(6)
        h.tick(3)
        assertTrue(h.exchange.raw(MeisterTrader.OWNER_ID).isNotEmpty())
        assertTrue(h.exchange.raw(MeisterTrader.OWNER_ID).size <= 3, "never more than the configured offers")

        h.trader.cancelAll("test")
        h.tick(5)
        assertTrue(h.exchange.raw(MeisterTrader.OWNER_ID).isEmpty())
        assertEquals(MeisterTrader.MAX_CASH, h.trader.cash)
        h.assertConserved()
    }

    @Test
    fun `a crash before the exchange saved the offer refunds the reserved coins exactly once`() {
        h.settings { it.copy(buyChancePercent = 100, sellChancePercent = 0) }
        h.start()
        h.autoSaveExchange = false
        h.runDecisions(1)
        h.tick(2)
        val pending = h.store.saved!!.pendingListing
        assertNotNull(pending)
        assertTrue(h.store.saved!!.cash < MeisterTrader.MAX_CASH)

        h.restart()
        h.autoSaveExchange = true
        h.tick(3)
        assertEquals(MeisterTrader.MAX_CASH, h.trader.cash)
        assertTrue(h.exchange.raw(MeisterTrader.OWNER_ID).isEmpty())
        h.restart()
        h.tick(3)
        assertEquals(MeisterTrader.MAX_CASH, h.trader.cash, "no second refund")
    }

    @Test
    fun `a crash after the exchange saved the offer keeps it and refunds nothing`() {
        h.settings { it.copy(buyChancePercent = 100, sellChancePercent = 0) }
        h.start()
        h.autoSaveExchange = false
        h.runDecisions(1)
        val placed = h.store.saved!!.pendingListing
        assertNotNull(placed)
        assertTrue(placed!!.placed)
        h.exchange.save()
        h.autoSaveExchange = true
        h.restart()
        h.tick(3)
        assertEquals(1, h.exchange.raw(MeisterTrader.OWNER_ID).size)
        assertEquals(MeisterTrader.MAX_CASH - placed.cost, h.trader.cash)
        h.assertConserved()
    }

    @Test
    fun `a crash between collecting and the exchange save never credits twice`() {
        h.settings { it.copy(buyChancePercent = 100, sellChancePercent = 0) }
        h.start()
        h.exchange.playerSells(PLAYER, LOGS, 50, 100)
        h.runDecisions(1)
        h.tick(6)
        h.trader.cancelAll("test")
        var guard = 0
        while (h.store.saved!!.pendingCollects.isEmpty() && guard++ < 20) {
            h.tick(1)
        }
        assertTrue(h.store.saved!!.pendingCollects.isNotEmpty())
        h.autoSaveExchange = false
        h.tick(1)
        val credited = h.trader.holding(LOGS)
        h.restart()
        h.autoSaveExchange = true
        h.tick(6)
        assertEquals(credited, h.trader.holding(LOGS))
        assertTrue(h.exchange.raw(MeisterTrader.OWNER_ID).isEmpty())
        h.assertConserved()
    }

    @Test
    fun `a fill the exchange loses in a crash is never collected`() {
        h.settings { it.copy(buyChancePercent = 100, sellChancePercent = 0, maxActiveOffers = 1) }
        h.catalogItems = listOf(MeisterItem(LOGS, "Logs", 100, 1000))
        h.start()
        h.runDecisions(1)
        h.tick(3)
        val bid = h.exchange.raw(MeisterTrader.OWNER_ID).single()
        h.autoSaveExchange = false
        h.exchange.place(PLAYER, 0, true, LOGS, bid.total, 1)
        h.tick(5)
        h.restart()
        h.autoSaveExchange = true
        h.tick(6)
        assertEquals(1000, h.trader.holding(LOGS), "the lost fill paid out nothing")
        assertTrue(h.exchange.raw(PLAYER).isEmpty(), "and the player's sale is gone with it")
        h.assertConserved()
    }

    @Test
    fun `he never has two offers for one item, so he cannot trade with himself`() {
        h.settings { it.copy(buyChancePercent = 50, sellChancePercent = 50, maxActiveOffers = 8, itemCooldownMinutes = 0, flipCooldownHours = 0) }
        h.start()
        repeat(60) {
            h.runDecisions(1)
            val offers = h.exchange.raw(MeisterTrader.OWNER_ID)
            assertEquals(offers.size, offers.map { it.item }.toSet().size, "two offers for one item: $offers")
            assertTrue(offers.none { it.gold > 0 && offers.any { o -> o !== it && o.item == it.item } })
        }
        h.assertConserved()
    }

    @Test
    fun `spending limits per offer, per item and per day are never exceeded`() {
        h.settings {
            it.copy(
                buyChancePercent = 100, sellChancePercent = 0, maxActiveOffers = 8, itemCooldownMinutes = 0,
                maxSpendPerOffer = 5_000, maxSpendPerItemPerDay = 8_000, maxSpendPerDay = 20_000, maxBuyQuantity = 1_000_000,
            )
        }
        h.start()
        for (item in h.catalog.items) {
            h.exchange.playerSells(PLAYER + item.id, item.id, 1_000_000, h.exchange.guidePrice(item.id)!!)
        }
        repeat(40) {
            h.runDecisions(1)
            for (offer in h.exchange.raw(MeisterTrader.OWNER_ID).filter { !it.sell }) {
                assertTrue(offer.total.toLong() * offer.price <= 5_000, "offer worth ${offer.total * offer.price}")
            }
        }
        h.tick(5)
        assertTrue(h.trader.spentTodayCoins <= 20_000, "spent ${h.trader.spentTodayCoins}")
        assertTrue(h.trader.stats.coinsSpent <= 20_000)
        h.assertConserved()
    }

    @Test
    fun `he buys nothing he cannot afford`() {
        h.settings { it.copy(buyChancePercent = 100, sellChancePercent = 0, maxActiveOffers = 8, itemCooldownMinutes = 0) }
        h.startingCash = 150
        h.start()
        repeat(10) { h.runDecisions(1) }
        h.tick(3)
        assertTrue(h.trader.cash >= 0)
        val locked = h.exchange.raw(MeisterTrader.OWNER_ID).filter { !it.sell && !it.finished }.sumOf { (it.total - it.completed).toLong() * it.price }
        assertTrue(locked <= 150)
        h.assertConserved()
    }

    @Test
    fun `an item bought recently is not sold back inside the no-flip window`() {
        h.settings { it.copy(buyChancePercent = 100, sellChancePercent = 0, itemCooldownMinutes = 0, flipCooldownHours = 24) }
        h.catalogItems = listOf(MeisterItem(LOGS, "Logs", 100, 1000))
        h.start()
        h.exchange.playerSells(PLAYER, LOGS, 10, 100)
        h.runDecisions(1)
        h.tick(6)
        h.trader.cancelAll("test")
        h.tick(6)
        h.trader.updateSettings { it.copy(buyChancePercent = 0, sellChancePercent = 100) }
        h.runDecisions(5)
        assertTrue(h.exchange.raw(MeisterTrader.OWNER_ID).none { it.sell }, "sold logs right after buying them")
    }

    @Test
    fun `sensitive items are not sold unless allowed, and no-sell items never`() {
        h.settings { it.copy(buyChancePercent = 0, sellChancePercent = 100, itemCooldownMinutes = 0, maxActiveOffers = 8) }
        h.catalogItems = listOf(MeisterItem(WHIP, "Abyssal whip", 100, 10, sensitive = true), MeisterItem(LOGS, "Logs", 100, 1000, noSell = true))
        h.startingCash = 1_000_000
        h.start()
        h.runDecisions(10)
        assertTrue(h.exchange.raw(MeisterTrader.OWNER_ID).isEmpty())
        h.trader.updateSettings { it.copy(sellSensitive = true) }
        h.runDecisions(3)
        assertEquals(setOf(WHIP), h.exchange.raw(MeisterTrader.OWNER_ID).map { it.item }.toSet())
    }

    @Test
    fun `no price data means no trade`() {
        h.settings { it.copy(buyChancePercent = 100, sellChancePercent = 0) }
        h.catalogItems = listOf(MeisterItem(LOGS, "Logs", 0, 1000))
        h.exchange.guide = null
        h.start()
        h.exchange.playerSells(PLAYER, LOGS, 10, 1)
        h.runDecisions(5)
        assertTrue(h.exchange.raw(MeisterTrader.OWNER_ID).isEmpty())
        assertEquals(MeisterTrader.MAX_CASH, h.trader.cash)
    }

    @Test
    fun `paused buying and selling place nothing, and trading off stops decisions`() {
        h.settings { it.copy(buyChancePercent = 50, sellChancePercent = 50, buyingPaused = true, sellingPaused = true) }
        h.start()
        h.runDecisions(10)
        assertTrue(h.exchange.raw(MeisterTrader.OWNER_ID).isEmpty())
        h.trader.updateSettings { it.copy(tradingEnabled = false, buyingPaused = false, sellingPaused = false) }
        h.tick(1)
        assertEquals(MeisterStatus.Paused, h.trader.status)
        h.runDecisions(10)
        assertTrue(h.exchange.raw(MeisterTrader.OWNER_ID).isEmpty())
    }

    @Test
    fun `invalid settings are refused`() {
        h.start()
        assertNotNull(h.trader.updateSettings { it.copy(maxBuyOffers = 9) })
        assertNotNull(h.trader.updateSettings { it.copy(maxActiveOffers = 9) })
        assertNull(h.trader.updateSettings { it.copy(maxActiveOffers = 2) })
        assertEquals(2, h.trader.settings.maxActiveOffers)
    }

    @Test
    fun `replenishing only happens when switched on`() {
        h.settings { it.copy(buyChancePercent = 0, sellChancePercent = 0) }
        h.startingCash = 1_000
        h.start()
        h.trader.cancelAll("none")
        h.now += MeisterTrader.DAY_MS * 2
        h.tick(2)
        assertEquals(1_000, h.trader.cash)
    }

    @Test
    fun `refilling cash counts the coins tied up in his offers`() {
        h.settings { it.copy(buyChancePercent = 100, sellChancePercent = 0, maxActiveOffers = 1, replenishCash = true) }
        h.startingCash = 100_000
        h.start()
        h.runDecisions(1)
        h.tick(3)
        val committed = h.trader.committedCoins()
        assertTrue(committed > 0, "a bid holds some of his coins")

        h.trader.updateSettings { it.copy(buyingPaused = true) }
        h.now += MeisterTrader.DAY_MS * 2
        h.tick(2)
        assertEquals(100_000L, h.trader.cash + h.trader.committedCoins())
    }

    @Test
    fun `a sale the exchange loses in a restart takes back its realized profit`() {
        h.settings { it.copy(buyChancePercent = 100, sellChancePercent = 0, maxActiveOffers = 1) }
        h.catalogItems = listOf(MeisterItem(LOGS, "Logs", 100, 1000))
        h.startingCash = 1_000_000
        h.start()
        h.runDecisions(1)
        h.tick(3)
        val bid = h.exchange.raw(MeisterTrader.OWNER_ID).single()
        h.exchange.place(PLAYER, 0, true, LOGS, bid.total, 1)
        h.tick(6)
        assertTrue(h.trader.holding(LOGS) > 0, "bought logs at a known cost")

        h.trader.updateSettings { it.copy(buyChancePercent = 0, sellChancePercent = 100, maxSellQuantity = 1000, flipCooldownHours = 0) }
        h.now += 2 * 60 * 60_000L
        h.runDecisions(3)
        h.tick(3)
        val sale = h.exchange.raw(MeisterTrader.OWNER_ID).single { it.sell }
        val before = h.trader.realizedProfit()
        h.autoSaveExchange = false
        h.exchange.playerBuys(PLAYER, LOGS, sale.total, sale.price)
        h.tick(5)
        assertTrue(h.trader.realizedProfit() != before, "the sale was counted")

        h.restart()
        h.autoSaveExchange = true
        h.tick(6)
        assertEquals(before, h.trader.realizedProfit())
    }

    @Test
    fun `allocation tiers pick the first tier at or above the price`() {
        val tiers = MeisterAllocation.DEFAULT_TIERS
        assertEquals(2_000, MeisterAllocation.countFor(5, tiers))
        assertEquals(1_000, MeisterAllocation.countFor(101, tiers))
        assertEquals(1, MeisterAllocation.countFor(2_000_000_000, tiers))
    }

    private companion object {
        const val LOGS = 1511
        const val WHIP = 4151
        const val PLAYER = 7
    }
}

/** Drives a [MeisterTrader] against a [FakeExchange], saving the ledger like [org.rsmod.content.other.bots.meister.world.MasterMeister]. */
class MeisterHarness {
    var now = 10_000_000_000L
    val exchange = FakeExchange { now }
    val store = InMemoryMeisterStore()
    val history = InMemoryHistorySink()
    var catalogItems: List<MeisterItem> =
        listOf(MeisterItem(1511, "Logs", 100, 1000), MeisterItem(4151, "Abyssal whip", 100, 10), MeisterItem(440, "Iron ore", 100, 500))
    var startingCash: Long = MeisterTrader.MAX_CASH
    var autoSaveExchange = true
    var settings = MeisterSettings(decisionIntervalSeconds = 10, sellIntervalSeconds = 10, decisionJitterPercent = 0)
    private val random = Random(42)
    lateinit var trader: MeisterTrader
    val catalog: MeisterCatalog
        get() = MeisterCatalog(catalogItems)

    private var startCash = 0L
    private val startItems = HashMap<Int, Long>()

    fun settings(change: (MeisterSettings) -> MeisterSettings) {
        settings = change(settings)
    }

    fun allocation(item: Int): Int = catalogItems.first { it.id == item }.allocation

    fun start() {
        trader = build(MeisterSave(settings = settings), recovering = false)
        trader.initialize(startingCash, now)
        trader.start(now)
        persist()
        startCash = trader.cash
        catalogItems.forEach { startItems[it.id] = trader.holding(it.id).toLong() }
    }

    /** A restart: the exchange keeps only what it saved, the trader only what the store holds. */
    fun restart(fromNothing: Boolean = false) {
        exchange.restoreFromSave()
        val save = store.saved
        trader = build(save ?: MeisterSave(settings = settings), recovering = save?.let { it.pendingListing != null || it.pendingCollects.isNotEmpty() } ?: false)
        if (fromNothing) {
            trader.initialize(startingCash, now)
            persist()
            startCash = trader.cash
            catalogItems.forEach { startItems[it.id] = trader.holding(it.id).toLong() }
        }
        trader.start(now)
    }

    private fun build(save: MeisterSave, recovering: Boolean): MeisterTrader =
        MeisterTrader(save, catalog, exchange, SilentLog, { !store.failing }, recovering, random, history = history)

    fun tick(count: Int = 1) {
        repeat(count) {
            trader.tick(now)
            persist()
            if (autoSaveExchange) exchange.save()
            now += 600
        }
    }

    fun runDecisions(count: Int) {
        repeat(count) {
            now += settings.decisionIntervalSeconds * 1000L + 1
            tick(2)
        }
    }

    private fun persist() {
        if (trader.urgent || trader.dirty) {
            try {
                store.write(trader.snapshot())
                trader.urgent = false
                trader.dirty = false
            } catch (_: java.io.IOException) {}
        }
    }

    /** Coins and items are only ever moved, never made or lost: what he holds plus what sits on the exchange. */
    fun assertConserved() {
        val owner = MeisterTrader.OWNER_ID
        val offers = exchange.offers(owner)
        val pending = trader.snapshot().pendingListing?.takeIf { p -> offers.none { it.slot == p.slot && it.itemId == p.itemId && it.total == p.quantity } }
        val coins =
            trader.cash + offers.sumOf { it.collectCoins.toLong() + if (!it.sell && it.isOpen) it.remaining.toLong() * it.price else 0L } +
                (pending?.takeIf { it.buy }?.cost ?: 0L)
        assertEquals(startCash - trader.stats.coinsSpent + trader.stats.coinsEarned, coins, "coins")
        var items = 0L
        var expected = 0L
        for (item in catalogItems) {
            items += trader.holding(item.id) +
                offers.filter { it.itemId == item.id }.sumOf { it.collectItems.toLong() + if (it.sell && it.isOpen) it.remaining.toLong() else 0L } +
                (pending?.takeIf { !it.buy && it.itemId == item.id }?.quantity?.toLong() ?: 0L)
            expected += startItems[item.id] ?: 0L
        }
        assertEquals(expected + trader.stats.itemsBought - trader.stats.itemsSold, items, "items")
    }

    private object SilentLog : MeisterLog {
        override fun info(message: String) {}

        override fun warn(message: String) {}
    }
}
