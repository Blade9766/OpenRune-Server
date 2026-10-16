package org.rsmod.content.other.bots.meister

import kotlin.random.Random
import org.rsmod.content.other.bots.core.BotExchange
import org.rsmod.content.other.bots.core.BotOffer
import org.rsmod.content.other.bots.core.PendingCollect
import org.rsmod.content.other.bots.core.PendingListing
import org.rsmod.content.other.bots.core.PriceGuide
import org.rsmod.content.other.bots.core.PriceGuideSettings
import org.rsmod.content.other.bots.core.PriceSignal
import org.rsmod.content.other.bots.core.SavedCoords

interface MeisterLog {
    fun info(message: String)

    fun warn(message: String)
}

enum class MeisterStatus(val label: String) {
    NotSetUp("Not set up"),
    Idle("Idle"),
    Buying("Buying"),
    Selling("Selling"),
    Paused("Trading Paused"),
}

/** Something the world layer may show: he walks to a booth when he places an offer. */
sealed interface MeisterEvent {
    data class OfferPlaced(val buy: Boolean, val itemName: String) : MeisterEvent

    data class Traded(val buy: Boolean, val itemName: String, val quantity: Int) : MeisterEvent
}

data class MeisterDecision(val time: Long, val text: String)

/** What a live offer has already been counted as, so each fill is logged and tallied once. */
data class CountedFill(val completed: Int = 0, val gold: Long = 0, val tax: Long = 0, val closed: Boolean = false)

/**
 * Master Meister's trading brain: a ledger of cash and items plus the decisions that move them
 * through the Grand Exchange.
 *
 * His wealth lives in this ledger, not in a player inventory, and only moves through the same
 * book players trade on: a purchase debits his cash before the offer exists, a sale debits his
 * items, and proceeds come back only by collecting the offer's box. Each such movement is done in
 * the gathering bots' two steps - the ledger change and a [PendingListing] / [PendingCollect] are
 * saved first, the exchange is changed on a later tick, and the pending entry is cleared once the
 * exchange has saved too - so a crash between the two files neither duplicates nor loses anything.
 * [recovering] marks a trader built from a save loaded at startup, when the exchange's file decides
 * what really happened.
 *
 * He keeps at most one offer per item, buy or sell, so his offers can never meet each other.
 */
