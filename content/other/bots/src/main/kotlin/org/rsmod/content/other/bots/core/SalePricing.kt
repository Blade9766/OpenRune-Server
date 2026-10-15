package org.rsmod.content.other.bots.core

import kotlin.math.abs
import kotlin.math.floor
import org.rsmod.api.exchange.ExchangeTax
import org.rsmod.api.exchange.PriceGuideSettings
import org.rsmod.api.exchange.PriceSignal

sealed class PriceDecision {
    data class List(val price: Int, val basis: String) : PriceDecision()

    data class Paused(val reason: String) : PriceDecision()
}

object SalePricing {
    fun guideSettings(config: PricingConfig): PriceGuideSettings =
        PriceGuideSettings(
            windowMillis = config.windowMinutes * 60_000L,
            minTrades = config.minTrades,
            minVolume = config.minVolume,
        )

    /**
     * Picks a listing price: a reliable volume-weighted trade price first, else the reference
     * price (the configured one, or the market-price service's value). The result is adjusted,
     * stepped down per failed retry, kept at or above the minimum *net-of-tax* price, and never
     * listed below another seller's current ask, so bots join the queue instead of undercutting.
     */
    fun decide(
        config: PricingConfig,
        signal: PriceSignal,
        serviceReference: Int?,
        bestCompetingAsk: Int?,
        retries: Int,
    ): PriceDecision {
        val reference = (config.referencePrice ?: serviceReference)?.takeIf { it > 0 }
        val (base, basis) =
            when {
                signal is PriceSignal.Reliable && reference != null &&
                    deviationPercent(signal.price, reference) > config.maxDeviationPercent ->
                    reference to "reference (trade average ${signal.price} rejected as anomalous)"
                signal is PriceSignal.Reliable -> signal.price to "trade average"
                reference != null -> reference to "reference"
                else -> {
                    val why = (signal as? PriceSignal.Unavailable)?.reason ?: "no trade data"
                    return PriceDecision.Paused("no safe reference price ($why)")
                }
            }

        val step = config.retryStepPercent * retries.coerceIn(0, config.maxRetries)
        val factor = (1 + config.adjustmentPercent / 100.0) * (1 - step / 100.0)
        var price = floor(base * factor).toLong().coerceAtLeast(1)

        val floorPrice = minGrossPrice(config.minPrice)
        if (price < floorPrice) {
            price = floorPrice
        }
        if (bestCompetingAsk != null && price < bestCompetingAsk) {
            price = bestCompetingAsk.toLong()
        }
        if (price > Int.MAX_VALUE) {
            return PriceDecision.Paused("computed price $price is out of range")
        }
        return PriceDecision.List(price.toInt(), basis)
    }

    /** Smallest listing price whose proceeds after tax still reach [minNet]. */
    fun minGrossPrice(minNet: Int): Long {
        if (net(minNet.toLong()) >= minNet) {
            return minNet.toLong()
        }
        val uncapped = (minNet.toLong() * 100 + 97) / 98
        var price = minOf(uncapped, minNet + ExchangeTax.CAP_PER_ITEM)
        while (price > minNet && net(price - 1) >= minNet) {
            price--
        }
        while (net(price) < minNet) {
            price++
        }
        return price
    }

    private fun net(price: Long): Long =
        if (price > Int.MAX_VALUE) {
            price - ExchangeTax.CAP_PER_ITEM
        } else {
            ExchangeTax.netPerItem(price.toInt())
        }

    private fun deviationPercent(value: Int, reference: Int): Double =
        abs(value - reference) * 100.0 / reference
}
