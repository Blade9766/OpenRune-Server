package org.rsmod.content.quest.area.digsite

import org.rsmod.api.random.GameRandom

/** One slot of a dig's table of finds. */
data class DigFind(val obj: String, val count: IntRange)

/**
 * A weighted table of what a dig, a specimen tray or a pan of river mud turns up. Empty slots are
 * part of the table, so a roll can come back with nothing at all.
 */
class DigFindTable(private val slots: List<Pair<Int, DigFind?>>) {
    private val totalWeight = slots.sumOf { it.first }

    fun roll(random: GameRandom): DigFind? {
        if (totalWeight <= 0) {
            return null
        }
        var pick = random.of(maxExclusive = totalWeight)
        for ((weight, find) in slots) {
            pick -= weight
            if (pick < 0) {
                return find
            }
        }
        return null
    }
}

class DigFindBuilder {
    private val slots = mutableListOf<Pair<Int, DigFind?>>()

    fun nothing(weight: Int) {
        slots += weight to null
    }

    fun item(weight: Int, obj: String, count: IntRange = 1..1) {
        slots += weight to DigFind(obj, count)
    }

    fun build(): DigFindTable = DigFindTable(slots.toList())
}

fun digFinds(block: DigFindBuilder.() -> Unit): DigFindTable = DigFindBuilder().apply(block).build()
