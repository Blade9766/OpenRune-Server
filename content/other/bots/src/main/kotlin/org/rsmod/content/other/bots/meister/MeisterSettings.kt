package org.rsmod.content.other.bots.meister

/**
 * Master Meister's trading knobs. Every value is checked by [validate] before it is stored, so an
 * administrator can never put him in a state the trader does not expect. Buying is the main
 * activity: by default about half of his buy checks place an offer and one in ten sell checks does.
 * [decisionIntervalSeconds] is the buy check interval (its saved name predates separate sell checks).
 * With [useRules] on he trades only the items on his buy and sell rule lists.
 */
data class MeisterSettings(
    val tradingEnabled: Boolean = true,
    val buyingPaused: Boolean = false,
    val sellingPaused: Boolean = false,
    val decisionIntervalSeconds: Int = 90,
    val sellIntervalSeconds: Int = 90,
    val decisionJitterPercent: Int = 50,
    val buyChancePercent: Int = 55,
    val sellChancePercent: Int = 10,
    val maxActiveOffers: Int = 4,
    val maxBuyOffers: Int = 3,
    val maxSellOffers: Int = 2,
    val useRules: Boolean = false,
    val maxBuyPremiumPercent: Int = 5,
    val standingBidDiscountPercent: Int = 6,
    val sellMarkupPercent: Int = 5,
    val minSellPercent: Int = 98,
    val maxReferenceDeviationPercent: Int = 25,
    val maxSpendPerOffer: Long = 5_000_000,
    val maxSpendPerItemPerDay: Long = 20_000_000,
    val maxSpendPerDay: Long = 100_000_000,
    val maxBuyQuantity: Int = 500,
    val maxSellQuantity: Int = 100,
    val maxSellFractionPercent: Int = 10,
    val itemCooldownMinutes: Int = 30,
    val flipCooldownHours: Int = 24,
    val buyOfferTimeoutMinutes: Int = 30,
    val sellOfferTimeoutMinutes: Int = 60,
    val buySensitive: Boolean = true,
    val sellSensitive: Boolean = false,
    val replenishCash: Boolean = false,
    val replenishItems: Boolean = false,
) {
    fun validate(): List<String> {
        val errors = ArrayList<String>()
        for (knob in MeisterKnobs.ALL) {
            val value = knob.get(this)
            if (value !in knob.range) {
                errors += "${knob.key} must be between ${knob.range.first} and ${knob.range.last}"
            }
        }
        return errors
    }
}

/** A numeric setting, described once for the `::meister set` command and the settings window. */
class MeisterKnob(
    val key: String,
    val label: String,
    val help: String,
    val range: LongRange,
    val step: Long,
    val format: (Long) -> String,
    val get: (MeisterSettings) -> Long,
    val set: (MeisterSettings, Long) -> MeisterSettings,
)

/** An on/off setting. */
class MeisterSwitch(
    val key: String,
    val label: String,
    val help: String,
    val get: (MeisterSettings) -> Boolean,
    val set: (MeisterSettings, Boolean) -> MeisterSettings,
)

object MeisterKnobs {
    private val PERCENT: (Long) -> String = { "$it%" }
    private val COINS: (Long) -> String = { formatCoins(it) }
    private val MINUTES: (Long) -> String = { "$it min" }

