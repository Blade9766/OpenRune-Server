package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.skills.construction.data.Topiary

@ResourceLock("ServerCacheManager")
class TopiaryTest {
    @Test
    fun `every shape's loc can be clipped and its kill count exists`() {
        for (loc in Topiary.LOCS) {
            assertEquals("Clip", ServerCacheManager.getObject(loc.asRSCM(RSCMType.LOC))!!.actions.getOpOrNull(3), loc)
        }
        Topiary.Shape.entries.forEach { it.kills.asRSCM(RSCMType.VARP) }
        Topiary.VARP.asRSCM(RSCMType.VARP)
        listOf("obj.secateurs", "obj.fairy_enchanted_secateurs").forEach { it.asRSCM(RSCMType.OBJ) }
        "seq.farming_plant_cure".asRSCM(RSCMType.SEQ)
    }

    @Test
    fun `the bush shows the clipped shape`() {
        assertEquals(Topiary.BUSH, Topiary.shown(Topiary.BUSH, 0))
        assertEquals("loc.poh_topiary_vorkath", Topiary.shown(Topiary.BUSH, Topiary.Shape.VORKATH.ordinal + 1))
        assertEquals(null, Topiary.shown("loc.poh_pool_restoration", 3))
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadCache() {
            ServerCacheManager.init(240).close()
        }
    }
}
