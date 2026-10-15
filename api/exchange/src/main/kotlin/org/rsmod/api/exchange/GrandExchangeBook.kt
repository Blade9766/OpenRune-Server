package org.rsmod.api.exchange

public sealed class PlaceResult {
    public data class Placed(val offer: ExchangeOffer, val mutation: ExchangeMutation) :
        PlaceResult()

    public sealed class Rejected(public val message: String) : PlaceResult()

    public data object NoFreeSlot : Rejected("You have no free Grand Exchange slots.")

    public data object InvalidQuantity : Rejected("Invalid quantity.")

    public data object InvalidPrice : Rejected("Invalid price.")

    public data object ValueTooHigh : Rejected("That offer is worth too much.")

    public data object DuplicateRef : Rejected("That offer has already been placed.")
}

public data class Collectable(val offerId: Long, val items: Int, val coins: Long) {
    val isEmpty: Boolean
        get() = items == 0 && coins == 0L
}

/**
 * The in-memory order book: price-time priority, trades execute at the resting offer's price, and
 * the 2% tax is taken from the seller's proceeds. Must only be used from the game thread; every
 * call returns the [ExchangeMutation] its caller has to persist.
 */
public class GrandExchangeBook(
    private val limits: ExchangeLimits = ExchangeLimits(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val offers = LinkedHashMap<Long, ExchangeOffer>()
    private val trades = HashMap<Int, ArrayDeque<ExchangeTrade>>()
    private var nextOfferId = 1L
    private var nextTradeId = 1L

    public fun load(offers: Collection<ExchangeOffer>, trades: Collection<ExchangeTrade>) {
        this.offers.clear()
        this.trades.clear()
        for (offer in offers.sortedBy { it.id }) {
            nextOfferId = maxOf(nextOfferId, offer.id + 1)
            if (offer.occupiesSlot) {
                this.offers[offer.id] = offer
            }
        }
        for (trade in trades.sortedBy { it.id }) {
            nextTradeId = maxOf(nextTradeId, trade.id + 1)
            appendTrade(trade)
        }
    }

    public fun reserveIds(minOfferId: Long, minTradeId: Long) {
        nextOfferId = maxOf(nextOfferId, minOfferId)
        nextTradeId = maxOf(nextTradeId, minTradeId)
    }

    public fun offer(id: Long): ExchangeOffer? = offers[id]

    public fun offers(owner: ExchangeOwner): List<ExchangeOffer> =
        offers.values.filter { it.owner == owner }.sortedBy { it.slot }

    public fun allOffers(): Collection<ExchangeOffer> = offers.values

    public fun findByRef(owner: ExchangeOwner, clientRef: String): ExchangeOffer? =
        offers.values.firstOrNull { it.owner == owner && it.clientRef == clientRef }

    public fun freeSlot(owner: ExchangeOwner): Int? {
        val used = offers.values.filter { it.owner == owner }.mapTo(HashSet()) { it.slot }
        return (0 until limits.slotsPerOwner).firstOrNull { it !in used }
    }

    /** Lowest price any other owner is currently asking for [itemId]. */
    public fun bestAsk(itemId: Int, excluding: ExchangeOwner? = null): Int? =
        offers.values
            .filter { it.isOpen && it.side == OfferSide.Sell && it.itemId == itemId }
            .filter { it.owner != excluding }
            .minOfOrNull { it.price }

    public fun bestBid(itemId: Int, excluding: ExchangeOwner? = null): Int? =
        offers.values
            .filter { it.isOpen && it.side == OfferSide.Buy && it.itemId == itemId }
            .filter { it.owner != excluding }
            .maxOfOrNull { it.price }

    public fun recentTrades(itemId: Int): List<ExchangeTrade> = trades[itemId]?.toList().orEmpty()

    /**
     * Places an offer. The caller must already have removed the escrow (items for a sell,
     * `quantity * price` coins for a buy) from the owner in the same game tick.
     */
    public fun place(
        owner: ExchangeOwner,
        side: OfferSide,
        itemId: Int,
        quantity: Int,
        price: Int,
        clientRef: String? = null,
    ): PlaceResult {
        if (quantity <= 0 || quantity > limits.maxQuantity) {
            return PlaceResult.InvalidQuantity
        }
        if (price <= 0) {
            return PlaceResult.InvalidPrice
        }
        if (side == OfferSide.Buy && quantity.toLong() * price > limits.maxOfferValue) {
            return PlaceResult.ValueTooHigh
        }
        if (clientRef != null && findByRef(owner, clientRef) != null) {
            return PlaceResult.DuplicateRef
        }
        val slot = freeSlot(owner) ?: return PlaceResult.NoFreeSlot
        val now = clock()
        val offer =
            ExchangeOffer(
                id = nextOfferId++,
                owner = owner,
                slot = slot,
                itemId = itemId,
                side = side,
                quantity = quantity,
                price = price,
                clientRef = clientRef,
                createdAt = now,
            )
        offers[offer.id] = offer
        val touched = LinkedHashMap<Long, ExchangeOffer>()
        touched[offer.id] = offer
        val newTrades = match(offer, touched, now)
        val mutation = ExchangeMutation(touched.values.map { it.snapshot() }, newTrades)
        return PlaceResult.Placed(offer, mutation)
    }

    private fun match(
        incoming: ExchangeOffer,
        touched: MutableMap<Long, ExchangeOffer>,
        now: Long,
    ): List<ExchangeTrade> {
        val candidates =
            offers.values
                .asSequence()
                .filter { it.isOpen && it.itemId == incoming.itemId && it.side != incoming.side }
                .filter { it.owner != incoming.owner && it.id != incoming.id }
                .filter {
                    if (incoming.side == OfferSide.Buy) {
                        it.price <= incoming.price
                    } else {
                        it.price >= incoming.price
                    }
                }
                .sortedWith(
                    if (incoming.side == OfferSide.Buy) {
                        compareBy<ExchangeOffer> { it.price }.thenBy { it.id }
                    } else {
                        compareByDescending<ExchangeOffer> { it.price }.thenBy { it.id }
                    }
                )
                .toList()
        val executed = ArrayList<ExchangeTrade>()
        for (resting in candidates) {
            if (incoming.remaining == 0) {
                break
            }
            val quantity = minOf(incoming.remaining, resting.remaining)
            val buy = if (incoming.side == OfferSide.Buy) incoming else resting
            val sell = if (incoming.side == OfferSide.Sell) incoming else resting
            fill(buy, sell, quantity, resting.price, now)
            touched[resting.id] = resting
            val trade =
                ExchangeTrade(
                    id = nextTradeId++,
                    itemId = incoming.itemId,
                    quantity = quantity,
                    price = resting.price,
                    buyOfferId = buy.id,
                    sellOfferId = sell.id,
                    executedAt = now,
                )
            appendTrade(trade)
            executed += trade
        }
        return executed
    }

    private fun fill(buy: ExchangeOffer, sell: ExchangeOffer, quantity: Int, price: Int, now: Long) {
        val value = quantity.toLong() * price
        val tax = ExchangeTax.total(price, quantity)

        sell.filled += quantity
        sell.coinsTraded += value
        sell.taxPaid += tax
        sell.collectCoins += value - tax

        buy.filled += quantity
        buy.coinsTraded += value
        buy.collectItems += quantity
        buy.collectCoins += quantity.toLong() * (buy.price - price)

        for (offer in arrayOf(buy, sell)) {
            offer.updatedAt = now
            if (offer.remaining == 0) {
                offer.status = OfferStatus.Completed
            }
        }
    }

    /** Aborts an open offer; whatever is left of the escrow becomes collectable. */
    public fun abort(owner: ExchangeOwner, offerId: Long): ExchangeMutation? {
        val offer = offers[offerId] ?: return null
        if (offer.owner != owner || !offer.isOpen) {
            return null
        }
        when (offer.side) {
            OfferSide.Sell -> offer.collectItems += offer.remaining
            OfferSide.Buy -> offer.collectCoins += offer.remaining.toLong() * offer.price
        }
        offer.status = OfferStatus.Aborted
        offer.updatedAt = clock()
        return ExchangeMutation(listOf(offer.snapshot()), emptyList())
    }

    /** What could be collected right now, bounded by the space the caller has available. */
    public fun collectable(
        owner: ExchangeOwner,
        offerId: Long,
        maxItems: Int = Int.MAX_VALUE,
        maxCoins: Long = Long.MAX_VALUE,
    ): Collectable? {
        val offer = offers[offerId] ?: return null
        if (offer.owner != owner) {
            return null
        }
        val items = minOf(offer.collectItems, maxItems.coerceAtLeast(0))
        val coins = minOf(offer.collectCoins, maxCoins.coerceAtLeast(0))
        return Collectable(offerId, items, coins)
    }

    /**
     * Deducts a collection the caller has already credited to the owner. Rejects anything larger
     * than what is owed, so a repeated or stale collection can never pay out twice.
     */
    public fun confirmCollect(owner: ExchangeOwner, collected: Collectable): ExchangeMutation? {
        val offer = offers[collected.offerId] ?: return null
        if (offer.owner != owner) {
            return null
        }
        if (collected.items > offer.collectItems || collected.coins > offer.collectCoins) {
            return null
        }
        offer.collectItems -= collected.items
        offer.collectCoins -= collected.coins
        offer.updatedAt = clock()
        if (!offer.isOpen && !offer.hasCollectable) {
            offer.status = OfferStatus.Closed
            offers.remove(offer.id)
        }
        return ExchangeMutation(listOf(offer.snapshot()), emptyList())
    }

    private fun appendTrade(trade: ExchangeTrade) {
        val history = trades.getOrPut(trade.itemId) { ArrayDeque() }
        history.addLast(trade)
        while (history.size > limits.tradeHistoryPerItem) {
            history.removeFirst()
        }
    }
}
