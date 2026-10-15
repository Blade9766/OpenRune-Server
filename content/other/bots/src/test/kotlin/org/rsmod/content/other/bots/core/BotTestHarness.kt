package org.rsmod.content.other.bots.core

/**
 * A whole bot world over in-memory stores. The exchange is saved at the end of every tick, as the
 * server's grand exchange saves at the end of a cycle; [restart] rebuilds everything from what both
 * stores last persisted.
 */
class BotTestHarness(
    val store: InMemoryBotStore = InMemoryBotStore(),
    var now: Long = 10_000_000L,
    private val actorFactory: (BotRecord) -> FakeActor = { FakeActor() },
) {
    var cycle = 0
    val log = RecordingLog()
    val exchange = FakeExchange { now }

    lateinit var spawner: FakeSpawner
        private set

    lateinit var coordinator: BotCoordinator
        private set

    init {
        boot()
    }

    private fun boot() {
        spawner = FakeSpawner(actorFactory)
        coordinator = BotCoordinator(store, exchange, spawner, TEST_ITEMS::get, log, COINS) { now }
        coordinator.load()
    }

    /** Simulates a crash: unsaved exchange changes and everything in memory are lost. */
    fun restart() {
        exchange.restoreFromSave()
        boot()
    }

    fun tick(times: Int = 1, saveExchange: Boolean = true) {
        repeat(times) {
            cycle++
            now += 600
            coordinator.tick(cycle)
            if (saveExchange) {
                exchange.save()
            }
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

    fun record(id: Long): BotRecord = coordinator.find(id.toString())!!

    fun offers(id: Long): List<BotOffer> = exchange.offers(-id.toInt())

    fun playerBuys(qty: Int, price: Int, item: Int = LOGS, player: Int = 99): FakeExchange.Offer =
        exchange.playerBuys(player, item, qty, price)

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
