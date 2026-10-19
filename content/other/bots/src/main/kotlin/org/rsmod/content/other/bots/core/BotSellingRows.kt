package org.rsmod.content.other.bots.core

import kotlin.math.roundToInt

/** A product a bot can sell: its gameval name and the name players see. */
data class SaleProduct(val key: String, val name: String)

/**
 * The Selling tab of a bot's page in the Bot Manager: which product it sells and how it prices
 * its exchange offers. Everything here only matters while selling is on, so the rows are
 * unavailable otherwise and say why.
 */
object BotSellingRows {
    const val BATCH_STEP: Int = 100
    const val PERCENT_STEP: Double = 0.5
    const val DEVIATION_STEP: Double = 5.0
    const val MINUTES_STEP: Int = 15
    const val WINDOW_STEP: Int = 60
    const val VOLUME_STEP: Long = 100
    const val RESTOCK_STEP: Int = 100

    fun of(config: BotConfig, site: SiteCapabilities, products: List<SaleProduct>): List<SettingRow> {
        val off =
            when {
                !site.canSell -> "Its site has no route to the Grand Exchange."
                !config.sale.enabled -> "Turn selling on (Behaviour tab) first."
                else -> null
            }
        val sale = config.sale
        val pricing = config.pricing
        return listOf(
            saleItem(config, products, off),
            SettingRow(
                "Batch size",
                "${sale.quantity}",
                "How many it lists per trip to the exchange; never more than its Sell at amount.",
                stepper(sale.quantity, BATCH_STEP, BATCH_STEP..sale.threshold) { c, v -> c.copy(sale = c.sale.copy(quantity = v)) },
                off,
            ),
            SettingRow(
                "Reference price",
                pricing.referencePrice?.let { "$it gp" } ?: "market",
                "Price it bases offers on. Market uses recent exchange trades and the guide price; a number fixes it. Enter 0 for market.",
                SettingControl.Input("Set...", "Reference price in gp (0 = market):") { c, v ->
                    c.copy(pricing = c.pricing.copy(referencePrice = v.takeIf { it > 0 }))
                },
                off,
            ),
            SettingRow(
                "Price adjustment",
                percent(pricing.adjustmentPercent),
                "Added to the reference price; a small negative value undercuts other sellers.",
                decimalStepper(pricing.adjustmentPercent, PERCENT_STEP, -50.0, 50.0) { c, v -> c.copy(pricing = c.pricing.copy(adjustmentPercent = v)) },
                off,
            ),
            SettingRow(
                "Minimum price",
                "${pricing.minPrice} gp",
                "Lowest price per item it accepts, after the exchange's tax.",
                SettingControl.Input("Set...", "Minimum price per item in gp:") { c, v -> c.copy(pricing = c.pricing.copy(minPrice = v)) },
                off,
            ),
            SettingRow(
                "Max deviation",
                percent(pricing.maxDeviationPercent, signed = false),
                "Trade averages further than this from the reference are ignored as unreliable.",
                decimalStepper(pricing.maxDeviationPercent, DEVIATION_STEP, DEVIATION_STEP, 100.0) { c, v -> c.copy(pricing = c.pricing.copy(maxDeviationPercent = v)) },
                off,
            ),
            SettingRow(
                "Listing timeout",
                minutes(pricing.listingTimeoutMinutes),
                "An offer that has not sold by then is taken down and listed again a little cheaper.",
                stepper(pricing.listingTimeoutMinutes, MINUTES_STEP, MINUTES_STEP..DAY_MINUTES) { c, v -> c.copy(pricing = c.pricing.copy(listingTimeoutMinutes = v)) },
                off,
            ),
            SettingRow(
                "Re-list retries",
                "${pricing.maxRetries}",
                "How many times it re-lists cheaper before pausing sales.",
                stepper(pricing.maxRetries, 1, 0..20) { c, v -> c.copy(pricing = c.pricing.copy(maxRetries = v)) },
                off,
            ),
            SettingRow(
                "Retry price cut",
                percent(pricing.retryStepPercent, signed = false),
                "How much cheaper each re-listing is.",
                decimalStepper(pricing.retryStepPercent, PERCENT_STEP, 0.0, 25.0) { c, v -> c.copy(pricing = c.pricing.copy(retryStepPercent = v)) },
                off,
            ),
            SettingRow(
                "Pause after giving up",
                minutes(pricing.cooldownMinutes),
                "How long selling pauses when it runs out of retries or has no safe price.",
                stepper(pricing.cooldownMinutes, MINUTES_STEP, MINUTES_STEP..DAY_MINUTES) { c, v -> c.copy(pricing = c.pricing.copy(cooldownMinutes = v)) },
                off,
            ),
            SettingRow(
                "Trade data window",
                minutes(pricing.windowMinutes),
                "How far back recent exchange trades count towards the market price.",
                stepper(pricing.windowMinutes, WINDOW_STEP, WINDOW_STEP..DAY_MINUTES) { c, v -> c.copy(pricing = c.pricing.copy(windowMinutes = v)) },
                off,
            ),
            SettingRow(
                "Min trades",
                "${pricing.minTrades}",
                "Fewest recent trades before it trusts the market price over the guide price.",
                stepper(pricing.minTrades, 1, 1..50) { c, v -> c.copy(pricing = c.pricing.copy(minTrades = v)) },
                off,
            ),
            SettingRow(
                "Min volume",
                "${pricing.minVolume}",
                "Fewest items traded recently before it trusts the market price.",
                longStepper(pricing.minVolume, VOLUME_STEP, 0L..1_000_000L) { c, v -> c.copy(pricing = c.pricing.copy(minVolume = v)) },
                off,
            ),
            SettingRow(
                "Restock batch",
                "${config.restock.quantity}",
                "How many supplies it buys at a time when its bank runs out.",
                stepper(config.restock.quantity, RESTOCK_STEP, RESTOCK_STEP..10_000) { c, v -> c.copy(restock = c.restock.copy(quantity = v)) },
                when {
                    !site.usesSupplies -> "This bot's work uses no supplies."
                    !site.canSell -> "Its site has no route to the Grand Exchange."
                    !config.restock.enabled -> "Turn restock on (Behaviour tab) first."
                    else -> null
                },
            ),
        )
    }

