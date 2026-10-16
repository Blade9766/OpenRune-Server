package org.rsmod.content.other.bots.core

import kotlin.random.Random

object BotTargeting {
    /** Extra tiles a target costs for each other player already working it. */
    const val CROWD_COST: Int = 10

    /**
     * The candidate with the lowest walking distance plus [CROWD_COST] per player already on it.
     * Equal costs are broken at random, so bots standing together spread out instead of all
     * clicking the same tree.
     */
    fun <T> pick(candidates: List<T>, random: Random, distance: (T) -> Int, crowd: (T) -> Int): T? =
        candidates.shuffled(random).minByOrNull { distance(it) + crowd(it) * CROWD_COST }
}
