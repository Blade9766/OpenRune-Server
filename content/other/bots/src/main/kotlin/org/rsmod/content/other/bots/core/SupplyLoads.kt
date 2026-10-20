package org.rsmod.content.other.bots.core

/** One input of a recipe used up in a fixed amount per product, such as the 2 coal per steel bar. */
data class SupplyPart(val itemId: Int, val count: Int)

/**
 * Plans withdrawals of supplies that only work together (an ore and its coal). The bot always takes
 * whole sets, so a trip never carries ore without the coal to smelt it. Parts are non-stackable, one
 * inventory slot per item.
 */
object SupplyLoads {
    /** How many of each part to withdraw: as many whole sets as [free] slots and [banked] allow. */
    fun plan(parts: List<SupplyPart>, free: Int, banked: (Int) -> Long): Map<Int, Int> {
        if (parts.isEmpty() || parts.any { it.count <= 0 } || free <= 0) {
            return emptyMap()
        }
        val slotsPerSet = parts.sumOf { it.count }
        val sets = parts.minOf { banked(it.itemId).coerceAtLeast(0) / it.count }.coerceAtMost((free / slotsPerSet).toLong())
        if (sets <= 0) {
            return emptyMap()
        }
        return parts.associate { it.itemId to (it.count * sets).toInt() }
    }

    /** Parts the bank holds too few of for one set, scarcest first: the ones worth buying. */
    fun shortfall(parts: List<SupplyPart>, banked: (Int) -> Long): List<Int> =
        parts
            .filter { banked(it.itemId) < it.count }
            .sortedBy { banked(it.itemId).toDouble() / it.count }
            .map { it.itemId }
}
