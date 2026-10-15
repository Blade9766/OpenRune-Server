package org.rsmod.api.death

import dev.openrune.types.util.UncheckedType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.game.inv.InvObj

@OptIn(UncheckedType::class)
class SplitKeptTest {
    @Test
    fun `a stack keeps one unit per kept slot and loses the rest`() {
        val darts = InvObj(DARTS, 100)
        val whip = InvObj(WHIP, 1)

        val (kept, lost) = splitKept(listOf(darts, whip), keepCount = 3)

        assertEquals(listOf(InvObj(DARTS, 3)), kept)
        assertEquals(listOf(InvObj(DARTS, 97), whip), lost)
    }

    @Test
    fun `slots left over after a small stack go to the next items`() {
        val (kept, lost) =
            splitKept(listOf(InvObj(DARTS, 2), InvObj(WHIP, 1), InvObj(BOOTS, 1)), keepCount = 3)

        assertEquals(listOf(InvObj(DARTS, 2), InvObj(WHIP, 1)), kept)
        assertEquals(listOf(InvObj(BOOTS, 1)), lost)
    }

    @Test
    fun `no kept slots loses everything`() {
        val objs = listOf(InvObj(DARTS, 5), InvObj(WHIP, 1))

        val (kept, lost) = splitKept(objs, keepCount = 0)

        assertEquals(emptyList<InvObj>(), kept)
        assertEquals(objs, lost)
    }

    private companion object {
        const val DARTS = 11230
        const val WHIP = 4151
        const val BOOTS = 11840
    }
}