    val ALL: List<MeisterKnob> =
        listOf(
            MeisterKnob("interval", "Buy check every", "Seconds between buy checks, before the random variation. Timing only decides when he places offers; fills depend on the exchange's matching.", 10L..3600L, 15, { "${it}s" }, { it.decisionIntervalSeconds.toLong() }, { s, v -> s.copy(decisionIntervalSeconds = v.toInt()) }),
            MeisterKnob("sellinterval", "Sell check every", "Seconds between sell checks, before the random variation.", 10L..3600L, 15, { "${it}s" }, { it.sellIntervalSeconds.toLong() }, { s, v -> s.copy(sellIntervalSeconds = v.toInt()) }),
            MeisterKnob("jitter", "Variation", "How much each wait randomly varies around its interval, so he never trades like clockwork.", 0L..90L, 10, PERCENT, { it.decisionJitterPercent.toLong() }, { s, v -> s.copy(decisionJitterPercent = v.toInt()) }),
            MeisterKnob("buychance", "Buy chance", "Chance that a buy check places an offer; otherwise he does nothing that time.", 0L..100L, 5, PERCENT, { it.buyChancePercent.toLong() }, { s, v -> s.copy(buyChancePercent = v.toInt()) }),
            MeisterKnob("sellchance", "Sell chance", "Chance that a sell check places an offer; otherwise he does nothing that time.", 0L..100L, 5, PERCENT, { it.sellChancePercent.toLong() }, { s, v -> s.copy(sellChancePercent = v.toInt()) }),
            MeisterKnob("maxoffers", "Max offers", "Most exchange offers he keeps open at once, buys and sells together (the exchange has 8 slots).", 1L..8L, 1, { it.toString() }, { it.maxActiveOffers.toLong() }, { s, v -> s.copy(maxActiveOffers = v.toInt()) }),
            MeisterKnob("maxbuys", "Max buy offers", "Most buy offers open at once.", 0L..8L, 1, { it.toString() }, { it.maxBuyOffers.toLong() }, { s, v -> s.copy(maxBuyOffers = v.toInt()) }),
            MeisterKnob("maxsells", "Max sell offers", "Most sell offers open at once.", 0L..8L, 1, { it.toString() }, { it.maxSellOffers.toLong() }, { s, v -> s.copy(maxSellOffers = v.toInt()) }),
            MeisterKnob("premium", "Max over price", "Most he pays above the reference price for an item someone is selling.", 0L..50L, 1, PERCENT, { it.maxBuyPremiumPercent.toLong() }, { s, v -> s.copy(maxBuyPremiumPercent = v.toInt()) }),
            MeisterKnob("biddiscount", "Bid under price", "How far under the reference price he bids when nobody is selling the item yet.", 0L..50L, 1, PERCENT, { it.standingBidDiscountPercent.toLong() }, { s, v -> s.copy(standingBidDiscountPercent = v.toInt()) }),
            MeisterKnob("markup", "Sell markup", "How far above the reference price he asks when nobody is bidding high enough.", 0L..100L, 1, PERCENT, { it.sellMarkupPercent.toLong() }, { s, v -> s.copy(sellMarkupPercent = v.toInt()) }),
            MeisterKnob("minsell", "Min sell price", "He never sells below this share of the reference price.", 50L..200L, 1, PERCENT, { it.minSellPercent.toLong() }, { s, v -> s.copy(minSellPercent = v.toInt()) }),
            MeisterKnob("deviation", "Trust trades", "Recent trade prices are only used when they are within this distance of the guide price.", 0L..100L, 5, PERCENT, { it.maxReferenceDeviationPercent.toLong() }, { s, v -> s.copy(maxReferenceDeviationPercent = v.toInt()) }),
            MeisterKnob("offerspend", "Spend per offer", "Most coins one buy offer may tie up.", 1_000L..Int.MAX_VALUE.toLong(), 1_000_000, COINS, { it.maxSpendPerOffer }, { s, v -> s.copy(maxSpendPerOffer = v) }),
            MeisterKnob("itemspend", "Spend per item/day", "Most coins he spends on any single item in 24 hours.", 1_000L..MAX_DAILY_SPEND, 5_000_000, COINS, { it.maxSpendPerItemPerDay }, { s, v -> s.copy(maxSpendPerItemPerDay = v) }),
            MeisterKnob("dayspend", "Spend per day", "Most coins he spends in total in 24 hours.", 1_000L..MAX_DAILY_SPEND, 25_000_000, COINS, { it.maxSpendPerDay }, { s, v -> s.copy(maxSpendPerDay = v) }),
            MeisterKnob("buyqty", "Max buy quantity", "Most items in one buy offer.", 1L..1_000_000L, 50, { it.toString() }, { it.maxBuyQuantity.toLong() }, { s, v -> s.copy(maxBuyQuantity = v.toInt()) }),
            MeisterKnob("sellqty", "Max sell quantity", "Most items in one sell offer.", 1L..1_000_000L, 10, { it.toString() }, { it.maxSellQuantity.toLong() }, { s, v -> s.copy(maxSellQuantity = v.toInt()) }),
            MeisterKnob("sellshare", "Max sell share", "Most of his stock of one item he puts into a single sale.", 1L..100L, 5, PERCENT, { it.maxSellFractionPercent.toLong() }, { s, v -> s.copy(maxSellFractionPercent = v.toInt()) }),
            MeisterKnob("cooldown", "Item cooldown", "Minutes he leaves an item alone after an offer for it ends or fails.", 0L..1440L, 5, MINUTES, { it.itemCooldownMinutes.toLong() }, { s, v -> s.copy(itemCooldownMinutes = v.toInt()) }),
            MeisterKnob("flip", "No-flip window", "Hours after buying an item before he may sell it, and the other way round.", 0L..720L, 6, { "$it h" }, { it.flipCooldownHours.toLong() }, { s, v -> s.copy(flipCooldownHours = v.toInt()) }),
            MeisterKnob("buytimeout", "Buy offer timeout", "Minutes before an unfilled buy offer is cancelled.", 1L..1440L, 5, MINUTES, { it.buyOfferTimeoutMinutes.toLong() }, { s, v -> s.copy(buyOfferTimeoutMinutes = v.toInt()) }),
            MeisterKnob("selltimeout", "Sell offer timeout", "Minutes before an unsold sell offer is cancelled.", 1L..1440L, 5, MINUTES, { it.sellOfferTimeoutMinutes.toLong() }, { s, v -> s.copy(sellOfferTimeoutMinutes = v.toInt()) }),
        )

