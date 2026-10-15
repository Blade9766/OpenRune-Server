package org.rsmod.content.other.bots.core

import org.rsmod.api.exchange.ExchangeMutation
import org.rsmod.api.exchange.ExchangeOwner
import org.rsmod.api.exchange.ExchangeTax
import org.rsmod.api.exchange.GrandExchangeBook
import org.rsmod.api.exchange.OwnerKind

/** Brings bots into and out of the world. Implemented by the game layer, faked in tests. */
interface BotSpawner {
    /** Places the bot in the world from its saved state; null when it cannot be spawned now. */
    fun spawn(record: BotRecord): BotActor?

    /** The live character's current state, for persisting. */
    fun snapshot(botId: Long): BotSave?

    /** Removes the bot from the world and returns its final state. */
    fun despawn(botId: Long): BotSave?
}

data class BotStatusView(
    val record: BotRecord,
    val active: Boolean,
    val inventoryUsed: Int,
    val inventoryCapacity: Int,
    val bankedSaleItems: Long,
    val activeListings: Int,
    val coins: Long,
)

/**
 * Owns every bot: admin operations, the master toggle, the active-bot cap, ticking, and
 * persistence. Single-threaded (game thread). Each write to [store] carries the bot's full
 * inventory/bank snapshot together with any exchange rows changed in the same action, so the
 * database never holds one side of an item movement without the other.
 */
