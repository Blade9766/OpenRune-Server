package org.rsmod.content.other.bots.world

import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.content.other.grandexchange.GeExchange
import org.rsmod.content.other.grandexchange.GeItemData
import org.rsmod.content.other.grandexchange.GeOfferType

class GeBotExchangeTest {
    private val items = GeItemData.fromLines(listOf("$LOGS\t15000\t40\tLogs"))
    private var now = 1_000_000L
    private val ge = GeExchange(items) { now }
    private val exchange = GeBotExchange(ge, items)

    @Test
    fun `a bot sale trades with a player and its proceeds are collected through the adapter`() {
        assertTrue(exchange.sell(BOT, "Sir Chops", 0, LOGS, 1000, 50))
        ge.place(PLAYER, "player", 0, GeOfferType.Buy, LOGS, 60, 400)

        val offer = exchange.offer(BOT, 0)!!
        assertEquals(400, offer.completed)
        assertEquals(400L * 50 - 400, offer.collectCoins.toLong())
        assertEquals(0, offer.coinsCollected)
        assertEquals(listOf(400 to 50), exchange.recentTrades(LOGS).map { it.quantity to it.price })

        assertEquals(19_600, exchange.takeCoins(BOT, 0, 19_600))
        assertEquals(19_600, exchange.offer(BOT, 0)!!.coinsCollected)

        assertTrue(exchange.abort(BOT, 0))
        val aborted = exchange.offer(BOT, 0)!!
        assertEquals(600, aborted.collectItems)
        assertEquals(0, aborted.itemsCollected)
        assertEquals(600, exchange.takeItems(BOT, 0, 600))
        assertNull(exchange.offer(BOT, 0), "an emptied finished offer frees the slot")
        assertEquals(0, exchange.freeSlot(BOT))
    }

    @Test
    fun `invalid offers are refused instead of throwing`() {
        assertTrue(exchange.sell(BOT, "Sir Chops", 0, LOGS, 10, 50))
        assertFalse(exchange.sell(BOT, "Sir Chops", 0, LOGS, 10, 50), "occupied slot")
        assertFalse(exchange.sell(BOT, "Sir Chops", 1, LOGS, 2, Int.MAX_VALUE), "value overflow")
        assertFalse(exchange.sell(BOT, "Sir Chops", 1, LOGS, 0, 50))
    }

    @Test
    fun `save count tracks writes and guide price ignores unknown items`() {
        val path = Files.createTempFile("ge", ".json")
        try {
            assertEquals(0, exchange.saveCount)
            exchange.sell(BOT, "Sir Chops", 0, LOGS, 10, 50)
            ge.save(path)
            assertEquals(1, exchange.saveCount)
        } finally {
            Files.deleteIfExists(path)
        }
        assertEquals(40, exchange.guidePrice(LOGS))
        assertNull(exchange.guidePrice(LOGS + 1))
        assertEquals(1, exchange.taxPerItem(LOGS, 50))
        assertEquals(50, exchange.lowestAsk(LOGS, PLAYER))
        assertNull(exchange.lowestAsk(LOGS, BOT))
    }

    private companion object {
        private const val LOGS = 1511
        private const val BOT = -7
        private const val PLAYER = 42
    }
}
