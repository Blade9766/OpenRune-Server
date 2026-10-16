package org.rsmod.content.other.bots.meister

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * One item Master Meister may hold and trade. [allocation] is how many he is given when he is
 * first set up. [sensitive] marks rare or economy-sensitive items, which the trading switches can
 * keep him from buying or selling; [noBuy] / [noSell] are hard per-item exclusions.
 */
data class MeisterItem(
    val id: Int,
    val name: String,
    val guidePrice: Int,
    val allocation: Int,
    val sensitive: Boolean = false,
    val noBuy: Boolean = false,
    val noSell: Boolean = false,
)

/** Every tradeable item he deals in; built from the exchange's item data and the cache. */
class MeisterCatalog(items: Collection<MeisterItem>) {
    private val byId: Map<Int, MeisterItem> = items.associateBy { it.id }

    val items: List<MeisterItem> = byId.values.sortedBy { it.id }

    val size: Int
        get() = byId.size

    operator fun get(id: Int): MeisterItem? = byId[id]

    fun name(id: Int): String = byId[id]?.name ?: "item $id"
}

/** How many of an item he starts with, by its guide price: the first tier at or above the price wins. */
data class AllocationTier(@JsonProperty("max_price") val maxPrice: Long = Long.MAX_VALUE, val count: Int = 1)

object MeisterAllocation {
    val DEFAULT_TIERS: List<AllocationTier> =
        listOf(
            AllocationTier(100, 2_000),
            AllocationTier(1_000, 1_000),
            AllocationTier(10_000, 250),
            AllocationTier(100_000, 50),
            AllocationTier(1_000_000, 10),
            AllocationTier(10_000_000, 3),
            AllocationTier(Long.MAX_VALUE, 1),
        )

    fun countFor(guidePrice: Int, tiers: List<AllocationTier>): Int {
        val tier = tiers.sortedBy { it.maxPrice }.firstOrNull { guidePrice <= it.maxPrice }
        return (tier?.count ?: 1).coerceAtLeast(0)
    }
}
