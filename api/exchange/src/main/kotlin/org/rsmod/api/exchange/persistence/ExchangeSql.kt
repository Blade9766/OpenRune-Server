package org.rsmod.api.exchange.persistence

import java.sql.ResultSet
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.db.util.setNullableString
import org.rsmod.api.exchange.ExchangeMutation
import org.rsmod.api.exchange.ExchangeOffer
import org.rsmod.api.exchange.ExchangeOwner
import org.rsmod.api.exchange.ExchangeTrade
import org.rsmod.api.exchange.OfferSide
import org.rsmod.api.exchange.OfferStatus
import org.rsmod.api.exchange.OwnerKind

/**
 * The exchange owns its tables (the Flyway set lives in the external central library), so they are
 * created idempotently on startup. Every write is an upsert keyed by the in-memory id, which makes
 * replaying a write after a retry harmless.
 */
public object ExchangeSql {
    private val SCHEMA =
        listOf(
            """
            CREATE TABLE IF NOT EXISTS exchange_offers (
                id BIGINT PRIMARY KEY,
                owner_kind VARCHAR(16) NOT NULL,
                owner_id BIGINT NOT NULL,
                slot INT NOT NULL,
                item_id INT NOT NULL,
                side VARCHAR(8) NOT NULL,
                quantity INT NOT NULL,
                price INT NOT NULL,
                filled INT NOT NULL,
                coins_traded BIGINT NOT NULL,
                tax_paid BIGINT NOT NULL,
                collect_items INT NOT NULL,
                collect_coins BIGINT NOT NULL,
                status VARCHAR(16) NOT NULL,
                client_ref VARCHAR(96),
                created_at BIGINT NOT NULL,
                updated_at BIGINT NOT NULL
            )
            """,
            """
            CREATE INDEX IF NOT EXISTS exchange_offers_open
                ON exchange_offers (status) WHERE status <> 'Closed'
            """,
            """
            CREATE TABLE IF NOT EXISTS exchange_trades (
                id BIGINT PRIMARY KEY,
                item_id INT NOT NULL,
                quantity INT NOT NULL,
                price INT NOT NULL,
                buy_offer_id BIGINT NOT NULL,
                sell_offer_id BIGINT NOT NULL,
                executed_at BIGINT NOT NULL
            )
            """,
            """
            CREATE INDEX IF NOT EXISTS exchange_trades_item_time
                ON exchange_trades (item_id, executed_at)
            """,
        )

    public fun createSchema(connection: DatabaseConnection) {
        for (sql in SCHEMA) {
            connection.prepareStatement(sql.trimIndent()).use { it.execute() }
        }
    }

    public fun write(connection: DatabaseConnection, mutation: ExchangeMutation) {
        if (mutation.offers.isNotEmpty()) {
            upsertOffers(connection, mutation.offers)
        }
        if (mutation.trades.isNotEmpty()) {
            insertTrades(connection, mutation.trades)
        }
    }

