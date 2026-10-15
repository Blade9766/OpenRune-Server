package org.rsmod.content.other.bots.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.exchange.ExchangeTax
import org.rsmod.api.exchange.PriceSignal

class SalePricingTest {
    private val config = PricingConfig(referencePrice = 1000, adjustmentPercent = -2.0, minPrice = 100)
    private val reliable = PriceSignal.Reliable(price = 900, trades = 10, volume = 5000)
    private val unavailable = PriceSignal.Unavailable("no trades")

    @Test
    fun `reliable trade average is preferred and adjusted`() {
        val decision = SalePricing.decide(config, reliable, null, null, retries = 0)
        assertEquals(PriceDecision.List(882, "trade average"), decision)
    }

    @Test
    fun `falls back to the configured reference without trade data`() {
        val decision = SalePricing.decide(config, unavailable, 50, null, retries = 0)
        assertEquals(PriceDecision.List(980, "reference"), decision)
    }

    @Test
    fun `falls back to the market price service without a configured reference`() {
        val noReference = config.copy(referencePrice = null)
        val decision = SalePricing.decide(noReference, unavailable, 500, null, retries = 0)
        assertEquals(PriceDecision.List(490, "reference"), decision)
    }

    @Test
    fun `anomalous trade averages are rejected in favour of the reference`() {
        val spiked = PriceSignal.Reliable(price = 5000, trades = 10, volume = 5000)
        val decision = SalePricing.decide(config, spiked, null, null, retries = 0)
        decision as PriceDecision.List
        assertEquals(980, decision.price)
        assertTrue("anomalous" in decision.basis)
    }

    @Test
    fun `pauses when there is no safe reference`() {
        val noReference = config.copy(referencePrice = null)
        val decision = SalePricing.decide(noReference, unavailable, null, null, retries = 0)
        assertTrue(decision is PriceDecision.Paused)
    }

    @Test
    fun `never lists below another seller`() {
        val decision = SalePricing.decide(config, reliable, null, bestCompetingAsk = 950, retries = 0)
        assertEquals(950, (decision as PriceDecision.List).price)
        val above = SalePricing.decide(config, reliable, null, bestCompetingAsk = 800, retries = 0)
        assertEquals(882, (above as PriceDecision.List).price)
    }

    @Test
    fun `retries step the price down but never past the minimum net of tax`() {
        val retrying = config.copy(retryStepPercent = 10.0, maxRetries = 3, minPrice = 750)
        val prices = (0..5).map { (SalePricing.decide(retrying, unavailable, null, null, it) as PriceDecision.List).price }
        val floor = SalePricing.minGrossPrice(750).toInt()
        assertEquals(765, floor)
        assertEquals(listOf(980, 882, 784, floor, floor, floor), prices)
        assertTrue(ExchangeTax.netPerItem(765) >= 750)
        assertTrue(ExchangeTax.netPerItem(764) < 750)
    }

    @Test
    fun `minimum gross price accounts for tax at every scale`() {
        for (net in listOf(1, 49, 50, 98, 1000, 250_000_000, 1_000_000_000)) {
            val gross = SalePricing.minGrossPrice(net)
            assertTrue(gross <= Int.MAX_VALUE)
            assertTrue(ExchangeTax.netPerItem(gross.toInt()) >= net, "net=$net gross=$gross")
            if (gross > net) {
                assertTrue(ExchangeTax.netPerItem((gross - 1).toInt()) < net, "net=$net not minimal")
            }
        }
    }
}
