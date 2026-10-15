package org.rsmod.content.other.bots.core

import org.rsmod.api.exchange.Collectable
import org.rsmod.api.exchange.ExchangeMutation
import org.rsmod.api.exchange.ExchangeOffer
import org.rsmod.api.exchange.ExchangeOwner
import org.rsmod.api.exchange.ExchangePriceGuide
import org.rsmod.api.exchange.GrandExchangeBook
import org.rsmod.api.exchange.OfferSide
import org.rsmod.api.exchange.PlaceResult
import org.rsmod.api.exchange.PriceGuideSettings
import org.rsmod.api.exchange.PriceSignal

/**
 * A bot's view of the exchange. Every change is buffered in [drain] so the caller can persist it in
 * the same transaction as the bot's own inventory and bank.
 */
class BotMarket(
    private val book: GrandExchangeBook,
    private val referencePrices: (Int) -> Int?,
    private val available: () -> Boolean,
) {
    private var pending = ExchangeMutation.EMPTY

    val isAvailable: Boolean
        get() = available()

    fun offers(owner: ExchangeOwner): List<ExchangeOffer> = book.offers(owner)

    fun hasOpenSale(owner: ExchangeOwner, itemId: Int): Boolean =
        book.offers(owner).any { it.isOpen && it.side == OfferSide.Sell && it.itemId == itemId }

    fun hasFreeSlot(owner: ExchangeOwner): Boolean = book.freeSlot(owner) != null

    fun sell(owner: ExchangeOwner, itemId: Int, quantity: Int, price: Int, ref: String): PlaceResult {
        val result = book.place(owner, OfferSide.Sell, itemId, quantity, price, ref)
        if (result is PlaceResult.Placed) {
            pending += result.mutation
        }
        return result
    }

    fun abort(owner: ExchangeOwner, offerId: Long): Boolean {
        val mutation = book.abort(owner, offerId) ?: return false
        pending += mutation
        return true
    }

    fun collectable(owner: ExchangeOwner, offerId: Long, maxItems: Int, maxCoins: Long): Collectable? =
        book.collectable(owner, offerId, maxItems, maxCoins)

    fun confirmCollect(owner: ExchangeOwner, collected: Collectable): Boolean {
        val mutation = book.confirmCollect(owner, collected) ?: return false
        pending += mutation
        return true
    }

    fun priceSignal(itemId: Int, settings: PriceGuideSettings, now: Long): PriceSignal =
        ExchangePriceGuide.volumeWeighted(book.recentTrades(itemId), now, settings)

    fun bestCompetingAsk(itemId: Int, owner: ExchangeOwner): Int? = book.bestAsk(itemId, owner)

    fun referencePrice(itemId: Int): Int? = referencePrices(itemId)

    fun drain(): ExchangeMutation {
        val drained = pending
        pending = ExchangeMutation.EMPTY
        return drained
    }
}
