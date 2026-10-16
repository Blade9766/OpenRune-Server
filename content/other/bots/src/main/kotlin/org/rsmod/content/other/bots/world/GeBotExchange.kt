package org.rsmod.content.other.bots.world

import org.rsmod.content.other.bots.core.BotExchange
import org.rsmod.content.other.bots.core.BotOffer
import org.rsmod.content.other.bots.core.MarketTrade
import org.rsmod.content.other.grandexchange.GeExchange
import org.rsmod.content.other.grandexchange.GeItemData
import org.rsmod.content.other.grandexchange.GeOffer
import org.rsmod.content.other.grandexchange.GeOfferType

/** Bots trading on the server's Grand Exchange book, the same one players use. */
class GeBotExchange(private val exchange: GeExchange, private val items: GeItemData) : BotExchange {
    override val available: Boolean = true

    override val saveCount: Long
        get() = exchange.saveCount

    override val hasUnsavedChanges: Boolean
        get() = exchange.dirty

    override fun offers(owner: Int): List<BotOffer> = exchange.slots(owner).mapNotNull { it?.toBotOffer() }

    override fun offer(owner: Int, slot: Int): BotOffer? = exchange.offer(owner, slot)?.toBotOffer()

    override fun freeSlot(owner: Int): Int? = exchange.firstEmptySlot(owner)

    override fun sell(owner: Int, ownerName: String, slot: Int, itemId: Int, quantity: Int, price: Int): Boolean =
        place(owner, ownerName, slot, GeOfferType.Sell, itemId, quantity, price)

    override fun buy(owner: Int, ownerName: String, slot: Int, itemId: Int, quantity: Int, price: Int): Boolean =
        place(owner, ownerName, slot, GeOfferType.Buy, itemId, quantity, price)

    private fun place(owner: Int, ownerName: String, slot: Int, type: GeOfferType, itemId: Int, quantity: Int, price: Int): Boolean =
        try {
            exchange.place(owner, ownerName, slot, type, itemId, price, quantity)
            true
        } catch (_: IllegalArgumentException) {
            false
        }

    override fun abort(owner: Int, slot: Int): Boolean = exchange.offer(owner, slot)?.let(exchange::abort) ?: false

    override fun takeItems(owner: Int, slot: Int, count: Int): Int =
        exchange.offer(owner, slot)?.let { exchange.takeItems(it, count) } ?: 0

    override fun takeCoins(owner: Int, slot: Int, count: Int): Int =
        exchange.offer(owner, slot)?.let { exchange.takeCoins(it, count) } ?: 0

    override fun recentTrades(itemId: Int): List<MarketTrade> =
        exchange.marketTrades(itemId).map { MarketTrade(it.quantity, it.price, it.time) }

    override fun lowestAsk(itemId: Int, excludingOwner: Int): Int? = exchange.lowestAsk(itemId, excludingOwner)

    override fun openAsks(excludingOwner: Int): Map<Int, Int> = bestOpen(GeOfferType.Sell, excludingOwner, ::minOf)

    override fun openBids(excludingOwner: Int): Map<Int, Int> = bestOpen(GeOfferType.Buy, excludingOwner, ::maxOf)

    private fun bestOpen(type: GeOfferType, excludingOwner: Int, better: (Int, Int) -> Int): Map<Int, Int> {
        val best = HashMap<Int, Int>()
        for (offer in exchange.allOffers()) {
            if (offer.type != type || !offer.isActive || offer.remaining <= 0 || offer.owner == excludingOwner) {
                continue
            }
            best.merge(offer.item, offer.price, better)
        }
        return best
    }

    override fun remainingBuyLimit(owner: Int, itemId: Int): Int = exchange.remainingBuyLimit(owner, itemId)

    override fun guidePrice(itemId: Int): Int? = items.entry(itemId)?.guidePrice?.takeIf { it > 0 }

    override fun taxPerItem(itemId: Int, price: Int): Int = items.taxPerItem(itemId, price)

    private fun GeOffer.toBotOffer(): BotOffer =
        BotOffer(
            slot = slot,
            itemId = item,
            sell = type == GeOfferType.Sell,
            price = price,
            total = total,
            completed = completed,
            collectItems = collectItems,
            collectCoins = collectCoins,
            finished = finished,
            createdAt = createdAt,
            gold = gold.toLong(),
            taxPaid = taxPaid.toLong(),
        )
}
