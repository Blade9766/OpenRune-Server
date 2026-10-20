package org.rsmod.content.other.bots.meister

/**
 * Applies one administrator's draft onto rules someone else may have saved meanwhile: every rule
 * the draft changed since [base] (added, edited or removed) is applied to [live]. Null when both
 * changed the same item's rule differently, so neither edit is silently lost.
 */
object MeisterRuleMerge {
    fun merge(base: MeisterRules, mine: MeisterRules, live: MeisterRules): MeisterRules? {
        val buy = mergeSide(base.buy, mine.buy, live.buy) { it.itemId } ?: return null
        val sell = mergeSide(base.sell, mine.sell, live.sell) { it.itemId } ?: return null
        return MeisterRules(buy, sell)
    }

    private fun <T> mergeSide(base: List<T>, mine: List<T>, live: List<T>, key: (T) -> Int): List<T>? {
        val baseBy = base.associateBy(key)
        val mineBy = mine.associateBy(key)
        val liveBy = live.associateBy(key)
        val result = LinkedHashMap<Int, T>()
        live.forEach { result[key(it)] = it }
        for (id in baseBy.keys + mineBy.keys) {
            val ours = mineBy[id]
            val before = baseBy[id]
            if (ours == before) {
                continue
            }
            val theirs = liveBy[id]
            if (theirs != before && theirs != ours) {
                return null
            }
            if (ours == null) result.remove(id) else result[id] = ours
        }
        return result.values.toList()
    }
}
