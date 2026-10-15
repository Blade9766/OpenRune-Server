package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.skills.construction.data.Chapel
import org.rsmod.content.skills.construction.data.Chapel.God
import org.rsmod.content.skills.construction.data.RoomType

@ResourceLock("ServerCacheManager")
class ChapelTest {
    private val altars = RoomType.CHAPEL.hotspot("altar")!!.options.map { it.built.single() }
    private val icons = RoomType.CHAPEL.hotspot("icon")!!.options
    private val lamps = RoomType.CHAPEL.hotspot("lamp")!!.options.map { it.built.single() }

    @Test
    fun `altar multipliers match the wiki table`() {
        val expected =
            listOf(
                listOf(1.0, 1.5, 2.0),
                listOf(1.1, 1.6, 2.1),
                listOf(1.25, 1.75, 2.25),
                listOf(1.5, 2.0, 2.5),
                listOf(1.75, 2.25, 2.75),
                listOf(2.0, 2.5, 3.0),
                listOf(2.5, 3.0, 3.5),
            )
        for ((index, altar) in altars.withIndex()) {
            for (burners in 0..2) {
                assertEquals(expected[index][burners], Chapel.multiplier(altar, burners)!!, 1e-9)
            }
        }
    }

    @Test
    fun `the furniture table builds the saradomin altar of every tier`() {
        assertEquals((1..Chapel.TIERS).map { Chapel.altar(God.SARADOMIN, it) }, altars)
    }

    @Test
    fun `an altar takes the god of the icon built beside it`() {
        val gilded = altars.last()
        val gods =
            icons.indices.map { icon ->
                Chapel.dedicate(gilded, icon).removePrefix("loc.poh_altar_").removeSuffix("_7")
            }
        assertEquals(
            listOf("saradomin", "zamorak", "guthix", "saradomin", "zamorak", "guthix", "saradomin"),
            gods,
        )
        assertEquals(gilded, Chapel.dedicate(gilded, null))
        assertEquals("loc.poh_torch_5", Chapel.dedicate("loc.poh_torch_5", 1))
    }

    @Test
    fun `a dedicated altar maps back to its furniture entry`() {
        for (altar in Chapel.ALTARS.keys) {
            assertTrue(Chapel.undedicated(altar) in altars, altar)
        }
    }

    @Test
    fun `both buildable incense burners can be lit`() {
        val burners = lamps.filter { it.startsWith("loc.poh_torch_") && it.last() in "56" }
        assertEquals(listOf("loc.poh_torch_5", "loc.poh_torch_6"), burners)
        assertTrue(Chapel.BURNERS.keys.containsAll(burners))
    }

    @Test
    fun `every altar and burner exists in the cache`() {
        val names = Chapel.ALTARS.keys + Chapel.BURNERS.keys + Chapel.LIT_BURNERS.keys
        for (name in names) {
            name.asRSCM(RSCMType.LOC)
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadCache() {
            ServerCacheManager.init(240).close()
        }
    }
}
