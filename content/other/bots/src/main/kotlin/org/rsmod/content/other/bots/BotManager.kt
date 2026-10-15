package org.rsmod.content.other.bots

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.nio.file.Path
import org.rsmod.content.other.bots.core.BotCoordinator
import org.rsmod.content.other.bots.core.BotLog
import org.rsmod.content.other.bots.core.BotRecord
import org.rsmod.content.other.bots.world.BotWorld
import org.rsmod.content.other.bots.world.FileBotStore
import org.rsmod.content.other.bots.world.GeBotExchange
import org.rsmod.content.other.grandexchange.GeExchange
import org.rsmod.content.other.grandexchange.GeItemData
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player

/**
 * Owns the [BotCoordinator] and connects it to the game: the Grand Exchange book players use, the
 * bot file store and the world spawner. Loads on the first tick, after the exchange has loaded its
 * book, and retries with a back-off if the bot file cannot be read.
 */
@Singleton
class BotManager
@Inject
constructor(
    private val exchange: GeExchange,
    private val geItems: GeItemData,
    private val world: BotWorld,
    private val mapClock: MapClock,
) {
    private val logger = InlineLogger()
    private var nextAttempt = 0
    private var halted = false

    var coordinator: BotCoordinator? = null
        private set

    fun tick() {
        if (halted) return
        val coordinator = coordinator ?: start() ?: return
        try {
            coordinator.tick(mapClock.cycle)
        } catch (e: Exception) {
            halted = true
            logger.error(e) { "Bot coordinator failed; bots are halted until restart." }
        }
    }

    fun botIdOf(player: Player): Long? = world.botIdOf(player)

    fun playerOf(botId: Long): Player? = world.player(botId)

    fun onBotLoggedOut(player: Player) {
        val coordinator = coordinator ?: return
        val id = world.botIdOf(player) ?: return
        val save = world.forget(id)
        coordinator.onExternalExit(id, save, shutdown = player.pendingShutdown)
    }

    fun onPlayerDeleted(player: Player) {
        world.onDeleted(player)
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
        val coordinator =
            BotCoordinator(
                store = FileBotStore(Path.of(SAVE_PATH)),
                exchange = GeBotExchange(exchange, geItems),
                spawner = world,
                items = ::itemId,
                log = BotLogger,
                coinsItemId = itemId("obj.coins"),
            )
        try {
            coordinator.load()
        } catch (t: Throwable) {
            logger.error(t) { "Could not load bots from $SAVE_PATH; retrying in $RETRY_TICKS ticks." }
            return null
        }
        this.coordinator = coordinator
        logger.info { "Loaded ${coordinator.all().size} bots (master ${coordinator.settings.masterEnabled})." }
        return coordinator
    }

    private object BotLogger : BotLog {
        private val logger = InlineLogger("bots")

        override fun info(bot: BotRecord, message: String) {
            logger.info { "[bot ${bot.id} ${bot.name}] $message" }
        }

        override fun warn(bot: BotRecord, message: String) {
            logger.warn { "[bot ${bot.id} ${bot.name}] $message" }
        }

        override fun error(message: String) {
            logger.error { message }
        }
    }

    private companion object {
        private const val RETRY_TICKS = 500
        private const val SAVE_PATH = ".data/bots.json"
    }
}