    private fun saleItem(config: BotConfig, products: List<SaleProduct>, off: String?): SettingRow {
        val current = products.indexOfFirst { it.key == config.sale.item }
        val next = products.getOrNull((current + 1) % products.size.coerceAtLeast(1))
        val name = products.getOrNull(current)?.name ?: config.sale.item.removePrefix("obj.").ifEmpty { "none" }
        return SettingRow(
            "Sells",
            name,
            "The product it takes to the exchange. Next switches to the site's next product.",
            SettingControl.Toggle(
                "Next",
                next?.takeIf { products.size > 1 }?.let { product ->
                    { c -> c.copy(sale = c.sale.copy(item = product.key, allowedItems = c.sale.allowedItems + product.key)) }
                },
            ),
            off ?: if (products.size < 2) "Its site has only one product." else null,
        )
    }

    private fun stepper(value: Int, step: Int, range: IntRange, set: (BotConfig, Int) -> BotConfig): SettingControl.Stepper =
        SettingControl.Stepper(
            down = if (value > range.first) { c -> set(c, maxOf(range.first, value - step)) } else null,
            up = if (value < range.last) { c -> set(c, minOf(range.last, value + step)) } else null,
        )

    private fun longStepper(value: Long, step: Long, range: LongRange, set: (BotConfig, Long) -> BotConfig): SettingControl.Stepper =
        SettingControl.Stepper(
            down = if (value > range.first) { c -> set(c, maxOf(range.first, value - step)) } else null,
            up = if (value < range.last) { c -> set(c, minOf(range.last, value + step)) } else null,
        )

    private fun decimalStepper(value: Double, step: Double, min: Double, max: Double, set: (BotConfig, Double) -> BotConfig): SettingControl.Stepper =
        SettingControl.Stepper(
            down = if (value > min) { c -> set(c, round(maxOf(min, value - step))) } else null,
            up = if (value < max) { c -> set(c, round(minOf(max, value + step))) } else null,
        )

    private fun round(value: Double): Double = (value * 10).roundToInt() / 10.0

    private fun percent(value: Double, signed: Boolean = true): String {
        val text = if (value == value.toLong().toDouble()) "${value.toLong()}" else "$value"
        return "${if (signed && value > 0) "+" else ""}$text%"
    }

    private fun minutes(value: Int): String =
        when {
            value % 60 == 0 -> "${value / 60} h"
            value > 60 -> "${value / 60} h ${value % 60} min"
            else -> "$value min"
        }

    private const val DAY_MINUTES = 24 * 60
}
