package org.rsmod.api.exchange

import kotlin.math.abs
import kotlin.math.roundToLong

public data class PriceGuideSettings(
    val windowMillis: Long = 6 * 60 * 60 * 1000L,
    val minTrades: Int = 3,
    val minVolume: Long = 100,
    /** Trades further than this fraction away from the median are discarded as outliers. */
    val outlierTolerance: Double = 0.5,
)

public sealed class PriceSignal {
    public data class Reliable(val price: Int, val trades: Int, val volume: Long) : PriceSignal()

    public data class Unavailable(val reason: String) : PriceSignal()
}

public object ExchangePriceGuide {
    public fun volumeWeighted(
        trades: Collection<ExchangeTrade>,
        now: Long,
        settings: PriceGuideSettings,
    ): PriceSignal {
        val recent =
            trades.filter {
                it.price > 0 && it.quantity > 0 && now - it.executedAt in 0..settings.windowMillis
            }
        if (recent.isEmpty()) {
            val reason = if (trades.isEmpty()) "no trades" else "trade data is stale"
            return PriceSignal.Unavailable(reason)
        }
        val median = median(recent.map { it.price })
        val accepted =
            recent.filter { abs(it.price - median) <= median * settings.outlierTolerance }
        val volume = accepted.sumOf { it.quantity.toLong() }
        if (accepted.size < settings.minTrades || volume < settings.minVolume) {
            return PriceSignal.Unavailable(
                "insufficient volume (${accepted.size} trades, $volume items)"
            )
        }
        val value = accepted.sumOf { it.price.toDouble() * it.quantity }
        val vwap = (value / volume).roundToLong()
        if (vwap <= 0 || vwap > Int.MAX_VALUE) {
            return PriceSignal.Unavailable("invalid average price")
        }
        return PriceSignal.Reliable(vwap.toInt(), accepted.size, volume)
    }

    private fun median(values: List<Int>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid].toDouble()
        } else {
            (sorted[mid - 1].toDouble() + sorted[mid]) / 2
        }
    }
}
