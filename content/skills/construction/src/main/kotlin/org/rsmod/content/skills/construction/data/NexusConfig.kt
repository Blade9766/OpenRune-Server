package org.rsmod.content.skills.construction.data

/**
 * The nexus configuration interface's working copy: its teleports by slot and its left-click
 * teleport, as the `_temp` varbits hold them while the interface is open.
 *
 * The client edits its own copy as things are dragged, by rules of its own; the server makes each
 * change here, by these, and sends the result back, so whatever the client did the two end up
 * agreeing. A removed teleport leaves its slot empty, as the client's does, and the next one added
 * fills the first empty slot. A left-click on a two-way teleport's second place is its id plus
 * [Nexus.ALTERNATE_OFFSET].
 */
class NexusConfig(val capacity: Int, slots: List<Int>, var leftClick: Int) {
    private val slots: IntArray = IntArray(SLOT_COUNT) { slots.getOrElse(it) { 0 } }

    /** Every slot's destination id, 0 for an empty one, in slot order. */
    val values: List<Int>
        get() = slots.toList()

    /** The teleports held, in slot order. */
    val held: List<Int>
        get() = slots.take(capacity).filter { it > 0 }

    fun valueAt(slot: Int): Int? = slots.getOrNull(slot - 1)?.takeIf { it > 0 && slot <= capacity }

    /** Puts [id] in the first empty slot; false when it is already held or every slot is full. */
    fun add(id: Int): Boolean {
        if (id in held) {
            return false
        }
        val free = (0 until capacity).firstOrNull { slots[it] == 0 } ?: return false
        slots[free] = id
        return true
    }

    /** Takes out the teleport in [slot], and the left-click with it if it went there. */
    fun remove(slot: Int) {
        val id = valueAt(slot) ?: return
        slots[slot - 1] = 0
        if (leftClick == id || leftClick == id + Nexus.ALTERNATE_OFFSET) {
            leftClick = 0
        }
    }

    fun swap(a: Int, b: Int) {
        if (a !in 1..capacity || b !in 1..capacity || a == b) {
            return
        }
        val held = slots[a - 1]
        slots[a - 1] = slots[b - 1]
        slots[b - 1] = held
    }

    fun leftClickSlot(slot: Int) {
        leftClick = valueAt(slot) ?: return
    }

    /** Points the left-click at the first or, for a two-way teleport, the second place. */
    fun choosePlace(second: Boolean, twoWay: Boolean) {
        val primary = if (leftClick > Nexus.ALTERNATE_OFFSET) leftClick - Nexus.ALTERNATE_OFFSET else leftClick
        if (primary <= 0) {
            return
        }
        leftClick = if (second && twoWay) primary + Nexus.ALTERNATE_OFFSET else primary
    }

    /** The teleports in this copy that [saved] does not have, which are what a save will cost. */
    fun addedSince(saved: NexusConfig): List<Int> = held.filter { it !in saved.held }

    fun sameAs(other: NexusConfig): Boolean = values == other.values && leftClick == other.leftClick

    companion object {
        /** The client keeps 45 slot varbits, though no nexus holds more than 41 teleports. */
        const val SLOT_COUNT: Int = 45
    }
}
