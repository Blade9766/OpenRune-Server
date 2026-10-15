package org.rsmod.content.other.bots.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.exchange.ExchangeOwner
import org.rsmod.api.exchange.ExchangeTax
import org.rsmod.api.exchange.OfferSide
import org.rsmod.api.exchange.OfferStatus

class BotSellingTest {
    private fun BotTestHarness.startBot(config: BotConfig, bankedLogs: Int): Long {
        val id = createBot(config).id
        coordinator.editSave(id) {
            it.copy(
                inv = listOf(SavedObj(0, AXE, 1)),
                bank = if (bankedLogs > 0) listOf(SavedObj(0, LOGS, bankedLogs)) else emptyList(),
            )
        }
        coordinator.enable(id)
        coordinator.setMaster(true)
        return id
    }

    private fun BotTestHarness.offers(id: Long) = book.offers(ExchangeOwner.bot(id))

    @Test
    fun `no sale below the threshold`() {
        val world = BotTestHarness()
        val id = world.startBot(BotTestHarness.woodcuttingConfig(threshold = 1000), bankedLogs = 950)
        world.tickUntil { world.live(id) { it.bankCount(LOGS) >= 977 } }
        assertTrue(world.offers(id).isEmpty())
        assertFalse(world.log.lines.any { "listed" in it })
    }

    @Test
    fun `reaching the threshold lists the configured quantity`() {
        val world = BotTestHarness()
        val config = BotTestHarness.woodcuttingConfig(threshold = 1000, quantity = 600)
        val id = world.startBot(config, bankedLogs = 990)

        world.tickUntil { world.offers(id).isNotEmpty() }
        val offer = world.offers(id).single()
        assertEquals(OfferSide.Sell, offer.side)
        assertEquals(600, offer.quantity)
        assertEquals(LOGS, offer.itemId)
        assertEquals(100, offer.price, "reference price, no adjustment")
        assertEquals(990 + 27 - 600L, world.actor(id).bankCount(LOGS))
        assertEquals(0, world.actor(id).held(LOGS))
        assertEquals(600, world.store.exchange.offers.values.single().quantity, "offer persisted")
    }

    @Test
    fun `items that are not explicitly allowed are never sold`() {
        val world = BotTestHarness()
        val config =
            BotTestHarness.woodcuttingConfig().copy(
                sale = SaleConfig(item = "obj.logs", threshold = 1000, quantity = 1000, allowedItems = setOf("obj.iron_ore"))
            )
        assertTrue(config.validate().isNotEmpty())
        val id = world.createBot(BotTestHarness.woodcuttingConfig()).id
        world.coordinator.editSave(id) {
            it.copy(inv = listOf(SavedObj(0, AXE, 1)), bank = listOf(SavedObj(0, LOGS, 1500)))
        }
        world.coordinator.updateConfig(id) { it.copy(sale = it.sale.copy(allowedItems = emptySet(), item = "")) }
        world.coordinator.enable(id)
        world.coordinator.setMaster(true)
        world.tick(500)
        assertTrue(world.offers(id).isEmpty())
    }

    @Test
    fun `a sale to a player settles once and proceeds are collected to the bank`() {
        val world = BotTestHarness()
        val id = world.startBot(BotTestHarness.woodcuttingConfig(quantity = 1000), bankedLogs = 1000)
        world.tickUntil { world.offers(id).isNotEmpty() }
        val offer = world.offers(id).single()

        world.playerBuys(qty = 1000, price = 120)
        assertEquals(OfferStatus.Completed, offer.status)
        val expected = 1000L * 100 - ExchangeTax.total(100, 1000)

        world.tickUntil { world.live(id) { it.coins() > 0 } }
        assertEquals(expected, world.actor(id).coins())
        world.tick(200)
        assertEquals(expected, world.actor(id).coins(), "collected exactly once")
        assertTrue(world.offers(id).isEmpty(), "settled offer freed its slot")
        val stats = world.coordinator.find("$id")!!.stats
        assertEquals(1000, stats.sold)
        assertEquals(expected, stats.coinsEarned)
    }

    @Test
    fun `only one listing per item and the listing reference is never reused`() {
        val world = BotTestHarness()
        val id = world.startBot(BotTestHarness.woodcuttingConfig(quantity = 1000), bankedLogs = 5000)
        world.tickUntil { world.offers(id).isNotEmpty() }
        world.tick(1500)
        assertEquals(1, world.offers(id).size)
        val refs = world.store.exchange.offers.values.mapNotNull { it.clientRef }
        assertEquals(refs.toSet().size, refs.size)
    }

    @Test
    fun `unsold listings are aborted after the timeout, retried at a lower price, then paused`() {
        val world = BotTestHarness()
        val config =
            BotTestHarness.woodcuttingConfig(quantity = 1000).let {
                it.copy(
                    pricing =
                        it.pricing.copy(listingTimeoutMinutes = 1, maxRetries = 2, retryStepPercent = 10.0, cooldownMinutes = 600)
                )
            }
        val id = world.startBot(config, bankedLogs = 3000)

        val prices = ArrayList<Int>()
        world.tickUntil(limit = 20_000) {
            world.offers(id).filter { it.isOpen }.forEach { if (prices.lastOrNull() != it.price) prices += it.price }
            world.log.lines.any { "selling paused" in it }
        }
        assertEquals(listOf(100, 90, 80), prices)
        val pausedUntil = world.coordinator.find("$id")!!.salePausedUntil
        assertTrue(pausedUntil > world.now)

        val listed = world.store.exchange.offers.size
        world.tick(3000)
        assertEquals(listed, world.store.exchange.offers.size, "no new listings during the cooldown")
    }

