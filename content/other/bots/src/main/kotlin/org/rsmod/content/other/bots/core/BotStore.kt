package org.rsmod.content.other.bots.core

import org.rsmod.api.exchange.ExchangeMutation
import org.rsmod.api.exchange.persistence.InMemoryExchangeStore

/** One atomic write: bot rows and the exchange rows the same action changed. */
data class BotWrite(
    val bots: List<BotRecord> = emptyList(),
    val deleted: List<Long> = emptyList(),
    val settings: BotGlobalSettings? = null,
    val exchange: ExchangeMutation = ExchangeMutation.EMPTY,
)

data class BotStoreSnapshot(val bots: List<BotRecord>, val settings: BotGlobalSettings)

interface BotStore {
    val healthy: Boolean

    fun load(): BotStoreSnapshot

    fun write(write: BotWrite)
}

/** Mirrors the database: only what was written survives a simulated restart. */
class InMemoryBotStore(val exchange: InMemoryExchangeStore = InMemoryExchangeStore()) : BotStore {
    val bots: MutableMap<Long, BotRecord> = LinkedHashMap()
    var settings: BotGlobalSettings = BotGlobalSettings()
    val writes: MutableList<BotWrite> = ArrayList()

    override var healthy: Boolean = true

    override fun load(): BotStoreSnapshot = BotStoreSnapshot(bots.values.toList(), settings)

    override fun write(write: BotWrite) {
        writes += write
        for (bot in write.bots) {
            bots[bot.id] = bot
        }
        for (id in write.deleted) {
            bots.remove(id)
        }
        write.settings?.let { settings = it }
        exchange.apply(write.exchange)
    }
}
