package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.areas.wilderness.locs.WildernessObelisks
import org.rsmod.content.skills.construction.data.RoomType
import org.rsmod.map.CoordGrid

@ResourceLock("ServerCacheManager")
class HouseTravelTest {
    private fun ops(loc: String): List<String?> {
        val type = ServerCacheManager.getObject(loc.asRSCM(RSCMType.LOC))!!
        return (0..4).map { type.actions.getOpOrNull(it) }
    }

    @Test
    fun `the teleport space's spirit trees and obelisk have the ops the scripts answer`() {
        val built = RoomType.SUPERIOR_GARDEN.hotspot("teleport")!!.options.map { it.built.single() }
        assertEquals(listOf("loc.poh_spirit_tree", "loc.poh_wilderness_obelisk", "loc.poh_fairy_ring", "loc.poh_spirit_ring"), built)
        assertEquals(listOf("Travel", null, "Last-destination"), ops("loc.poh_spirit_tree").take(3))
        assertEquals("Tree", ops("loc.poh_spirit_ring")[0])
        assertEquals(listOf("Activate", "Teleport to destination", "Set destination"), ops("loc.poh_wilderness_obelisk").take(3))
    }

    @Test
    fun `every wilderness obelisk answers the same ops and the sites are told apart`() {
        for (stone in 0..5) {
            assertEquals(listOf("Activate", "Teleport to Destination", "Set Destination"), ops("loc.wilderness_portal_stone_$stone").take(3))
        }
        assertEquals(WildernessObelisks.Site.FEROX, WildernessObelisks.Site.nearest(CoordGrid(3158, 3622, 0)))
        assertEquals(WildernessObelisks.Site.ROGUES_CASTLE, WildernessObelisks.Site.nearest(CoordGrid(3305, 3918, 0)))
        listOf("varbit.teleblock_cycles", "varbit.wilderness_diary_hard_complete").forEach { it.asRSCM(RSCMType.VARBIT) }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadCache() {
            ServerCacheManager.init(240).close()
        }
    }
}
