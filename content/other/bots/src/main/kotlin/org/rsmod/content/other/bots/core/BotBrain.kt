package org.rsmod.content.other.bots.core

import org.rsmod.api.exchange.ExchangeOwner
import org.rsmod.api.exchange.OfferSide
import org.rsmod.api.exchange.PlaceResult

/**
 * The per-bot state machine. It only decides; every world interaction goes through [BotActor] and
 * every exchange interaction through [BotMarket]. A state that cannot make progress within its
 * budget fails, failures are retried a bounded number of times, and repeated failures park the
 * bot in [BotState.Error] so it can never spin forever.
 */
class BotBrain(
    initial: BotRecord,
    private val actor: BotActor,
    private val market: BotMarket,
    private val items: (String) -> Int?,
    private val log: BotLog,
) {
    var record: BotRecord = initial
        private set

    /** Set when the record changed in a way that should reach the database soon. */
    var dirty: Boolean = false

    /** Set when the record (and inventory) must be persisted before anything else happens. */
    var urgent: Boolean = false

    private val owner = ExchangeOwner.bot(initial.id)

    private var stateCycle = 0
    private var lastProgressCycle = 0
    private var busyTicks = 0
    private var stateFailures = 0
    private var gatherTarget = BotDestination.Resource
    private var travelTarget = BotDestination.Resource
    private var errorUntil = 0L

    val state: BotState
        get() = record.state

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
        if (saleItem != null && actor.held(saleItem) > 0) {
            val next = if (saleAllowed(now)) BotState.TravelToExchange else BotState.TravelToBank
            transition(next, cycle, "Carrying items for sale")
            return
        }
        if (actor.inventoryUsed >= actor.inventoryCapacity) {
            transition(BotState.TravelToBank, cycle, "Inventory full")
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
        collectProceeds()
        when (val outcome = actor.depositGathered()) {
            DepositOutcome.BankFull -> {
                error("bank is full", now, recoverable = false)
                return
            }
            is DepositOutcome.Deposited -> {
                val total = outcome.items.values.sumOf { it.toLong() }
                if (total > 0) {
                    record = record.copy(stats = record.stats.copy(deposited = record.stats.deposited + total))
                    log.info(
                        record,
                        "deposited ${describe(outcome.items)} (gathered ${record.stats.gathered}, " +
                            "deposited ${record.stats.deposited} in total)",
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
        if (saleDue(now)) {
            transition(BotState.PrepareSale, cycle, "Sale threshold reached")
            return
        }
        travelTo(gatherTarget, cycle)
    }

    /** Returns false when the bot cannot continue gathering yet. */
    private fun restock(now: Long, cycle: Int): Boolean {
        if (!actor.needsSupplies()) {
            gatherTarget = BotDestination.Resource
            return true
        }
        if (record.config.essenceSource == EssenceSource.Mine) {
            gatherTarget = BotDestination.SupplySource
            return true
        }
        return when (actor.withdrawSupplies()) {
            SupplyOutcome.NotNeeded,
            is SupplyOutcome.Withdrawn -> {
                gatherTarget = BotDestination.Resource
                true
            }
            SupplyOutcome.Unavailable -> {
                transition(BotState.WaitingForSupplies, cycle, "Waiting for supplies in the bank")
                log.warn(record, "paused: no supplies in bank")
                false
            }
            SupplyOutcome.NoSpace -> {
                error("no inventory space for supplies", now, recoverable = false)
                false
            }
        }
    }

    private fun waitForSupplies(now: Long, cycle: Int) {
        if ((cycle - stateCycle) % SUPPLY_RECHECK_TICKS != 0) {
            return
        }
        if (actor.travel(BotDestination.Bank) != TravelStatus.Arrived) {
            transition(BotState.TravelToBank, cycle, "Returning to bank")
            return
        }
        when (actor.withdrawSupplies()) {
            SupplyOutcome.NotNeeded,
            is SupplyOutcome.Withdrawn -> {
                log.info(record, "supplies restocked")
                urgent = true
                travelTo(BotDestination.Resource, cycle)
            }
            SupplyOutcome.Unavailable -> setActivity("Waiting for supplies in the bank")
            SupplyOutcome.NoSpace -> error("no inventory space for supplies", now, false)
        }
    }

    private fun collectProceeds() {
        if (!market.isAvailable) {
            return
        }
        for (offer in market.offers(owner)) {
            if (!offer.hasCollectable) {
                continue
            }
            val owed = market.collectable(owner, offer.id, Int.MAX_VALUE, Long.MAX_VALUE) ?: continue
            val items = if (owed.items > 0 && actor.creditBank(offer.itemId, owed.items)) owed.items else 0
            val coins = if (owed.coins > 0) actor.creditBankCoins(owed.coins) else 0L
            val collected = owed.copy(items = items, coins = coins)
            if (collected.isEmpty) {
                log.warn(record, "could not collect offer ${offer.id}: bank has no room")
                continue
            }
            check(market.confirmCollect(owner, collected)) {
                "Collection for offer ${offer.id} was rejected after crediting the bank."
            }
            log.info(record, "collected offer ${offer.id}: $coins coins, $items items")
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
        abortStaleOffers(now)
        val item = saleItemId()
        val held = item?.let(actor::held) ?: 0
        if (item == null || held == 0) {
            transition(BotState.TravelToBank, cycle, "Nothing to list")
            return
        }
        if (!saleAllowed(now) || market.hasOpenSale(owner, item)) {
            transition(BotState.TravelToBank, cycle, "Returning unsold items to the bank")
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
        when (val result = market.sell(owner, item, held, decision.price, record.listingRef)) {
            is PlaceResult.Placed -> {
                record =
                    record.copy(
                        saleSeq = record.saleSeq + 1,
                        stats = record.stats.copy(listed = record.stats.listed + held),
                    )
                log.info(
                    record,
                    "listed $held x item $item at ${decision.price} each (${decision.basis}), " +
                        "offer ${result.offer.id}",
                )
                urgent = true
                travelTo(gatherTarget, cycle)
            }
            is PlaceResult.Rejected -> {
                check(actor.returnHeld(item, held)) {
                    "Could not return $held x $item after a rejected listing."
                }
                if (result == PlaceResult.DuplicateRef) {
                    record = record.copy(saleSeq = record.saleSeq + 1)
                }
                pauseSales("listing rejected: ${result.message}", now)
                urgent = true
                transition(BotState.TravelToBank, cycle, "Listing rejected")
            }
        }
    }

    private fun abortStaleOffers(now: Long) {
        val timeout = record.config.pricing.listingTimeoutMinutes * 60_000L
        for (offer in market.offers(owner)) {
            if (!offer.isOpen || offer.side != OfferSide.Sell || now - offer.createdAt < timeout) {
                continue
            }
            if (!market.abort(owner, offer.id)) {
                continue
            }
            val retries = record.saleRetries + 1
            log.info(record, "aborted unsold offer ${offer.id} (${offer.remaining} left), retry $retries")
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
        val signal = market.priceSignal(itemId, SalePricing.guideSettings(pricing), now)
        return SalePricing.decide(
            config = pricing,
            signal = signal,
            serviceReference = market.referencePrice(itemId),
            bestCompetingAsk = market.bestCompetingAsk(itemId, owner),
            retries = record.saleRetries,
        )
    }

    private fun pauseSales(reason: String, now: Long) {
        val until = now + record.config.pricing.cooldownMinutes * 60_000L
        record = record.copy(salePausedUntil = until)
        log.warn(record, "selling paused: $reason")
        dirty = true
    }

    private fun saleItemId(): Int? {
        val sale = record.config.sale
        if (!sale.enabled || sale.item.isBlank() || sale.item !in sale.allowedItems) {
            return null
        }
        return items(sale.item)
    }

    private fun saleAllowed(now: Long): Boolean =
        saleItemId() != null && market.isAvailable && now >= record.salePausedUntil

    private fun saleDue(now: Long): Boolean {
        val item = saleItemId() ?: return false
        if (!saleAllowed(now)) {
            return false
        }
        if (market.hasOpenSale(owner, item)) {
            return hasStaleOffer(now)
        }
        if (!market.hasFreeSlot(owner)) {
            return false
        }
        return actor.bankCount(item) >= record.config.sale.threshold
    }

    private fun hasStaleOffer(now: Long): Boolean {
        val timeout = record.config.pricing.listingTimeoutMinutes * 60_000L
        return market.offers(owner).any {
            it.isOpen && it.side == OfferSide.Sell && now - it.createdAt >= timeout
        }
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
        const val TRAVEL_BUDGET_TICKS: Int = 500
        const val GATHER_STALL_TICKS: Int = 200
        const val NO_RESOURCE_TICKS: Int = 300
        const val BUSY_LIMIT_TICKS: Int = 100
        const val SUPPLY_RECHECK_TICKS: Int = 100
        const val MAX_STATE_FAILURES: Int = 3
        const val MAX_ERROR_RECOVERIES: Int = 3
        const val ERROR_COOLDOWN_MS: Long = 5 * 60_000L
        private const val HOUR_MS = 60 * 60_000L
    }
}
