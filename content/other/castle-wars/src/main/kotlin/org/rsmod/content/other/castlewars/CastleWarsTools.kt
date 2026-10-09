package org.rsmod.content.other.castlewars

/**
 * The supply tables hand out ordinary tinderboxes, pickaxes and buckets, which players may also
 * bring in themselves. Each group is counted on entry so that leaving only takes back the ones the
 * game handed out.
 */
internal object CastleWarsTools {
    val GROUPS: List<List<String>> =
        listOf(
            listOf("obj.tinderbox"),
            listOf("obj.bronze_pickaxe"),
            listOf("obj.bucket_empty", "obj.bucket_water"),
        )

    fun count(inventories: List<List<Int?>>, groups: List<List<Int>>): List<Int> =
        groups.map { group -> inventories.sumOf { slots -> slots.count { it != null && it in group } } }

    /**
     * Returns `(inventory index, slot)` pairs to clear so every group is back down to [brought].
     * Within a group, ids listed first are taken first.
     */
    fun excess(
        inventories: List<List<Int?>>,
        groups: List<List<Int>>,
        brought: List<Int>,
    ): List<Pair<Int, Int>> {
        val remove = ArrayList<Pair<Int, Int>>()
        val held = count(inventories, groups)
        for ((index, group) in groups.withIndex()) {
            var surplus = held[index] - brought.getOrElse(index) { 0 }
            for (id in group) {
                for ((invIndex, slots) in inventories.withIndex()) {
                    for ((slot, obj) in slots.withIndex()) {
                        if (surplus <= 0) break
                        if (obj == id) {
                            remove += invIndex to slot
                            surplus--
                        }
                    }
                }
            }
        }
        return remove
    }
}
