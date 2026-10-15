package org.rsmod.api.exchange.persistence

import org.rsmod.api.exchange.ExchangeMutation
import org.rsmod.api.exchange.ExchangeOffer
import org.rsmod.api.exchange.ExchangeTrade

public interface ExchangeStore {
    public val healthy: Boolean

    public fun load(tradesSince: Long): ExchangeSnapshot

    public fun persist(mutation: ExchangeMutation)
}

public class JdbcExchangeStore(private val writer: SerialDbWriter) : ExchangeStore {
    override val healthy: Boolean
        get() = writer.healthy

    override fun load(tradesSince: Long): ExchangeSnapshot =
        writer.readBlocking { connection ->
            ExchangeSql.createSchema(connection)
            ExchangeSql.load(connection, tradesSince)
        }

    override fun persist(mutation: ExchangeMutation) {
        if (mutation.isEmpty()) {
            return
        }
        writer.submit("exchange") { connection -> ExchangeSql.write(connection, mutation) }
    }
}

/** Keeps the last persisted copy of every row, the way the database would after a restart. */
public class InMemoryExchangeStore : ExchangeStore {
    public val offers: MutableMap<Long, ExchangeOffer> = LinkedHashMap()
    public val trades: MutableMap<Long, ExchangeTrade> = LinkedHashMap()

    override var healthy: Boolean = true

    override fun load(tradesSince: Long): ExchangeSnapshot =
        ExchangeSnapshot(
            offers = offers.values.map { it.snapshot() },
            trades = trades.values.filter { it.executedAt >= tradesSince },
            nextOfferId = (offers.keys.maxOrNull() ?: 0) + 1,
            nextTradeId = (trades.keys.maxOrNull() ?: 0) + 1,
        )

    override fun persist(mutation: ExchangeMutation) {
        apply(mutation)
    }

    public fun apply(mutation: ExchangeMutation) {
        for (offer in mutation.offers) {
            offers[offer.id] = offer.snapshot()
        }
        for (trade in mutation.trades) {
            trades.putIfAbsent(trade.id, trade)
        }
    }
}