    private fun upsertOffers(connection: DatabaseConnection, offers: List<ExchangeOffer>) {
        val sql =
            """
            INSERT INTO exchange_offers (id, owner_kind, owner_id, slot, item_id, side, quantity,
                price, filled, coins_traded, tax_paid, collect_items, collect_coins, status,
                client_ref, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                filled = EXCLUDED.filled,
                coins_traded = EXCLUDED.coins_traded,
                tax_paid = EXCLUDED.tax_paid,
                collect_items = EXCLUDED.collect_items,
                collect_coins = EXCLUDED.collect_coins,
                status = EXCLUDED.status,
                updated_at = EXCLUDED.updated_at
            """
                .trimIndent()
        connection.prepareStatement(sql).use { statement ->
            for (offer in offers) {
                statement.setLong(1, offer.id)
                statement.setString(2, offer.owner.kind.name)
                statement.setLong(3, offer.owner.id)
                statement.setInt(4, offer.slot)
                statement.setInt(5, offer.itemId)
                statement.setString(6, offer.side.name)
                statement.setInt(7, offer.quantity)
                statement.setInt(8, offer.price)
                statement.setInt(9, offer.filled)
                statement.setLong(10, offer.coinsTraded)
                statement.setLong(11, offer.taxPaid)
                statement.setInt(12, offer.collectItems)
                statement.setLong(13, offer.collectCoins)
                statement.setString(14, offer.status.name)
                statement.setNullableString(15, offer.clientRef)
                statement.setLong(16, offer.createdAt)
                statement.setLong(17, offer.updatedAt)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    private fun insertTrades(connection: DatabaseConnection, trades: List<ExchangeTrade>) {
        val sql =
            """
            INSERT INTO exchange_trades (id, item_id, quantity, price, buy_offer_id,
                sell_offer_id, executed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """
                .trimIndent()
        connection.prepareStatement(sql).use { statement ->
            for (trade in trades) {
                statement.setLong(1, trade.id)
                statement.setInt(2, trade.itemId)
                statement.setInt(3, trade.quantity)
                statement.setInt(4, trade.price)
                statement.setLong(5, trade.buyOfferId)
                statement.setLong(6, trade.sellOfferId)
                statement.setLong(7, trade.executedAt)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    public fun load(connection: DatabaseConnection, tradesSince: Long): ExchangeSnapshot {
        val offers = ArrayList<ExchangeOffer>()
        connection
            .prepareStatement("SELECT * FROM exchange_offers WHERE status <> 'Closed'")
            .use { statement ->
                statement.executeQuery().use { rs ->
                    while (rs.next()) {
                        offers += rs.toOffer()
                    }
                }
            }
        val trades = ArrayList<ExchangeTrade>()
        connection
            .prepareStatement("SELECT * FROM exchange_trades WHERE executed_at >= ? ORDER BY id")
            .use { statement ->
                statement.setLong(1, tradesSince)
                statement.executeQuery().use { rs ->
                    while (rs.next()) {
                        trades += rs.toTrade()
                    }
                }
            }
        val maxOfferId = selectMax(connection, "SELECT COALESCE(MAX(id), 0) FROM exchange_offers")
        val maxTradeId = selectMax(connection, "SELECT COALESCE(MAX(id), 0) FROM exchange_trades")
        return ExchangeSnapshot(offers, trades, maxOfferId + 1, maxTradeId + 1)
    }

    private fun selectMax(connection: DatabaseConnection, sql: String): Long =
        connection.prepareStatement(sql).use { statement ->
            statement.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0L }
        }

    private fun ResultSet.toOffer(): ExchangeOffer =
        ExchangeOffer(
            id = getLong("id"),
            owner = ExchangeOwner(OwnerKind.valueOf(getString("owner_kind")), getLong("owner_id")),
            slot = getInt("slot"),
            itemId = getInt("item_id"),
            side = OfferSide.valueOf(getString("side")),
            quantity = getInt("quantity"),
            price = getInt("price"),
            filled = getInt("filled"),
            coinsTraded = getLong("coins_traded"),
            taxPaid = getLong("tax_paid"),
            collectItems = getInt("collect_items"),
            collectCoins = getLong("collect_coins"),
            status = OfferStatus.valueOf(getString("status")),
            clientRef = getString("client_ref"),
            createdAt = getLong("created_at"),
            updatedAt = getLong("updated_at"),
        )

    private fun ResultSet.toTrade(): ExchangeTrade =
        ExchangeTrade(
            id = getLong("id"),
            itemId = getInt("item_id"),
            quantity = getInt("quantity"),
            price = getInt("price"),
            buyOfferId = getLong("buy_offer_id"),
            sellOfferId = getLong("sell_offer_id"),
            executedAt = getLong("executed_at"),
        )
}

public data class ExchangeSnapshot(
    val offers: List<ExchangeOffer>,
    val trades: List<ExchangeTrade>,
    val nextOfferId: Long,
    val nextTradeId: Long,
)
