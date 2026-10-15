package org.rsmod.content.other.bots.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SalePricingTest {
    private val config = PricingConfig(referencePrice = 1000, adjustmentPercent = -2.0, minPrice = 100)
    private val reliable = PriceSignal.Reliable(price = 900, trades = 10, volume = 5000)
    private val unavailable = PriceSignal.Unavailable("no trades")
    private val tax: (Int) -> Int = { price -> minOf(price.toLong() * 2 / 100, 5_000_000L).toInt() }

    private fun decide(
        config: PricingConfig = this.config,
        signal: PriceSignal = reliable,
        serviceReference: Int? = null,
        competingAsk: Int? = null,
        retries: Int = 0,
    ) = SalePricing.decide(config, signal, serviceReference, competingAsk, retries, tax)

    @Test
    fun `reliable trade average is preferred and adjusted`() {
        assertEquals(PriceDecision.List(882, "trade average"), decide())
    }

    @Test
    fun `falls back to the configured reference without trade data`() {
        assertEquals(PriceDecision.List(980, "reference"), decide(signal = unavailable, serviceReference = 50))
    }

    @Test
    fun `falls back to the market price service without a configured reference`() {
        val decision = decide(config.copy(referencePrice = null), unavailable, serviceReference = 500)
        assertEquals(PriceDecision.List(490, "reference"), decision)
    }

    @Test
    fun `anomalous trade averages are rejected in favour of the reference`() {
        val spiked = PriceSignal.Reliable(price = 5000, trades = 10, volume = 5000)
        val decision = decide(signal = spiked) as PriceDecision.List
        assertEquals(980, decision.price)
        assertTrue("anomalous" in decision.basis)
    }

    @Test
    fun `pauses when there is no safe reference`() {
        assertTrue(decide(config.copy(referencePrice = null), unavailable) is PriceDecision.Paused)
    }

    @Test
    fun `never lists below another seller`() {
        assertEquals(950, (decide(competingAsk = 950) as PriceDecision.List).price)
        assertEquals(882, (decide(competingAsk = 800) as PriceDecision.List).price)
    }

    @Test
    fun `retries step the price down but never past the minimum net of tax`() {
        val retrying = config.copy(retryStepPercent = 10.0, maxRetries = 3, minPrice = 750)
        val prices = (0..5).map { (decide(retrying, unavailable, retries = it) as PriceDecision.List).price }
        val floor = SalePricing.minGrossPrice(750, tax)
        assertEquals(765, floor)
        assertEquals(listOf(980, 882, 784, 765, 765, 765), prices)
        assertTrue(765 - tax(765) >= 750)
        assertTrue(764 - tax(764) < 750)
    }

    @Test
    fun `minimum gross price accounts for tax at every scale`() {
        for (net in listOf(1, 49, 50, 98, 1000, 250_000_000, 1_000_000_000)) {
            val gross = SalePricing.minGrossPrice(net, tax)!!
            assertTrue(gross - tax(gross) >= net, "net=$net gross=$gross")
            if (gross > net) {
                assertTrue((gross - 1) - tax(gross - 1) < net, "net=$net not minimal")
            }
        }
    }

    @Test
    fun `an unreachable minimum pauses instead of listing`() {
        val greedy: (Int) -> Int = { it }
        assertNull(SalePricing.minGrossPrice(10, greedy))
        assertTrue(SalePricing.decide(config, reliable, null, null, 0, greedy) is PriceDecision.Paused)
    }

    @Test
    fun `volume weighted price ignores stale trades and outliers`() {
        val now = 100_000_000L
        val settings = PriceGuideSettings(windowMillis = 60_000, minTrades = 3, minVolume = 10)
        val trades =
            listOf(
                MarketTrade(10, 100, now - 1_000),
                MarketTrade(30, 120, now - 2_000),
                MarketTrade(10, 110, now - 3_000),
                MarketTrade(1, 10_000, now - 4_000),
                MarketTrade(500, 1, now - 120_000),
            )
        val signal = PriceGuide.volumeWeighted(trades, now, settings)
        assertEquals(PriceSignal.Reliable(114, 3, 50), signal)
    }

    @Test
    fun `thin markets are unreliable`() {
        val now = 100_000_000L
        val settings = PriceGuideSettings(minTrades = 3, minVolume = 100)
        val thin = listOf(MarketTrade(5, 100, now), MarketTrade(5, 100, now), MarketTrade(5, 100, now))
        assertTrue(PriceGuide.volumeWeighted(thin, now, settings) is PriceSignal.Unavailable)
        assertTrue(PriceGuide.volumeWeighted(emptyList(), now, settings) is PriceSignal.Unavailable)
    }
}