class MeisterTrader(
    save: MeisterSave,
    private val catalog: MeisterCatalog,
    private val exchange: BotExchange,
    private val log: MeisterLog,
    private val storeHealthy: () -> Boolean,
    private var recovering: Boolean,
    private val random: Random = Random.Default,
    val owner: Int = OWNER_ID,
) {
    private var initialized = save.initialized
    private var initializedAt = save.initializedAt
    private var startingCash = save.startingCash
    private var allocation: Map<Int, Int> = save.allocation
    private val holdings = HashMap<Int, Int>(save.holdings.filterValues { it > 0 })
    private var pendingListing: PendingListing? = save.pendingListing
    private var pendingCollects: List<PendingCollect> = save.pendingCollects
    private val fills = HashMap(save.fillsCounted)
    private val cooldowns = HashMap(save.itemCooldowns)
    private val lastBought = HashMap(save.lastBought)
    private val lastSold = HashMap(save.lastSold)
    private val spentByItem = HashMap(save.spentTodayByItem)
    private var dayStart = save.dayStart
    private var spentToday = save.spentToday
    private var lastReplenishAt = save.lastReplenishAt
    private var nextDecisionAt = 0L
    private var now = 0L
    private val decisions = ArrayDeque<MeisterDecision>()
    private val sightings = HashMap<Long, Sighting>()
    private val pendingEvents = ArrayDeque<MeisterEvent>()

    var cash: Long = save.cash.coerceIn(0, MAX_CASH)
        private set

    var settings: MeisterSettings = save.settings
        private set

    var stats: MeisterStats = save.stats
        private set

    var coords: SavedCoords? = save.coords

    var status: MeisterStatus = if (save.initialized) MeisterStatus.Idle else MeisterStatus.NotSetUp
        private set

    /** Set when the ledger changed and should be saved soon. */
    var dirty: Boolean = false

    /** Set when the ledger must be saved before the exchange is touched again. */
    var urgent: Boolean = false

    val isInitialized: Boolean
        get() = initialized

    val trace: List<MeisterDecision>
        get() = decisions.toList()

    val hasPendingExchangeWork: Boolean
        get() = pendingListing != null || pendingCollects.isNotEmpty()

    fun holding(itemId: Int): Int = holdings[itemId] ?: 0

    fun holdingsView(): Map<Int, Int> = HashMap(holdings)

    fun itemName(itemId: Int): String = catalog.name(itemId)

    fun guideOf(itemId: Int): Long = catalog[itemId]?.guidePrice?.toLong() ?: 0L

    fun offers(): List<BotOffer> = exchange.offers(owner)

    fun drainEvents(): List<MeisterEvent> = pendingEvents.toList().also { pendingEvents.clear() }

    /**
     * Gives him his starting cash and one [MeisterItem.allocation] of every catalogued item. Does
     * nothing once a save with the allocation exists, so restarts never add to it.
     */
    fun initialize(startingCash: Long, now: Long): Boolean {
        if (initialized) {
            return false
        }
        val cashGrant = startingCash.coerceIn(0, MAX_CASH)
        val granted = catalog.items.filter { it.allocation > 0 }.associate { it.id to it.allocation }
        holdings.clear()
        holdings.putAll(granted)
        allocation = granted
        cash = cashGrant
        this.startingCash = cashGrant
        initialized = true
        initializedAt = now
        dayStart = now
        status = MeisterStatus.Idle
        urgent = true
        val units = granted.values.sumOf { it.toLong() }
        log.info("allocation: ${formatCoins(cashGrant)} coins and $units items of ${granted.size} kinds")
        remember(now, "Received his starting allocation")
        return true
    }

    fun start(now: Long) {
        this.now = now
        scheduleNextDecision(now, first = true)
    }

    fun tick(now: Long) {
        this.now = now
        if (!initialized) {
            return
        }
        settle()
        if (!exchange.available || !storeHealthy()) {
            return
        }
        collect()
        abortStaleOffers()
        rollDay()
        replenish()
        updateStatus()
        if (now >= nextDecisionAt) {
            decide()
            scheduleNextDecision(now, first = false)
        }
    }

    fun updateSettings(transform: (MeisterSettings) -> MeisterSettings): String? {
        val changed = transform(settings)
        val errors = changed.validate()
        if (errors.isNotEmpty()) {
            return errors.joinToString("; ")
        }
        if (changed.decisionIntervalSeconds != settings.decisionIntervalSeconds) {
            nextDecisionAt = minOf(nextDecisionAt, now + changed.decisionIntervalSeconds * 1000L)
        }
        settings = changed
        updateStatus()
        dirty = true
        log.info("settings changed: $changed")
        return null
    }

    /** Cancels every open offer; what they still hold is collected over the next ticks. */
    fun cancelAll(reason: String): Int {
        var cancelled = 0
        for (offer in exchange.offers(owner)) {
            if (offer.isOpen && exchange.abort(owner, offer.slot)) {
                cancelled++
                cooldown(offer.itemId)
                log.info("cancelled ${describe(offer)} ($reason)")
            }
        }
        if (cancelled > 0) {
            stats = stats.copy(offersCancelled = stats.offersCancelled + cancelled)
            remember(now, "Cancelled $cancelled offer(s): $reason")
            dirty = true
        }
        return cancelled
    }

    fun snapshot(): MeisterSave =
        MeisterSave(
            initialized = initialized,
            initializedAt = initializedAt,
            cash = cash,
            holdings = HashMap(holdings),
            allocation = allocation,
            startingCash = startingCash,
            settings = settings,
            pendingListing = pendingListing,
            pendingCollects = pendingCollects,
            fillsCounted = HashMap(fills),
            itemCooldowns = HashMap(cooldowns),
            lastBought = HashMap(lastBought),
            lastSold = HashMap(lastSold),
            dayStart = dayStart,
            spentToday = spentToday,
            spentTodayByItem = HashMap(spentByItem),
            lastReplenishAt = lastReplenishAt,
            stats = stats,
            coords = coords,
        )

    /** Coins tied up in open buy offers and an unconfirmed buy, which count against the budgets. */
    fun committedCoins(itemId: Int? = null): Long {
        val open =
            exchange.offers(owner)
                .filter { !it.sell && it.isOpen && (itemId == null || it.itemId == itemId) }
                .sumOf { it.remaining.toLong() * it.price }
        val pending = pendingListing?.takeIf { it.buy && !confirmedInBook(it) && (itemId == null || it.itemId == itemId) }?.cost ?: 0L
        return open + pending
    }

    val spentTodayCoins: Long
        get() = spentToday

    /* Settling exchange work */

    private fun settle() {
        settleListing()
        settleCollects()
        countFills()
        recovering = false
    }

    private fun confirmedInBook(pending: PendingListing): Boolean {
        val offer = exchange.offer(owner, pending.slot) ?: return false
        return offer.sell != pending.buy && offer.itemId == pending.itemId &&
            offer.total == pending.quantity && offer.price == pending.price
    }

    private fun settleListing() {
        val pending = pendingListing ?: return
        val ours = confirmedInBook(pending)
        when {
            ours && (recovering || exchange.saveCount > pending.placedAtSave) -> {
                pendingListing = null
                exchange.offer(owner, pending.slot)?.let { fills.putIfAbsent(it.createdAt, CountedFill()) }
                stats = stats.copy(offersPlaced = stats.offersPlaced + 1)
                log.info("offer confirmed: ${if (pending.buy) "buy" else "sell"} ${pending.quantity} x ${catalog.name(pending.itemId)} at ${pending.price} in slot ${pending.slot}")
                urgent = true
            }
            ours -> {}
            !pending.placed && !recovering && durable() && exchange.available -> {
                val place = if (pending.buy) exchange::buy else exchange::sell
                if (place(owner, NAME, pending.slot, pending.itemId, pending.quantity, pending.price)) {
                    pendingListing = pending.copy(placed = true, placedAtSave = exchange.saveCount)
                    exchange.offer(owner, pending.slot)?.let { fills.putIfAbsent(it.createdAt, CountedFill()) }
                    pendingEvents += MeisterEvent.OfferPlaced(pending.buy, catalog.name(pending.itemId))
                    urgent = true
                } else {
                    rollBack(pending, "the exchange refused the offer")
                }
            }
            !pending.placed && !recovering && !exchange.available -> rollBack(pending, "the exchange is unavailable")
            !pending.placed && !recovering -> {}
            recovering -> rollBack(pending, "the offer never reached the exchange's save")
        }
    }

    private fun rollBack(pending: PendingListing, reason: String) {
        if (pending.buy) {
            cash = Math.addExact(cash, pending.cost)
            check(cash <= MAX_CASH) { "Refunding ${pending.cost} coins would overflow his cash." }
            log.warn("buy offer for ${pending.quantity} x ${catalog.name(pending.itemId)} rolled back ($reason); ${pending.cost} coins refunded")
        } else {
            credit(pending.itemId, pending.quantity)
            log.warn("sell offer for ${pending.quantity} x ${catalog.name(pending.itemId)} rolled back ($reason); items returned")
        }
        pendingListing = null
        cooldown(pending.itemId)
        remember(now, "Offer for ${catalog.name(pending.itemId)} rolled back: $reason")
        urgent = true
    }

    private fun settleCollects() {
        if (pendingCollects.isEmpty()) {
            return
        }
        val remaining = ArrayList<PendingCollect>()
        for (pending in pendingCollects) {
            val offer = exchange.offer(owner, pending.slot)?.takeIf { it.createdAt == pending.createdAt } ?: continue
            val coinsOwed = pending.coinsTarget - offer.coinsCollected
            val itemsOwed = pending.itemsTarget - offer.itemsCollected
            when {
                coinsOwed <= 0 && itemsOwed <= 0 ->
                    if (!recovering && exchange.saveCount <= pending.takenAtSave) {
                        remaining += pending
                    }
                (!pending.taken || recovering) && durable() -> {
                    if (coinsOwed > 0) {
                        exchange.takeCoins(owner, pending.slot, coinsOwed.toInt())
                    }
                    if (itemsOwed > 0) {
                        exchange.takeItems(owner, pending.slot, itemsOwed)
                    }
                    remaining += pending.copy(taken = true, takenAtSave = exchange.saveCount)
                }
                else -> remaining += pending
            }
        }
        if (remaining != pendingCollects) {
            pendingCollects = remaining
            urgent = true
        }
    }

    /**
     * Tallies each offer's fills once. A fill the exchange lost in a crash (its offer came back from
     * the exchange's save with fewer completed) is taken back out of the tallies.
     */
    private fun countFills() {
        val offers = exchange.offers(owner)
        for (offer in offers) {
            val counted = fills[offer.createdAt] ?: continue
            if (offer.completed == counted.completed) {
                continue
            }
            val quantity = offer.completed - counted.completed
            val gold = offer.gold - counted.gold
            val tax = offer.taxPaid - counted.tax
            val name = catalog.name(offer.itemId)
            if (offer.sell) {
                stats = stats.copy(itemsSold = stats.itemsSold + quantity, coinsEarned = stats.coinsEarned + gold - tax, taxPaid = stats.taxPaid + tax)
            } else {
                stats = stats.copy(itemsBought = stats.itemsBought + quantity, coinsSpent = stats.coinsSpent + gold)
                spentToday = (spentToday + gold).coerceAtLeast(0)
                spentByItem[offer.itemId] = ((spentByItem[offer.itemId] ?: 0) + gold).coerceAtLeast(0)
            }
            if (quantity > 0) {
                if (offer.sell) lastSold[offer.itemId] = now else lastBought[offer.itemId] = now
                log.info("${if (offer.sell) "sold" else "bought"} $quantity x $name for $gold coins ($tax tax) in slot ${offer.slot}")
                pendingEvents += MeisterEvent.Traded(!offer.sell, name, quantity)
            } else {
                log.warn("the exchange lost ${-quantity} fill(s) of ${describe(offer)} in a restart; tallies corrected")
            }
            fills[offer.createdAt] = counted.copy(completed = offer.completed, gold = offer.gold, tax = offer.taxPaid)
            dirty = true
        }
        for (offer in offers) {
            val counted = fills[offer.createdAt] ?: continue
            if (offer.finished != counted.closed) {
                fills[offer.createdAt] = counted.copy(closed = offer.finished)
                if (offer.finished) {
                    cooldown(offer.itemId)
                    log.info("offer finished: ${describe(offer)}")
                }
            }
        }
        val live = offers.mapTo(HashSet()) { it.createdAt }
        if (fills.keys.removeIf { it !in live }) {
            dirty = true
        }
    }

    /** What an offer's collect box held when he looked, and the exchange save count at that moment. */
    private class Sighting(val save: Long, val items: Int, val coins: Int)

    /**
     * Credits what his offers' collect boxes hold to the ledger and records it as pending; the
     * exchange is emptied by [settleCollects] once the credit is on disk. Only amounts the exchange
     * has saved are credited - with unsaved changes in the book, a box is looked at first and
     * collected after the exchange's next save - so a fill or cancellation the exchange loses in a
     * crash is never paid out. Coins
     * beyond the cash cap stay in the box until there is room.
     */
    private fun collect() {
        val busy = pendingCollects.mapTo(HashSet()) { it.slot }
        pendingListing?.let { busy += it.slot }
        val offers = exchange.offers(owner)
        sightings.keys.retainAll(offers.filter { it.hasCollectable }.mapTo(HashSet()) { it.createdAt })
        for (offer in offers) {
            if (!offer.hasCollectable || offer.slot in busy) {
                continue
            }
            var seen = sightings[offer.createdAt]
            if (seen == null) {
                seen = Sighting(exchange.saveCount, offer.collectItems, offer.collectCoins)
                if (exchange.hasUnsavedChanges) {
                    sightings[offer.createdAt] = seen
                    continue
                }
            } else if (exchange.saveCount <= seen.save) {
                continue
            }
            sightings.remove(offer.createdAt)
            val coins = minOf(seen.coins, offer.collectCoins).toLong().coerceAtMost(MAX_CASH - cash).coerceAtLeast(0)
            val items = minOf(seen.items, offer.collectItems, Int.MAX_VALUE - holding(offer.itemId)).coerceAtLeast(0)
            if (coins == 0L && items == 0) {
                continue
            }
            cash += coins
            credit(offer.itemId, items)
            pendingCollects =
                pendingCollects +
                    PendingCollect(
                        slot = offer.slot,
                        createdAt = offer.createdAt,
                        coinsTarget = offer.coinsCollected + coins,
                        itemsTarget = offer.itemsCollected + items,
                    )
            log.info("collected slot ${offer.slot} (${describe(offer)}): $coins coins, $items items")
            urgent = true
        }
    }

    private fun abortStaleOffers() {
        for (offer in exchange.offers(owner)) {
            if (!offer.isOpen) {
                continue
            }
            val minutes = if (offer.sell) settings.sellOfferTimeoutMinutes else settings.buyOfferTimeoutMinutes
            if (now - offer.createdAt < minutes * 60_000L || !exchange.abort(owner, offer.slot)) {
                continue
            }
            stats = stats.copy(offersCancelled = stats.offersCancelled + 1)
            cooldown(offer.itemId)
            log.info("timed out after $minutes min: ${describe(offer)}")
            remember(now, "Cancelled a stale offer for ${catalog.name(offer.itemId)}")
            dirty = true
        }
    }

    /* Decisions */

    private fun decide() {
        if (!settings.tradingEnabled) {
            return
        }
        if (pendingListing != null) {
            note("Waiting for his last offer to be confirmed")
            return
        }
        val roll = random.nextInt(100)
        when {
            roll < settings.buyChancePercent ->
                if (settings.buyingPaused) note("Wanted to buy, but buying is paused") else buy()
            roll < settings.buyChancePercent + settings.sellChancePercent ->
                if (settings.sellingPaused) note("Wanted to sell, but selling is paused") else sell()
            else -> note("Decided to do nothing this time")
        }
    }

    private class Choice(val item: MeisterItem, val price: Int, val basis: String, val score: Double, val buyerWaiting: Boolean = false)

    private fun buy() {
        val offers = exchange.offers(owner)
        val slot = freeSlot(offers) ?: return
        val busy = busyItems(offers)
        val asks = exchange.openAsks(owner)
        var unpriced = 0
        var overpriced = 0
        val listed = ArrayList<Choice>()
        for ((id, ask) in asks) {
            val item = catalog[id]?.takeIf { buyable(it, busy) } ?: continue
            val reference = buyReference(item)
            if (reference == null) {
                unpriced++
                continue
            }
            val limit = percentOf(reference, 100 + settings.maxBuyPremiumPercent)
            if (ask > limit) {
                overpriced++
                continue
            }
            val cheapness = ((reference - ask).toDouble() / reference * 4).coerceIn(0.0, 2.0)
            listed += Choice(item, ask, "lowest ask $ask, reference $reference", 1.0 + cheapness + demand(id) + scarcity(item))
        }
        val choice =
            weightedPick(listed)
                ?: standingBid(busy)
                ?: return note("Found nothing worth buying ($overpriced overpriced, $unpriced without a price)")
        if (overpriced > 0) {
            log.info("ignored $overpriced overpriced listing(s) this decision")
        }
        val quantity = buyQuantity(choice.item, choice.price, offers)
        if (quantity <= 0) {
            cooldown(choice.item.id)
            return
        }
        val cost = quantity.toLong() * choice.price
        check(cost <= cash) { "Buy of $quantity x ${choice.item.name} costs $cost but he has $cash." }
        cash -= cost
        pendingListing = PendingListing(slot, choice.item.id, quantity, choice.price, buy = true)
        status = MeisterStatus.Buying
        urgent = true
        log.info("decided to buy $quantity x ${choice.item.name} at ${choice.price} (${choice.basis}); $cost coins reserved, ${formatCoins(cash)} left")
        remember(now, "Buying $quantity x ${choice.item.name} at ${choice.price} (${choice.basis})")
    }

    /** With no seller worth buying from, he sometimes leaves a bid under the price for players to fill. */
    private fun standingBid(busy: Set<Int>): Choice? {
        val sample = catalog.items.shuffled(random).asSequence().filter { buyable(it, busy) }.take(SAMPLE_SIZE).toList()
        val choices =
            sample.mapNotNull { item ->
                val reference = buyReference(item) ?: return@mapNotNull null
                val price = percentOf(reference, 100 - settings.standingBidDiscountPercent).coerceAtLeast(1)
                Choice(item, price, "standing bid ${settings.standingBidDiscountPercent}% under reference $reference", 0.5 + demand(item.id) + scarcity(item))
            }
        return weightedPick(choices)
    }

    private fun buyQuantity(item: MeisterItem, price: Int, offers: List<BotOffer>): Int {
        val p = price.toLong()
        val limits =
            listOf(
                "cash" to cash / p,
                "spend per offer" to settings.maxSpendPerOffer / p,
                "item budget" to (settings.maxSpendPerItemPerDay - (spentByItem[item.id] ?: 0) - committedCoins(item.id)) / p,
                "daily budget" to (settings.maxSpendPerDay - spentToday - committedCoins()) / p,
                "buy limit" to exchange.remainingBuyLimit(owner, item.id).toLong(),
                "holding cap" to (Int.MAX_VALUE.toLong() - holding(item.id)),
                "offer value" to Int.MAX_VALUE / p,
                "max quantity" to settings.maxBuyQuantity.toLong(),
            )
        val (binding, cap) = limits.minBy { it.second }
        if (cap <= 0) {
            note("Wanted ${item.name} at $price but the $binding allows none")
            return 0
        }
        val max = cap.toInt()
        return if (max <= 1) 1 else random.nextInt(maxOf(1, max / 4), max + 1)
    }

    private fun sell() {
        val offers = exchange.offers(owner)
        val slot = freeSlot(offers) ?: return
        val busy = busyItems(offers)
        val bids = exchange.openBids(owner)
        val withBuyers =
            bids.keys.mapNotNull { id -> catalog[id]?.takeIf { holding(id) > 0 && sellable(it, busy) } }
                .mapNotNull { item -> sellChoice(item, bids[item.id])?.takeIf { it.buyerWaiting } }
        val choice =
            weightedPick(withBuyers)
                ?: weightedPick(
                    holdings.keys.shuffled(random).asSequence()
                        .mapNotNull { catalog[it]?.takeIf { item -> sellable(item, busy) } }
                        .take(SAMPLE_SIZE)
                        .mapNotNull { sellChoice(it, bids[it.id]) }
                        .toList()
                )
                ?: return note("Found nothing to sell")
        val item = choice.item
        val held = holding(item.id)
        val share = (held.toLong() * settings.maxSellFractionPercent / 100).coerceAtLeast(1)
        var quantity = minOf(settings.maxSellQuantity.toLong(), share, held.toLong(), (Int.MAX_VALUE / choice.price).toLong()).toInt()
        val netEach = (choice.price - exchange.taxPerItem(item.id, choice.price)).toLong().coerceAtLeast(1)
        val room = MAX_CASH - cash - incomingCoins(offers)
        if (quantity * netEach > room) {
            quantity = (room / netEach).toInt()
        }
        if (quantity <= 0) {
            cooldown(item.id)
            return note("Wanted to sell ${item.name}, but his cash is already at the maximum")
        }
        holdings.compute(item.id) { _, count -> (count ?: 0) - quantity }
        holdings.remove(item.id, 0)
        pendingListing = PendingListing(slot, item.id, quantity, choice.price)
        status = MeisterStatus.Selling
        urgent = true
        log.info("decided to sell $quantity of his $held x ${item.name} at ${choice.price} (${choice.basis})")
        remember(now, "Selling $quantity x ${item.name} at ${choice.price} (${choice.basis})")
    }

    private fun sellChoice(item: MeisterItem, bid: Int?): Choice? {
        val reference = sellReference(item) ?: return null
        val floor = percentOfCeil(reference, settings.minSellPercent)
        val surplus = (holding(item.id).toDouble() / maxOf(1, allocation[item.id] ?: item.allocation)).coerceIn(0.0, 3.0)
        if (bid != null && bid >= floor) {
            return Choice(item, bid, "bid $bid, reference $reference", 2.0 + surplus, buyerWaiting = true)
        }
        val price = maxOf(floor, percentOfCeil(reference, 100 + settings.sellMarkupPercent))
        return Choice(item, price, "asking ${settings.sellMarkupPercent}% over reference $reference", 0.5 + surplus)
    }

    private fun incomingCoins(offers: List<BotOffer>): Long =
        offers.sumOf { offer ->
            offer.collectCoins.toLong() + if (offer.sell && offer.isOpen) offer.remaining.toLong() * offer.price else 0L
        }

    private fun freeSlot(offers: List<BotOffer>): Int? {
        val open = offers.count { it.isOpen } + if (pendingListing != null) 1 else 0
        if (open >= settings.maxActiveOffers) {
            note("Already has $open open offer(s)")
            return null
        }
        val slot = exchange.freeSlot(owner)
        if (slot == null) {
            note("Every exchange slot is in use")
        }
        return slot
    }

    private fun busyItems(offers: List<BotOffer>): Set<Int> {
        val busy = offers.mapTo(HashSet()) { it.itemId }
        pendingListing?.let { busy += it.itemId }
        return busy
    }

    private fun buyable(item: MeisterItem, busy: Set<Int>): Boolean =
        !item.noBuy && (settings.buySensitive || !item.sensitive) && item.id !in busy && !coolingDown(item.id) &&
            !within(lastSold[item.id], settings.flipCooldownHours * HOUR_MS)

    private fun sellable(item: MeisterItem, busy: Set<Int>): Boolean =
        !item.noSell && (settings.sellSensitive || !item.sensitive) && item.id !in busy && !coolingDown(item.id) &&
            !within(lastBought[item.id], settings.flipCooldownHours * HOUR_MS)

    /** Recent trades only count when they agree with the guide price; buys use the lower of the two. */
    private fun buyReference(item: MeisterItem): Int? {
        val guide = guide(item) ?: return null
        val traded = tradedPrice(item.id, guide) ?: return guide
        return minOf(guide, traded)
    }

    /** Sales use the higher of the guide price and trusted recent trades, so he never sells cheap. */
    private fun sellReference(item: MeisterItem): Int? {
        val guide = guide(item) ?: return null
        val traded = tradedPrice(item.id, guide) ?: return guide
        return maxOf(guide, traded)
    }

    private fun guide(item: MeisterItem): Int? = (exchange.guidePrice(item.id) ?: item.guidePrice).takeIf { it > 0 }

    private fun tradedPrice(itemId: Int, guide: Int): Int? {
        val signal = PriceGuide.volumeWeighted(exchange.recentTrades(itemId), now, PRICE_SETTINGS)
        if (signal !is PriceSignal.Reliable) {
            return null
        }
        val deviation = kotlin.math.abs(signal.price - guide).toDouble() / guide * 100
        return signal.price.takeIf { deviation <= settings.maxReferenceDeviationPercent }
    }

    private fun demand(itemId: Int): Double {
        val trades = exchange.recentTrades(itemId).count { now - it.time in 0..DAY_MS }
        return (trades / 10.0).coerceAtMost(1.0)
    }

    private fun scarcity(item: MeisterItem): Double {
        val target = maxOf(1, allocation[item.id] ?: item.allocation)
        val held = holding(item.id)
        return when {
            held < target -> 1.0
            held < target * 2L -> 0.3
            else -> 0.0
        }
    }

    private fun weightedPick(choices: List<Choice>): Choice? {
        val top = choices.sortedByDescending { it.score }.take(TOP_CHOICES)
        if (top.isEmpty()) {
            return null
        }
        var roll = random.nextDouble() * top.sumOf { it.score }
        for (choice in top) {
            roll -= choice.score
            if (roll <= 0) {
                return choice
            }
        }
        return top.last()
    }

    /* Bookkeeping */

    private fun credit(itemId: Int, count: Int) {
        if (count <= 0) {
            return
        }
        val total = holding(itemId).toLong() + count
        check(total <= Int.MAX_VALUE) { "Crediting $count x item $itemId would overflow his holding." }
        holdings[itemId] = total.toInt()
    }

    private fun cooldown(itemId: Int) {
        if (settings.itemCooldownMinutes > 0) {
            cooldowns[itemId] = now + settings.itemCooldownMinutes * 60_000L
            dirty = true
        }
    }

    private fun coolingDown(itemId: Int): Boolean = (cooldowns[itemId] ?: 0) > now

    private fun within(time: Long?, window: Long): Boolean = time != null && now - time < window

    private fun rollDay() {
        if (now - dayStart < DAY_MS) {
            return
        }
        dayStart = now
        spentToday = 0
        spentByItem.clear()
        cooldowns.values.removeIf { it <= now }
        val keep = settings.flipCooldownHours * HOUR_MS
        lastBought.values.removeIf { now - it >= keep }
        lastSold.values.removeIf { now - it >= keep }
        log.info("new spending day; budgets reset")
        dirty = true
    }

    private fun replenish() {
        if (!(settings.replenishCash || settings.replenishItems) || now - lastReplenishAt < DAY_MS) {
            return
        }
        lastReplenishAt = now
        if (settings.replenishCash && cash < startingCash) {
            val added = startingCash - cash
            cash = startingCash
            log.info("allocation: refilled $added coins (replenish is on)")
        }
        if (settings.replenishItems) {
            var restored = 0
            for ((id, count) in allocation) {
                if (holding(id) == 0 && catalog[id] != null) {
                    holdings[id] = count
                    restored++
                }
            }
            if (restored > 0) {
                log.info("allocation: restored $restored item kinds he had run out of (replenish is on)")
            }
        }
        urgent = true
    }

    private fun updateStatus() {
        status =
            when {
                !initialized -> MeisterStatus.NotSetUp
                !settings.tradingEnabled -> MeisterStatus.Paused
                pendingListing?.buy == true -> MeisterStatus.Buying
                pendingListing?.buy == false -> MeisterStatus.Selling
                status == MeisterStatus.Paused || status == MeisterStatus.NotSetUp -> MeisterStatus.Idle
                else -> status
            }
    }

    private fun scheduleNextDecision(now: Long, first: Boolean) {
        val base = settings.decisionIntervalSeconds * 1000L
        val spread = base * settings.decisionJitterPercent / 100
        val wait = base + if (spread > 0) random.nextLong(-spread, spread + 1) else 0L
        nextDecisionAt = now + if (first) wait / 2 else wait
        if (!first && pendingListing == null) {
            status = if (settings.tradingEnabled) MeisterStatus.Idle else MeisterStatus.Paused
        }
    }

    private fun note(text: String) {
        remember(now, text)
        log.info("decision: $text")
    }

    private fun remember(time: Long, text: String) {
        decisions.addLast(MeisterDecision(time, text))
        while (decisions.size > TRACE_SIZE) {
            decisions.removeFirst()
        }
    }

    private fun durable(): Boolean = !urgent && storeHealthy()

    private fun describe(offer: BotOffer): String =
        "${if (offer.sell) "sell" else "buy"} ${offer.completed}/${offer.total} x ${catalog.name(offer.itemId)} at ${offer.price}"

    companion object {
        const val NAME: String = "Master Meister"

        /** His exchange owner id: negative like every server-run trader, far from any bot's id. */
        const val OWNER_ID: Int = -1_000_000_000

        /** A coin stack, and every exchange offer, is an Int. */
        const val MAX_CASH: Long = Int.MAX_VALUE.toLong()

        const val DAY_MS: Long = 24 * 60 * 60_000L
        const val HOUR_MS: Long = 60 * 60_000L
        private const val SAMPLE_SIZE = 40
        private const val TOP_CHOICES = 10
        private const val TRACE_SIZE = 20
        private val PRICE_SETTINGS = PriceGuideSettings()

        fun percentOf(value: Int, percent: Int): Int = (value.toLong() * percent / 100).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

        fun percentOfCeil(value: Int, percent: Int): Int =
            ((value.toLong() * percent + 99) / 100).coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
    }
}
