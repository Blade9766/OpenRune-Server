package org.rsmod.content.other.bots

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.exchange.ExchangeMutation
import org.rsmod.api.exchange.GrandExchange
import org.rsmod.api.exchange.persistence.SerialDbWriter
import org.rsmod.api.market.MarketPrices
import org.rsmod.content.other.bots.core.BotCoordinator
import org.rsmod.content.other.bots.core.BotLog
import org.rsmod.content.other.bots.core.BotMarket
import org.rsmod.content.other.bots.core.BotRecord
import org.rsmod.content.other.bots.world.BotWorld
import org.rsmod.content.other.bots.world.JdbcBotStore
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player

/**
 * Owns the [BotCoordinator] and connects it to the game: the shared exchange, the ordered database
 * writer, the world spawner and the market-price service. Loads lazily on the first tick after the
 * exchange is available and retries with a back-off if the database is not ready.
 */
@Singleton
class BotManager
@Inject
constructor(
    private val exchange: GrandExchange,
    private val writer: SerialDbWriter,
    private val world: BotWorld,
    private val marketPrices: MarketPrices,
    private val mapClock: MapClock,
) {
    private val logger = InlineLogger()
    private var nextAttempt = 0
    private var notifyingOwnChanges = false

    var coordinator: BotCoordinator? = null
        private set

    fun tick() {
        val coordinator = coordinator ?: start() ?: return
        coordinator.tick(mapClock.cycle)
    }

    fun botIdOf(player: Player): Long? = world.botIdOf(player)

    fun playerOf(botId: Long): Player? = world.player(botId)

    fun onBotLoggedOut(player: Player) {
        val coordinator = coordinator ?: return
        val id = world.botIdOf(player) ?: return
        val save = world.forget(id)
        coordinator.onExternalExit(id, save, shutdown = player.pendingShutdown)
    }

    fun shutdown() {
        coordinator?.shutdown()
    }

    fun itemId(name: String): Int? = runCatching { name.asRSCM(RSCMType.OBJ) }.getOrNull()

    private fun start(): BotCoordinator? {
        if (mapClock.cycle < nextAttempt) {
            return null
        }
        nextAttempt = mapClock.cycle + RETRY_TICKS
        if (!exchange.ensureLoaded()) {
            return null
        }
        val market = BotMarket(exchange.book, ::referencePrice) { exchange.available }
        val coordinator =
            BotCoordinator(
                store = JdbcBotStore(writer),
                book = exchange.book,
                market = market,
                spawner = world,
                items = ::itemId,
                log = BotLogger,
                coinsItemId = itemId("obj.coins"),
                onExchangeChange = ::notifyExchange,
            )
        try {
            coordinator.load()
        } catch (t: Throwable) {
            logger.error(t) { "Could not load bots; retrying in $RETRY_TICKS ticks." }
            return null
        }
        exchange.addListener { mutation ->
            if (!notifyingOwnChanges) {
                coordinator.onExchangeMutation(mutation)
            }
        }
        this.coordinator = coordinator
        logger.info { "Loaded ${coordinator.all().size} bots (master ${coordinator.settings.masterEnabled})." }
        return coordinator
    }

    private fun notifyExchange(mutation: ExchangeMutation) {
        notifyingOwnChanges = true
        try {
            exchange.notify(mutation)
        } finally {
            notifyingOwnChanges = false
        }
    }

    private fun referencePrice(itemId: Int): Int? =
        ServerCacheManager.getItem(itemId)?.let { marketPrices[it] }?.takeIf { it > 0 }

    private object BotLogger : BotLog {
        private val logger = InlineLogger("bots")

        override fun info(bot: BotRecord, message: String) {
            logger.info { "[bot ${bot.id} ${bot.name}] $message" }
        }

        override fun warn(bot: BotRecord, message: String) {
            logger.warn { "[bot ${bot.id} ${bot.name}] $message" }
        }
    }

    private companion object {
        private const val RETRY_TICKS = 500
    }
}
