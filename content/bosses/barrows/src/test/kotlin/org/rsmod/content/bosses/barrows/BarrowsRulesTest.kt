package org.rsmod.content.bosses.barrows

import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BarrowsRulesTest {
    @Test
    fun `every layout opens one chest door and keeps the outer rooms joined`() {
        val random = Random(7)
        repeat(2_000) {
            val locked = TunnelLayout.generate(random)
            val open = BarrowsDoorway.entries.toSet() - locked
            assertEquals(1, open.count { it.entersCentre }, "open chest doors in $open")
            assertTrue(TunnelLayout.outerRoomsConnected(open), "outer rooms split by $locked")
        }
    }

    @Test
    fun `layouts survive the varbit round trip`() {
        val random = Random(13)
        repeat(500) {
            val locked = TunnelLayout.generate(random)
            val mask = TunnelLayout.toMask(locked)
            assertTrue(mask in 1 until (1 shl BarrowsDoorway.entries.size))
            assertEquals(locked, TunnelLayout.fromMask(mask))
        }
    }

    @Test
    fun `doorways into the chest room are the four around it`() {
        val centre = BarrowsDoorway.entries.filter { it.entersCentre }
        val expected =
            listOf(
                BarrowsDoorway.NORTH_TO_CENTER,
                BarrowsDoorway.WEST_TO_CENTER,
                BarrowsDoorway.EAST_TO_CENTER,
                BarrowsDoorway.SOUTH_TO_CENTER,
            )
        assertEquals(expected, centre)
    }

    @Test
    fun `puzzle keeps the answer in the stored slot`() {
        for (type in BarrowsPuzzleType.entries) {
            for (slot in 0 until BarrowsPuzzle.SLOTS) {
                val puzzle = BarrowsPuzzle(type, slot)
                assertEquals(type.answer, puzzle.options[slot])
                assertEquals(type.sequence.first() - 3, puzzle.options[slot])
                assertEquals(type.options.toSet(), puzzle.options.toSet())
            }
        }
    }
}
