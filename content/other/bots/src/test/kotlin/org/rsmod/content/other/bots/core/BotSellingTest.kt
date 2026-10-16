package org.rsmod.content.other.bots.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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

    private fun BotTestHarness.listed(id: Long): Boolean =
        offers(id).isNotEmpty() && record(id).pendingListing == null

    /** Logs the bot owns on its character and in its exchange slots, as both stores last saved them. */
    private fun BotTestHarness.persistedLogs(id: Long): Long {
        val saved = store.bots.getValue(id).save
        val character =
            (saved.inv + saved.bank)
                .filter { it.id == LOGS || it.id == LOGS + NOTE_OFFSET }
                .sumOf { it.count.toLong() }
        return character + offers(id).sumOf { it.remaining + it.collectItems.toLong() }
    }

    private fun BotTestHarness.gathered(id: Long): Long = record(id).stats.gathered

    /** Ticks without saving the exchange until [predicate] holds, so a restart loses that tick. */
    private fun BotTestHarness.tickUnsavedUntil(predicate: () -> Boolean) {
        repeat(5_000) {
            tick(saveExchange = false)
            if (predicate()) {
                return
            }
            exchange.save()
        }
        error("condition not reached; log=${log.lines.takeLast(15)}")
    }

    @Test
    fun `no sale below the threshold`() {
        val world = BotTestHarness()
        val id = world.startBot(BotTestHarness.woodcuttingConfig(threshold = 1000), bankedLogs = 950)
        world.tickUntil { world.live(id) { it.bankCount(LOGS) >= 977 } }
        assertTrue(world.offers(id).isEmpty())
        assertFalse(world.log.lines.any { "listing" in it })
    }

    @Test
    fun `reaching the threshold lists the configured quantity`() {
        val world = BotTestHarness()
        val config = BotTestHarness.woodcuttingConfig(threshold = 1000, quantity = 600)
        val id = world.startBot(config, bankedLogs = 990)

        world.tickUntil { world.listed(id) }
        val offer = world.offers(id).single()
        assertTrue(offer.sell)
        assertEquals(600, offer.total)
        assertEquals(LOGS, offer.itemId)
        assertEquals(100, offer.price, "reference price, no adjustment")
        assertEquals(990 + 27 - 600L, world.actor(id).bankCount(LOGS))
        assertEquals(0, world.actor(id).held(LOGS))
        assertEquals(600, world.record(id).stats.listed)
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
    fun `every allowed item past the threshold is listed on the same trip`() {
        val world = BotTestHarness()
        val config = BotTestHarness.woodcuttingConfig(allowed = setOf("obj.logs", "obj.iron_ore"))
        val id = world.createBot(config).id
        world.coordinator.editSave(id) {
            it.copy(
                inv = listOf(SavedObj(0, AXE, 1)),
                bank = listOf(SavedObj(0, LOGS, 1200), SavedObj(1, ORE, 1100)),
            )
        }
        world.coordinator.enable(id)
        world.coordinator.setMaster(true)
        world.tickUntil { world.offers(id).size == 2 && world.record(id).pendingListing == null }
        val listed = world.offers(id).associate { it.itemId to it.total }
        assertEquals(mapOf(LOGS to 1000, ORE to 1000), listed)
        assertEquals(1100L - 1000, world.actor(id).bankCount(ORE))
        assertEquals(0, world.actor(id).held(ORE))
    }

    @Test
    fun `an allowed item below the threshold is kept`() {
        val world = BotTestHarness()
        val config = BotTestHarness.woodcuttingConfig(allowed = setOf("obj.logs", "obj.iron_ore"))
        val id = world.createBot(config).id
        world.coordinator.editSave(id) {
            it.copy(
                inv = listOf(SavedObj(0, AXE, 1)),
                bank = listOf(SavedObj(0, LOGS, 1200), SavedObj(1, ORE, 999)),
            )
        }
        world.coordinator.enable(id)
        world.coordinator.setMaster(true)
        world.tickUntil { world.listed(id) }
        world.tick(200)
        assertEquals(listOf(LOGS), world.offers(id).map { it.itemId })
        assertEquals(999L, world.actor(id).bankCount(ORE))
    }

    @Test
    fun `a sale to a player is collected to the bank exactly once`() {
        val world = BotTestHarness()
        val id = world.startBot(BotTestHarness.woodcuttingConfig(quantity = 1000), bankedLogs = 1000)
        world.tickUntil { world.listed(id) }

        val buyer = world.playerBuys(qty = 1000, price = 120)
        assertEquals(1000, buyer.collectItems)
        assertEquals(20 * 1000, buyer.collectCoins, "the buyer is refunded above the resting price")
        val expected = 1000L * 100 - 1000L * 2

        world.tickUntil { world.live(id) { it.coins() > 0 } }
        assertEquals(expected, world.actor(id).coins())
        world.tick(200)
        assertEquals(expected, world.actor(id).coins(), "collected exactly once")
        assertTrue(world.offers(id).isEmpty(), "the emptied offer freed its slot")
        assertTrue(world.record(id).pendingCollects.isEmpty())
        val stats = world.record(id).stats
        assertEquals(1000, stats.sold)
        assertEquals(expected, stats.coinsEarned)
    }

    @Test
    fun `only one listing per item at a time`() {
        val world = BotTestHarness()
        val id = world.startBot(BotTestHarness.woodcuttingConfig(quantity = 1000), bankedLogs = 5000)
        world.tickUntil { world.listed(id) }
        world.tick(1500)
        assertEquals(1, world.offers(id).size)
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
        assertTrue(world.record(id).salePausedUntil > world.now)

        val listings = world.log.lines.count { "listing " in it }
        world.tick(3000)
        assertEquals(listings, world.log.lines.count { "listing " in it }, "no new listings during the cooldown")
        assertEquals(3000 + world.gathered(id), world.actor(id).total(LOGS), "aborted items came back")
    }

    @Test
    fun `a timed-out listing is relisted in full, not replaced by what is left in the bank`() {
        val world = BotTestHarness()
        val config =
            BotTestHarness.woodcuttingConfig(quantity = 1000).let {
                it.copy(pricing = it.pricing.copy(listingTimeoutMinutes = 1, maxRetries = 3))
            }
        val id = world.startBot(config, bankedLogs = 1000)

        val listings = LinkedHashMap<Long, Int>()
        world.tickUntil(limit = 20_000) {
            world.offers(id).filter { it.isOpen }.forEach { listings[it.createdAt] = it.total }
            listings.size >= 2
        }
        val (first, second) = listings.values.toList()
        assertEquals(1000, first)
        assertTrue(second >= 1000, "relisted $second instead of the returned 1000")
    }

    @Test
    fun `selling pauses when no safe reference price exists`() {
        val world = BotTestHarness()
        world.exchange.guide = null
        val config =
            BotTestHarness.woodcuttingConfig().let { it.copy(pricing = it.pricing.copy(referencePrice = null)) }
        val id = world.startBot(config, bankedLogs = 1500)
        world.tick(800)
        assertTrue(world.offers(id).isEmpty())
        assertTrue(world.log.lines.any { "selling paused: no safe reference price" in it })
        assertEquals(1500 + world.gathered(id), world.actor(id).total(LOGS))
    }

    @Test
    fun `nothing reaches the exchange while the bot store cannot be written`() {
        val world = BotTestHarness()
        val id = world.startBot(BotTestHarness.woodcuttingConfig(), bankedLogs = 1500)
        world.store.failing = true
        world.tick(800)
        assertTrue(world.offers(id).isEmpty())
        assertFalse(world.coordinator.storeHealthy)
        assertEquals(1, world.log.lines.count { it.startsWith("ERROR") }, "reported once, not every tick")
        assertEquals(1500 + world.gathered(id), world.actor(id).total(LOGS))
    }

    @Test
    fun `every log is accounted for across gathering, banking, listing and selling`() {
        val world = BotTestHarness()
        val id = world.startBot(BotTestHarness.woodcuttingConfig(quantity = 1000), bankedLogs = 990)
        world.tickUntil { world.listed(id) }
        val buyer = world.playerBuys(qty = 400, price = 100)
        world.tick(400)

        val inExchange = world.offers(id).sumOf { it.remaining + it.collectItems.toLong() }
        assertEquals(990 + world.gathered(id), world.actor(id).total(LOGS) + inExchange + buyer.collectItems)
    }

    @Test
    fun `a crash after the listing is written but before it is placed returns the items`() {
        val world = BotTestHarness()
        val id = world.startBot(BotTestHarness.woodcuttingConfig(quantity = 1000), bankedLogs = 1000)
        world.tickUntil { world.record(id).pendingListing != null }
        assertNotNull(world.store.bots.getValue(id).pendingListing, "written before the exchange is touched")
        assertTrue(world.offers(id).isEmpty())

        world.restart()
        world.tick()
        assertTrue(world.offers(id).isEmpty())
        assertNull(world.record(id).pendingListing)
        assertEquals(1000 + world.gathered(id), world.actor(id).total(LOGS))
    }

    @Test
    fun `a crash after placing but before the exchange saved returns the items`() {
        val world = BotTestHarness()
        val id = world.startBot(BotTestHarness.woodcuttingConfig(quantity = 1000), bankedLogs = 1000)
        world.tickUnsavedUntil { world.record(id).pendingListing?.placed == true }
        assertEquals(1, world.offers(id).size)

        world.restart()
        assertTrue(world.offers(id).isEmpty(), "the unsaved offer is gone")
        world.tick()
        assertNull(world.record(id).pendingListing)
        assertEquals(1000 + world.gathered(id), world.actor(id).total(LOGS))
        assertTrue(world.log.lines.any { "never reached the exchange's save" in it })
    }

    @Test
    fun `a crash after the exchange saved the offer keeps it and does not return the items`() {
        val world = BotTestHarness()
        val id = world.startBot(BotTestHarness.woodcuttingConfig(quantity = 1000), bankedLogs = 1000)
        world.tickUntil { world.record(id).pendingListing?.placed == true }
        assertEquals(1000 + world.gathered(id), world.persistedLogs(id))

        world.restart()
        world.tick()
        assertNull(world.record(id).pendingListing)
        assertEquals(1000, world.offers(id).single().remaining)
        assertEquals(1000 + world.gathered(id), world.actor(id).total(LOGS) + 1000)
    }

    @Test
    fun `a crash after crediting proceeds but before the exchange is emptied pays once`() {
        val world = BotTestHarness()
        val id = world.startBot(BotTestHarness.woodcuttingConfig(quantity = 1000), bankedLogs = 1000)
        world.tickUntil { world.listed(id) }
        world.playerBuys(qty = 1000, price = 100)
        world.tickUntil { world.record(id).pendingCollects.isNotEmpty() }
        assertEquals(98_000, world.offers(id).single().collectCoins, "the exchange still holds the coins")

        world.restart()
        world.tick(50)
        assertEquals(98_000, world.actor(id).coins())
        assertTrue(world.offers(id).isEmpty())
        assertTrue(world.record(id).pendingCollects.isEmpty())
    }

    @Test
    fun `a crash after taking proceeds but before the exchange saved pays once`() {
        val world = BotTestHarness()
        val id = world.startBot(BotTestHarness.woodcuttingConfig(quantity = 1000), bankedLogs = 1000)
        world.tickUntil { world.listed(id) }
        world.playerBuys(qty = 1000, price = 100)
        world.tickUnsavedUntil { world.record(id).pendingCollects.any { it.taken } }
        assertTrue(world.offers(id).isEmpty(), "taken from the exchange in memory")

        world.restart()
        assertEquals(98_000, world.offers(id).single().collectCoins, "the take was never saved")
        world.tick(50)
        assertEquals(98_000, world.actor(id).coins())
        assertTrue(world.offers(id).isEmpty())
    }

    @Test
    fun `a crash after the exchange saved the collection pays once`() {
        val world = BotTestHarness()
        val id = world.startBot(BotTestHarness.woodcuttingConfig(quantity = 1000), bankedLogs = 1000)
        world.tickUntil { world.listed(id) }
        world.playerBuys(qty = 1000, price = 100)
        world.tickUntil { world.record(id).pendingCollects.any { it.taken } }

        world.restart()
        world.tick(50)
        assertEquals(98_000, world.actor(id).coins())
        assertTrue(world.record(id).pendingCollects.isEmpty())
    }

    @Test
    fun `a sale the exchange never saved is never paid out`() {
        val world = BotTestHarness()
        val id = world.startBot(BotTestHarness.woodcuttingConfig(quantity = 1000), bankedLogs = 1000)
        world.tickUntil { world.listed(id) }
        world.playerBuys(qty = 1000, price = 100)

        var banked = false
        var ticks = 0
        while (ticks++ < 2_000 && !(banked && world.state(id) != BotState.Banking)) {
            world.tick(saveExchange = false)
            banked = banked || world.state(id) == BotState.Banking
        }
        assertTrue(banked, "the bot visited the bank while the fill was unsaved")
        assertEquals(98_000, world.offers(id).single().collectCoins, "the unsaved fill is in the collect box")
        assertEquals(0, world.actor(id).coins())

        world.restart()
        assertTrue(world.offers(id).single().isOpen, "the exchange reloaded without the fill")
        world.tick(500)
        assertEquals(0, world.actor(id).coins())
        assertTrue(world.record(id).pendingCollects.isEmpty())
        val inExchange = world.offers(id).sumOf { it.remaining + it.collectItems.toLong() }
        assertEquals(1000 + world.gathered(id), world.actor(id).total(LOGS) + inExchange)
    }

    @Test
    fun `a refused listing returns every item and pauses instead of retrying in a loop`() {
        val world = BotTestHarness()
        world.exchange.refuseSells = true
        val id = world.startBot(BotTestHarness.woodcuttingConfig(quantity = 1000), bankedLogs = 1000)

        world.tickUntil { world.log.lines.any { "listing rolled back" in it } }
        world.tick(600)
        assertEquals(1, world.log.lines.count { "selling paused: listing failed" in it })
        assertEquals(1000 + world.gathered(id), world.actor(id).total(LOGS))
        assertTrue(world.offers(id).isEmpty())
    }

    @Test
    fun `a disabled bot settles its pending listing before leaving the world`() {
        val world = BotTestHarness()
        val id = world.startBot(BotTestHarness.woodcuttingConfig(quantity = 1000), bankedLogs = 1000)
        world.tickUntil { world.record(id).pendingListing != null }
        world.coordinator.disable(id)
        world.tickUntil(limit = 100) { !world.coordinator.isLive(id) }
        assertNull(world.store.bots.getValue(id).pendingListing)
        assertEquals(1000 + world.gathered(id), world.persistedLogs(id))
    }

    @Test
    fun `removing a bot with offers needs force and cancels them`() {
        val world = BotTestHarness()
        val id = world.startBot(BotTestHarness.woodcuttingConfig(quantity = 1000), bankedLogs = 1000)
        world.tickUntil { world.listed(id) }
        world.coordinator.disable(id)
        world.tickUntil { !world.coordinator.isLive(id) }

        assertNotNull(world.coordinator.remove(id, force = false))
        assertNull(world.coordinator.remove(id, force = true))
        assertTrue(world.offers(id).isEmpty())
        assertNull(world.store.bots[id])
    }
}