class BotCoordinator(
    private val store: BotStore,
    private val book: GrandExchangeBook,
    private val market: BotMarket,
    private val spawner: BotSpawner,
    private val items: (String) -> Int?,
    private val log: BotLog,
    private val coinsItemId: Int? = null,
    private val onExchangeChange: (ExchangeMutation) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val records = LinkedHashMap<Long, BotRecord>()
    private val live = LinkedHashMap<Long, LiveBot>()
    private val stopping = LinkedHashMap<Long, String>()
    private val exits = HashMap<Long, List<Long>>()
    private val spawnRetryAt = HashMap<Long, Int>()
    private var nextId = 1L
    private var lastPeriodicSave = 0

    var settings: BotGlobalSettings = BotGlobalSettings()
        private set

    var loaded: Boolean = false
        private set

    private class LiveBot(val brain: BotBrain, val actor: BotActor, var nextActionCycle: Int = 0)

    fun load() {
        val snapshot = store.load()
        settings = snapshot.settings
        records.clear()
        for (record in snapshot.bots) {
            val restored =
                when (record.state) {
                    BotState.Error -> record
                    else -> record.copy(state = BotState.Disabled, activity = "Loaded")
                }
            records[record.id] = restored
            nextId = maxOf(nextId, record.id + 1)
        }
        loaded = true
    }

    fun all(): List<BotRecord> = records.values.map { live[it.id]?.brain?.record ?: it }

    fun find(idOrName: String): BotRecord? {
        idOrName.toLongOrNull()?.let { id -> return all().firstOrNull { it.id == id } }
        val name = idOrName.replace('_', ' ')
        return all().firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    fun isLive(id: Long): Boolean = id in live

    fun create(config: BotConfig, name: String?, takenNames: Set<String>): Result<BotRecord> {
        val errors = config.validate()
        if (errors.isNotEmpty()) {
            return Result.failure(IllegalArgumentException(errors.joinToString("; ")))
        }
        val taken = takenNames + records.values.map { it.name }
        val chosen = name?.trim() ?: BotNames.generate(taken)
        if (!BotNames.isAcceptable(chosen)) {
            return Result.failure(
                IllegalArgumentException("names are 1-12 letters/digits and may not end in 'bot'")
            )
        }
        if (taken.any { it.equals(chosen, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("the name '$chosen' is taken"))
        }
        val now = clock()
        val record =
            BotRecord(
                id = nextId++,
                name = chosen,
                config = config,
                createdAt = now,
                updatedAt = now,
                activity = "Created",
            )
        records[record.id] = record
        write(BotWrite(bots = listOf(record)))
        log.info(record, "created (${config.role.key} at ${config.site})")
        return Result.success(record)
    }

    fun enable(id: Long): String? {
        val record = records[id] ?: return "no such bot"
        val errors = record.config.validate()
        if (errors.isNotEmpty()) {
            return errors.joinToString("; ")
        }
        stopping.remove(id)
        val updated =
            currentOf(record).copy(enabled = true).let {
                if (it.state == BotState.Error && id !in live) {
                    it.copy(state = BotState.Disabled, lastError = null, errorRecoveries = 0)
                } else {
                    it
                }
            }
        put(updated)
        live[id]?.brain?.let { brain ->
            brain.updateRecord { it.copy(enabled = true) }
            if (brain.state == BotState.Error) {
                brain.clearError(0)
            }
        }
        persist(id)
        log.info(updated, "enabled")
        return null
    }

    /** Disables a bot; it leaves the world once it is not mid-action. Items stay with the bot. */
    fun disable(id: Long, reason: String = "Disabled by an administrator"): String? {
        val record = records[id] ?: return "no such bot"
        put(currentOf(record).copy(enabled = false))
        if (id in live) {
            stopping[id] = reason
            live[id]?.brain?.updateRecord { it.copy(enabled = false) }
        } else {
            put(records.getValue(id).copy(state = BotState.Disabled, activity = reason))
            persist(id)
        }
        log.info(records.getValue(id), "disabled")
        return null
    }

    fun remove(id: Long, force: Boolean): String? {
        val record = records[id] ?: return "no such bot"
        if (id in live) {
            return "disable the bot and wait for it to leave the world first"
        }
        val offers = book.offers(ExchangeOwner.bot(id))
        if (offers.isNotEmpty() && !force) {
            return "the bot still has ${offers.size} exchange offer(s); collect them or use force"
        }
        var mutation = ExchangeMutation.EMPTY
        val owner = ExchangeOwner.bot(id)
        for (offer in offers) {
            book.abort(owner, offer.id)?.let { mutation += it }
            val owed = book.collectable(owner, offer.id) ?: continue
            book.confirmCollect(owner, owed)?.let { mutation += it }
        }
        records.remove(id)
        write(BotWrite(deleted = listOf(id), exchange = mutation))
        log.info(record, "removed${if (force) " (forced, offers cancelled)" else ""}")
        return null
    }

    fun setMaster(enabled: Boolean) {
        settings = settings.copy(masterEnabled = enabled)
        write(BotWrite(settings = settings))
        if (!enabled) {
            for (id in live.keys.toList()) {
                stopping[id] = "Master toggle is off"
            }
        }
    }

    fun setMaxActive(max: Int): String? {
        if (max !in 0..MAX_ACTIVE_LIMIT) {
            return "max active bots must be between 0 and $MAX_ACTIVE_LIMIT"
        }
        settings = settings.copy(maxActiveBots = max)
        write(BotWrite(settings = settings))
        return null
    }

    fun updateConfig(id: Long, transform: (BotConfig) -> BotConfig): String? {
        val record = records[id] ?: return "no such bot"
        val config = transform(currentOf(record).config)
        val errors = config.validate()
        if (errors.isNotEmpty()) {
            return errors.joinToString("; ")
        }
        val brain = live[id]?.brain
        if (brain != null) {
            brain.updateRecord { it.copy(config = config) }
        } else {
            put(record.copy(config = config))
        }
        persist(id)
        log.info(currentOf(record), "config changed")
        return null
    }

    /** Edits a bot's stored character; only allowed while it is out of the world. */
    fun editSave(id: Long, edit: (BotSave) -> BotSave?): String? {
        val record = records[id] ?: return "no such bot"
        if (id in live) {
            return "disable the bot first; its character is in use"
        }
        val edited = edit(record.save) ?: return "that change does not fit"
        put(record.copy(save = edited))
        persist(id)
        return null
    }

    fun clearError(id: Long): String? {
        val record = records[id] ?: return "no such bot"
        val brain = live[id]?.brain
        if (brain != null) {
            brain.clearError(0)
        } else {
            put(record.copy(state = BotState.Disabled, lastError = null, errorRecoveries = 0))
        }
        persist(id)
        return null
    }

    fun tick(cycle: Int) {
        if (!loaded) {
            return
        }
        processStops()
        reconcileActive(cycle)
        val now = clock()
        for ((id, bot) in live) {
            if (id in stopping || cycle < bot.nextActionCycle) {
                continue
            }
            bot.nextActionCycle = cycle + bot.brain.record.config.actionIntervalTicks
            bot.brain.tick(now, cycle)
            val mutation = market.drain()
            handleTrades(mutation)
            if (bot.brain.urgent || !mutation.isEmpty()) {
                persist(id, mutation)
            }
        }
        if (cycle - lastPeriodicSave >= PERIODIC_SAVE_TICKS) {
            lastPeriodicSave = cycle
            for ((id, bot) in live) {
                if (bot.brain.dirty) {
                    persist(id)
                }
            }
        }
    }

    /** Called for every exchange change made outside a bot's own tick (players trading). */
    fun onExchangeMutation(mutation: ExchangeMutation) {
        handleTrades(mutation)
    }

    /**
     * The engine removed a bot by itself (shutdown, forced disconnect). Saves it; a bot that keeps
     * getting removed is parked in [BotState.Error] instead of being respawned forever.
     */
    fun onExternalExit(id: Long, save: BotSave?, shutdown: Boolean) {
        val bot = live.remove(id) ?: return
        stopping.remove(id)
        bot.brain.stop(if (shutdown) "Server shutting down" else "Removed from the world")
        val now = clock()
        val previous = (exits[id] ?: emptyList()).filter { now - it < EXIT_WINDOW_MS }
        val recent = if (shutdown) previous else previous + now
        exits[id] = recent
        val parked = recent.size >= MAX_EXITS_PER_WINDOW
        val record =
            bot.brain.record.copy(
                save = save ?: bot.brain.record.save,
                state = if (parked) BotState.Error else BotState.Disabled,
                lastError = if (parked) "repeatedly removed from the world" else bot.brain.record.lastError,
                errorRecoveries = if (parked) BotBrain.MAX_ERROR_RECOVERIES else bot.brain.record.errorRecoveries,
                updatedAt = now,
            )
        records[id] = record
        write(BotWrite(bots = listOf(record), exchange = market.drain()))
        val reason = if (shutdown) "server shutdown" else "unexpectedly"
        log.warn(record, "left the world ($reason)${if (parked) "; parked in error" else ""}")
    }

    fun shutdown() {
        for (id in live.keys.toList()) {
            despawn(id, "Server shutting down", keepState = false)
        }
    }

    fun status(): List<BotStatusView> =
        all().map { record ->
            val actor = live[record.id]?.actor
            val saleItem = record.config.sale.item.takeIf { it.isNotBlank() }?.let(items)
            val save = record.save
            val banked =
                when {
                    saleItem == null -> 0L
                    actor != null -> actor.bankCount(saleItem)
                    else -> save.bank.filter { it.id == saleItem }.sumOf { it.count.toLong() }
                }
            BotStatusView(
                record = record,
                active = actor != null,
                inventoryUsed = actor?.inventoryUsed ?: save.inv.size,
                inventoryCapacity = actor?.inventoryCapacity ?: INVENTORY_SIZE,
                bankedSaleItems = banked,
                activeListings = book.offers(ExchangeOwner.bot(record.id)).count { it.isOpen },
                coins = actor?.coins() ?: savedCoins(save),
            )
        }

    private fun savedCoins(save: BotSave): Long {
        val coins = coinsItemId ?: return 0
        return (save.inv + save.bank).filter { it.id == coins }.sumOf { it.count.toLong() }
    }

    private fun processStops() {
        for ((id, reason) in stopping.toList()) {
            val bot = live[id]
            if (bot == null) {
                stopping.remove(id)
                continue
            }
            if (bot.actor.isBusy) {
                bot.actor.stopActivity()
                continue
            }
            despawn(id, reason, keepState = true)
            stopping.remove(id)
        }
    }

    private fun reconcileActive(cycle: Int) {
        if (!settings.masterEnabled) {
            return
        }
        val wanted =
            records.values
                .filter { it.enabled && (it.state != BotState.Error || it.id in live) }
                .map { it.id }
                .take(settings.maxActiveBots)
                .toSet()
        for (id in live.keys.toList()) {
            if (id !in wanted && id !in stopping) {
                stopping[id] = "Over the active bot limit"
            }
        }
        for (id in wanted) {
            if (id in live || id in stopping) {
                continue
            }
            if (cycle < (spawnRetryAt[id] ?: 0)) {
                continue
            }
            val record = records.getValue(id)
            val actor = spawner.spawn(record)
            if (actor == null) {
                spawnRetryAt[id] = cycle + SPAWN_RETRY_TICKS
                log.warn(record, "could not spawn; retrying in $SPAWN_RETRY_TICKS ticks")
                continue
            }
            spawnRetryAt.remove(id)
            val brain = BotBrain(record, actor, market, items, log)
            brain.start(cycle)
            live[id] = LiveBot(brain, actor, cycle)
            log.info(record, "spawned")
        }
    }

    private fun despawn(id: Long, reason: String, keepState: Boolean) {
        val bot = live[id] ?: return
        bot.brain.stop(reason)
        val mutation = market.drain()
        val save = spawner.despawn(id) ?: bot.brain.record.save
        live.remove(id)
        val state = if (keepState && bot.brain.record.lastError != null &&
            bot.brain.record.errorRecoveries >= BotBrain.MAX_ERROR_RECOVERIES
        ) {
            BotState.Error
        } else {
            BotState.Disabled
        }
        val record = bot.brain.record.copy(save = save, state = state, updatedAt = clock())
        records[id] = record
        write(BotWrite(bots = listOf(record), exchange = mutation))
        log.info(record, "left the world ($reason)")
    }

    private fun handleTrades(mutation: ExchangeMutation) {
        for (trade in mutation.trades) {
            val offer = book.offer(trade.sellOfferId)
                ?: mutation.offers.firstOrNull { it.id == trade.sellOfferId }
                ?: continue
            if (offer.owner.kind != OwnerKind.Bot) {
                continue
            }
            val id = offer.owner.id
            val net = trade.quantity.toLong() * trade.price - ExchangeTax.total(trade.price, trade.quantity)
            val update: (BotRecord) -> BotRecord = {
                it.copy(
                    stats =
                        it.stats.copy(
                            sold = it.stats.sold + trade.quantity,
                            coinsEarned = it.stats.coinsEarned + net,
                        )
                )
            }
            val brain = live[id]?.brain
            if (brain != null) {
                brain.updateRecord(update)
            } else {
                records[id]?.let { records[id] = update(it) }
            }
            records[id]?.let { log.info(currentOf(it), "sold ${trade.quantity} x item ${trade.itemId} at ${trade.price}") }
            if (brain == null) {
                persist(id)
            }
        }
    }

    private fun persist(id: Long, mutation: ExchangeMutation = ExchangeMutation.EMPTY) {
        val bot = live[id]
        val record =
            if (bot != null) {
                val save = spawner.snapshot(id) ?: bot.brain.record.save
                bot.brain.updateRecord { it.copy(save = save, updatedAt = clock()) }
                bot.brain.dirty = false
                bot.brain.urgent = false
                bot.brain.record
            } else {
                records[id] ?: return
            }
        records[id] = record
        write(BotWrite(bots = listOf(record), exchange = mutation))
    }

    private fun write(write: BotWrite) {
        store.write(write)
        if (!write.exchange.isEmpty()) {
            onExchangeChange(write.exchange)
        }
    }

    private fun currentOf(record: BotRecord): BotRecord = live[record.id]?.brain?.record ?: record

    private fun put(record: BotRecord) {
        records[record.id] = record
    }

    companion object {
        const val MAX_ACTIVE_LIMIT: Int = 200
        const val INVENTORY_SIZE: Int = 28
        private const val EXIT_WINDOW_MS = 60 * 60_000L
        private const val MAX_EXITS_PER_WINDOW = 3
        private const val PERIODIC_SAVE_TICKS = 100
        private const val SPAWN_RETRY_TICKS = 100
    }
}
