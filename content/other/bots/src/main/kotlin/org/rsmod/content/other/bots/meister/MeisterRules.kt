package org.rsmod.content.other.bots.meister

enum class BuyPriceMode(val label: String, val help: String) {
    FixedCeiling("Fixed ceiling", "He bids the maximum price; the exchange fills at sellers' lower prices and refunds the rest."),
    MarketCapped("Market, capped", "He buys at the cheapest listing, or bids under the market, never above the maximum."),
}

enum class SellPriceMode(val label: String, val help: String) {
    FixedFloor("Fixed floor", "He asks the minimum price; buyers bidding more pay their own higher price."),
    MarketFloored("Market, floored", "He asks over the market price, or sells into a bid, never below the minimum."),
}

/**
 * Buy [itemId] whenever a buy check picks this rule. [maxPrice] is a ceiling per item, never a
 * target: the exchange matches against sellers' prices. He stops once he holds [maxHoldings]
 * (counting what his open bids would bring in). [dailySpend] caps coins spent on this item per
 * 24 hours on top of the global budgets (0 = only the global budgets).
 */
data class BuyRule(
    val itemId: Int = 0,
    val maxPrice: Int = 1,
    val quantity: Int = 1,
    val maxHoldings: Int = Int.MAX_VALUE,
    val cooldownMinutes: Int = 10,
    val dailySpend: Long = 0,
    val mode: BuyPriceMode = BuyPriceMode.FixedCeiling,
    val enabled: Boolean = true,
)

/** Sell [itemId], never below [minPrice] per item and never below [keepAtLeast] held. */
data class SellRule(
    val itemId: Int = 0,
    val minPrice: Int = 1,
    val quantity: Int = 1,
    val keepAtLeast: Int = 0,
    val cooldownMinutes: Int = 10,
    val mode: SellPriceMode = SellPriceMode.FixedFloor,
    val enabled: Boolean = true,
)

data class MeisterRules(val buy: List<BuyRule> = emptyList(), val sell: List<SellRule> = emptyList()) {
    fun buyRule(itemId: Int): BuyRule? = buy.firstOrNull { it.itemId == itemId }

    fun sellRule(itemId: Int): SellRule? = sell.firstOrNull { it.itemId == itemId }

    /** Problems that make the rules unsavable; [known] says whether an item id may be traded. */
    fun validate(known: (Int) -> Boolean): List<String> {
        val errors = ArrayList<String>()
        if (buy.size > MAX_RULES || sell.size > MAX_RULES) {
            errors += "at most $MAX_RULES rules per list"
        }
        for ((side, ids) in listOf("buy" to buy.map { it.itemId }, "sell" to sell.map { it.itemId })) {
            ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { errors += "item $it has two $side rules" }
            ids.filterNot(known).forEach { errors += "item $it cannot be traded on the exchange" }
        }
        for (rule in buy) {
            val at = "buy rule for item ${rule.itemId}"
            if (rule.maxPrice < 1) errors += "$at: the maximum price must be at least 1"
            if (rule.quantity < 1) errors += "$at: the offer quantity must be at least 1"
            if (rule.maxPrice.toLong() * rule.quantity > Int.MAX_VALUE) errors += "$at: price x quantity is more than one offer can hold"
            if (rule.maxHoldings < 1) errors += "$at: the holdings limit must be at least 1"
            if (rule.cooldownMinutes !in 0..MAX_COOLDOWN) errors += "$at: cooldown must be 0-$MAX_COOLDOWN minutes"
            if (rule.dailySpend < 0) errors += "$at: the daily spend limit cannot be negative"
        }
        for (rule in sell) {
            val at = "sell rule for item ${rule.itemId}"
            if (rule.minPrice < 1) errors += "$at: the minimum price must be at least 1"
            if (rule.quantity < 1) errors += "$at: the offer quantity must be at least 1"
            if (rule.minPrice.toLong() * rule.quantity > Int.MAX_VALUE) errors += "$at: price x quantity is more than one offer can hold"
            if (rule.keepAtLeast < 0) errors += "$at: the amount to keep cannot be negative"
            if (rule.cooldownMinutes !in 0..MAX_COOLDOWN) errors += "$at: cooldown must be 0-$MAX_COOLDOWN minutes"
        }
        return errors
    }

    /**
     * Items he may both buy and sell where a sale could undercut the purchase price: buying at up
     * to the ceiling and selling at the floor would lose money on every round trip.
     */
    fun lossLoops(): List<Int> =
        buy.filter { it.enabled }.mapNotNull { b ->
            val s = sellRule(b.itemId)?.takeIf { it.enabled } ?: return@mapNotNull null
            b.itemId.takeIf { s.minPrice <= b.maxPrice }
        }

    companion object {
        const val MAX_RULES: Int = 200
        const val MAX_COOLDOWN: Int = 1440
    }
}
