package org.rsmod.content.other.bots.core

import kotlin.random.Random

/**
 * The per-bot state machine. It only decides; every world interaction goes through [BotActor] and
 * every exchange interaction through [BotExchange]. A state that cannot make progress within its
 * budget fails, failures are retried a bounded number of times, and repeated failures park the
 * bot in [BotState.Error] so it can never spin forever.
 *
 * Bot state and the exchange's book are saved separately, so every item movement between them is
 * done in two steps: the bot's side is recorded and saved first ([PendingListing] /
 * [PendingCollect]), the exchange is changed on a later tick, and the record is cleared only once
 * the exchange has saved too. [recovering] marks a brain built from a record loaded at startup,
 * when the exchange's file is the authority on what actually happened.
 */
class BotBrain(
    initial: BotRecord,
    private val actor: BotActor,
    private val exchange: BotExchange,
    private val items: (String) -> Int?,
    private val log: BotLog,
    private val storeHealthy: () -> Boolean,
    private var recovering: Boolean = false,
    private val random: Random = Random.Default,
) {
    var record: BotRecord = initial
        private set

    /** Set when the record changed in a way that should be saved soon. */
    var dirty: Boolean = false

    /** Set when the record (and inventory) must be saved before the next tick. */
    var urgent: Boolean = false

    private val owner = initial.ownerId

    private var stateCycle = 0
    private var lastProgressCycle = 0
    private var busyTicks = 0
    private var busyMark = 0
    private var stateFailures = 0
    private var gatherTarget = BotDestination.Resource
    private var travelTarget = BotDestination.Resource
    private var errorUntil = 0L
    private var listingOutcome: Boolean? = null
    private var plannedPurchase: PlannedPurchase? = null
    private var reactionCycle = NO_REACTION
    private var now = 0L
    private var suppliesMissingSince = NOT_WAITING
    private var failedRollback: String? = null
    private val decisions = ArrayDeque<BotDecision>()
    private val sightings = HashMap<Long, Sighting>()

    /** The latest state changes and their reasons, newest last. */
    val trace: List<BotDecision>
        get() = decisions.toList()

    val state: BotState
        get() = record.state

    /** Set when the bot should be disabled with this reason; the coordinator acts on it. */
    var parkRequest: String? = null

    val hasPendingExchangeWork: Boolean
        get() = record.hasPendingExchangeWork

    fun start(cycle: Int, now: Long) {
        this.now = now
        stateFailures = 0
        suppliesMissingSince = NOT_WAITING
        parkRequest = null
        if (record.state == BotState.Error) {
            errorUntil = 0
            return
        }
        transition(BotState.Idle, cycle, "Starting up")
    }

    fun stop(reason: String) {
        actor.stopActivity()
        record = record.copy(state = BotState.Disabled, activity = reason)
        urgent = true
    }

    fun clearError(cycle: Int) {
        record = record.copy(errorRecoveries = 0, lastError = null)
        transition(BotState.Idle, cycle, "Error cleared")
    }

    /** Settles exchange work only; used while a disabled bot waits to leave the world. */
    fun settle(now: Long) {
        if (!actor.isBusy) {
            settleExchange(now)
        }
    }

    fun tick(now: Long, cycle: Int) {
        this.now = now
        if (record.state == BotState.Disabled) {
            return
        }
        if (actor.isBusy) {
            val mark = actor.progressMark
            if (mark != busyMark) {
                busyMark = mark
                busyTicks = 0
            }
            if (++busyTicks > BUSY_LIMIT_TICKS) {
                busyTicks = 0
                actor.stopActivity()
                fail("stuck mid-action for $BUSY_LIMIT_TICKS ticks", cycle, now)
            }
            return
        }
        busyTicks = 0
        settleExchange(now)
        when (record.state) {
            BotState.Disabled -> {}
            BotState.Idle -> decide(now, cycle)
            BotState.TravelToResource -> travel(travelTarget, BotState.Gathering, cycle, now)
            BotState.Gathering -> gather(now, cycle)
            BotState.TravelToBank -> travel(BotDestination.Bank, BotState.Banking, cycle, now)
            BotState.Banking -> bank(now, cycle)
            BotState.WaitingForSupplies -> waitForSupplies(now, cycle)
            BotState.PrepareSale -> prepareSale(now, cycle)
            BotState.TravelToExchange -> {
                val next = if (plannedPurchase != null) BotState.BuyingSupplies else BotState.Listing
                travel(BotDestination.Exchange, next, cycle, now)
            }
            BotState.Listing -> list(now, cycle)
            BotState.BuyingSupplies -> buySupplies(now, cycle)
            BotState.Error -> recover(now, cycle)
        }
    }

    private fun decide(now: Long, cycle: Int) {
        val problem = actor.requirementProblem()
        if (problem != null) {
            error(problem, now, recoverable = false)
            return
        }
        if (actor.kitToFetch()) {
            transition(BotState.TravelToBank, cycle, "Fetching its kit from the bank")
            return
        }
        if (gatherLimitReached(now)) {
            setActivity("Hourly gathering limit reached")
            return
        }
        if (saleItemIds().any { actor.heldNoted(it) > 0 }) {
            val next = if (saleAllowed(now)) BotState.TravelToExchange else BotState.TravelToBank
            transition(next, cycle, "Carrying items for sale")
            return
        }
        if (actor.inventoryUsed >= actor.inventoryCapacity) {
            transition(BotState.TravelToBank, cycle, "Inventory full")
            return
        }
        if (actor.needsSourceVisit()) {
            travelTo(BotDestination.SupplySource, cycle)
            return
        }
        if (actor.needsSupplies()) {
            if (minesSupplies()) {
                travelTo(BotDestination.SupplySource, cycle)
            } else {
                transition(BotState.TravelToBank, cycle, "Fetching supplies")
            }
            return
        }
        travelTo(BotDestination.Resource, cycle)
    }

    private fun travelTo(target: BotDestination, cycle: Int) {
        gatherTarget = target
        travelTarget = target
        val label = if (target == BotDestination.SupplySource) "supplies" else "resources"
        transition(BotState.TravelToResource, cycle, "Heading to $label")
    }

    private fun travel(target: BotDestination, next: BotState, cycle: Int, now: Long) {
        when (val status = actor.travel(target)) {
            TravelStatus.Arrived -> {
                lastProgressCycle = cycle
                transition(next, cycle, "Arrived at ${target.name.lowercase()}")
            }
            TravelStatus.Moving ->
                if (cycle - stateCycle > TRAVEL_BUDGET_TICKS) {
                    fail("timed out travelling to ${target.name.lowercase()}", cycle, now)
                }
            is TravelStatus.Blocked -> fail(status.reason, cycle, now)
            is TravelStatus.Waiting -> {
                stateCycle = cycle
                setActivity(status.reason)
            }
        }
    }

    private fun gather(now: Long, cycle: Int) {
        actor.requirementProblem()?.let {
            actor.stopActivity()
            error(it, now, recoverable = false)
            return
        }
        if (gatherLimitReached(now)) {
            actor.stopActivity()
            transition(BotState.Idle, cycle, "Hourly gathering limit reached")
            return
        }
        if (hesitating(cycle)) {
            return
        }
        when (val status = actor.gather(gatherTarget)) {
            is GatherStatus.Working -> {
                if (status.gained > 0) {
                    lastProgressCycle = cycle
                    recordGathered(status.gained, now)
                    setActivity(gatherActivity())
                } else if (status.progressed) {
                    lastProgressCycle = cycle
                } else if (cycle - lastProgressCycle > GATHER_STALL_TICKS) {
                    fail("no gathering progress", cycle, now)
                }
            }
            GatherStatus.InventoryFull -> {
                actor.stopActivity()
                if (gatherTarget == BotDestination.SupplySource) {
                    travelTo(BotDestination.Resource, cycle)
                } else {
                    transition(BotState.TravelToBank, cycle, "Inventory full")
                }
            }
            GatherStatus.NeedsSupplies -> {
                actor.stopActivity()
                transition(BotState.TravelToBank, cycle, "Out of supplies")
            }
            GatherStatus.Hurt -> {
                actor.stopActivity()
                transition(BotState.TravelToBank, cycle, "Hurt; going to rest")
            }
            GatherStatus.Done ->
                if (gatherTarget == BotDestination.SupplySource && actor.needsSupplies()) {
                    transition(BotState.TravelToBank, cycle, "Fetching supplies")
                } else if (gatherTarget == BotDestination.SupplySource) {
                    travelTo(if (actor.needsSourceVisit()) BotDestination.SupplySource else BotDestination.Resource, cycle)
                } else {
                    transition(BotState.TravelToBank, cycle, "Finished here")
                }
            GatherStatus.NoResource -> {
                setActivity("Waiting for resources to respawn")
                if (cycle - lastProgressCycle > NO_RESOURCE_TICKS) {
                    fail("no available resources", cycle, now)
                }
            }
            is GatherStatus.Failed -> error(status.reason, now, recoverable = false)
        }
    }

    /**
     * A person takes a moment to notice an action has finished before clicking again; the bot
     * waits a random [BotConfig.reactionMinTicks]..[BotConfig.reactionMaxTicks] ticks between clicks.
     */
    private fun hesitating(cycle: Int): Boolean {
        if (actor.isActing) {
            reactionCycle = NO_REACTION
            return false
        }
        if (reactionCycle == NO_REACTION) {
            val config = record.config
            reactionCycle = cycle + random.nextInt(config.reactionMinTicks, config.reactionMaxTicks + 1)
        }
        if (cycle < reactionCycle) {
            return true
        }
        reactionCycle = NO_REACTION
        return false
    }

    private fun bank(now: Long, cycle: Int) {
        if (exchange.available && storeHealthy()) {
            abortStaleOffers(now)
        }
        collectProceeds()
        when (val outcome = actor.depositGathered()) {
            DepositOutcome.BankFull -> {
                error("bank is full", now, recoverable = false)
                return
            }
            is DepositOutcome.Deposited -> {
                val total = outcome.items.values.sumOf { it.toLong() }
                if (total > 0) {
                    val stats = record.stats.copy(deposited = record.stats.deposited + total)
                    record = record.copy(stats = stats)
                    log.info(
                        record,
                        "deposited ${describe(outcome.items)} (gathered ${stats.gathered}, " +
                            "deposited ${stats.deposited} in total)",
                    )
                }
                actor.fetchKit()
            }
        }
        stateFailures = 0
        record = record.copy(errorRecoveries = 0, lastError = null)
        urgent = true

        if (dueSaleItems(now).isNotEmpty() && !actor.needsRest()) {
            transition(BotState.PrepareSale, cycle, "Sale threshold reached")
            return
        }
        heldSale()?.let { remember("Sale waits: $it") }
        if (!restock(now, cycle)) {
            return
        }
        if (actor.needsRest()) {
            transition(BotState.WaitingForSupplies, cycle, RESTING)
            return
        }
        travelTo(gatherTarget, cycle)
    }

    /** Returns false when the bot cannot continue gathering yet. */
    private fun restock(now: Long, cycle: Int): Boolean {
        if (!actor.needsSupplies()) {
            suppliesMissingSince = NOT_WAITING
            gatherTarget = workTarget()
            return true
        }
        if (minesSupplies()) {
            gatherTarget = BotDestination.SupplySource
            return true
        }
        return when (actor.withdrawSupplies()) {
            SupplyOutcome.NotNeeded,
            is SupplyOutcome.Withdrawn -> {
                suppliesMissingSince = NOT_WAITING
                gatherTarget = workTarget()
                true
            }
            SupplyOutcome.Unavailable -> {
                if (actor.canWorkWithoutSupplies()) {
                    gatherTarget = workTarget()
                    return true
                }
                noteMissingSupplies(cycle)
                if (parkRequest != null) {
                    return false
                }
                val waiting = purchaseSupplies(now, cycle) ?: return false
                if (actor.restsWithoutSupplies()) {
                    transition(BotState.WaitingForSupplies, cycle, RESTING)
                    log.info(record, "resting at the bank: no supplies left ($waiting)")
                } else {
                    transition(BotState.WaitingForSupplies, cycle, waiting)
                    log.warn(record, "paused: no supplies in bank ($waiting)")
                }
                false
            }
            SupplyOutcome.NoSpace -> {
                error("no inventory space for supplies", now, recoverable = false)
                false
            }
        }
    }

    private fun minesSupplies(): Boolean =
        record.config.essenceSource == EssenceSource.Mine || actor.minesOwnSupplies()

    private fun workTarget(): BotDestination =
        if (actor.needsSourceVisit()) BotDestination.SupplySource else BotDestination.Resource

    private fun waitForSupplies(now: Long, cycle: Int) {
        if ((cycle - stateCycle) % SUPPLY_RECHECK_TICKS != 0) {
            return
        }
        if (actor.travel(BotDestination.Bank) != TravelStatus.Arrived) {
            transition(BotState.TravelToBank, cycle, "Returning to bank")
            return
        }
        if (actor.needsRest()) {
            setActivity(RESTING)
            return
        }
        if (exchange.available && storeHealthy()) {
            abortStaleOffers(now)
        }
        collectProceeds()
        when (actor.withdrawSupplies()) {
            SupplyOutcome.NotNeeded,
            is SupplyOutcome.Withdrawn -> {
                suppliesMissingSince = NOT_WAITING
                log.info(record, "supplies restocked")
                urgent = true
                travelTo(workTarget(), cycle)
            }
            SupplyOutcome.Unavailable ->
                if (actor.canWorkWithoutSupplies()) {
                    travelTo(workTarget(), cycle)
                } else {
                    noteMissingSupplies(cycle)
                    if (parkRequest != null) {
                        return
                    }
                    val waiting = purchaseSupplies(now, cycle) ?: return
                    setActivity(if (actor.restsWithoutSupplies()) RESTING else waiting)
                }
            SupplyOutcome.NoSpace -> error("no inventory space for supplies", now, false)
        }
    }

    /**
     * Starts restocking supplies the bank has run out of: buys the first supply someone sells at a
     * fair lowest ask, or with no fair ask leaves a buy offer resting at the guide price until it
     * fills or times out. When coins are short it first lists enough banked products to afford
     * them. Returns null once the bot is on its way to the exchange, otherwise why it has to wait.
     */
    private fun purchaseSupplies(now: Long, cycle: Int): String? {
        val supplies = actor.suppliesToBuy()
        if (!record.config.restock.enabled || supplies.isEmpty()) {
            return NO_SUPPLIES
        }
        if (!exchange.available || !storeHealthy() || record.pendingListing != null) {
            return "$NO_SUPPLIES; the exchange is unavailable"
        }
        actor.exchangeTripHold()?.let { return "$NO_SUPPLIES; $it" }
        if (exchange.offers(owner).any { !it.sell && it.itemId in supplies }) {
            return "Waiting for the supply buy offer to fill"
        }
        val quote = supplyQuote(supplies) ?: return "$NO_SUPPLIES; nobody is selling them and they have no guide price"
        val item = quote.itemId
        val price = quote.price
        val wanted = minOf(record.config.restock.quantity, exchange.remainingBuyLimit(owner, item))
        if (wanted <= 0) {
            return "$NO_SUPPLIES; the buy limit is reached"
        }
        val coins = actor.coins()
        val selling = saleItemIds().any(::hasOpenSale)
        val affordable =
            when {
                coins >= price.toLong() * wanted -> wanted
                !selling && fundSupplies(price.toLong() * wanted - coins, now, cycle) -> return null
                else -> minOf(coins / price, wanted.toLong()).toInt()
            }
        if (affordable <= 0) {
            return if (selling) "Waiting for sales to afford supplies" else "$NO_SUPPLIES; no coins or products to buy them"
        }
        plannedPurchase = PlannedPurchase(item, affordable, price, quote.resting)
        val how = if (quote.resting) " at the guide price" else ""
        transition(BotState.TravelToExchange, cycle, "Going to buy $affordable supplies$how")
        return null
    }

    private fun supplyQuote(supplies: List<Int>): PlannedPurchase? {
        val fair = 1 + record.config.pricing.maxDeviationPercent / 100
        for (item in supplies) {
            val ask = exchange.lowestAsk(item, owner) ?: continue
            val guide = exchange.guidePrice(item)
            if (guide == null || ask <= guide * fair) {
                return PlannedPurchase(item, 0, ask, resting = false)
            }
        }
        for (item in supplies) {
            val guide = exchange.guidePrice(item)?.takeIf { it > 0 } ?: continue
            return PlannedPurchase(item, 0, guide, resting = true)
        }
        return null
    }

    /**
     * Counts how long the bank has been out of supplies; past [SUPPLY_PARK_TICKS] the bot is parked
     * so it frees its active slot for bots that can work. Fighters only rest, so they never park.
     */
    private fun noteMissingSupplies(cycle: Int) {
        if (actor.restsWithoutSupplies()) {
            return
        }
        if (suppliesMissingSince == NOT_WAITING) {
            suppliesMissingSince = cycle
            return
        }
        if (cycle - suppliesMissingSince < SUPPLY_PARK_TICKS || parkRequest != null) {
            return
        }
        if (exchange.available && storeHealthy()) {
            val supplies = actor.suppliesToBuy()
            for (offer in exchange.offers(owner)) {
                if (!offer.sell && offer.isOpen && offer.itemId in supplies && exchange.abort(owner, offer.slot)) {
                    log.info(record, "aborted supply buy offer in slot ${offer.slot} before parking")
                }
            }
        }
        val minutes = SUPPLY_PARK_TICKS * TICK_MS / 60_000
        val reason = "Parked: no supplies for $minutes minutes; tries again in 2 hours"
        log.warn(record, "parked: its bank has had no supplies for $minutes minutes and none could be bought")
        setActivity(reason)
        parkRequest = reason
        urgent = true
    }

    /** Takes enough products, below the usual sale threshold if need be, to raise [shortfall] coins. */
    private fun fundSupplies(shortfall: Long, now: Long, cycle: Int): Boolean {
        val item = saleItemIds().maxByOrNull(actor::bankCount)?.takeIf { actor.bankCount(it) > 0 } ?: return false
        if (!saleAllowed(now) || exchange.offers(owner).size > EXCHANGE_SLOTS - 2) {
            return false
        }
        val decision = priceFor(item, now) as? PriceDecision.List ?: return false
        val net = decision.price - exchange.taxPerItem(item, decision.price)
        if (net <= 0) {
            return false
        }
        val needed = (shortfall + net - 1) / net
        val quantity = minOf(needed, actor.bankCount(item), Int.MAX_VALUE.toLong()).toInt()
        val withdrawn = if (quantity > 0) actor.withdrawNoted(item, quantity) else 0
        if (withdrawn <= 0) {
            return false
        }
        log.info(record, "withdrew $withdrawn x item $item to sell for supplies")
        urgent = true
        transition(BotState.TravelToExchange, cycle, "Selling $withdrawn items to afford supplies")
        return true
    }

    private fun buySupplies(now: Long, cycle: Int) {
        if (record.pendingListing != null) {
            setActivity("Placing buy offer")
            return
        }
        when (listingOutcome) {
            true -> {
                listingOutcome = null
                transition(BotState.TravelToBank, cycle, "Collecting bought supplies")
                return
            }
            false -> {
                listingOutcome = null
                transition(BotState.TravelToBank, cycle, "Buy offer failed")
                return
            }
            null -> {}
        }
        val plan = plannedPurchase
        if (plan == null || !exchange.available || !storeHealthy()) {
            transition(BotState.TravelToBank, cycle, "Returning to bank")
            return
        }
        abortStaleOffers(now)
        val ask = exchange.lowestAsk(plan.itemId, owner)
        if (!plan.resting && (ask == null || ask > plan.price)) {
            transition(BotState.TravelToBank, cycle, "The supply price changed")
            return
        }
        val quantity = minOf(plan.quantity.toLong(), actor.coins() / plan.price).toInt()
        val slot = exchange.freeSlot(owner)
        if (quantity <= 0 || slot == null || exchange.offers(owner).any { !it.sell && it.itemId == plan.itemId }) {
            transition(BotState.TravelToBank, cycle, "Could not place a buy offer")
            return
        }
        val pending = PendingListing(slot, plan.itemId, quantity, plan.price, buy = true)
        if (!actor.debitBankCoins(pending.cost)) {
            transition(BotState.TravelToBank, cycle, "Could not pay for supplies")
            return
        }
        record = record.copy(pendingListing = pending)
        val resting = if (plan.resting) ", resting at the guide price" else ""
        log.info(record, "buying $quantity x item ${plan.itemId} at ${plan.price} each$resting")
        urgent = true
    }

    /** What an offer's collect box held when the bot looked, and the exchange save count then. */
    private class Sighting(val save: Long, val items: Int, val coins: Int)

    /**
     * Credits what the bot's offers hold to its bank and records it as pending; the exchange is
     * emptied on a later tick by [settleCollects], once the credited bank has been saved. Only
     * amounts the exchange has saved are credited - with unsaved exchange changes a box is looked at
     * first and collected after the next save - so a fill or abort lost in a crash is never paid out.
     */
    private fun collectProceeds() {
        if (!exchange.available || !storeHealthy()) {
            return
        }
        val busySlots = record.pendingCollects.mapTo(HashSet()) { it.slot }
        record.pendingListing?.let { busySlots += it.slot }
        val offers = exchange.offers(owner)
        sightings.keys.retainAll(offers.filter { it.hasCollectable }.mapTo(HashSet()) { it.createdAt })
        for (offer in offers) {
            if (!offer.hasCollectable || offer.slot in busySlots) {
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
            val itemCount = minOf(seen.items, offer.collectItems)
            val coinCount = minOf(seen.coins, offer.collectCoins)
            val items = if (itemCount > 0 && actor.creditBank(offer.itemId, itemCount)) itemCount else 0
            val coins = if (coinCount > 0) actor.creditBankCoins(coinCount.toLong()) else 0L
            if (items == 0 && coins == 0L) {
                log.warn(record, "could not collect slot ${offer.slot}: bank has no room")
                continue
            }
            val pending =
                PendingCollect(
                    slot = offer.slot,
                    createdAt = offer.createdAt,
                    coinsTarget = offer.coinsCollected + coins,
                    itemsTarget = offer.itemsCollected + items,
                )
            val stats =
                if (offer.sell) {
                    record.stats.copy(coinsEarned = record.stats.coinsEarned + coins)
                } else {
                    record.stats.copy(suppliesBought = record.stats.suppliesBought + items)
                }
            record = record.copy(pendingCollects = record.pendingCollects + pending, stats = stats)
            log.info(record, "collected slot ${offer.slot}: $coins coins, $items items")
            urgent = true
        }
    }

    private fun prepareSale(now: Long, cycle: Int) {
        val due = dueSaleItems(now)
        if (due.isEmpty()) {
            travelTo(gatherTarget, cycle)
            return
        }
        if (actor.inventoryUsed >= actor.inventoryCapacity) {
            log.info(record, "no room to take items for sale; selling on the next bank visit")
            travelTo(gatherTarget, cycle)
            return
        }
        var withdrawn = 0
        var unpriced: String? = null
        for (item in due) {
            when (val decision = priceFor(item, now)) {
                is PriceDecision.Paused -> {
                    log.info(record, "not selling item $item: ${decision.reason}")
                    unpriced = decision.reason
                    continue
                }
                is PriceDecision.List -> {}
            }
            val quantity = minOf(record.config.sale.quantity.toLong(), actor.bankCount(item)).toInt()
            val taken = if (quantity > 0) actor.withdrawNoted(item, quantity) else 0
            if (taken > 0) {
                log.info(record, "withdrew $taken x item $item for sale")
                withdrawn += taken
            }
        }
        if (withdrawn <= 0) {
            pauseSales(unpriced ?: "could not withdraw items for sale", now)
            travelTo(gatherTarget, cycle)
            return
        }
        urgent = true
        transition(BotState.TravelToExchange, cycle, "Taking $withdrawn items to the exchange")
    }

    private fun list(now: Long, cycle: Int) {
        if (record.pendingListing != null) {
            setActivity("Placing offer")
            return
        }
        when (listingOutcome) {
            true -> {
                listingOutcome = null
                if (saleItemIds().any { actor.held(it) > 0 }) {
                    setActivity("Listing the next item")
                    return
                }
                if (actor.needsSupplies() && !actor.canWorkWithoutSupplies() && record.config.restock.enabled) {
                    transition(BotState.TravelToBank, cycle, "Waiting for sales to afford supplies")
                } else {
                    travelTo(gatherTarget, cycle)
                }
                return
            }
            false -> {
                listingOutcome = null
                transition(BotState.TravelToBank, cycle, "Returning unsold items to the bank")
                return
            }
            null -> {}
        }
        abortStaleOffers(now)
        val item = saleItemIds().firstOrNull { actor.held(it) > 0 }
        val held = item?.let(actor::held) ?: 0
        if (item == null) {
            transition(BotState.TravelToBank, cycle, "Nothing to list")
            return
        }
        if (!saleAllowed(now) || hasOpenSale(item)) {
            transition(BotState.TravelToBank, cycle, "Returning unsold items to the bank")
            return
        }
        val slot = exchange.freeSlot(owner)
        if (slot == null) {
            transition(BotState.TravelToBank, cycle, "No free exchange slot")
            return
        }
        val decision = priceFor(item, now)
        if (decision is PriceDecision.Paused) {
            pauseSales(decision.reason, now)
            transition(BotState.TravelToBank, cycle, "Returning unsold items to the bank")
            return
        }
        decision as PriceDecision.List
        if (!actor.takeHeld(item, held)) {
            transition(BotState.TravelToBank, cycle, "Could not take items to list")
            return
        }
        val pending = PendingListing(slot, item, held, decision.price)
        record = record.copy(pendingListing = pending)
        log.info(record, "listing $held x item $item at ${decision.price} each (${decision.basis})")
        urgent = true
    }

    /** Moves pending exchange work forward; see the class documentation. */
    private fun settleExchange(now: Long) {
        settleListing(now)
        settleCollects()
        countSales()
        recovering = false
    }

    private fun settleListing(now: Long) {
        val pending = record.pendingListing ?: return
        failedRollback?.let { reason ->
            rollBackListing(pending, reason, now)
            return
        }
        val offer = exchange.offer(owner, pending.slot)
        val ours =
            offer != null && offer.sell != pending.buy && offer.itemId == pending.itemId &&
                offer.total == pending.quantity && offer.price == pending.price
        when {
            ours && (recovering || exchange.saveCount > pending.placedAtSave) -> {
                val listed = if (pending.buy) 0 else pending.quantity
                record =
                    record.copy(
                        pendingListing = null,
                        stats = record.stats.copy(listed = record.stats.listed + listed),
                    )
                val verb = if (pending.buy) "placed a buy offer for" else "listed"
                log.info(record, "$verb ${pending.quantity} x item ${pending.itemId} in slot ${pending.slot}")
                listingOutcome = true
                urgent = true
            }
            ours -> {}
            !pending.placed && !recovering && durable() && exchange.available -> {
                val place = if (pending.buy) exchange::buy else exchange::sell
                val placed = place(owner, record.name, pending.slot, pending.itemId, pending.quantity, pending.price)
                if (placed) {
                    record =
                        record.copy(
                            pendingListing = pending.copy(placed = true, placedAtSave = exchange.saveCount)
                        )
                    urgent = true
                } else {
                    rollBackListing(pending, "the exchange refused the offer", now)
                }
            }
            !pending.placed && !recovering ->
                rollBackListing(pending, "the exchange or bot store is unavailable", now)
            recovering -> rollBackListing(pending, "the offer never reached the exchange's save", now)
            else -> setActivity("Waiting for the exchange to confirm the offer")
        }
    }

    /**
     * Gives back what a listing that never reached the exchange took. When the bank cannot take it
     * the pending record stays, so nothing is lost, and the bot is parked in error until an
     * administrator makes room; the rollback is retried every tick until then.
     */
    private fun rollBackListing(pending: PendingListing, reason: String, now: Long) {
        if (pending.buy) {
            val refunded = actor.creditBankCoins(pending.cost)
            if (refunded != pending.cost) {
                if (refunded > 0 && !actor.debitBankCoins(refunded)) {
                    log.error("bot ${record.id}: could not undo a partial refund of $refunded coins")
                }
                rollBackFailed("could not refund ${pending.cost} coins after a failed buy offer", now)
                return
            }
            failedRollback = null
            record = record.copy(pendingListing = null)
            log.warn(record, "buy offer rolled back ($reason); ${pending.cost} coins refunded")
            listingOutcome = false
            urgent = true
            return
        }
        val returned =
            actor.returnHeld(pending.itemId, pending.quantity) ||
                actor.creditBank(pending.itemId, pending.quantity)
        if (!returned) {
            rollBackFailed("could not return ${pending.quantity} x item ${pending.itemId} after a failed listing", now)
            return
        }
        failedRollback = null
        record = record.copy(pendingListing = null)
        log.warn(record, "listing rolled back ($reason); ${pending.quantity} items returned")
        pauseSales("listing failed: $reason", now)
        listingOutcome = false
        urgent = true
    }

    private fun rollBackFailed(reason: String, now: Long) {
        failedRollback = reason
        if (record.state != BotState.Error || record.lastError != reason) {
            error(reason, now, recoverable = false)
        }
    }

    private fun settleCollects() {
        if (record.pendingCollects.isEmpty()) {
            return
        }
        val remaining = ArrayList<PendingCollect>()
        for (pending in record.pendingCollects) {
            val offer = exchange.offer(owner, pending.slot)?.takeIf { it.createdAt == pending.createdAt }
            if (offer == null) {
                // Taking a box empty releases the offer before the exchange saves; a crash then
                // brings the box back, so the record stays until that release is on disk.
                if (pending.taken && !recovering && exchange.saveCount <= pending.takenAtSave) {
                    remaining += pending
                }
                continue
            }
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
        if (remaining != record.pendingCollects) {
            record = record.copy(pendingCollects = remaining)
            urgent = true
        }
    }

    private fun countSales() {
        val offers = exchange.offers(owner).filter { it.sell }
        var counted = record.salesCounted
        var stats = record.stats
        for (offer in offers) {
            val before = counted[offer.createdAt] ?: 0
            if (offer.completed > before) {
                val sold = offer.completed - before
                stats = stats.copy(sold = stats.sold + sold)
                counted = counted + (offer.createdAt to offer.completed)
                log.info(record, "sold $sold x item ${offer.itemId} from slot ${offer.slot} at ${offer.price} or better")
            }
        }
        val live = offers.mapTo(HashSet()) { it.createdAt }
        counted = counted.filterKeys { it in live }
        if (counted != record.salesCounted || stats != record.stats) {
            record = record.copy(salesCounted = counted, stats = stats)
            dirty = true
        }
    }

    private fun abortStaleOffers(now: Long) {
        val timeout = record.config.pricing.listingTimeoutMinutes * 60_000L
        for (offer in exchange.offers(owner)) {
            if (!offer.isOpen || now - offer.createdAt < timeout) {
                continue
            }
            if (!exchange.abort(owner, offer.slot)) {
                continue
            }
            if (!offer.sell) {
                log.info(record, "aborted unfilled supply buy offer in slot ${offer.slot} (${offer.remaining} left)")
                urgent = true
                continue
            }
            val retries = record.saleRetries + 1
            log.info(record, "aborted unsold slot ${offer.slot} (${offer.remaining} left), retry $retries")
            record = record.copy(saleRetries = retries)
            if (retries > record.config.pricing.maxRetries) {
                pauseSales("unsold after ${record.config.pricing.maxRetries} retries", now)
                record = record.copy(saleRetries = 0)
            }
            urgent = true
        }
    }

    private fun priceFor(itemId: Int, now: Long): PriceDecision {
        val pricing = record.config.pricing
        val signal =
            PriceGuide.volumeWeighted(
                exchange.recentTrades(itemId),
                now,
                SalePricing.guideSettings(pricing),
            )
        return SalePricing.decide(
            config = pricing,
            signal = signal,
            serviceReference = exchange.guidePrice(itemId),
            competingAsk = exchange.lowestAsk(itemId, owner),
            retries = record.saleRetries,
            tax = { price -> exchange.taxPerItem(itemId, price) },
        )
    }

    private fun pauseSales(reason: String, now: Long) {
        val until = now + record.config.pricing.cooldownMinutes * 60_000L
        record = record.copy(salePausedUntil = until)
        log.warn(record, "selling paused: $reason")
        dirty = true
    }

    /** True once this bot's own last urgent change has been written to the store. */
    private fun durable(): Boolean = !urgent && storeHealthy()

    private fun hasOpenSale(itemId: Int): Boolean =
        exchange.offers(owner).any { it.isOpen && it.sell && it.itemId == itemId }

    /** Every item the bot may sell, its main sale item first. */
    private fun saleItemIds(): List<Int> {
        val sale = record.config.sale
        if (!sale.enabled) {
            return emptyList()
        }
        val ordered = listOf(sale.item).filter { it in sale.allowedItems } + sale.allowedItems.sorted()
        return ordered.distinct().mapNotNull(items)
    }

    private fun heldSale(): String? =
        actor.exchangeTripHold()?.takeIf {
            saleItemIds().any { item -> actor.bankCount(item) >= record.config.sale.threshold }
        }

    private fun saleAllowed(now: Long): Boolean =
        saleItemIds().isNotEmpty() && exchange.available && storeHealthy() && now >= record.salePausedUntil &&
            actor.exchangeTripHold() == null

    /** Items banked past the sale threshold with no listing open yet, as many as there are free slots. */
    private fun dueSaleItems(now: Long): List<Int> {
        if (!saleAllowed(now) || record.pendingListing != null) {
            return emptyList()
        }
        val freeSlots = EXCHANGE_SLOTS - exchange.offers(owner).size
        return saleItemIds()
            .filter { !hasOpenSale(it) && actor.bankCount(it) >= record.config.sale.threshold }
            .take(maxOf(freeSlots, 0))
    }

    private fun gatherLimitReached(now: Long): Boolean {
        val limit = record.config.maxGatheredPerHour
        if (limit <= 0) {
            return false
        }
        if (now - record.gatherWindowStart >= HOUR_MS) {
            record = record.copy(gatherWindowStart = now, gatherWindowCount = 0)
        }
        return record.gatherWindowCount >= limit
    }

    private fun recordGathered(count: Int, now: Long) {
        if (now - record.gatherWindowStart >= HOUR_MS) {
            record = record.copy(gatherWindowStart = now, gatherWindowCount = 0)
        }
        record =
            record.copy(
                gatherWindowCount = record.gatherWindowCount + count,
                stats = record.stats.copy(gathered = record.stats.gathered + count),
            )
        dirty = true
    }

    private fun fail(reason: String, cycle: Int, now: Long) {
        actor.stopActivity()
        stateFailures++
        log.warn(record, "${record.state.label} failed ($stateFailures/$MAX_STATE_FAILURES): $reason")
        if (stateFailures >= MAX_STATE_FAILURES) {
            error(reason, now, recoverable = true)
            return
        }
        transition(BotState.Idle, cycle, "Retrying after: $reason")
    }

    private fun error(reason: String, now: Long, recoverable: Boolean) {
        actor.stopActivity()
        val recoveries = if (recoverable) record.errorRecoveries else MAX_ERROR_RECOVERIES
        record =
            record.copy(
                state = BotState.Error,
                activity = reason,
                lastError = reason,
                errorRecoveries = recoveries,
            )
        errorUntil = now + ERROR_COOLDOWN_MS
        remember("ERROR: $reason")
        log.warn(record, "error: $reason")
        urgent = true
    }

    private fun recover(now: Long, cycle: Int) {
        if (record.errorRecoveries >= MAX_ERROR_RECOVERIES || now < errorUntil) {
            return
        }
        stateFailures = 0
        record = record.copy(errorRecoveries = record.errorRecoveries + 1)
        log.info(record, "recovering from error (attempt ${record.errorRecoveries})")
        transition(BotState.Idle, cycle, "Recovering")
    }

    private fun transition(next: BotState, cycle: Int, activity: String) {
        if (next != record.state || activity != record.activity) {
            remember("${record.state.label} -> ${next.label}: $activity")
        }
        if (next != BotState.TravelToExchange && next != BotState.BuyingSupplies) {
            plannedPurchase = null
        }
        if (next != record.state) {
            stateCycle = cycle
            if (next == BotState.Gathering) {
                lastProgressCycle = cycle
            }
        }
        record = record.copy(state = next, activity = activity)
        dirty = true
    }

    private fun remember(text: String) {
        decisions.addLast(BotDecision(now, text))
        while (decisions.size > TRACE_SIZE) {
            decisions.removeFirst()
        }
    }

    private fun setActivity(activity: String) {
        if (record.activity != activity) {
            record = record.copy(activity = activity)
        }
    }

    private fun gatherActivity(): String =
        if (gatherTarget == BotDestination.SupplySource) {
            "Gathering supplies"
        } else {
            "Gathering (${record.stats.gathered} total)"
        }

    private fun describe(items: Map<Int, Int>): String =
        items.entries.joinToString { (id, count) -> "$count x item $id" }

    fun updateRecord(transform: (BotRecord) -> BotRecord) {
        record = transform(record)
        dirty = true
    }

    companion object {
        const val TRAVEL_BUDGET_TICKS: Int = 1000
        const val GATHER_STALL_TICKS: Int = 200
        const val NO_RESOURCE_TICKS: Int = 1500
        const val BUSY_LIMIT_TICKS: Int = 100
        const val SUPPLY_RECHECK_TICKS: Int = 100
        const val SUPPLY_PARK_TICKS: Int = 6000
        private const val TICK_MS = 600
        private const val NOT_WAITING = -1
        private const val RESTING = "Resting until healed"
        private const val NO_SUPPLIES = "Waiting for supplies in the bank"
        private const val EXCHANGE_SLOTS = 8
        const val MAX_STATE_FAILURES: Int = 3
        const val MAX_ERROR_RECOVERIES: Int = 3
        const val ERROR_COOLDOWN_MS: Long = 5 * 60_000L
        private const val HOUR_MS = 60 * 60_000L
        private const val NO_REACTION = -1
        private const val TRACE_SIZE = 12
    }
}

private data class PlannedPurchase(val itemId: Int, val quantity: Int, val price: Int, val resting: Boolean)

data class BotDecision(val at: Long, val text: String)

data class BotTrace(val decisions: List<BotDecision>, val focus: String?)
