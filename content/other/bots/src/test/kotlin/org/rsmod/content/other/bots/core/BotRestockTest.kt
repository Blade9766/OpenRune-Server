package org.rsmod.content.other.bots.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BotRestockTest {
    private fun fisher(): BotTestHarness =
        BotTestHarness(actorFactory = { FakeActor(supplyItem = FEATHER, stackable = setOf(COINS, FEATHER)) })

    private fun BotTestHarness.startBot(
        bankedLogs: Int,
        coins: Int,
        restock: RestockConfig = RestockConfig(quantity = WANTED),
        timeoutMinutes: Int = 60,
    ): Long {
        val base = BotTestHarness.woodcuttingConfig()
        val config = base.copy(restock = restock, pricing = base.pricing.copy(listingTimeoutMinutes = timeoutMinutes))
        val id = createBot(config).id
        val bank = listOfNotNull(
            SavedObj(0, LOGS, bankedLogs).takeIf { bankedLogs > 0 },
            SavedObj(1, COINS, coins).takeIf { coins > 0 },
        )
        coordinator.editSave(id) { it.copy(inv = listOf(SavedObj(0, AXE, 1)), bank = bank) }
        coordinator.enable(id)
        coordinator.setMaster(true)
        return id
    }

    private fun BotTestHarness.featherSeller(quantity: Int = 10_000): FakeExchange.Offer =
        exchange.playerSells(SELLER, FEATHER, quantity, FEATHER_PRICE)

    private fun BotTestHarness.feathersSold(): Int = exchange.raw(SELLER).sumOf { it.completed }

    /** Feathers the bot owns: carried, banked, consumed by gathering, or waiting in its collect boxes. */
    private fun BotTestHarness.feathersOwned(id: Long): Long =
        actor(id).total(FEATHER) + record(id).stats.gathered +
            exchange.raw(-id.toInt()).filter { !it.sell }.sumOf { it.collectItems.toLong() }

    /** Coins the bot owns: banked, or escrowed in and refunded to its exchange offers. */
    private fun BotTestHarness.coinsOwned(id: Long): Long =
        actor(id).coins() +
            exchange.raw(-id.toInt()).sumOf {
                it.collectCoins.toLong() + if (!it.sell && !it.finished) (it.total - it.completed).toLong() * it.price else 0
            } +
            (record(id).pendingListing?.takeIf { it.buy }?.cost ?: 0)

    private fun BotTestHarness.logsOwned(id: Long): Long =
        actor(id).total(LOGS) + exchange.raw(-id.toInt()).filter { it.sell }.sumOf { (it.total - it.completed).toLong() + it.collectItems }

    @Test
    fun `a bot out of supplies with coins buys them and goes back to work`() {
        val world = fisher()
        world.featherSeller()
        val id = world.startBot(bankedLogs = 0, coins = 1000)

        world.tickUntil { world.record(id).stats.gathered > 0 }
        assertEquals(WANTED, world.feathersSold())
        assertEquals(WANTED.toLong(), world.feathersOwned(id))
        assertEquals(1000L - WANTED * FEATHER_PRICE, world.coinsOwned(id))
        assertEquals(WANTED.toLong(), world.record(id).stats.suppliesBought)
        assertEquals(0, world.record(id).stats.listed, "had the coins; nothing sold")
    }

    @Test
    fun `a bot with no coins sells products to fund supplies`() {
        val world = fisher()
        world.featherSeller()
        val id = world.startBot(bankedLogs = 300, coins = 0)

        world.tickUntil { world.offers(id).any { it.sell } && world.record(id).pendingListing == null }
        val sale = world.offers(id).single { it.sell }
        val net = sale.price - world.exchange.taxPerItem(LOGS, sale.price)
        assertEquals((WANTED * FEATHER_PRICE + net - 1) / net, sale.total, "only what the supplies cost, below the threshold")
        world.tickUntil { world.state(id) == BotState.WaitingForSupplies }
        assertTrue("afford supplies" in world.record(id).activity, world.record(id).activity)

        world.playerBuys(sale.total, sale.price)
        world.tickUntil { world.record(id).stats.gathered > 0 }
        assertEquals(WANTED, world.feathersSold())
        assertEquals(WANTED.toLong(), world.feathersOwned(id))
        assertEquals(sale.total.toLong() * net - WANTED * FEATHER_PRICE, world.coinsOwned(id))
        assertEquals(300L - sale.total + world.record(id).stats.gathered, world.logsOwned(id))
    }

    @Test
    fun `nobody selling supplies leaves products and coins untouched`() {
        val world = fisher()
        val id = world.startBot(bankedLogs = 300, coins = 1000)

        world.tickUntil { world.state(id) == BotState.WaitingForSupplies }
        world.tick(BotBrain.SUPPLY_RECHECK_TICKS * 3)
        assertEquals(BotState.WaitingForSupplies, world.state(id))
        assertTrue("nobody is selling" in world.record(id).activity, world.record(id).activity)
        assertTrue(world.offers(id).isEmpty())
        assertEquals(300L, world.actor(id).total(LOGS))
        assertEquals(1000L, world.actor(id).coins())
    }

    @Test
    fun `no coins and nothing to sell waits with a clear reason`() {
        val world = fisher()
        world.featherSeller()
        val id = world.startBot(bankedLogs = 0, coins = 0)

        world.tickUntil { world.state(id) == BotState.WaitingForSupplies }
        assertTrue("no coins or products" in world.record(id).activity, world.record(id).activity)
        assertTrue(world.offers(id).isEmpty())
    }

    @Test
    fun `restock off waits for an administrator as before`() {
        val world = fisher()
        world.featherSeller()
        val id = world.startBot(bankedLogs = 300, coins = 1000, restock = RestockConfig(enabled = false))

        world.tickUntil { world.state(id) == BotState.WaitingForSupplies }
        world.tick(BotBrain.SUPPLY_RECHECK_TICKS * 2)
        assertEquals("Waiting for supplies in the bank", world.record(id).activity)
        assertTrue(world.offers(id).isEmpty())
        assertEquals(0, world.feathersSold())
    }

    @Test
    fun `a partly filled buy offer is aborted after the timeout and refunded`() {
        val world = fisher()
        world.featherSeller(quantity = 30)
        val id = world.startBot(bankedLogs = 0, coins = 1000, timeoutMinutes = 1)

        world.tickUntil { world.feathersSold() == 30 && world.offers(id).isEmpty() && world.state(id) == BotState.WaitingForSupplies }
        assertEquals(30L, world.feathersOwned(id))
        assertEquals(1000L - 30 * FEATHER_PRICE, world.coinsOwned(id))
        assertEquals(1000L - 30 * FEATHER_PRICE, world.actor(id).coins(), "the unfilled part came back to the bank")
        assertTrue(world.log.lines.any { "aborted unfilled supply buy offer" in it })
    }

    @Test
    fun `a refused buy offer refunds the coins`() {
        val world = fisher()
        world.featherSeller()
        world.exchange.refuseBuys = true
        val id = world.startBot(bankedLogs = 0, coins = 1000)

        world.tickUntil { world.log.lines.any { "buy offer rolled back" in it } }
        world.tick()
        assertEquals(1000L, world.actor(id).coins())
        assertEquals(0, world.feathersSold())
    }

    @Test
    fun `a crash at any point of a purchase never duplicates or loses coins or supplies`() {
        for (crashAfter in 0..CRASH_WINDOW) {
            val world = fisher()
            world.featherSeller()
            val id = world.startBot(bankedLogs = 0, coins = 1000)
            world.tickUntil { world.state(id) == BotState.TravelToExchange }
            world.tick(crashAfter)
            world.tick(saveExchange = false)
            world.restart()

            world.tickUntil {
                world.live(id) { true } && world.record(id).stats.gathered > 0
            }
            val sold = world.feathersSold()
            assertEquals(sold.toLong(), world.feathersOwned(id), "crash after $crashAfter ticks: feathers")
            assertEquals(1000L - sold * FEATHER_PRICE, world.coinsOwned(id), "crash after $crashAfter ticks: coins")
            assertTrue(sold > 0, "crash after $crashAfter ticks: restocked")
        }
    }

    @Test
    fun `a crash at any point of a funding sale never duplicates or loses products or coins`() {
        for (crashAfter in 0..CRASH_WINDOW) {
            val world = fisher()
            world.featherSeller()
            val id = world.startBot(bankedLogs = 300, coins = 0)
            world.playerBuys(qty = 300, price = 100)
            world.tickUntil { world.state(id) == BotState.TravelToExchange }
            world.tick(crashAfter)
            world.tick(saveExchange = false)
            world.restart()

            world.tickUntil(limit = 10_000) { world.live(id) { true } && world.record(id).stats.gathered > 0 }
            val logsSold = world.exchange.raw(99).sumOf { it.completed }
            val net = 100 - world.exchange.taxPerItem(LOGS, 100)
            val sold = world.feathersSold()
            val gathered = world.record(id).stats.gathered
            assertEquals(300L - logsSold + gathered, world.logsOwned(id), "crash after $crashAfter ticks: logs")
            assertEquals(sold.toLong(), world.feathersOwned(id), "crash after $crashAfter ticks: feathers")
            assertEquals(logsSold.toLong() * net - sold * FEATHER_PRICE, world.coinsOwned(id), "crash after $crashAfter ticks: coins")
        }
    }

    private companion object {
        const val FEATHER = 314
        const val FEATHER_PRICE = 5
        const val WANTED = 100
        const val SELLER = 98
        const val CRASH_WINDOW = 40
    }
}
