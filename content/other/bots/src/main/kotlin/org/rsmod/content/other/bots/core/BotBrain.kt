package org.rsmod.content.other.bots.core

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
    private var stateFailures = 0
    private var gatherTarget = BotDestination.Resource
    private var travelTarget = BotDestination.Resource
    private var errorUntil = 0L
    private var listingOutcome: Boolean? = null

    val state: BotState
        get() = record.state

    val hasPendingExchangeWork: Boolean
        get() = record.hasPendingExchangeWork

    fun start(cycle: Int) {
        stateFailures = 0
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
        if (record.state == BotState.Disabled) {
            return
        }
        if (actor.isBusy) {
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
            BotState.TravelToExchange ->
                travel(BotDestination.Exchange, BotState.Listing, cycle, now)
            BotState.Listing -> list(now, cycle)
            BotState.Error -> recover(now, cycle)
        }
    }

    private fun decide(now: Long, cycle: Int) {
        val problem = actor.requirementProblem()
        if (problem != null) {
            error(problem, now, recoverable = false)
            return
        }
        if (gatherLimitReached(now)) {
            setActivity("Hourly gathering limit reached")
            return
        }
        val saleItem = saleItemId()
        if (saleItem != null && actor.heldNoted(saleItem) > 0) {
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
            if (record.config.essenceSource == EssenceSource.Mine) {
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
            GatherStatus.InventoryFull ->
                if (gatherTarget == BotDestination.SupplySource) {
                    travelTo(BotDestination.Resource, cycle)
                } else {
                    transition(BotState.TravelToBank, cycle, "Inventory full")
                }
            GatherStatus.NeedsSupplies ->
                transition(BotState.TravelToBank, cycle, "Out of supplies")
            GatherStatus.Hurt -> {
                actor.stopActivity()
                transition(BotState.TravelToBank, cycle, "Hurt; going to rest")
            }
            GatherStatus.Done ->
                if (gatherTarget == BotDestination.SupplySource && actor.needsSupplies()) {
                    transition(BotState.TravelToBank, cycle, "Fetching supplies")
                } else if (gatherTarget == BotDestination.SupplySource) {
                    travelTo(BotDestination.Resource, cycle)
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
            }
        }
        stateFailures = 0
        record = record.copy(errorRecoveries = 0)
        urgent = true

        if (!restock(now, cycle)) {
            return
        }
        if (actor.needsRest()) {
            transition(BotState.WaitingForSupplies, cycle, RESTING)
            return
        }
        if (saleDue(now)) {
            transition(BotState.PrepareSale, cycle, "Sale threshold reached")
            return
        }
        travelTo(gatherTarget, cycle)
    }

    /** Returns false when the bot cannot continue gathering yet. */
    private fun restock(now: Long, cycle: Int): Boolean {
        if (!actor.needsSupplies()) {
            gatherTarget = workTarget()
            return true
        }
        if (record.config.essenceSource == EssenceSource.Mine) {
            gatherTarget = BotDestination.SupplySource
            return true
        }
        return when (actor.withdrawSupplies()) {
            SupplyOutcome.NotNeeded,
            is SupplyOutcome.Withdrawn -> {
                gatherTarget = workTarget()
                true
            }
            SupplyOutcome.Unavailable -> {
                if (actor.canWorkWithoutSupplies()) {
                    gatherTarget = workTarget()
                    return true
                }
                if (actor.restsWithoutSupplies()) {
                    transition(BotState.WaitingForSupplies, cycle, RESTING)
                    log.info(record, "resting at the bank: no supplies left")
                } else {
                    transition(BotState.WaitingForSupplies, cycle, "Waiting for supplies in the bank")
                    log.warn(record, "paused: no supplies in bank")
                }
                false
            }
            SupplyOutcome.NoSpace -> {
                error("no inventory space for supplies", now, recoverable = false)
                false
            }
        }
    }

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
        when (actor.withdrawSupplies()) {
            SupplyOutcome.NotNeeded,
            is SupplyOutcome.Withdrawn -> {
                log.info(record, "supplies restocked")
                urgent = true
                travelTo(workTarget(), cycle)
            }
            SupplyOutcome.Unavailable ->
                if (actor.canWorkWithoutSupplies()) {
                    travelTo(workTarget(), cycle)
                } else {
                    setActivity(if (actor.restsWithoutSupplies()) RESTING else "Waiting for supplies in the bank")
                }
            SupplyOutcome.NoSpace -> error("no inventory space for supplies", now, false)
        }
    }

    /**
     * Credits everything the bot's offers hold to its bank and records it as pending; the exchange
     * is emptied on a later tick by [settleCollects], once the credited bank has been saved.
     */
    private fun collectProceeds() {
        if (!exchange.available || !storeHealthy()) {
            return
        }
        val busySlots = record.pendingCollects.mapTo(HashSet()) { it.slot }
        record.pendingListing?.let { busySlots += it.slot }
        for (offer in exchange.offers(owner)) {
            if (!offer.sell || !offer.hasCollectable || offer.slot in busySlots) {
                continue
            }
            val items = if (offer.collectItems > 0 && actor.creditBank(offer.itemId, offer.collectItems)) offer.collectItems else 0
            val coins = if (offer.collectCoins > 0) actor.creditBankCoins(offer.collectCoins.toLong()) else 0L
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
            val stats = record.stats.copy(coinsEarned = record.stats.coinsEarned + coins)
            record = record.copy(pendingCollects = record.pendingCollects + pending, stats = stats)
            log.info(record, "collected slot ${offer.slot}: $coins coins, $items items")
            urgent = true
        }
    }

    private fun prepareSale(now: Long, cycle: Int) {
        val item = saleItemId()
        if (item == null || !saleAllowed(now)) {
            travelTo(gatherTarget, cycle)
            return
        }
        when (val decision = priceFor(item, now)) {
            is PriceDecision.Paused -> {
                pauseSales(decision.reason, now)
                travelTo(gatherTarget, cycle)
                return
            }
            is PriceDecision.List -> {}
        }
        val quantity = minOf(record.config.sale.quantity.toLong(), actor.bankCount(item)).toInt()
        val withdrawn = if (quantity > 0) actor.withdrawNoted(item, quantity) else 0
        if (withdrawn <= 0) {
            log.warn(record, "could not withdraw items for sale")
            travelTo(gatherTarget, cycle)
            return
        }
        log.info(record, "withdrew $withdrawn x item $item for sale")
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
                travelTo(gatherTarget, cycle)
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
        val item = saleItemId()
        val held = item?.let(actor::held) ?: 0
        if (item == null || held == 0) {
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
        val offer = exchange.offer(owner, pending.slot)
        val ours =
            offer != null && offer.sell && offer.itemId == pending.itemId &&
                offer.total == pending.quantity && offer.price == pending.price
        when {
            ours && (recovering || exchange.saveCount > pending.placedAtSave) -> {
                record =
                    record.copy(
                        pendingListing = null,
                        stats = record.stats.copy(listed = record.stats.listed + pending.quantity),
                    )
                log.info(record, "listed ${pending.quantity} x item ${pending.itemId} in slot ${pending.slot}")
                listingOutcome = true
                urgent = true
            }
            ours -> {}
            !pending.placed && !recovering && durable() && exchange.available -> {
                val placed =
                    exchange.sell(
                        owner,
                        record.name,
                        pending.slot,
                        pending.itemId,
                        pending.quantity,
                        pending.price,
                    )
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

    private fun rollBackListing(pending: PendingListing, reason: String, now: Long) {
        val returned =
            actor.returnHeld(pending.itemId, pending.quantity) ||
                actor.creditBank(pending.itemId, pending.quantity)
        check(returned) {
            "Could not return ${pending.quantity} x ${pending.itemId} after a failed listing."
        }
        record = record.copy(pendingListing = null)
        log.warn(record, "listing rolled back ($reason); ${pending.quantity} items returned")
        pauseSales("listing failed: $reason", now)
        listingOutcome = false
        urgent = true
    }

    private fun settleCollects() {
        if (record.pendingCollects.isEmpty()) {
            return
        }
        val remaining = ArrayList<PendingCollect>()
        for (pending in record.pendingCollects) {
            val offer = exchange.offer(owner, pending.slot)?.takeIf { it.createdAt == pending.createdAt }
            if (offer == null) {
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
            if (!offer.isOpen || !offer.sell || now - offer.createdAt < timeout) {
                continue
            }
            if (!exchange.abort(owner, offer.slot)) {
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

    private fun saleItemId(): Int? {
        val sale = record.config.sale
        if (!sale.enabled || sale.item.isBlank() || sale.item !in sale.allowedItems) {
            return null
        }
        return items(sale.item)
    }

    private fun saleAllowed(now: Long): Boolean =
        saleItemId() != null && exchange.available && storeHealthy() && now >= record.salePausedUntil

    private fun saleDue(now: Long): Boolean {
        val item = saleItemId() ?: return false
        if (!saleAllowed(now) || record.pendingListing != null) {
            return false
        }
        if (hasOpenSale(item) || exchange.freeSlot(owner) == null) {
            return false
        }
        return actor.bankCount(item) >= record.config.sale.threshold
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
        if (next != record.state) {
            stateCycle = cycle
            if (next == BotState.Gathering) {
                lastProgressCycle = cycle
            }
        }
        record = record.copy(state = next, activity = activity)
        dirty = true
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
        const val NO_RESOURCE_TICKS: Int = 300
        const val BUSY_LIMIT_TICKS: Int = 100
        const val SUPPLY_RECHECK_TICKS: Int = 100
        private const val RESTING = "Resting until healed"
        const val MAX_STATE_FAILURES: Int = 3
        const val MAX_ERROR_RECOVERIES: Int = 3
        const val ERROR_COOLDOWN_MS: Long = 5 * 60_000L
        private const val HOUR_MS = 60 * 60_000L
    }
}
