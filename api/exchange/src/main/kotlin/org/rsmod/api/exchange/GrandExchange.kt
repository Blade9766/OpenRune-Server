package org.rsmod.api.exchange

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.exchange.persistence.ExchangeStore
import org.rsmod.api.exchange.persistence.JdbcExchangeStore
import org.rsmod.api.exchange.persistence.SerialDbWriter

/**
 * The server's single exchange. Loaded from the database on first use; until then (or while the
 * database writer is failing) [available] is false and no new economic state may be created.
 */
@Singleton
public class GrandExchange(
    private val store: ExchangeStore,
    private val clock: () -> Long,
    private val writer: SerialDbWriter?,
) {
    @Inject
    public constructor(
        writer: SerialDbWriter
    ) : this(JdbcExchangeStore(writer), System::currentTimeMillis, writer)

    private val logger = InlineLogger()
    private val listeners = ArrayList<(ExchangeMutation) -> Unit>()

    public val book: GrandExchangeBook = GrandExchangeBook(clock = clock)

    public var loaded: Boolean = false
        private set

    public val available: Boolean
        get() = loaded && store.healthy

    public fun ensureLoaded(): Boolean {
        if (loaded) {
            return true
        }
        try {
            val snapshot = store.load(clock() - TRADE_HISTORY_MS)
            book.load(snapshot.offers, snapshot.trades)
            book.reserveIds(snapshot.nextOfferId, snapshot.nextTradeId)
            loaded = true
            logger.info {
                "Grand Exchange loaded ${snapshot.offers.size} open offers and " +
                    "${snapshot.trades.size} recent trades."
            }
        } catch (t: Throwable) {
            logger.error(t) { "Grand Exchange could not load; trading stays closed." }
        }
        return loaded
    }

    /** Persists a player-initiated change and tells listeners (bots) about any trades. */
    public fun commit(mutation: ExchangeMutation) {
        if (mutation.isEmpty()) {
            return
        }
        store.persist(mutation)
        notify(mutation)
    }

    /** Tells listeners about a change whose persistence the caller handled itself. */
    public fun notify(mutation: ExchangeMutation) {
        for (listener in listeners) {
            listener(mutation)
        }
    }

    public fun addListener(listener: (ExchangeMutation) -> Unit) {
        listeners += listener
    }

    public fun shutdown() {
        writer?.shutdown(SHUTDOWN_FLUSH_MS)
    }

    public companion object {
        public const val TRADE_HISTORY_MS: Long = 7L * 24 * 60 * 60 * 1000
        private const val SHUTDOWN_FLUSH_MS = 30_000L
    }
}
