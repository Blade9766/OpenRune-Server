package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.skills.construction.data.Floor
import org.rsmod.content.skills.construction.data.RoomType

@ResourceLock("ServerCacheManager")
class SuperiorGardenTest {
    private val room = RoomType.SUPERIOR_GARDEN

    @Test
    fun `the superior garden is an outdoor ground floor room with four doors`() {
        assertEquals(setOf(Floor.GROUND), room.floors)
        assertEquals(26, room.roomTypeId)
        assertEquals(listOf(0, 1, 2, 3), (0..3).filter { room.hasDoor(it, 0) })
    }

    @Test
    fun `every built loc exists and each pool is built over the last`() {
        for (group in room.hotspots) {
            group.locs.forEach { it.asRSCM(RSCMType.LOC) }
            group.options.flatMap { it.built }.forEach { it.asRSCM(RSCMType.LOC) }
        }
        val pools = room.hotspot("pool")!!.options
        assertEquals(listOf(false, true, true, true, true), pools.map { it.upgrade })
        for (pool in pools) {
            val type = ServerCacheManager.getObject(pool.built.single().asRSCM(RSCMType.LOC))!!
            assertEquals("Drink", type.actions.getOpOrNull(0), pool.label)
        }
        "seq.poh_pool_drink".asRSCM(RSCMType.SEQ)
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadCache() {
            ServerCacheManager.init(240).close()
        }
    }
}
