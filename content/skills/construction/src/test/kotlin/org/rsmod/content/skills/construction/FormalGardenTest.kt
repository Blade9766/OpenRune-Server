package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.skills.construction.data.Floor
import org.rsmod.content.skills.construction.data.RoomType
import org.rsmod.content.skills.construction.house.HouseState
import org.rsmod.content.skills.construction.house.Room

@ResourceLock("ServerCacheManager")
class FormalGardenTest {
    private val room = RoomType.FORMAL_GARDEN

    @Test
    fun `the formal garden is an outdoor ground floor room with four doors`() {
        assertEquals(21, room.roomTypeId)
        assertEquals(setOf(Floor.GROUND), room.floors)
        assertEquals(listOf(0, 1, 2, 3), (0..3).filter { room.hasDoor(it, 0) })
    }

    @Test
    fun `every hotspot and built piece exists, and planting trains farming`() {
        for (group in room.hotspots) {
            group.locs.forEach { it.asRSCM(RSCMType.LOC) }
            group.options.flatMap { it.built }.forEach { it.asRSCM(RSCMType.LOC) }
            group.options.flatMap { it.materials }.forEach { it.obj.asRSCM(RSCMType.OBJ) }
        }
        for (key in listOf("big_plant_1", "big_plant_2", "small_plant_1", "small_plant_2", "hedging")) {
            assertTrue(room.hotspot(key)!!.options.all { it.skill == "stat.farming" && it.skillXp == it.xp }, key)
        }
        val hedge = room.hotspot("hedging")!!
        assertEquals(listOf("loc.poh_hedgecorner3", "loc.poh_hedgemiddle3", "loc.poh_hedgeend3"), hedge.options[2].built)
    }

    @Test
    fun `an exit portal in a formal garden is the house entrance`() {
        val garden = Room(room, rotation = 0)
        garden.furniture["centrepiece"] = 0
        assertTrue(HouseState().isEntrance(garden))
        assertEquals("loc.poh_exit_portal", room.hotspot("centrepiece")!!.options[0].built.single())
        assertTrue(ServerCacheManager.getObject("loc.poh_posh_garden_1".asRSCM(RSCMType.LOC)) != null)
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadCache() {
            ServerCacheManager.init(240).close()
        }
    }
}
