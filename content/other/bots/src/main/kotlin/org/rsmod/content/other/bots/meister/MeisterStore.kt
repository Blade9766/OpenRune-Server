package org.rsmod.content.other.bots.meister

import org.rsmod.content.other.bots.core.PendingCollect
import org.rsmod.content.other.bots.core.PendingListing
import org.rsmod.content.other.bots.core.SavedCoords

data class MeisterStats(
    val offersPlaced: Long = 0,
    val offersCancelled: Long = 0,
    val itemsBought: Long = 0,
    val coinsSpent: Long = 0,
    val itemsSold: Long = 0,
    val coinsEarned: Long = 0,
    val taxPaid: Long = 0,
)

/**
 * Everything about Master Meister that survives a restart. [initialized] is set in the same write
 * that stores his starting allocation, so a crash before that write leaves nothing to duplicate,
 * and once it is on disk the allocation is never applied again.
 */
data class MeisterSave(
    val initialized: Boolean = false,
    val initializedAt: Long = 0,
    val cash: Long = 0,
    /** Item id to count; zero counts are dropped. */
    val holdings: Map<Int, Int> = emptyMap(),
    /** Ids and counts of the starting allocation, for [MeisterSettings.replenishItems]. */
    val allocation: Map<Int, Int> = emptyMap(),
    val startingCash: Long = 0,
    val settings: MeisterSettings = MeisterSettings(),
    val pendingListing: PendingListing? = null,
    val pendingCollects: List<PendingCollect> = emptyList(),
    /** Fills already counted per offer, keyed by the offer's creation time. */
    val fillsCounted: Map<Long, CountedFill> = emptyMap(),
    val itemCooldowns: Map<Int, Long> = emptyMap(),
    val lastBought: Map<Int, Long> = emptyMap(),
    val lastSold: Map<Int, Long> = emptyMap(),
    val dayStart: Long = 0,
    val spentToday: Long = 0,
    val spentTodayByItem: Map<Int, Long> = emptyMap(),
    val lastReplenishAt: Long = 0,
    val stats: MeisterStats = MeisterStats(),
    val coords: SavedCoords? = null,
)

/** Persists Master Meister. [write] is synchronous: when it returns the data is durable, or it throws. */
interface MeisterStore {
    fun load(): MeisterSave?

    fun write(save: MeisterSave)
}

class InMemoryMeisterStore : MeisterStore {
    var saved: MeisterSave? = null
    var writes: Int = 0
    var failing: Boolean = false

    override fun load(): MeisterSave? = saved

    override fun write(save: MeisterSave) {
        if (failing) {
            throw java.io.IOException("simulated write failure")
        }
        writes++
        saved = save
    }
}
