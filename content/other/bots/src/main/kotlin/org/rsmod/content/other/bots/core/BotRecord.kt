package org.rsmod.content.other.bots.core

data class SavedObj(val slot: Int, val id: Int, val count: Int, val vars: Int = 0)

data class SavedCoords(val x: Int, val z: Int, val level: Int = 0)

/** Everything about the bot's character that must survive a restart. */
data class BotSave(
    val coords: SavedCoords? = null,
    val inv: List<SavedObj> = emptyList(),
    val worn: List<SavedObj> = emptyList(),
    val bank: List<SavedObj> = emptyList(),
    /** Fine xp per stat, keyed by the stat's gameval name. */
    val xp: Map<String, Int> = emptyMap(),
)

data class BotStats(
    val gathered: Long = 0,
    val deposited: Long = 0,
    val listed: Long = 0,
    val sold: Long = 0,
    val coinsEarned: Long = 0,
)

data class BotRecord(
    val id: Long,
    val name: String,
    val config: BotConfig,
    val enabled: Boolean = false,
    val state: BotState = BotState.Disabled,
    val activity: String = "",
    val lastError: String? = null,
    val save: BotSave = BotSave(),
    val stats: BotStats = BotStats(),
    /** Incremented with every successful listing; part of the listing's idempotency key. */
    val saleSeq: Long = 0,
    val saleRetries: Int = 0,
    val salePausedUntil: Long = 0,
    val errorRecoveries: Int = 0,
    val gatherWindowStart: Long = 0,
    val gatherWindowCount: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    val listingRef: String
        get() = "bot:$id:sale:$saleSeq"
}
