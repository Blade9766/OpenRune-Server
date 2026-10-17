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
data class CountedFill(
    val completed: Int = 0,
    val gold: Long = 0,
    val tax: Long = 0,
    val closed: Boolean = false,
    /** Bumped when the exchange loses fills, so refills of the same count get new history ids. */
    val generation: Int = 0,
)

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
 *
 * Buy and sell checks run on their own timers. With [MeisterSettings.useRules] off he picks from
 * the whole market within the global safeguards; with it on he trades only the enabled items on
 * his [MeisterRules] lists, at their prices and quantities. Every settled fill goes to the
 * [history] with its weighted-average cost basis.
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
    private val history: MeisterHistorySink = InMemoryHistorySink(),
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
    private val basis = HashMap(save.basis)
    private var basisSeeded = save.basisSeeded
    private val replacements = LinkedHashMap<Int, Boolean>()
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

    var rules: MeisterRules = save.rules
        private set

    /** When the next buy check runs (epoch millis). */
    var nextBuyAt: Long = 0
        private set

    /** When the next sell check runs (epoch millis). */
    var nextSellAt: Long = 0
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

    fun catalogItem(itemId: Int): MeisterItem? = catalog[itemId]

    fun searchCatalog(text: String, limit: Int): List<MeisterItem> {
        val needle = text.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        return catalog.items
            .filter { it.name.lowercase().contains(needle) }
            .sortedWith(compareBy<MeisterItem>({ !it.name.lowercase().startsWith(needle) }, { it.name.length }, { it.name }))
            .take(limit)
    }

    fun basisOf(itemId: Int): CostBasis = basis[itemId] ?: CostBasis()

    fun historyFills(): List<MeisterFill> = history.all()

    val historyTrackingSince: Long
        get() = history.trackingSince

    /** Realized profit over all recorded sales with a known cost basis. */
    fun realizedProfit(): Long = history.all().sumOf { it.realized ?: 0L }

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
        basis.clear()
        granted.forEach { (id, count) -> basis[id] = CostBasis(unknownQuantity = count.toLong()) }
        basisSeeded = true
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
        nextBuyAt = now + wait(settings.decisionIntervalSeconds) / 2
        nextSellAt = now + wait(settings.sellIntervalSeconds) / 2
        if (initialized && !basisSeeded) {
            seedBasis()
        }
    }

    /** Saves from before the trade history: everything he owns becomes stock of unknown cost. */
    private fun seedBasis() {
        val owned = HashMap<Int, Long>()
        holdings.forEach { (id, count) -> owned.merge(id, count.toLong(), Long::plus) }
        for (offer in exchange.offers(owner)) {
            val listed = (if (offer.sell && offer.isOpen) offer.remaining else 0) + offer.collectItems
            if (listed > 0) owned.merge(offer.itemId, listed.toLong(), Long::plus)
        }
        pendingListing?.takeIf { !it.buy }?.let { owned.merge(it.itemId, it.quantity.toLong(), Long::plus) }
        basis.clear()
        owned.forEach { (id, count) -> basis[id] = CostBasis(unknownQuantity = count) }
        basisSeeded = true
        dirty = true
        log.info("cost basis started: ${owned.size} item kinds he already owned are stock of unknown cost")
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
        processReplacements()
        if (now >= nextBuyAt) {
            buyCheck()
            nextBuyAt = now + wait(settings.decisionIntervalSeconds)
        }
        if (now >= nextSellAt) {
            sellCheck()
            nextSellAt = now + wait(settings.sellIntervalSeconds)
        }
    }

    fun updateSettings(transform: (MeisterSettings) -> MeisterSettings): String? {
        val changed = transform(settings)
        val errors = changed.validate()
        if (errors.isNotEmpty()) {
            return errors.joinToString("; ")
        }
        if (changed.decisionIntervalSeconds != settings.decisionIntervalSeconds) {
            nextBuyAt = minOf(nextBuyAt, now + changed.decisionIntervalSeconds * 1000L)
        }
        if (changed.sellIntervalSeconds != settings.sellIntervalSeconds) {
            nextSellAt = minOf(nextSellAt, now + changed.sellIntervalSeconds * 1000L)
        }
        settings = changed
        updateStatus()
        dirty = true
        log.info("settings changed: $changed")
        return null
    }

    /** Replaces his rule lists; offers already on the exchange keep their old terms. */
    fun updateRules(changed: MeisterRules): String? {
        val errors = changed.validate { catalog[it] != null }
        if (errors.isNotEmpty()) {
            return errors.take(3).joinToString("; ")
        }
        rules = changed
        dirty = true
        log.info("rules changed: ${changed.buy.size} buy, ${changed.sell.size} sell")
        return null
    }

    /**
     * Cancels the offer in [slot] through the exchange; fills it already had stay his, and the rest
     * comes back through its collect box. With [replace] he places a fresh offer for the same item
     * on the current rules as soon as that box is emptied, skipping the item's cooldown.
     */
    fun cancelOffer(slot: Int, replace: Boolean, reason: String): String? {
        val offer = exchange.offer(owner, slot) ?: return "that slot is empty"
        if (!offer.isOpen) return "that offer has already finished"
        if (!exchange.abort(owner, slot)) return "the exchange refused to cancel it"
        stats = stats.copy(offersCancelled = stats.offersCancelled + 1)
        if (replace) {
            replacements[offer.itemId] = !offer.sell
        } else {
            cooldown(offer.itemId, !offer.sell)
        }
        log.info("cancelled ${describe(offer)} ($reason${if (replace) "; will replace it" else ""})")
        remember(now, "Cancelled ${catalog.name(offer.itemId)}${if (replace) " to replace it" else ""}: $reason")
        dirty = true
        return null
    }

    private fun processReplacements() {
        if (replacements.isEmpty() || pendingListing != null || !settings.tradingEnabled) return
        val busy = busyItems(exchange.offers(owner))
        val (itemId, buy) = replacements.entries.firstOrNull { it.key !in busy }?.toPair() ?: return
        replacements.remove(itemId)
        if (buy && !settings.buyingPaused) {
            if (settings.useRules) buyFromRules(only = itemId) else buy(only = itemId)
        } else if (!buy && !settings.sellingPaused) {
            if (settings.useRules) sellFromRules(only = itemId) else sell(only = itemId)
        }
    }

    /** Cancels every open offer; what they still hold is collected over the next ticks. */
    fun cancelAll(reason: String): Int {
        var cancelled = 0
        for (offer in exchange.offers(owner)) {
            if (offer.isOpen && exchange.abort(owner, offer.slot)) {
                cancelled++
                cooldown(offer.itemId, !offer.sell)
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
            rules = rules,
            basis = HashMap(basis),
            basisSeeded = basisSeeded,
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
        cooldown(pending.itemId, pending.buy)
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
            val generation =
                try {
                    recordFill(offer, counted, quantity, gold, tax)
                } catch (e: java.io.IOException) {
                    log.warn("could not write the trade history (${e.message}); the fill is recorded on a later tick")
                    continue
                }
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
            fills[offer.createdAt] = counted.copy(completed = offer.completed, gold = offer.gold, tax = offer.taxPaid, generation = generation)
            dirty = true
        }
        for (offer in offers) {
            val counted = fills[offer.createdAt] ?: continue
            if (offer.finished != counted.closed) {
                fills[offer.createdAt] = counted.copy(closed = offer.finished)
                if (offer.finished) {
                    cooldown(offer.itemId, !offer.sell)
                    log.info("offer finished: ${describe(offer)}")
                }
            }
        }
        val live = offers.mapTo(HashSet()) { it.createdAt }
        if (fills.keys.removeIf { it !in live }) {
            dirty = true
        }
    }

    /**
     * Writes one settled fill (or, for a negative [quantity], the reversal of fills the exchange
     * lost) to the history and moves the item's cost basis. The fill's id is built from the offer,
     * its completed count and the offer's reversal generation, so recounting the same fill after a
     * restart is recognised as a duplicate. The basis moves even for a duplicate: the saved basis
     * predates it. Returns the offer's generation after this fill.
     */
    private fun recordFill(offer: BotOffer, counted: CountedFill, quantity: Int, gold: Long, tax: Long): Int {
        val item = offer.itemId
        val before = basisOf(item)
        val base =
            MeisterFill(
                time = now,
                itemId = item,
                buy = !offer.sell,
                quantity = quantity,
                gross = gold,
                fee = tax,
                offerId = offer.offerId,
                slot = offer.slot,
                offerPrice = offer.price,
                offerTotal = offer.total,
                filledAfter = offer.completed,
            )
        if (quantity < 0) {
            val generation = counted.generation + 1
            history.record(
                base.copy(
                    id = "${offer.offerId}:${counted.completed}>${offer.completed}:r$generation",
                    net = if (offer.sell) gold - tax else -gold,
                    reversal = true,
                )
            )
            basis[item] =
                if (offer.sell) {
                    before.copy(unknownQuantity = before.unknownQuantity - quantity)
                } else {
                    CostBasis((before.knownQuantity + quantity).coerceAtLeast(0), (before.knownCost + gold).coerceAtLeast(0), before.unknownQuantity)
                }
            return generation
        }
        val id = "${offer.offerId}:${offer.completed}:${counted.generation}"
        val after: CostBasis
        val fill =
            if (offer.sell) {
                val sale = CostBasisBook.sell(before, quantity)
                after = sale.basis
                val net = gold - tax
                val knownNet = net * sale.knownQuantity / quantity
                base.copy(
                    id = id,
                    net = net,
                    knownQuantity = sale.knownQuantity,
                    unknownQuantity = sale.unknownQuantity,
                    costBasis = sale.cost,
                    realized = sale.cost?.let { knownNet - it },
                )
            } else {
                after = CostBasisBook.buy(before, quantity, gold)
                base.copy(id = id, net = -gold, knownQuantity = quantity)
            }
        if (!history.record(fill)) {
            log.info("fill $id was already in the history")
        }
        basis[item] = after
        return counted.generation
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
            cooldown(offer.itemId, !offer.sell)
            log.info("timed out after $minutes min: ${describe(offer)}")
            remember(now, "Cancelled a stale offer for ${catalog.name(offer.itemId)}")
            dirty = true
        }
    }

    /* Decisions */

    private fun buyCheck() {
        when {
            !settings.tradingEnabled -> return
            settings.buyingPaused -> return
            pendingListing != null -> note("Buy check skipped: waiting for his last offer to be confirmed")
            random.nextInt(100) >= settings.buyChancePercent -> note("Buy check: decided not to buy this time")
            settings.useRules -> buyFromRules(only = null)
            else -> buy(only = null)
        }
    }

    private fun sellCheck() {
        when {
            !settings.tradingEnabled -> return
            settings.sellingPaused -> return
            pendingListing != null -> note("Sell check skipped: waiting for his last offer to be confirmed")
            random.nextInt(100) >= settings.sellChancePercent -> note("Sell check: decided not to sell this time")
            settings.useRules -> sellFromRules(only = null)
            else -> sell(only = null)
        }
    }

    private class Choice(val item: MeisterItem, val price: Int, val basis: String, val score: Double, val buyerWaiting: Boolean = false)

    private fun buy(only: Int?) {
        val offers = exchange.offers(owner)
        val slot = freeSlot(offers, buy = true) ?: return
        val busy = busyItems(offers)
        val asks = exchange.openAsks(owner).filterKeys { only == null || it == only }
        var unpriced = 0
        var overpriced = 0
        val listed = ArrayList<Choice>()
        for ((id, ask) in asks) {
            val item = catalog[id]?.takeIf { buyable(it, busy, ignoreCooldown = only != null) } ?: continue
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
                ?: standingBid(busy, only)
                ?: return note("Found nothing worth buying ($overpriced overpriced, $unpriced without a price)")
        if (overpriced > 0) {
            log.info("ignored $overpriced overpriced listing(s) this decision")
        }
        val cap = buyCap(choice.item, choice.price, rule = null)
        if (cap <= 0) {
            cooldown(choice.item.id, buy = true)
            return
        }
        val quantity = if (cap <= 1) 1 else random.nextInt(maxOf(1, cap / 4), cap + 1)
        placeBuy(slot, choice.item, quantity, choice.price, choice.basis)
    }

    private fun placeBuy(slot: Int, item: MeisterItem, quantity: Int, price: Int, basis: String) {
        val cost = quantity.toLong() * price
        check(cost <= cash) { "Buy of $quantity x ${item.name} costs $cost but he has $cash." }
        cash -= cost
        pendingListing = PendingListing(slot, item.id, quantity, price, buy = true)
        status = MeisterStatus.Buying
        urgent = true
        log.info("decided to buy $quantity x ${item.name} at $price ($basis); $cost coins reserved, ${formatCoins(cash)} left")
        remember(now, "Buying $quantity x ${item.name} at $price ($basis)")
    }

    private fun buyFromRules(only: Int?) {
        val offers = exchange.offers(owner)
        val enabled = rules.buy.filter { it.enabled && (only == null || it.itemId == only) }
        if (enabled.isEmpty()) {
            if (only == null) note("Buy check: no enabled buy rules, so nothing to buy")
            return
        }
        val slot = freeSlot(offers, buy = true) ?: return
        val busy = busyItems(offers)
        val ready =
            enabled.filter { rule ->
                val item = catalog[rule.itemId]
                item != null && !item.noBuy && rule.itemId !in busy && (only != null || !coolingDown(rule.itemId)) &&
                    !within(lastSold[rule.itemId], settings.flipCooldownHours * HOUR_MS) && holdingRoom(rule, offers) > 0
            }
        if (ready.isEmpty()) {
            return note("Buy check: none of ${enabled.size} buy rule(s) is ready (cooldown, open offer, holdings limit or recent sale)")
        }
        val rule = ready[random.nextInt(ready.size)]
        val item = catalog[rule.itemId]!!
        val (price, basis) = rulePrice(rule, item)
        val cap = minOf(buyCap(item, price, rule).toLong(), holdingRoom(rule, offers), rule.quantity.toLong()).toInt()
        if (cap <= 0) {
            cooldown(item.id, buy = true)
            return
        }
        placeBuy(slot, item, cap, price, basis)
    }

    private fun rulePrice(rule: BuyRule, item: MeisterItem): Pair<Int, String> {
        if (rule.mode == BuyPriceMode.FixedCeiling) {
            return rule.maxPrice to "buy rule, ceiling ${rule.maxPrice}"
        }
        val ask = exchange.lowestAsk(item.id, owner)
        if (ask != null && ask <= rule.maxPrice) {
            return ask to "buy rule, lowest ask $ask under ceiling ${rule.maxPrice}"
        }
        val reference = buyReference(item) ?: return rule.maxPrice to "buy rule, ceiling ${rule.maxPrice} (no market price)"
        val bid = percentOf(reference, 100 - settings.standingBidDiscountPercent).coerceIn(1, rule.maxPrice)
        return bid to "buy rule, bid $bid under reference $reference, ceiling ${rule.maxPrice}"
    }

    /** How many more he may own under [rule], counting what his open bids for it would bring in. */
    private fun holdingRoom(rule: BuyRule, offers: List<BotOffer>): Long {
        val incoming = offers.filter { it.itemId == rule.itemId }.sumOf { (if (!it.sell && it.isOpen) it.remaining else 0) + it.collectItems }.toLong()
        return rule.maxHoldings.toLong() - holding(rule.itemId) - incoming
    }

    private fun sellFromRules(only: Int?) {
        val offers = exchange.offers(owner)
        val enabled = rules.sell.filter { it.enabled && (only == null || it.itemId == only) }
        if (enabled.isEmpty()) {
            if (only == null) note("Sell check: no enabled sell rules, so nothing to sell")
            return
        }
        val slot = freeSlot(offers, buy = false) ?: return
        val busy = busyItems(offers)
        val ready =
            enabled.filter { rule ->
                val item = catalog[rule.itemId]
                item != null && !item.noSell && rule.itemId !in busy && (only != null || !coolingDown(rule.itemId)) &&
                    !within(lastBought[rule.itemId], settings.flipCooldownHours * HOUR_MS) && holding(rule.itemId) > rule.keepAtLeast
            }
        if (ready.isEmpty()) {
            return note("Sell check: none of ${enabled.size} sell rule(s) is ready (cooldown, open offer, reserve or recent purchase)")
        }
        val rule = ready[random.nextInt(ready.size)]
        val item = catalog[rule.itemId]!!
        val (price, basis) = rulePrice(rule, item)
        val spare = holding(item.id) - rule.keepAtLeast
        placeSell(slot, item, minOf(rule.quantity, spare), price, basis, offers)
    }

    private fun rulePrice(rule: SellRule, item: MeisterItem): Pair<Int, String> {
        if (rule.mode == SellPriceMode.FixedFloor) {
            return rule.minPrice to "sell rule, floor ${rule.minPrice}"
        }
        val bid = exchange.openBids(owner)[item.id]
        if (bid != null && bid >= rule.minPrice) {
            return bid to "sell rule, bid $bid over floor ${rule.minPrice}"
        }
        val reference = sellReference(item) ?: return rule.minPrice to "sell rule, floor ${rule.minPrice} (no market price)"
        val ask = maxOf(rule.minPrice, percentOfCeil(reference, 100 + settings.sellMarkupPercent))
        return ask to "sell rule, asking $ask over reference $reference, floor ${rule.minPrice}"
    }

    /** With no seller worth buying from, he sometimes leaves a bid under the price for players to fill. */
    private fun standingBid(busy: Set<Int>, only: Int?): Choice? {
        val pool = if (only != null) listOfNotNull(catalog[only]) else catalog.items.shuffled(random)
        val sample = pool.asSequence().filter { buyable(it, busy, ignoreCooldown = only != null) }.take(SAMPLE_SIZE).toList()
        val choices =
            sample.mapNotNull { item ->
                val reference = buyReference(item) ?: return@mapNotNull null
                val price = percentOf(reference, 100 - settings.standingBidDiscountPercent).coerceAtLeast(1)
                Choice(item, price, "standing bid ${settings.standingBidDiscountPercent}% under reference $reference", 0.5 + demand(item.id) + scarcity(item))
            }
        return weightedPick(choices)
    }

    /** The most he may buy of [item] at [price] under every budget and limit; notes what binds at 0. */
    private fun buyCap(item: MeisterItem, price: Int, rule: BuyRule?): Int {
        val p = price.toLong()
        val limits =
            listOfNotNull(
                "cash" to cash / p,
                "spend per offer" to settings.maxSpendPerOffer / p,
                "item budget" to (settings.maxSpendPerItemPerDay - (spentByItem[item.id] ?: 0) - committedCoins(item.id)) / p,
                "daily budget" to (settings.maxSpendPerDay - spentToday - committedCoins()) / p,
                "buy limit" to exchange.remainingBuyLimit(owner, item.id).toLong(),
                "holding cap" to (Int.MAX_VALUE.toLong() - holding(item.id)),
                "offer value" to Int.MAX_VALUE / p,
                ("max quantity" to settings.maxBuyQuantity.toLong()).takeIf { rule == null },
                rule?.takeIf { it.dailySpend > 0 }?.let { "rule's daily spend" to (it.dailySpend - (spentByItem[item.id] ?: 0) - committedCoins(item.id)) / p },
            )
        val (binding, cap) = limits.minBy { it.second }
        if (cap <= 0) {
            note("Wanted ${item.name} at $price but the $binding allows none")
            return 0
        }
        return cap.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun sell(only: Int?) {
        val offers = exchange.offers(owner)
        val slot = freeSlot(offers, buy = false) ?: return
        val busy = busyItems(offers)
        val bids = exchange.openBids(owner).filterKeys { only == null || it == only }
        val ignoreCooldown = only != null
        val withBuyers =
            bids.keys.mapNotNull { id -> catalog[id]?.takeIf { holding(id) > 0 && sellable(it, busy, ignoreCooldown) } }
                .mapNotNull { item -> sellChoice(item, bids[item.id])?.takeIf { it.buyerWaiting } }
        val choice =
            weightedPick(withBuyers)
                ?: weightedPick(
                    (if (only != null) listOf(only).filter { holding(it) > 0 } else holdings.keys.shuffled(random)).asSequence()
                        .mapNotNull { catalog[it]?.takeIf { item -> sellable(item, busy, ignoreCooldown) } }
                        .take(SAMPLE_SIZE)
                        .mapNotNull { sellChoice(it, bids[it.id]) }
                        .toList()
                )
                ?: return note("Found nothing to sell")
        val item = choice.item
        val held = holding(item.id)
        val share = (held.toLong() * settings.maxSellFractionPercent / 100).coerceAtLeast(1)
        placeSell(slot, item, minOf(settings.maxSellQuantity.toLong(), share).toInt(), choice.price, choice.basis, offers)
    }

    private fun placeSell(slot: Int, item: MeisterItem, wanted: Int, price: Int, basis: String, offers: List<BotOffer>) {
        val held = holding(item.id)
        var quantity = minOf(wanted.toLong(), held.toLong(), (Int.MAX_VALUE / price).toLong()).toInt()
        val netEach = (price - exchange.taxPerItem(item.id, price)).toLong().coerceAtLeast(1)
        val room = MAX_CASH - cash - incomingCoins(offers)
        if (quantity * netEach > room) {
            quantity = (room / netEach).toInt()
        }
        if (quantity <= 0) {
            cooldown(item.id, buy = false)
            return note("Wanted to sell ${item.name}, but his cash is already at the maximum")
        }
        holdings.compute(item.id) { _, count -> (count ?: 0) - quantity }
        holdings.remove(item.id, 0)
        pendingListing = PendingListing(slot, item.id, quantity, price)
        status = MeisterStatus.Selling
        urgent = true
        log.info("decided to sell $quantity of his $held x ${item.name} at $price ($basis)")
        remember(now, "Selling $quantity x ${item.name} at $price ($basis)")
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

    private fun freeSlot(offers: List<BotOffer>, buy: Boolean): Int? {
        val open = offers.count { it.isOpen } + if (pendingListing != null) 1 else 0
        if (open >= settings.maxActiveOffers) {
            note("Already has $open open offer(s)")
            return null
        }
        val side = offers.count { it.isOpen && it.sell != buy }
        val sideCap = if (buy) settings.maxBuyOffers else settings.maxSellOffers
        if (side >= sideCap) {
            note("Already has $side open ${if (buy) "buy" else "sell"} offer(s)")
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

    private fun buyable(item: MeisterItem, busy: Set<Int>, ignoreCooldown: Boolean = false): Boolean =
        !item.noBuy && (settings.buySensitive || !item.sensitive) && item.id !in busy && (ignoreCooldown || !coolingDown(item.id)) &&
            !within(lastSold[item.id], settings.flipCooldownHours * HOUR_MS)

    private fun sellable(item: MeisterItem, busy: Set<Int>, ignoreCooldown: Boolean = false): Boolean =
        !item.noSell && (settings.sellSensitive || !item.sensitive) && item.id !in busy && (ignoreCooldown || !coolingDown(item.id)) &&
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

    /** Rests [itemId] after an offer for it ends or fails; a rule's own cooldown wins in rule mode. */
    private fun cooldown(itemId: Int, buy: Boolean) {
        val ruleMinutes = if (!settings.useRules) null else if (buy) rules.buyRule(itemId)?.cooldownMinutes else rules.sellRule(itemId)?.cooldownMinutes
        val minutes = ruleMinutes ?: settings.itemCooldownMinutes
        if (minutes > 0) {
            cooldowns[itemId] = maxOf(cooldowns[itemId] ?: 0, now + minutes * 60_000L)
            dirty = true
        }
    }

    fun cooldownUntil(itemId: Int): Long = (cooldowns[itemId] ?: 0).takeIf { it > now } ?: 0

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
                    basis[id] = basisOf(id).let { it.copy(unknownQuantity = it.unknownQuantity + count) }
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

    private fun wait(seconds: Int): Long {
        val base = seconds * 1000L
        val spread = base * settings.decisionJitterPercent / 100
        if (pendingListing == null && status != MeisterStatus.NotSetUp) {
            status = if (settings.tradingEnabled) MeisterStatus.Idle else MeisterStatus.Paused
        }
        return base + if (spread > 0) random.nextLong(-spread, spread + 1) else 0L
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
