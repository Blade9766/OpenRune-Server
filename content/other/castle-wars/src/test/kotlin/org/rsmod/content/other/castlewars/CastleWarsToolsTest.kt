package org.rsmod.content.other.castlewars

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CastleWarsToolsTest {
    private val groups = listOf(listOf(TINDERBOX), listOf(PICKAXE), listOf(BUCKET, WATER))

    @Test
    fun `tools the player brought in are kept`() {
        val inv = listOf(TINDERBOX, PICKAXE, null, OTHER)
        val worn = listOf<Int?>(null, null)
        val brought = CastleWarsTools.count(listOf(inv, worn), groups)

        assertEquals(listOf(1, 1, 0), brought)
        assertEquals(emptyList<Pair<Int, Int>>(), CastleWarsTools.excess(listOf(inv, worn), groups, brought))
    }

    @Test
    fun `only the handed out tools are taken back`() {
        val brought = listOf(1, 0, 1)
        val inv = listOf(TINDERBOX, TINDERBOX, WATER, BUCKET, OTHER)
        val worn = listOf<Int?>(null, PICKAXE)

        val excess = CastleWarsTools.excess(listOf(inv, worn), groups, brought)

        assertEquals(listOf(0 to 0, 1 to 1, 0 to 3), excess)
    }

    @Test
    fun `a used bucket of water still counts as the player's own bucket`() {
        val brought = listOf(0, 0, 1)
        val inv = listOf<Int?>(BUCKET)

        assertEquals(emptyList<Pair<Int, Int>>(), CastleWarsTools.excess(listOf(inv), groups, brought))
    }

    private companion object {
        const val TINDERBOX = 1
        const val PICKAXE = 2
        const val BUCKET = 3
        const val WATER = 4
        const val OTHER = 5
    }
}
