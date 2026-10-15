package org.rsmod.content.other.bots.core

import org.rsmod.api.exchange.ExchangeOwner
import org.rsmod.api.exchange.GrandExchangeBook
import org.rsmod.api.exchange.OfferSide
import org.rsmod.api.exchange.PlaceResult

/** A whole bot world over in-memory stores; [restart] rebuilds it from what was persisted. */
class BotTestHarness(
    val store: InMemoryBotStore = InMemoryBotStore(),
    var now: Long = 10_000_000L,
    private val actorFactory: (BotRecord) -> FakeActor = { FakeActor() },
) {
    var cycle = 0
    val log = RecordingLog()
    var exchangeHealthy = true
    var serviceReference: Int? = 100

    lateinit var book: GrandExchangeBook
        private set

    lateinit var spawner: FakeSpawner
        private set

    lateinit var coordinator: BotCoordinator
        private set

    init {
        boot()
    }

    fun boot() {
        book = GrandExchangeBook(clock = { now })
        val snapshot = store.exchange.load(0)
        book.load(snapshot.offers, snapshot.trades)
        book.reserveIds(snapshot.nextOfferId, snapshot.nextTradeId)
        spawner = FakeSpawner(actorFactory)
        val market = BotMarket(book, { serviceReference }, { exchangeHealthy })
        coordinator =
            BotCoordinator(store, book, market, spawner, TEST_ITEMS::get, log, COINS) { now }
        coordinator.load()
    }

    /** Simulates a crash: everything in memory is dropped and rebuilt from the stores. */
    fun restart() {
        boot()
    }

    fun tick(times: Int = 1) {
        repeat(times) {
            cycle++
            now += 600
            coordinator.tick(cycle)
        }
    }

    fun tickUntil(limit: Int = 5_000, predicate: () -> Boolean) {
        repeat(limit) {
            if (predicate()) {
                return
            }
            tick()
        }
        error("condition not reached in $limit ticks; log=${log.lines.takeLast(15)}")
    }

    fun createBot(config: BotConfig = woodcuttingConfig()): BotRecord =
        coordinator.create(config, null, emptySet()).getOrThrow()

    fun actor(id: Long): FakeActor = spawner.actors.getValue(id)

    /** Evaluates [predicate] against the live character, or false while the bot is not spawned. */
    fun live(id: Long, predicate: (FakeActor) -> Boolean): Boolean =
        spawner.actors[id]?.let(predicate) ?: false

    fun state(id: Long): BotState = coordinator.find(id.toString())!!.state

    fun playerBuys(qty: Int, price: Int, item: Int = LOGS, player: Int = 99) {
        val result = book.place(ExchangeOwner.player(player), OfferSide.Buy, item, qty, price)
        result as PlaceResult.Placed
        store.exchange.persist(result.mutation)
        coordinator.onExchangeMutation(result.mutation)
    }

    companion object {
        fun woodcuttingConfig(
            threshold: Int = 1000,
            quantity: Int = 1000,
            item: String = "obj.logs",
            allowed: Set<String> = setOf("obj.logs"),
        ): BotConfig =
            BotConfig(
                role = BotRoleType.Woodcutting,
                site = "test_trees",
                sale = SaleConfig(item = item, threshold = threshold, quantity = quantity, allowedItems = allowed),
                pricing = PricingConfig(referencePrice = 100, adjustmentPercent = 0.0, minPrice = 50),
            )
    }
}
