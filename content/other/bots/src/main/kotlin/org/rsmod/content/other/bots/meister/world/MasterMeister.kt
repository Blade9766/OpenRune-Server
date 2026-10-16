package org.rsmod.content.other.bots.meister.world

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.nio.file.Path
import org.rsmod.content.other.bots.core.SavedCoords
import org.rsmod.content.other.bots.meister.MeisterCatalog
import org.rsmod.content.other.bots.meister.MeisterLog
import org.rsmod.content.other.bots.meister.MeisterSave
import org.rsmod.content.other.bots.meister.MeisterTrader
import org.rsmod.content.other.bots.world.GeBotExchange
import org.rsmod.content.other.grandexchange.GeExchange
import org.rsmod.content.other.grandexchange.GeItemData
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player

/**
 * Owns Master Meister: his trader, his ledger file and his player-bot body. Starts on the first tick (after
 * the exchange has loaded its book) and retries with a back-off if the ledger cannot be read; a
 * ledger that exists but cannot be read is never replaced by a fresh allocation.
 */
@Singleton
class MasterMeister
@Inject
constructor(
    private val exchange: GeExchange,
    private val geItems: GeItemData,
    private val presence: MeisterPresence,
    private val mapClock: MapClock,
) {
    private val logger = InlineLogger()
    private val store = FileMeisterStore(Path.of(SAVE_PATH))
    private var nextAttempt = 0
    private var lastSaveCycle = 0
    private var halted = false
    private var storeHealthy = true

    var config: MeisterWorldConfig = MeisterWorldConfig()
        private set

    var catalog: MeisterCatalog? = null
        private set

    var trader: MeisterTrader? = null
        private set

    val player: Player?
        get() = presence.player

    val storeOk: Boolean
        get() = storeHealthy

    fun tick() {
        if (halted) return
        val trader = trader ?: start() ?: return
        try {
            trader.tick(System.currentTimeMillis())
            presence.tick(trader, config)
            persist(trader, force = false)
        } catch (e: Exception) {
            halted = true
            logger.error(e) { "Master Meister failed; he is halted until restart." }
            persist(trader, force = true)
        }
    }

    fun shutdown() {
        val trader = trader ?: return
        persist(trader, force = true)
    }

    /** Saves at once; admin changes go through here so they survive a crash. */
    fun saveNow(): Boolean = trader?.let { persist(it, force = true) } ?: false

    fun sendHome(): Boolean = presence.sendHome(config)

    fun onLoggedOut(player: Player) = presence.onLoggedOut(player)

    private fun start(): MeisterTrader? {
        if (mapClock.cycle < nextAttempt) {
            return null
        }
        nextAttempt = mapClock.cycle + RETRY_TICKS
        config = MeisterWorldConfigs.load()
        if (!config.enabled) {
            return null
        }
        val save =
            try {
                store.load()
            } catch (t: Throwable) {
                logger.error(t) { "Could not read $SAVE_PATH; Master Meister waits $RETRY_TICKS ticks and tries again." }
                return null
            }
        val catalog = MeisterCatalogs.build(geItems, config)
        val now = System.currentTimeMillis()
        val trader =
            MeisterTrader(
                save = save ?: MeisterSave(),
                catalog = catalog,
                exchange = GeBotExchange(exchange, geItems),
                log = TradeLog,
                storeHealthy = { storeHealthy },
                recovering = save?.let { it.pendingListing != null || it.pendingCollects.isNotEmpty() } ?: false,
            )
        trader.initialize(config.startingCash, now)
        trader.start(now)
        if (!persist(trader, force = true)) {
            logger.warn { "Master Meister's ledger could not be written; he waits before trading." }
        }
        this.catalog = catalog
        this.trader = trader
        presence.restore(trader.coords, config)
        logger.info {
            "Master Meister ready: ${trader.cash} coins, ${trader.holdingsView().size} item kinds, " +
                "${trader.offers().size} exchange offers${if (save == null) " (first start)" else ""}."
        }
        return trader
    }

    private fun persist(trader: MeisterTrader, force: Boolean): Boolean {
        val due = force || trader.urgent || (trader.dirty && mapClock.cycle - lastSaveCycle >= PERIODIC_SAVE_TICKS)
        if (!due) {
            return true
        }
        presence.player?.let { trader.coords = SavedCoords(it.coords.x, it.coords.z, it.coords.level) }
        return try {
            store.write(trader.snapshot())
            storeHealthy = true
            trader.urgent = false
            trader.dirty = false
            lastSaveCycle = mapClock.cycle
            true
        } catch (e: Exception) {
            if (storeHealthy) {
                logger.error(e) { "Could not write $SAVE_PATH; Master Meister leaves the exchange alone until it works again." }
            }
            storeHealthy = false
            false
        }
    }

    private object TradeLog : MeisterLog {
        private val logger = InlineLogger("bots.meister")

        override fun info(message: String) {
            logger.info { "[Master Meister] $message" }
        }

        override fun warn(message: String) {
            logger.warn { "[Master Meister] $message" }
        }
    }

    private companion object {
        private const val SAVE_PATH = ".data/master-meister.json"
        private const val RETRY_TICKS = 500
        private const val PERIODIC_SAVE_TICKS = 100
    }
}
