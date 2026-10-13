package org.rsmod.content.other.transmog

import dev.openrune.ServerCacheManager
import dev.openrune.types.ItemServerType
import dev.openrune.util.Wearpos

internal object TransmogIndex {
    private class Entry(val name: String, val slot: Int, val item: ItemServerType)

    class Result(val items: List<ItemServerType>, val total: Int)

    @Volatile private var entries: List<Entry> = emptyList()

    @Volatile private var slots: Map<Int, Int> = emptyMap()

    fun build() {
        val built =
            ServerCacheManager.getItemTypes()
                .mapNotNull { item ->
                    val slot = Wearpos[item.wearpos1]?.slot ?: return@mapNotNull null
                    if (!isTransmogSlot(slot) || !wearable(item)) {
                        return@mapNotNull null
                    }
                    Entry(item.name.lowercase(), slot, item)
                }
                .sortedWith(compareBy({ it.name }, { it.item.id }))
        entries = built
        slots = built.associate { it.item.id to it.slot }
    }

    fun slotOf(obj: Int): Int? = slots[obj]

    fun search(slot: Int?, query: String, limit: Int): Result {
        val exact = ArrayList<ItemServerType>()
        val prefix = ArrayList<ItemServerType>()
        val partial = ArrayList<ItemServerType>()
        for (entry in entries) {
            if (slot != null && entry.slot != slot) {
                continue
            }
            when {
                query.isEmpty() -> partial
                entry.name == query -> exact
                entry.name.startsWith(query) -> prefix
                entry.name.contains(query) -> partial
                else -> null
            }?.add(entry.item)
        }
        val total = exact.size + prefix.size + partial.size
        return Result((exact + prefix + partial).take(limit), total)
    }

    private fun wearable(item: ItemServerType): Boolean =
        item.isEquipable &&
            !item.isCert &&
            !item.isPlaceholder &&
            !item.isDummyItem &&
            item.name.isNotBlank() &&
            !item.name.equals("null", ignoreCase = true)
}
