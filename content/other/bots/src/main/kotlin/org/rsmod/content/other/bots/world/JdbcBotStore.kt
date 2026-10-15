package org.rsmod.content.other.bots.world

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.exchange.persistence.ExchangeSql
import org.rsmod.api.exchange.persistence.SerialDbWriter
import org.rsmod.content.other.bots.core.BotGlobalSettings
import org.rsmod.content.other.bots.core.BotRecord
import org.rsmod.content.other.bots.core.BotStore
import org.rsmod.content.other.bots.core.BotStoreSnapshot
import org.rsmod.content.other.bots.core.BotWrite

/**
 * Bot rows live next to the exchange tables and go through the same ordered writer, so a bot's
 * inventory/bank snapshot and the offers it changed are committed in one transaction.
 */
class JdbcBotStore(private val writer: SerialDbWriter) : BotStore {
    private val mapper: ObjectMapper =
        jacksonObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    override val healthy: Boolean
        get() = writer.healthy

    override fun load(): BotStoreSnapshot =
        writer.readBlocking { connection ->
            createSchema(connection)
            ExchangeSql.createSchema(connection)
            BotStoreSnapshot(loadBots(connection), loadSettings(connection))
        }

    override fun write(write: BotWrite) {
        val bots = write.bots.map { it to mapper.writeValueAsString(it) }
        writer.submit("bots") { connection ->
            for ((bot, json) in bots) {
                upsertBot(connection, bot, json)
            }
            for (id in write.deleted) {
                connection.prepareStatement("DELETE FROM bots WHERE id = ?").use {
                    it.setLong(1, id)
                    it.executeUpdate()
                }
            }
            write.settings?.let { upsertSettings(connection, it) }
            ExchangeSql.write(connection, write.exchange)
        }
    }

    private fun createSchema(connection: DatabaseConnection) {
        val statements =
            listOf(
                """
                CREATE TABLE IF NOT EXISTS bots (
                    id BIGINT PRIMARY KEY,
                    name VARCHAR(32) NOT NULL UNIQUE,
                    record TEXT NOT NULL,
                    updated_at BIGINT NOT NULL
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS bot_settings (
                    id INT PRIMARY KEY,
                    master_enabled BOOLEAN NOT NULL,
                    max_active INT NOT NULL
                )
                """,
            )
        for (sql in statements) {
            connection.prepareStatement(sql.trimIndent()).use { it.execute() }
        }
    }

    private fun loadBots(connection: DatabaseConnection): List<BotRecord> =
        connection.prepareStatement("SELECT record FROM bots ORDER BY id").use { statement ->
            statement.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(mapper.readValue<BotRecord>(rs.getString(1)))
                    }
                }
            }
        }

    private fun loadSettings(connection: DatabaseConnection): BotGlobalSettings =
        connection.prepareStatement("SELECT master_enabled, max_active FROM bot_settings WHERE id = 1").use {
            it.executeQuery().use { rs ->
                if (rs.next()) BotGlobalSettings(rs.getBoolean(1), rs.getInt(2)) else BotGlobalSettings()
            }
        }

    private fun upsertBot(connection: DatabaseConnection, bot: BotRecord, json: String) {
        val sql =
            """
            INSERT INTO bots (id, name, record, updated_at) VALUES (?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name, record = EXCLUDED.record, updated_at = EXCLUDED.updated_at
            """
                .trimIndent()
        connection.prepareStatement(sql).use {
            it.setLong(1, bot.id)
            it.setString(2, bot.name)
            it.setString(3, json)
            it.setLong(4, bot.updatedAt)
            it.executeUpdate()
        }
    }

    private fun upsertSettings(connection: DatabaseConnection, settings: BotGlobalSettings) {
        val sql =
            """
            INSERT INTO bot_settings (id, master_enabled, max_active) VALUES (1, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                master_enabled = EXCLUDED.master_enabled, max_active = EXCLUDED.max_active
            """
                .trimIndent()
        connection.prepareStatement(sql).use {
            it.setBoolean(1, settings.masterEnabled)
            it.setInt(2, settings.maxActiveBots)
            it.executeUpdate()
        }
    }
}
