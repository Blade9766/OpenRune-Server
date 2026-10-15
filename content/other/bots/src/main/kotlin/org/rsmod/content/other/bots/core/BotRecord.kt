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
    /** Non-zero permanent varps (quest stages, slayer task) by varp id. */
    val vars: Map<Int, Int> = emptyMap(),
)

data class BotStats(
    val gathered: Long = 0,
    val deposited: Long = 0,
    val listed: Long = 0,
    val sold: Long = 0,
    val coinsEarned: Long = 0,
)

/**
 * A listing whose items have left the bot's inventory but whose offer is not yet known to be on
 * disk. Written before the offer is placed, cleared once the exchange has saved it.
 */
data class PendingListing(
    val slot: Int,
    val itemId: Int,
    val quantity: Int,
    val price: Int,
    val placed: Boolean = false,
    val placedAtSave: Long = 0,
)

/**
 * A collection already credited to the bot's bank but not yet known to be taken out of the
 * exchange on disk. The targets are the offer's ever-increasing collected totals.
 */
data class PendingCollect(
    val slot: Int,
    val createdAt: Long,
    val coinsTarget: Long,
    val itemsTarget: Int,
    val taken: Boolean = false,
    val takenAtSave: Long = 0,
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
    val saleRetries: Int = 0,
    val salePausedUntil: Long = 0,
    val errorRecoveries: Int = 0,
    val gatherWindowStart: Long = 0,
    val gatherWindowCount: Int = 0,
    val pendingListing: PendingListing? = null,
    val pendingCollects: List<PendingCollect> = emptyList(),
    /** Completed count already credited to [stats] per offer, keyed by its creation time. */
    val salesCounted: Map<Long, Int> = emptyMap(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    /** The exchange owner id: bots use negative account ids, which no real account has. */
    val ownerId: Int
        get() = -id.toInt()

    val hasPendingExchangeWork: Boolean
        get() = pendingListing != null || pendingCollects.isNotEmpty()
}