    @Test
    fun `selling pauses when no safe reference price exists`() {
        val world = BotTestHarness()
        world.serviceReference = null
        val config =
            BotTestHarness.woodcuttingConfig().let { it.copy(pricing = it.pricing.copy(referencePrice = null)) }
        val id = world.startBot(config, bankedLogs = 1500)
        world.tick(800)
        assertTrue(world.offers(id).isEmpty())
        assertTrue(world.log.lines.any { "selling paused: no safe reference price" in it })
        val gathered = world.coordinator.find("$id")!!.stats.gathered
        assertEquals(1500 + gathered, world.actor(id).total(LOGS))
    }

    @Test
    fun `no listing while the exchange store is unhealthy`() {
        val world = BotTestHarness()
        world.exchangeHealthy = false
        val id = world.startBot(BotTestHarness.woodcuttingConfig(), bankedLogs = 1500)
        world.tick(800)
        assertTrue(world.offers(id).isEmpty())
    }

    @Test
    fun `every log is accounted for across gathering, banking, listing and selling`() {
        val world = BotTestHarness()
        val id = world.startBot(BotTestHarness.woodcuttingConfig(quantity = 1000), bankedLogs = 990)
        world.tickUntil { world.offers(id).isNotEmpty() }
        world.playerBuys(qty = 400, price = 100)
        world.tick(400)

        val actor = world.actor(id)
        val gathered = world.coordinator.find("$id")!!.stats.gathered
        val inExchange = world.offers(id).sumOf { it.remaining + it.collectItems.toLong() }
        val boughtByPlayer = world.book.offers(ExchangeOwner.player(99)).sumOf { it.collectItems.toLong() }
        assertEquals(990 + gathered, actor.total(LOGS) + inExchange + boughtByPlayer)
    }

    @Test
    fun `crash between listing and the next save cannot duplicate items`() {
        val world = BotTestHarness()
        val id = world.startBot(BotTestHarness.woodcuttingConfig(quantity = 1000), bankedLogs = 1000)
        world.tickUntil { world.offers(id).isNotEmpty() }
        val listingWrite = world.store.writes.last { it.exchange.offers.isNotEmpty() }
        val savedBot = listingWrite.bots.single()
        val savedLogs =
            (savedBot.save.inv + savedBot.save.bank)
                .filter { it.id == LOGS || it.id == LOGS + NOTE_OFFSET }
                .sumOf { it.count.toLong() }
        val escrowed = listingWrite.exchange.offers.single().remaining
        assertEquals(1000 + savedBot.stats.gathered, savedLogs + escrowed, "one write holds both sides")

        world.restart()
        world.tick()
        assertEquals(1, world.offers(id).size, "restart does not relist")
        assertEquals(1000, world.offers(id).single().remaining)
        val persisted = world.store.bots.getValue(id)
        val persistedLogs =
            (persisted.save.inv + persisted.save.bank)
                .filter { it.id == LOGS || it.id == LOGS + NOTE_OFFSET }
                .sumOf { it.count.toLong() }
        assertEquals(1000 + persisted.stats.gathered, persistedLogs + 1000)
    }

    @Test
    fun `restart after a sale collects proceeds exactly once`() {
        val world = BotTestHarness()
        val id = world.startBot(BotTestHarness.woodcuttingConfig(quantity = 1000), bankedLogs = 1000)
        world.tickUntil { world.offers(id).isNotEmpty() }
        world.playerBuys(qty = 1000, price = 100)
        world.tickUntil { world.live(id) { it.coins() > 0 } }
        val coins = world.actor(id).coins()
        world.tick(20)

        world.restart()
        world.tick(400)
        assertEquals(coins, world.actor(id).coins())
    }

    @Test
    fun `a rejected listing returns every item and pauses instead of retrying in a loop`() {
        val world = BotTestHarness()
        val id = world.createBot(BotTestHarness.woodcuttingConfig(quantity = 1000)).id
        world.book.place(ExchangeOwner.bot(id), OfferSide.Sell, ORE, 1, 500, "bot:$id:sale:0")
        world.coordinator.editSave(id) {
            it.copy(inv = listOf(SavedObj(0, AXE, 1)), bank = listOf(SavedObj(0, LOGS, 1000)))
        }
        world.coordinator.enable(id)
        world.coordinator.setMaster(true)

        world.tickUntil { world.log.lines.any { "listing rejected" in it } }
        world.tick(600)
        val rejections = world.log.lines.count { "selling paused: listing rejected" in it }
        assertEquals(1, rejections)
        val gathered = world.coordinator.find("$id")!!.stats.gathered
        assertEquals(1000 + gathered, world.actor(id).total(LOGS))
        assertEquals(1, world.coordinator.find("$id")!!.saleSeq, "the consumed reference is skipped")
    }

    @Test
    fun `removing a bot with offers needs force and cancels them`() {
        val world = BotTestHarness()
        val id = world.startBot(BotTestHarness.woodcuttingConfig(quantity = 1000), bankedLogs = 1000)
        world.tickUntil { world.offers(id).isNotEmpty() }
        world.coordinator.disable(id)
        world.tickUntil { !world.coordinator.isLive(id) }

        assertTrue(world.coordinator.remove(id, force = false) != null)
        assertEquals(null, world.coordinator.remove(id, force = true))
        assertTrue(world.offers(id).isEmpty())
        assertTrue(world.store.bots[id] == null)
        assertTrue(world.store.exchange.offers.values.all { it.status == OfferStatus.Closed })
    }
}