    val SWITCHES: List<MeisterSwitch> =
        listOf(
            MeisterSwitch("rules", "Rule lists only", "On: he trades only the enabled items on his buy and sell lists, at their prices; an empty list means no trading that way. Off: he picks from the whole market with the safeguards below.", { it.useRules }, { s, v -> s.copy(useRules = v) }),
            MeisterSwitch("trading", "Trading", "Master switch: with trading off he places no offers but still collects what his open offers bring in.", { it.tradingEnabled }, { s, v -> s.copy(tradingEnabled = v) }),
            MeisterSwitch("buying", "Buying", "Whether he places new buy offers.", { !it.buyingPaused }, { s, v -> s.copy(buyingPaused = !v) }),
            MeisterSwitch("selling", "Selling", "Whether he places new sell offers.", { !it.sellingPaused }, { s, v -> s.copy(sellingPaused = !v) }),
            MeisterSwitch("buysensitive", "Buy rare items", "Whether he buys items flagged as rare or economy-sensitive.", { it.buySensitive }, { s, v -> s.copy(buySensitive = v) }),
            MeisterSwitch("sellsensitive", "Sell rare items", "Whether he sells items flagged as rare or economy-sensitive.", { it.sellSensitive }, { s, v -> s.copy(sellSensitive = v) }),
            MeisterSwitch("replenishcash", "Refill cash", "Once a day, top his cash back up to his starting allocation. Off by default.", { it.replenishCash }, { s, v -> s.copy(replenishCash = v) }),
            MeisterSwitch("replenishitems", "Refill items", "Once a day, restore the starting allocation of items he has run out of. Off by default.", { it.replenishItems }, { s, v -> s.copy(replenishItems = v) }),
        )

    fun knob(key: String): MeisterKnob? = ALL.firstOrNull { it.key == key }

    fun switch(key: String): MeisterSwitch? = SWITCHES.firstOrNull { it.key == key }

    private const val MAX_DAILY_SPEND = 100_000_000_000L
}

fun formatCoins(value: Long): String =
    when {
        value >= 10_000_000_000L || value <= -10_000_000_000L -> "${value / 1_000_000_000}B"
        value >= 10_000_000 || value <= -10_000_000 -> "${value / 1_000_000}M"
        value >= 100_000 || value <= -100_000 -> "${value / 1_000}K"
        else -> value.toString()
    }
