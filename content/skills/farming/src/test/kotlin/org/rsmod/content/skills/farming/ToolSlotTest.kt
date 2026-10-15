package org.rsmod.content.skills.farming

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.content.skills.farming.data.ToolSlot

class ToolSlotTest {
    @Test
    fun `every slot can hold its full capacity`() {
        for (slot in ToolSlot.entries) {
            assertTrue(
                slot.capacity <= slot.maxStorable,
                "${slot.name} holds ${slot.capacity} but its varbits only reach ${slot.maxStorable}",
            )
        }
    }

    @Test
    fun `counts survive the trip through their varbits`() {
        for (slot in ToolSlot.entries) {
            for (count in 0..slot.capacity) {
                assertEquals(count, slot.join(slot.split(count)), "${slot.name} lost $count")
            }
        }
    }

    @Test
    fun `no piece of a count overflows the varbit holding it`() {
        for (slot in ToolSlot.entries) {
            for (count in 0..slot.capacity) {
                val parts = slot.split(count)
                for (index in slot.varbits.indices) {
                    val width = slot.varbits[index].width
                    assertTrue(
                        parts[index] < (1 shl width),
                        "${slot.name} at $count writes ${parts[index]} into a $width bit varbit",
                    )
                }
            }
        }
    }

    /**
     * The client reads an empty bucket count as
     * `2^8 * extra2buckets + 2^5 * extrabuckets + buckets`, so the pieces have to land in that
     * order or a count past 31 comes back as something else entirely.
     */
    @Test
    fun `empty buckets split the way the client reassembles them`() {
        val slot = ToolSlot.BUCKET
        for (count in listOf(0, 1, 31, 32, 63, 255, 256, 511, 999, 1000)) {
            val (base, extra, extra2) = slot.split(count).toList()
            assertEquals(count, 256 * extra2 + 32 * extra + base, "buckets at $count")
        }
    }

    /**
     * Tools kept their original one-bit varbit when the cap was raised, so the low bit of the count
     * stays there and the rest moved to a second varbit.
     */
    @Test
    fun `tool counts keep their low bit in the original varbit`() {
        val slot = ToolSlot.RAKE
        for (count in listOf(0, 1, 2, 3, 63, 64, 99, 100)) {
            val (base, extra) = slot.split(count).toList()
            assertEquals(count, 2 * extra + base, "rakes at $count")
        }
    }

    @Test
    fun `a count beyond what the varbits hold is clamped rather than wrapped`() {
        for (slot in ToolSlot.entries) {
            val parts = slot.split(slot.maxStorable + 1)
            assertEquals(slot.maxStorable, slot.join(parts), "${slot.name} wrapped past its maximum")
        }
    }

    @Test
    fun `varbit widths are never zero`() {
        for (slot in ToolSlot.entries) {
            for (part in slot.varbits) {
                assertTrue(part.width > 0, "${slot.name} has a zero width piece: ${part.varbit}")
            }
        }
    }

    private operator fun List<Int>.component3(): Int = this[2]
}
