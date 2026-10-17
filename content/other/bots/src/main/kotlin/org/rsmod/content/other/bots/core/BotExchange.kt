package org.rsmod.content.other.bots.core

/** A bot's view of one of its exchange slots. */
data class BotOffer(
    val slot: Int,
    val itemId: Int,
    val sell: Boolean,
    val price: Int,
    val total: Int,
    val completed: Int,
    val collectItems: Int,
    val collectCoins: Int,
    val finished: Boolean,
    val createdAt: Long,
    val gold: Long,
    val taxPaid: Long,
    val offerId: Long = 0,
) {
    val isOpen: Boolean
        get() = !finished

    val remaining: Int
        get() = total - completed

    val hasCollectable: Boolean
        get() = collectItems > 0 || collectCoins > 0

    /** Coins (sale proceeds, or a buy offer's refunds) taken out of the collect box; never decreases. */
    val coinsCollected: Long
        get() =
            if (sell) {
                gold - taxPaid - collectCoins
            } else {
                price.toLong() * (if (finished) total else completed) - gold - collectCoins
            }

    /** Items (unsold, or bought) taken out of the collect box; never decreases. */
    val itemsCollected: Int
        get() =
            if (sell) {
                (if (finished && completed < total) total - completed else 0) - collectItems
            } else {
                completed - collectItems
            }
}

data class MarketTrade(val quantity: Int, val price: Int, val time: Long)

/**
 * The Grand Exchange as bots use it. Owners are the bot's (negative) account id. [saveCount]
 * increases every time the exchange's book reaches disk, which is how a bot knows an offer it placed
 * or a collection it made is durable.
 */
interface BotExchange {
    val available: Boolean
    val saveCount: Long

    /** True while the book has changes that have not reached disk yet. */
    val hasUnsavedChanges: Boolean
        get() = true

    fun offers(owner: Int): List<BotOffer>

    fun offer(owner: Int, slot: Int): BotOffer? = offers(owner).firstOrNull { it.slot == slot }

    fun freeSlot(owner: Int): Int?

    fun sell(owner: Int, ownerName: String, slot: Int, itemId: Int, quantity: Int, price: Int): Boolean

    fun buy(owner: Int, ownerName: String, slot: Int, itemId: Int, quantity: Int, price: Int): Boolean

    fun abort(owner: Int, slot: Int): Boolean

    fun takeItems(owner: Int, slot: Int, count: Int): Int

    fun takeCoins(owner: Int, slot: Int, count: Int): Int

    fun recentTrades(itemId: Int): List<MarketTrade>

    fun lowestAsk(itemId: Int, excludingOwner: Int): Int?

    /** The cheapest open sell offer of every item, ignoring [excludingOwner]'s offers. */
    fun openAsks(excludingOwner: Int): Map<Int, Int> = emptyMap()

    /** The highest open buy offer of every item, ignoring [excludingOwner]'s offers. */
    fun openBids(excludingOwner: Int): Map<Int, Int> = emptyMap()

    /** How many more of [itemId] [owner] may buy in the current buy-limit window. */
    fun remainingBuyLimit(owner: Int, itemId: Int): Int = Int.MAX_VALUE

    fun guidePrice(itemId: Int): Int?

    fun taxPerItem(itemId: Int, price: Int): Int
}
