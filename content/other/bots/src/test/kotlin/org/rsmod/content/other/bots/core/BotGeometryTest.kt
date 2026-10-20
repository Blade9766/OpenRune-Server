package org.rsmod.content.other.bots.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BotGeometryTest {
    @Test
    fun `a door beside the way is near, one off to the side or behind is not`() {
        assertEquals(0, BotGeometry.distanceToSegment(3205, 3200, 3200, 3200, 3210, 3200))
        assertEquals(2, BotGeometry.distanceToSegment(3205, 3202, 3200, 3200, 3210, 3200))
        assertEquals(8, BotGeometry.distanceToSegment(3205, 3208, 3200, 3200, 3210, 3200))
        assertEquals(5, BotGeometry.distanceToSegment(3195, 3200, 3200, 3200, 3210, 3200))
    }

    @Test
    fun `diagonal lines and a zero-length line`() {
        assertEquals(0, BotGeometry.distanceToSegment(3203, 3203, 3200, 3200, 3210, 3210))
        assertEquals(1, BotGeometry.distanceToSegment(3203, 3204, 3200, 3200, 3210, 3210))
        assertEquals(3, BotGeometry.distanceToSegment(3203, 3200, 3200, 3200, 3200, 3200))
    }
}
