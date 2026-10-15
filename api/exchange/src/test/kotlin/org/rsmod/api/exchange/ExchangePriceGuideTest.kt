package org.rsmod.api.exchange

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExchangePriceGuideTest {
    private val settings = PriceGuideSettings(windowMillis = 10_000, minTrades = 3, minVolume = 30)
    private val now = 100_000L

    private fun trade(id: Long, qty: Int, price: Int, age: Long = 0) =
        ExchangeTrade(id, 1511, qty, price, 0, 0, now - age)

    @Test
    fun `volume weighted average of recent trades`() {
        val trades = listOf(trade(1, 10, 100), trade(2, 30, 120), trade(3, 10, 110))
        val signal = ExchangePriceGuide.volumeWeighted(trades, now, settings)
        assertEquals(PriceSignal.Reliable(114, 3, 50), signal)
    }

    @Test
    fun `stale trades are not reliable`() {
        val trades = listOf(trade(1, 50, 100, age = 20_000), trade(2, 50, 100, age = 30_000))
        val signal = ExchangePriceGuide.volumeWeighted(trades, now, settings)
        assertEquals(PriceSignal.Unavailable("trade data is stale"), signal)
    }

    @Test
    fun `outliers are discarded before averaging`() {
        val trades =
            listOf(trade(1, 10, 100), trade(2, 10, 102), trade(3, 10, 98), trade(4, 10, 10_000))
        val signal = ExchangePriceGuide.volumeWeighted(trades, now, settings)
        assertEquals(PriceSignal.Reliable(100, 3, 30), signal)
    }

    @Test
    fun `too little volume is not reliable`() {
        val trades = listOf(trade(1, 1, 100), trade(2, 1, 100), trade(3, 1, 100))
        val signal = ExchangePriceGuide.volumeWeighted(trades, now, settings)
        assertTrue(signal is PriceSignal.Unavailable)
    }

    @Test
    fun `no trades at all is not reliable`() {
        val signal = ExchangePriceGuide.volumeWeighted(emptyList(), now, settings)
        assertEquals(PriceSignal.Unavailable("no trades"), signal)
    }
}
