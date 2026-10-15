package org.rsmod.content.other.bots.core

data class BotWrite(
    val bots: List<BotRecord> = emptyList(),
    val deleted: List<Long> = emptyList(),
    val settings: BotGlobalSettings? = null,
)

data class BotStoreSnapshot(val bots: List<BotRecord>, val settings: BotGlobalSettings)

/** Persists bots. [write] is synchronous: when it returns the data is durable, or it throws. */
interface BotStore {
    fun load(): BotStoreSnapshot

    fun write(write: BotWrite)
}

/** Mirrors a file on disk: only what was written survives a simulated restart. */
class InMemoryBotStore : BotStore {
    val bots: MutableMap<Long, BotRecord> = LinkedHashMap()
    var settings: BotGlobalSettings = BotGlobalSettings()
    val writes: MutableList<BotWrite> = ArrayList()
    var failing: Boolean = false

    override fun load(): BotStoreSnapshot = BotStoreSnapshot(bots.values.toList(), settings)

    override fun write(write: BotWrite) {
        if (failing) {
            throw java.io.IOException("simulated write failure")
        }
        writes += write
        for (bot in write.bots) {
            bots[bot.id] = bot
        }
        for (id in write.deleted) {
            bots.remove(id)
        }
        write.settings?.let { settings = it }
    }
}
