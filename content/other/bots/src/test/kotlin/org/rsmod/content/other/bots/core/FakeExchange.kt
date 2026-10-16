package org.rsmod.content.other.bots.core

/**
 * The same book semantics as the server's `GeExchange` (8 slots per owner, trades at the resting
 * offer's price, 2% seller fee, collect boxes that keep a finished offer in its slot until emptied)
 * with an explicit [save] so tests can crash between a bot write and an exchange save.
 */
class FakeExchange(private val clock: () -> Long) : BotExchange {
    class Offer(
        val owner: Int,
        val slot: Int,
        val sell: Boolean,
        val item: Int,
        val price: Int,
        val total: Int,
        val createdAt: Long,
        var completed: Int = 0,
        var gold: Long = 0,
        var tax: Long = 0,
        var collectItems: Int = 0,
        var collectCoins: Int = 0,
        var finished: Boolean = false,
    ) {
        fun copy(): Offer =
            Offer(owner, slot, sell, item, price, total, createdAt, completed, gold, tax, collectItems, collectCoins, finished)

        fun view(): BotOffer =
            BotOffer(slot, item, sell, price, total, completed, collectItems, collectCoins, finished, createdAt, gold, tax)
    }

    private var offers = ArrayList<Offer>()
    private var trades = ArrayList<Pair<Int, MarketTrade>>()
    private var savedOffers: List<Offer> = emptyList()
    private var savedTrades: List<Pair<Int, MarketTrade>> = emptyList()
    private var lastCreatedAt = 0L

    override var available: Boolean = true
    override var saveCount: Long = 0
        private set

    var guide: Int? = 100
    var refuseSells: Boolean = false
    var refuseBuys: Boolean = false

    fun save() {
        savedOffers = offers.map { it.copy() }
        savedTrades = trades.toList()
        saveCount++
    }

    /** Throws away everything since the last [save], as a crash would. */
    fun restoreFromSave() {
        offers = ArrayList(savedOffers.map { it.copy() })
        trades = ArrayList(savedTrades)
    }

    fun raw(owner: Int): List<Offer> = offers.filter { it.owner == owner }

    override fun offers(owner: Int): List<BotOffer> = raw(owner).sortedBy { it.slot }.map { it.view() }

    override fun freeSlot(owner: Int): Int? {
        val used = raw(owner).mapTo(HashSet()) { it.slot }
        return (0 until 8).firstOrNull { it !in used }
    }

    override fun sell(owner: Int, ownerName: String, slot: Int, itemId: Int, quantity: Int, price: Int): Boolean =
        !refuseSells && place(owner, slot, true, itemId, quantity, price) != null

    override fun buy(owner: Int, ownerName: String, slot: Int, itemId: Int, quantity: Int, price: Int): Boolean =
        !refuseBuys && place(owner, slot, false, itemId, quantity, price) != null

    fun place(owner: Int, slot: Int, sell: Boolean, item: Int, quantity: Int, price: Int): Offer? {
        if (!available || raw(owner).any { it.slot == slot } || quantity <= 0 || price <= 0) {
            return null
        }
        val createdAt = maxOf(clock(), lastCreatedAt + 1)
        lastCreatedAt = createdAt
        val offer = Offer(owner, slot, sell, item, price, quantity, createdAt)
        offers += offer
        match(offer)
        return offer
    }

    /** A player buy offer; returns the offer so tests can check what the buyer received. */
    fun playerBuys(owner: Int, item: Int, quantity: Int, price: Int): Offer =
        checkNotNull(place(owner, freeSlot(owner)!!, false, item, quantity, price))

    /** A player sell offer resting on the book for bots to buy from. */
    fun playerSells(owner: Int, item: Int, quantity: Int, price: Int): Offer =
        checkNotNull(place(owner, freeSlot(owner)!!, true, item, quantity, price))

    private fun match(incoming: Offer) {
        val candidates =
            offers
                .filter { it !== incoming && !it.finished && it.item == incoming.item && it.sell != incoming.sell }
                .filter { if (incoming.sell) it.price >= incoming.price else it.price <= incoming.price }
                .sortedWith(
                    if (incoming.sell) compareBy<Offer>({ -it.price }, { it.createdAt }) else compareBy({ it.price }, { it.createdAt })
                )
        for (resting in candidates) {
            val remaining = incoming.total - incoming.completed
            if (remaining <= 0) {
                break
            }
            val quantity = minOf(remaining, resting.total - resting.completed)
            val buy = if (incoming.sell) resting else incoming
            val sell = if (incoming.sell) incoming else resting
            val price = resting.price
            val gross = quantity.toLong() * price
            val fee = taxPerItem(sell.item, price).toLong() * quantity
            buy.completed += quantity
            buy.gold += gross
            buy.collectItems += quantity
            buy.collectCoins += quantity * (buy.price - price)
            sell.completed += quantity
            sell.gold += gross
            sell.tax += fee
            sell.collectCoins += (gross - fee).toInt()
            trades += sell.item to MarketTrade(quantity, price, clock())
            for (offer in listOf(buy, sell)) {
                if (offer.completed >= offer.total) {
                    offer.finished = true
                }
            }
        }
    }

    override fun abort(owner: Int, slot: Int): Boolean {
        val offer = raw(owner).firstOrNull { it.slot == slot && !it.finished } ?: return false
        val remaining = offer.total - offer.completed
        if (offer.sell) offer.collectItems += remaining else offer.collectCoins += remaining * offer.price
        offer.finished = true
        release(offer)
        return true
    }

    override fun takeItems(owner: Int, slot: Int, count: Int): Int {
        val offer = raw(owner).firstOrNull { it.slot == slot } ?: return 0
        val taken = count.coerceIn(0, offer.collectItems)
        offer.collectItems -= taken
        release(offer)
        return taken
    }

    override fun takeCoins(owner: Int, slot: Int, count: Int): Int {
        val offer = raw(owner).firstOrNull { it.slot == slot } ?: return 0
        val taken = count.coerceIn(0, offer.collectCoins)
        offer.collectCoins -= taken
        release(offer)
        return taken
    }

    private fun release(offer: Offer) {
        if (offer.finished && offer.collectItems == 0 && offer.collectCoins == 0) {
            offers.remove(offer)
        }
    }

    override fun recentTrades(itemId: Int): List<MarketTrade> = trades.filter { it.first == itemId }.map { it.second }

    override fun lowestAsk(itemId: Int, excludingOwner: Int): Int? =
        offers.filter { it.sell && !it.finished && it.item == itemId && it.owner != excludingOwner }.minOfOrNull { it.price }

    override fun guidePrice(itemId: Int): Int? = guide

    override fun taxPerItem(itemId: Int, price: Int): Int = minOf(price.toLong() * 20 / 1000, 5_000_000L).toInt()
}
