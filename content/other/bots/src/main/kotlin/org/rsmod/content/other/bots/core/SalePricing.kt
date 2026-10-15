package org.rsmod.content.other.bots.core

import kotlin.math.abs
import kotlin.math.floor

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
     * price (the configured one, or the exchange's guide price). The result is adjusted, stepped
     * down per failed retry, kept at or above the minimum *net-of-tax* price, and never listed below
     * another seller's current ask, so bots join the queue instead of undercutting.
     */
    fun decide(
        config: PricingConfig,
        signal: PriceSignal,
        serviceReference: Int?,
        competingAsk: Int?,
        retries: Int,
        tax: (Int) -> Int,
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

        val floorPrice = minGrossPrice(config.minPrice, tax)
            ?: return PriceDecision.Paused("minimum price ${config.minPrice} is unreachable after tax")
        if (price < floorPrice) {
            price = floorPrice.toLong()
        }
        if (competingAsk != null && price < competingAsk) {
            price = competingAsk.toLong()
        }
        if (price > Int.MAX_VALUE) {
            return PriceDecision.Paused("computed price $price is out of range")
        }
        return PriceDecision.List(price.toInt(), basis)
    }

    /**
     * Smallest listing price whose proceeds after [tax] still reach [minNet]. Proceeds never fall as
     * the price rises (the fee grows by at most a coin per coin), so a binary search finds it.
     */
    fun minGrossPrice(minNet: Int, tax: (Int) -> Int): Int? {
        fun net(price: Int): Long = price.toLong() - tax(price)
        if (net(Int.MAX_VALUE) < minNet) {
            return null
        }
        var low = minNet.coerceAtLeast(1)
        var high = Int.MAX_VALUE
        while (low < high) {
            val mid = low + (high - low) / 2
            if (net(mid) >= minNet) high = mid else low = mid + 1
        }
        return low
    }

    private fun deviationPercent(value: Int, reference: Int): Double =
        abs(value - reference) * 100.0 / reference
}
