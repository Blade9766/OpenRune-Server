package org.rsmod.content.quest.area.falador.recruitmentdrive

import org.rsmod.api.random.GameRandom

/**
 * Every order of five distinct rooms that includes Sir Kuam Ferentse's combat room: the combat room
 * in any of the five places and four of the six other rooms around it, 1,800 orders in all. An
 * attempt is saved as its index in [all], which fits the 11 bits of `varbit.rd_order`.
 */
object RoomOrder {
    const val SIZE = 5

    val all: List<List<TestRoom>> by lazy { build() }

    fun roll(random: GameRandom): List<TestRoom> = all[random.of(all.size)]

    fun encode(order: List<TestRoom>): Int {
        val index = all.indexOf(order)
        require(index >= 0) { "Not a valid room order: $order" }
        return index
    }

    fun decode(index: Int): List<TestRoom>? = all.getOrNull(index)

    private fun build(): List<List<TestRoom>> {
        val others = TestRoom.entries - TestRoom.COMBAT
        val orders = mutableListOf<List<TestRoom>>()
        fun extend(prefix: List<TestRoom>) {
            if (prefix.size == SIZE) {
                if (TestRoom.COMBAT in prefix) orders += prefix
                return
            }
            for (room in TestRoom.entries) {
                if (room in prefix) continue
                val remaining = SIZE - prefix.size - 1
                val combatStillPlaceable = TestRoom.COMBAT in prefix || room == TestRoom.COMBAT || remaining > 0
                if (combatStillPlaceable) extend(prefix + room)
            }
        }
        extend(emptyList())
        check(orders.size == SIZE * others.size * (others.size - 1) * (others.size - 2) * (others.size - 3))
        return orders
    }
}
